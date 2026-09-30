package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.FamilyMember;
import com.example.order.entity.MenuItem;
import com.example.order.entity.Order;
import com.example.order.entity.PushLog;
import com.example.order.entity.PushPending;
import com.example.order.entity.User;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.PushLogMapper;
import com.example.order.mapper.PushPendingMapper;
import com.example.order.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 微信订阅消息推送服务。
 *
 * <h3>微信的规则（决定了这里的代码结构）</h3>
 * <ul>
 *   <li><b>一次性订阅</b>：用户在小程序里点一次「允许」，服务端就只能推 <b>一条</b>；
 *       想再推必须让用户再点一次。所以真实可发条数（订阅额度）必须由客户端上报，见
 *       {@link #reportSubscribeGrant}。</li>
 *   <li><b>只能推给本人</b>：订阅授权是「用户 × 模板」维度的，主厨没授权就一条都发不出去。</li>
 *   <li><b>失败要能自愈</b>：额度用完是 43101，网络抖动是 5xx，两者处理方式不同——
 *       前者放弃，后者进重试队列。</li>
 * </ul>
 *
 * <h3>两种推送</h3>
 * <ul>
 *   <li>{@link #pushNewOrderToChef(Long)} —— 有人下单 → 通知该家庭的主厨。</li>
 *   <li>{@link #pushStatusChanged(Long)} —— 主厨确认/驳回/撤销 → 通知下单人。</li>
 * </ul>
 * 两者的入参都可以是 <b>订单 id 或单个菜单项 id</b>：传订单 id 时会把整单的菜名合并成一条，
 * 避免「一个订单 6 个菜发 6 条」把主厨的订阅额度一次烧光。
 */
@Slf4j
@Service
public class PushService {

    public static final String TYPE_NEW_ORDER = "NEW_ORDER";
    public static final String TYPE_STATUS_CHANGED = "STATUS_CHANGED";

    /** 微信模板字段长度上限：thing 20 字、phrase 5 字，这里取安全值 */
    private static final int MAX_TEXT_LEN = 20;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    @Autowired private UserMapper userMapper;
    @Autowired private MenuItemMapper menuItemMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PushLogMapper pushLogMapper;
    @Autowired private PushPendingMapper pushPendingMapper;
    @Autowired private FamilyMemberMapper familyMemberMapper;
    @Autowired private com.example.order.mapper.FamilyMapper familyMapper;
    @Autowired private WeChatTokenService tokenService;

    @Value("${wechat.template.new-order:}")
    private String templateNewOrder;

    @Value("${wechat.template.status-changed:}")
    private String templateStatusChanged;

    /** dry-run 开关：测试期间为 true，只写 t_push_log 不真发 */
    @Value("${push.dry-run:false}")
    private boolean pushDryRun;

    private final RestTemplate rt = new RestTemplate();

    /** 推送线程池：2 条足够（推送是低频操作），daemon 不阻塞 JVM 退出 */
    private final ExecutorService pushExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "push-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 事务提交后再异步推送。
     * <p><b>为什么必须这样：</b>调用点多数标了 {@code @Transactional}（CartService.checkout、
     * OrderService.confirmItem 等），Spring 的事务绑定在**当前线程**上。如果在方法体里直接
     * 起新线程去查刚 insert 的 t_menu_item，事务还没提交，另一个连接读到的是「记录不存在」——
     * 主厨收不到「点菜通知」就是这个原因。
     * <p>这里注册事务同步回调：有事务就在 afterCommit 里提交任务，没事务就直接异步执行。
     *
     * @param task 实际推送动作；异常在 worker 线程里被吞掉并记日志，不影响业务
     */
    public void pushAfterCommit(Runnable task) {
        if (task == null) return;
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("[Push] 异步推送异常", e);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            pushExecutor.execute(guarded);
                        }
                    });
            return;
        }
        pushExecutor.execute(guarded);
    }

    private final ObjectMapper om = new ObjectMapper();

    /**
     * 用户订阅额度缓存（openId -> 还能发几条）。
     * 微信不提供「查用户还剩几次订阅」的接口，只能靠小程序在
     * {@code requestSubscribeMessage} 成功后上报，这里记账。
     * 进程重启后归零；为了不让刚发的通知丢，未知用户按「允许发送」处理（见 {@link #takeQuota}）。
     */
    private final Map<String, Deque<String>> quota = new ConcurrentHashMap<>();

    // ==================================================================
    //  对外入口
    //
    //  刻意分成 4 个方法，而不是一个「id 随便传」的方法：
    //  t_order.id 和 t_menu_item.id 是两个独立自增序列，数值会撞车，
    //  靠「查订单表有没有这条记录」来猜语义早晚会推错人。
    // ==================================================================

    /**
     * 新订单 → 通知该家庭主厨（订单级，整单合并成一条）。
     *
     * @param orderId 订单 id；{@code null} 或订单不存在时静默返回
     */
    public void pushNewOrderToChef(Long orderId) {
        if (orderId == null || orderId <= 0) {
            log.warn("[Push] pushNewOrderToChef 收到非法 orderId={}，跳过", orderId);
            return;
        }
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("[Push] 订单 {} 不存在，跳过新订单通知", orderId);
                return;
            }
            List<MenuItem> items = itemsOfOrder(orderId);
            MenuItem anchor = items.isEmpty() ? null : items.get(0);
            if (anchor == null) {
                log.warn("[Push] 订单 {} 下没有菜品，跳过新订单通知", orderId);
                return;
            }
            Long familyId = order.getFamilyId() != null ? order.getFamilyId() : anchor.getFamilyId();
            notifyNewOrder(order, anchor, items, familyId);
        } catch (Exception e) {
            log.error("[Push] 新订单通知异常 orderId={}", orderId, e);
        }
    }

    /**
     * 新订单 → 通知主厨（单品级，「从菜单页直接下单」这条路径没有订单记录）。
     * <p>若该菜品其实挂在某个订单下，会自动转成订单级推送，避免重复。
     *
     * @param itemId 菜单项 id
     */
    public void pushNewOrderForItem(Long itemId) {
        if (itemId == null || itemId <= 0) {
            log.warn("[Push] pushNewOrderForItem 收到非法 itemId={}，跳过", itemId);
            return;
        }
        try {
            MenuItem item = menuItemMapper.selectById(itemId);
            if (item == null) {
                log.warn("[Push] 菜品 {} 不存在，跳过新订单通知", itemId);
                return;
            }
            if (item.getOrderId() != null) {
                pushNewOrderToChef(item.getOrderId());
                return;
            }
            if (item.getStatus() != null && item.getStatus() != 0) return; // 已撤销/已确认的不再通知
            notifyNewOrder(null, item, List.of(item), item.getFamilyId());
        } catch (Exception e) {
            log.error("[Push] 新订单通知异常 itemId={}", itemId, e);
        }
    }

    /** 单个菜品状态变更 → 通知该菜的下单人 */
    public void pushStatusChanged(Long itemId) {
        if (itemId == null || itemId <= 0) {
            log.warn("[Push] pushStatusChanged 收到非法 itemId={}，跳过", itemId);
            return;
        }
        try {
            MenuItem item = menuItemMapper.selectById(itemId);
            if (item == null) {
                log.warn("[Push] 菜品 {} 不存在，跳过状态通知", itemId);
                return;
            }
            Order order = item.getOrderId() == null ? null : orderMapper.selectById(item.getOrderId());
            // 单菜推送用「这道菜」的状态，不用订单状态：
            // 一个订单里确认了一道菜、另一道还在等，订单仍是「待确认」，但下单人要知道自己那道好了
            String statusText = statusText(item.getStatus());
            if (item.getStatus() != null && item.getStatus() == 0) return; // 退回待确认（如驳回后重确认）不推送
            notifyStatusChanged(item.getUserId(), item, order,
                    order != null ? itemsOfOrder(order.getId()) : List.of(item), statusText);
        } catch (Exception e) {
            log.error("[Push] 状态变更通知异常 itemId={}", itemId, e);
        }
    }

    /** 整笔订单状态变更 → 通知下单人（订单级，整单合并成一条） */
    public void pushOrderStatusChanged(Long orderId) {
        if (orderId == null || orderId <= 0) {
            log.warn("[Push] pushOrderStatusChanged 收到非法 orderId={}，跳过", orderId);
            return;
        }
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("[Push] 订单 {} 不存在，跳过状态通知", orderId);
                return;
            }
            List<MenuItem> items = itemsOfOrder(orderId);
            MenuItem anchor = items.isEmpty() ? null : items.get(0);
            if (anchor == null) {
                log.warn("[Push] 订单 {} 下没有菜品，跳过状态通知", orderId);
                return;
            }
            notifyStatusChanged(order.getUserId(), anchor, order, items, statusText(order.getStatus()));
        } catch (Exception e) {
            log.error("[Push] 订单状态通知异常 orderId={}", orderId, e);
        }
    }

    // ---------- 两个 notify 内部实现 ----------

    private void notifyNewOrder(Order order, MenuItem anchor, List<MenuItem> items, Long familyId) {
        User chef = resolveChef(familyId);
        if (chef == null || isBlank(chef.getOpenId())) {
            log.warn("[Push] 家庭 {} 没有主厨，跳过新订单通知", familyId);
            return;
        }
        String summary = summaryOf(order, items);
        if (pushDryRun) {
            log.info("[Push DryRun] 新订单 → 主厨 {} | 家庭 {} | {}", chef.getNickname(), familyId, summary);
            saveLog(chef.getOpenId(), chef.getOpenId(), familyId, anchor.getId(),
                    TYPE_NEW_ORDER, 1, "dry-run", null);
            return;
        }
        // 额度在 isAllowed() 里原子扣掉（不是等到发送成功才扣），
        // 这样并发的多个订单不会复用同一份额度
        if (!isAllowed(chef)) {
            log.warn("[Push] 主厨 {} 订阅额度已用完，跳过新订单通知（{}）", chef.getNickname(), summary);
            return;
        }
        sendTemplate(chef, templateNewOrder, buildNewOrderData(order, anchor, items),
                anchor.getId(), familyId, TYPE_NEW_ORDER);
    }

    private void notifyStatusChanged(String buyerId, MenuItem anchor, Order order,
                                     List<MenuItem> items, String statusText) {
        User buyer = isBlank(buyerId) ? null : userMapper.selectById(buyerId);
        if (buyer == null || isBlank(buyer.getOpenId())) {
            log.warn("[Push] 下单人 {} 不存在，跳过状态通知", buyerId);
            return;
        }
        Long familyId = order != null ? order.getFamilyId() : anchor.getFamilyId();
        if (pushDryRun) {
            log.info("[Push DryRun] 状态变更 → {} | 订单 {} | 状态 {}",
                    buyer.getNickname(), order != null ? order.getId() : null, statusText);
            saveLog(buyer.getOpenId(), buyer.getOpenId(), familyId, anchor.getId(),
                    TYPE_STATUS_CHANGED, 1, "dry-run", null);
            return;
        }
        if (!isAllowed(buyer)) {
            log.warn("[Push] 下单人 {} 订阅额度已用完，跳过状态通知（{}）", buyer.getNickname(), statusText);
            return;
        }
        sendTemplate(buyer, templateStatusChanged, buildStatusChangedData(order, anchor, items, statusText),
                anchor.getId(), familyId, TYPE_STATUS_CHANGED);
    }

    // ==================================================================
    //  模板数据
    // ==================================================================

    /** 「下单成功通知」：门店名称 / 订单编号 / 订单内容 / 下单时间 / 联系人姓名 */
    private Map<String, String> buildNewOrderData(Order order, MenuItem anchor, List<MenuItem> items) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("thing2", familyName(anchor.getFamilyId()));
        d.put("character_string3", orderNo(order, anchor));
        d.put("thing6", summaryOf(order, items));
        LocalDateTime t = order != null && order.getCreatedAt() != null
                ? order.getCreatedAt()
                : (anchor.getCreatedAt() != null ? anchor.getCreatedAt() : LocalDateTime.now());
        d.put("time7", t.format(TIME_FMT));
        // name8 是「姓名」类型：见 safeName()
        d.put("name8", safeName(anchor.getUserNickname()));
        return d;
    }

    /**
     * 「姓名」字段净化。{@code name8} 是微信的 <b>name 类型</b>，规则和 thing 完全不同：
     * <pre>
     *   10 个以内纯汉字        → 张三、顾晨曦
     *   20 个以内纯字母/符号   → Tom、Alice-Wang
     *   中英混合按中文名算，10 个字以内
     * </pre>
     * 新用户默认昵称是 {@code "用户" + openId 前 6 位}（形如「用户o4o4-3」），
     * <b>汉字 + 字母数字混合</b>正是这个类型不接受的形式，微信会直接返回
     * {@code 47003 argument invalid! data.name8.value invalid}，整条通知发不出去。
     *
     * <p>降级策略：昵称是纯汉字就用昵称；含字母/数字的一律换成「家人」，
     * 宁可少显示一个名字，也不能让整条下单通知失败。
     */
    private static String safeName(String nickname) {
        if (isBlank(nickname)) return "家人";
        String s = nickname.trim();
        // 纯汉字（含常见姓名分隔符）且不超过 10 个字 → 直接用
        if (s.length() <= 10 && s.matches("[\\u4e00-\\u9fa5·]+")) return s;
        // 纯字母/符号且不超过 20 位 → 微信允许
        if (s.length() <= 20 && s.matches("[A-Za-z .\\-']+")) return s;
        return "家人";
    }

    /** 「订单状态变更通知」：订单号 / 订单状态 / 温馨提示 / 更新时间 / 订单商品 */
    private Map<String, String> buildStatusChangedData(Order order, MenuItem anchor,
                                                       List<MenuItem> items, String statusText) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("character_string2", orderNo(order, anchor));
        d.put("phrase3", statusText);
        d.put("thing5", truncate("主厨已" + statusText.replace("已", "") + "，点开小程序查看", MAX_TEXT_LEN));
        d.put("date6", LocalDateTime.now().format(DATE_FMT));
        d.put("thing10", summaryOf(order, items));
        return d;
    }

    /** 菜品摘要：整单按「菜名×份数」合并，最多 3 个菜名 */
    private String summaryOf(Order order, List<MenuItem> items) {
        if (items == null || items.isEmpty()) return "无菜品";
        Map<String, Integer> count = new LinkedHashMap<>();
        for (MenuItem it : items) {
            String name = isBlank(it.getDishName()) ? "菜品" : it.getDishName().trim();
            count.merge(name, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (shown > 0) sb.append("、");
            sb.append(e.getKey());
            if (e.getValue() > 1) sb.append("×").append(e.getValue());
            shown++;
            if (shown >= 3) break;
        }
        if (count.size() > shown) sb.append(" 等").append(count.size()).append("种");
        else if (order != null && order.getItemCount() != null && order.getItemCount() > 0) {
            sb.append("，共").append(order.getItemCount()).append("份");
        }
        return truncate(sb.toString(), MAX_TEXT_LEN);
    }

    private List<MenuItem> itemsOfOrder(Long orderId) {
        if (orderId == null) return List.of();
        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>()
                        .eq(MenuItem::getOrderId, orderId)
                        .ne(MenuItem::getStatus, -1)   // 已撤销的菜不进摘要
                        .orderByAsc(MenuItem::getId));
        return items == null ? List.of() : items;
    }

    private String orderNo(Order order, MenuItem anchor) {
        Long id = order != null && order.getId() != null ? order.getId() : anchor.getOrderId();
        if (id == null) id = anchor.getId();
        return String.format("%08d", id);
    }

    private String familyName(Long familyId) {
        if (familyId == null) return "家庭厨房";
        try {
            com.example.order.entity.Family f = familyMapper.selectById(familyId);
            if (f != null && !isBlank(f.getName())) return truncate(f.getName(), 10);
        } catch (Exception e) {
            log.debug("[Push] 读家庭名失败 familyId={}", familyId);
        }
        return "家庭厨房";
    }

    /** 微信 phrase 字段有长度和枚举限制，只用固定四档 */
    private static String statusText(Integer status) {
        int s = status == null ? 0 : status;
        if (s == 1) return "已确认";
        if (s == -1) return "已撤销";
        if (s == 2) return "已驳回";
        return "待确认";
    }

    // ==================================================================
    //  真实推送
    // ==================================================================

    /**
     * 真实下发。
     *
     * @param quotaTaken 调用方是否已经通过 {@link #isAllowed} 扣掉了一次额度。
     *                   只有为 true 时失败才会 {@link #refundQuota} 退还；
     *                   自测推送（{@link #sendTestToChef}）不扣额度，传 false，
     *                   否则一次失败反而给用户凭空加了一份额度。
     */
    private void sendTemplate(User user, String templateId, Map<String, String> data,
                              Long menuItemId, Long familyId, String type) {
        sendTemplate(user, templateId, data, menuItemId, familyId, type, true);
    }

    private void sendTemplate(User user, String templateId, Map<String, String> data,
                              Long menuItemId, Long familyId, String type, boolean quotaTaken) {
        if (isBlank(templateId)) {
            log.warn("[Push Mock] 模板 ID 未配置（wechat.template.*），只记日志。type={} to={} data={}",
                    type, user.getNickname(), data);
            saveLog(user.getOpenId(), user.getOpenId(), familyId, menuItemId, type, 1, "未配置模板ID", toJson(data));
            return;
        }

        String payloadJson;
        try {
            payloadJson = om.writeValueAsString(data);
        } catch (Exception e) {
            payloadJson = String.valueOf(data);
        }

        // 微信接口只认本地 token 缓存，拿不到就别白跑一趟（也避免 40001 刷屏）
        String accessToken = tokenService.getAccessToken();
        if (accessToken == null) {
            log.warn("[Push] 无可用 access_token，进入重试队列 type={} to={}", type, user.getNickname());
            if (quotaTaken) refundQuota(user.getOpenId(), templateId);   // 没送达，退还额度
            enqueuePending(user, templateId, data, menuItemId, familyId, type);
            saveLog(user.getOpenId(), user.getOpenId(), familyId, menuItemId, type, 0,
                    "access_token 不可用，已入重试队列", payloadJson);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", user.getOpenId());
        body.put("template_id", templateId);
        body.put("page", "pages/index/index");
        Map<String, Object> dataNode = new LinkedHashMap<>();
        data.forEach((k, v) -> dataNode.put(k, Map.of("value", v)));
        body.put("data", dataNode);
        // 一次性订阅消息：微信默认允许跳转小程序，这里保留默认
        body.put("miniprogram_state", "formal");

        Map<?, ?> resp = doSend(accessToken, body, type);
        if (resp == null) {
            if (quotaTaken) refundQuota(user.getOpenId(), templateId);   // 网络异常，用户没收到
            enqueuePending(user, templateId, data, menuItemId, familyId, type);
            saveLog(user.getOpenId(), user.getOpenId(), familyId, menuItemId, type, 0,
                    "微信接口调用异常，已入重试队列", payloadJson);
            return;
        }

        int errcode = asInt(resp.get("errcode"), -1);
        if (errcode == 0) {
            // 额度在 isAllowed() 里已经扣过，这里只记日志
            saveLog(user.getOpenId(), user.getOpenId(), familyId, menuItemId, type, 1, null, payloadJson);
            log.info("[Push] {} 发送成功 → {} ({})", type, user.getNickname(), user.getOpenId());
            return;
        }

        String errmsg = String.valueOf(resp.get("errmsg"));
        saveLog(user.getOpenId(), user.getOpenId(), familyId, menuItemId, type, 0,
                "errcode=" + errcode + " " + errmsg, payloadJson);
        log.warn("[Push] {} 发送失败 → {} errcode={} errmsg={}", type, user.getNickname(), errcode, errmsg);

        switch (errcode) {
            case 40001, 42001 -> {  // token 无效/过期：清缓存，入队下次重试就能成功
                tokenService.invalidate();
                if (quotaTaken) refundQuota(user.getOpenId(), templateId);   // 消息没送达，额度还给用户
                enqueuePending(user, templateId, data, menuItemId, familyId, type);
            }
            case 43101 -> {         // 用户拒绝订阅 / 没有可用的订阅次数，重试也没用
                // 微信用同一个错误码表达两件事：「用户点过拒绝/不再接收」和「一次性额度用完了」。
                // 后者是常态（一次授权只能发一条），不能据此清空本地记账：
                // takeQuota 已经把这一份取走了，再 clear 就会把用户真实攒下的剩余次数一起抹掉，
                // 逼得他每次都得重新点授权。日志里区分不出，所以只记录、不动额度。
                log.warn("[Push] {} 收到 43101（额度已用完或用户拒收），本地记账余量 {}，跳过本次推送",
                        user.getNickname(), quotaOf(user.getOpenId()));
            }
            case 40037, 47003 -> {
                log.error("[Push] 模板 ID 或字段不合法，请检查 .env 里的模板配置和字段类型：{}", templateId);
                if (quotaTaken) refundQuota(user.getOpenId(), templateId);   // 参数错误不是用户的错
            }
            default -> {
                if (quotaTaken) refundQuota(user.getOpenId(), templateId);
                enqueuePending(user, templateId, data, menuItemId, familyId, type);
            }
        }
    }

    /**
     * 真正调用微信下发接口。
     *
     * <p><b>为什么必须自己序列化成 byte[]（重要，别改回 String/Map）：</b>
     * Spring 的 SimpleClientHttpRequestFactory 对 String / 对象 body 走**流式写出**，
     * 因为没有长度信息只能用 {@code Transfer-Encoding: chunked}；而微信
     * {@code message/subscribe/send} 对 chunked 请求体直接返回
     * <b>412 Precondition Failed（body 为空）</b>——不是 40001 也不是 43101，
     * 排查时极具迷惑性（curl 同样的 body 却正常，因为 curl 默认带 Content-Length）。
     * 传 byte[] / 显式 setContentLength 时 Spring 走 buffered 路径，才会带上 Content-Length。
     */
    private Map<?, ?> doSend(String accessToken, Map<String, Object> body, String type) {
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
        byte[] payload;
        try {
            payload = om.writeValueAsBytes(body);
        } catch (Exception e) {
            log.error("[Push] 请求体序列化失败 type={}", type, e);
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 显式给出长度 → 强制 Content-Length，避免 chunked 被微信 412 拒绝
        headers.setContentLength(payload.length);
        HttpEntity<byte[]> entity = new HttpEntity<>(payload, headers);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Map<?, ?> resp = rt.exchange(url, HttpMethod.POST, entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
                if (resp != null) return resp;
                log.warn("[Push] 微信接口返回空 body（第 {} 次）type={}", attempt, type);
            } catch (HttpStatusCodeException e) {
                // 微信错误时返回 text/plain 的错误 JSON，直接转 Map 会抛转换异常，这里取原文修复现场
                String raw = e.getResponseBodyAsString();
                if (raw != null && raw.trim().startsWith("{")) {
                    try {
                        return om.readValue(raw, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception ignore) {
                        // 落到下面的日志
                    }
                }
                log.warn("[Push] 调用微信接口异常（第 {} 次）type={}：HTTP {} body={}",
                        attempt, type, e.getStatusCode(), raw);
            } catch (Exception e) {
                log.warn("[Push] 调用微信接口异常（第 {} 次）type={}：{}", attempt, type, e.getMessage());
            }
        }
        return null;
    }

    // ==================================================================
    //  订阅额度
    // ==================================================================

    /** 单次上报最多累加多少条：与小程序端 {@code subscribe.js} 的 {@code MAX_BULK} 对齐 */
    private static final int MAX_GRANT_PER_CALL = 20;

    /** 单人额度池上限：脚本灌水也撑不爆内存，且不至于让重试队列无限尝试 */
    private static final int MAX_QUOTA_TOTAL = 60;

    /**
     * 小程序 {@code requestSubscribeMessage} 成功后上报：该用户在这个模板上多了 N 次可发机会。
     * 微信按「一次性订阅」计费，用户点一次允许 = 一条。
     *
     * <p><b>为什么 count 必须由客户端给、而不是固定 +1：</b>
     * 主厨勾了「总是保持以上选择」时，客户端会连着调 N 次面板（静默、不打扰），
     * 微信那边真的攒下了 N 条。服务端如果只记 1 条，第 2 条推送就会被
     * {@link #isAllowed} 拦掉并打日志「额度已用完」——而微信其实能收，
     * 这正是「测试能推、真实下单推不出去」的一类假故障。所以按客户端上报的条数记账。
     *
     * <p><b>但不能无限信任（信任边界）：</b>这个接口只要登录态，客户端撒谎
     * 只能给自己灌额度（openId 取自 token，改不了别人）。灌多了也不是灾难——
     * 发送会撞 43101 → {@link #clearQuota} 自动归零。这里仍加两道闸：
     * 单次不超过 {@link #MAX_GRANT_PER_CALL}，池子总量不超过 {@link #MAX_QUOTA_TOTAL}。
     *
     * @return 记账后的剩余额度；-1 表示未知
     */
    public int reportSubscribeGrant(String openId, String templateId, int count) {
        if (isBlank(openId) || isBlank(templateId) || count <= 0) return quotaOf(openId);
        int n = Math.min(count, MAX_GRANT_PER_CALL);
        Deque<String> q = quota.computeIfAbsent(openId, k -> new ArrayDeque<>());
        int added = 0;
        synchronized (q) {
            for (int i = 0; i < n && q.size() < MAX_QUOTA_TOTAL; i++) {
                q.addLast(templateId);
                added++;
            }
        }
        // 同时把 t_user.allow_push 置 1，后台统计和旧逻辑都依赖它
        try {
            User u = userMapper.selectById(openId);
            if (u != null && (u.getAllowPush() == null || u.getAllowPush() == 0)) {
                u.setAllowPush(1);
                userMapper.updateById(u);
            }
        } catch (Exception e) {
            log.debug("[Push] 更新 allow_push 失败 openId={}", openId);
        }
        log.info("[Push] {} 上报订阅成功：模板 {} 请求 {} 条、实记 {} 条，当前余量 {}",
                openId, templateId, count, added, quotaOf(openId));
        return quotaOf(openId);
    }

    /** 该 openId 的剩余可发次数；-1 表示未知（未上报过，按允许发送处理） */
    public int quotaOf(String openId) {
        Deque<String> q = quota.get(openId);
        if (q == null) return -1;
        synchronized (q) {
            return q.size();
        }
    }

    /**
     * 取一次发送机会：<b>查到即扣，原子完成</b>。
     *
     * <p>旧实现只「看一眼队列空不空」、真正的扣减留到发送成功的回调里，
     * 于是同一份额度能被并发放行多次，而 {@code @Async} 下单路径下多个订单
     * 确实会同时进来；等 43101 一到又把整池清空，主厨就再也收不到新订单。
     * 现在改成「先取走，发失败再退」，失败退还在 {@link #refundQuota}。
     *
     * @return true 表示已取到一次机会（或该用户未知，放行交给微信判决）
     */
    private boolean takeQuota(String openId) {
        Deque<String> q = quota.get(openId);
        if (q == null) return true; // 未知 → 放行，让微信来判决（43101 会在发送结果里体现）
        synchronized (q) {
            return q.pollFirst() != null;
        }
    }

    /** 发送未送达时退还一次机会（token 失效、接口异常等，用户其实没收到） */
    private void refundQuota(String openId, String templateId) {
        Deque<String> q = quota.get(openId);
        if (q == null) return;  // 该用户根本没记过账（quotaOf==-1），退还反而会凭空造出额度
        synchronized (q) {
            if (q.size() >= MAX_QUOTA_TOTAL) return;  // 与 reportSubscribeGrant 同一道上限
            q.addLast(templateId == null ? "" : templateId);
        }
    }

    private void clearQuota(String openId) {
        Deque<String> q = quota.get(openId);
        if (q != null) {
            synchronized (q) {
                q.clear();
            }
        }
    }

    /**
     * 是否允许给该用户推送。
     * <p>{@code allow_push=0} 只代表「从未授权过」，不代表「明确拒绝」——
     * 用户可能是绕过弹窗直接下单的，此时微信那边其实还有额度，所以这里不拦，交给 43101 判定。
     */
    private boolean isAllowed(User user) {
        if (user == null || isBlank(user.getOpenId())) return false;
        return takeQuota(user.getOpenId());
    }

    // ==================================================================
    //  重试队列
    // ==================================================================

    private void enqueuePending(User user, String templateId, Map<String, String> data,
                                Long menuItemId, Long familyId, String type) {
        try {
            PushPending pp = new PushPending();
            pp.setUserId(user.getOpenId());
            pp.setOpenid(user.getOpenId());
            pp.setTemplateId(templateId);
            pp.setPushType(type);
            pp.setFamilyId(familyId == null ? 0L : familyId);
            pp.setPayload(om.writeValueAsString(data));
            pp.setMenuItemId(menuItemId);
            pp.setAttempts(0);
            pp.setNextRetryAt(LocalDateTime.now().plusMinutes(3));
            pp.setStatus(0);
            pushPendingMapper.insert(pp);
            log.info("[Push] 已入重试队列 #{} type={} to={}", pp.getId(), type, user.getNickname());
        } catch (Exception e) {
            log.error("[Push] 入队失败 userId={}", user.getOpenId(), e);
        }
    }

    private static final int MAX_ATTEMPTS = 3;

    /** 每 2 分钟扫描重试队列，真正重发（原来只改计数不发送，等于队列是死的） */
    @Scheduled(fixedDelay = 120_000L)
    public void retryPending() {
        List<PushPending> pending;
        try {
            pending = pushPendingMapper.selectList(
                    new LambdaQueryWrapper<PushPending>()
                            .eq(PushPending::getStatus, 0)
                            .le(PushPending::getNextRetryAt, LocalDateTime.now())
                            .orderByAsc(PushPending::getId)
                            .last("LIMIT 10"));
        } catch (Exception e) {
            log.error("[Push Retry] 查询待重试队列失败", e);
            return;
        }
        if (pending == null || pending.isEmpty()) return;

        String accessToken = tokenService.getAccessToken();
        if (accessToken == null) {
            log.warn("[Push Retry] 无可用 access_token，本轮 {} 条延后", pending.size());
            return;
        }

        for (PushPending p : pending) {
            if (p.getAttempts() != null && p.getAttempts() >= MAX_ATTEMPTS) {
                p.setStatus(2);
                p.setLastError("超过最大重试次数 " + MAX_ATTEMPTS);
                pushPendingMapper.updateById(p);
                log.warn("[Push Retry] #{} 放弃（已试 {} 次）", p.getId(), p.getAttempts());
                continue;
            }
            Map<String, String> data;
            try {
                data = om.readValue(p.getPayload(), new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                p.setStatus(2);
                p.setLastError("payload 解析失败: " + e.getMessage());
                pushPendingMapper.updateById(p);
                continue;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("touser", p.getOpenid());
            body.put("template_id", p.getTemplateId());
            body.put("page", "pages/index/index");
            Map<String, Object> dataNode = new LinkedHashMap<>();
            data.forEach((k, v) -> dataNode.put(k, Map.of("value", v)));
            body.put("data", dataNode);

            Map<?, ?> resp = doSend(accessToken, body, p.getPushType());
            int errcode = resp == null ? -1 : asInt(resp.get("errcode"), -1);
            int attempts = (p.getAttempts() == null ? 0 : p.getAttempts()) + 1;
            p.setAttempts(attempts);

            if (errcode == 0) {
                p.setStatus(1);
                p.setLastError(null);
                pushPendingMapper.updateById(p);
                saveLog(p.getUserId(), p.getOpenid(), p.getFamilyId(), p.getMenuItemId(),
                        p.getPushType(), 1, null, p.getPayload());
                log.info("[Push Retry] #{} 第 {} 次重试成功", p.getId(), attempts);
                continue;
            }

            String errmsg = resp == null ? "接口异常" : ("errcode=" + errcode + " " + resp.get("errmsg"));
            p.setLastError(truncate(errmsg, 200));
            if (errcode == 43101 || attempts >= MAX_ATTEMPTS) {
                p.setStatus(2);
                pushPendingMapper.updateById(p);
                log.warn("[Push Retry] #{} 放弃：{}", p.getId(), errmsg);
            } else {
                p.setNextRetryAt(LocalDateTime.now().plusMinutes(5L * attempts));
                pushPendingMapper.updateById(p);
                log.warn("[Push Retry] #{} 第 {} 次失败：{}，稍后再试", p.getId(), attempts, errmsg);
            }
        }
    }

    private void saveLog(String userId, String openid, Long familyId, Long menuItemId,
                         String type, int success, String errorMsg, String payload) {
        try {
            PushLog entity = new PushLog();
            entity.setUserId(userId != null ? userId : "");
            entity.setOpenid(openid != null ? openid : "");
            entity.setFamilyId(familyId == null ? 0L : familyId);
            entity.setMenuItemId(menuItemId);
            entity.setType(type == null ? "UNKNOWN" : truncate(type, 16));
            entity.setSuccess(success);
            entity.setErrorMsg(truncate(errorMsg, 250));
            entity.setPayload(payload);
            entity.setCreatedAt(LocalDateTime.now());
            pushLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("[Push] 写日志失败 userId={} type={}", userId, type, e);
        }
    }

    /** 旧签名保留（其它地方可能还在用） */
    private void saveLog(String userId, String openid, Long menuItemId, String type, int success, String errorMsg) {
        saveLog(userId, openid, null, menuItemId, type, success, errorMsg, null);
    }

    // ==================================================================
    //  查询 / 诊断
    // ==================================================================

    /** 主厨侧的推送概况：今天发了几条、几条失败、队列里还压着几条 */
    public Map<String, Object> stats(Long familyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        try {
            Long total = pushLogMapper.selectCount(new LambdaQueryWrapper<PushLog>()
                    .ge(PushLog::getCreatedAt, dayStart)
                    .eq(familyId != null, PushLog::getFamilyId, familyId));
            Long failed = pushLogMapper.selectCount(new LambdaQueryWrapper<PushLog>()
                    .ge(PushLog::getCreatedAt, dayStart)
                    .eq(PushLog::getSuccess, 0)
                    .eq(familyId != null, PushLog::getFamilyId, familyId));
            Long queued = pushPendingMapper.selectCount(new LambdaQueryWrapper<PushPending>()
                    .eq(PushPending::getStatus, 0)
                    .eq(familyId != null, PushPending::getFamilyId, familyId));
            m.put("todayTotal", total == null ? 0 : total);
            m.put("todayFailed", failed == null ? 0 : failed);
            m.put("queued", queued == null ? 0 : queued);
        } catch (Exception e) {
            log.warn("[Push] 统计失败：{}", e.getMessage());
        }
        m.put("dryRun", pushDryRun);
        m.put("newOrderTemplate", isBlank(templateNewOrder) ? "" : templateNewOrder);
        m.put("statusChangedTemplate", isBlank(templateStatusChanged) ? "" : templateStatusChanged);
        m.put("token", tokenService.status());
        return m;
    }

    /** 最近 N 条推送记录（主厨诊断用） */
    public List<Map<String, Object>> recentLogs(Long familyId, int limit) {        List<PushLog> list = pushLogMapper.selectList(new LambdaQueryWrapper<PushLog>()
                .eq(familyId != null, PushLog::getFamilyId, familyId)
                .orderByDesc(PushLog::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        List<Map<String, Object>> out = new ArrayList<>();
        if (list == null) return out;
        for (PushLog l : list) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", l.getId());
            e.put("type", l.getType());
            e.put("nickname", nicknameOf(l.getOpenid()));
            e.put("openid", l.getOpenid());
            e.put("success", l.getSuccess() != null && l.getSuccess() == 1);
            e.put("errorMsg", l.getErrorMsg());
            e.put("payload", l.getPayload());
            e.put("createdAt", l.getCreatedAt() == null ? null : l.getCreatedAt().format(TIME_FMT));
            out.add(e);
        }
        return out;
    }

    private String nicknameOf(String openId) {
        try {
            User u = userMapper.selectById(openId);
            return u == null || isBlank(u.getNickname()) ? "" : u.getNickname();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================================================================
    //  授权（保留旧接口）
    // ==================================================================

    /**
     * 推送配置：客户端拿模板 ID 去调 {@code requestSubscribeMessage}，
     * 并据此决定是否还要弹授权框（remaining==0 时才值得打扰用户）。
     *
     * <p><b>role 只作为兜底（重要）：</b>以前这里直接信 query 传来的 role，
     * 但「我的」页 onShow 之类的调用绕开了角色判断，就会拿到错的模板 ID →
     * 给非主厨授权「下单成功通知」。角色必须以 {@code t_family_member.role} 为准。
     *
     * @param role 前端传的当前角色（CHEF / MEMBER），仅在查不到家庭成员记录时兜底
     */
    public Map<String, Object> pushConfig(String openId, String role) {
        boolean chef = isChef(openId, UserContext.getFamily(), role);
        String tmpl = chef ? templateNewOrder : templateStatusChanged;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", chef ? "CHEF" : "MEMBER");
        m.put("templateId", isBlank(tmpl) ? "" : tmpl);
        m.put("newOrderTemplate", isBlank(templateNewOrder) ? "" : templateNewOrder);
        m.put("statusChangedTemplate", isBlank(templateStatusChanged) ? "" : templateStatusChanged);
        m.put("configured", !isBlank(templateNewOrder) && !isBlank(templateStatusChanged));
        m.put("dryRun", pushDryRun);
        m.put("remaining", quotaOf(openId));
        return m;
    }

    /**
     * 该用户在当前家庭里是不是主厨。
     * 判定顺序：{@code t_family_member} → {@code t_user.is_chef}（全局主厨/管理员）
     * → 客户端给的 role。宁可退回客户端声明，也不要因为一次查库失败就把主厨的模板换成成员的。
     */
    private boolean isChef(String openId, Long familyId, String fallbackRole) {
        if (isBlank(openId)) return false;
        try {
            List<FamilyMember> members = familyMemberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getUserId, openId)
                            .last("LIMIT 5"));
            if (members != null && !members.isEmpty()) {
                for (FamilyMember m : members) {
                    // 有活跃家庭就用活跃家庭的角色；没有活跃家庭时任一家庭是 CHEF 即算主厨
                    boolean sameFamily = familyId == null || familyId.equals(m.getFamilyId());
                    if (sameFamily && "CHEF".equalsIgnoreCase(m.getRole())) return true;
                }
                if (familyId != null) return false; // 有记录、但在这个家庭里不是主厨
                for (FamilyMember m : members) {
                    if ("CHEF".equalsIgnoreCase(m.getRole())) return true;
                }
                return false;
            }
        } catch (Exception e) {
            log.debug("[Push] 查询家庭成员角色失败 openId={}", openId);
        }
        // 没有家庭记录：可能是全局主厨（is_chef=1），与 resolveChef 的回退口径保持一致
        try {
            User u = userMapper.selectById(openId);
            if (u != null && u.getIsChef() != null && u.getIsChef() == 1) return true;
        } catch (Exception e) {
            log.debug("[Push] 查询 is_chef 失败 openId={}", openId);
        }
        return "CHEF".equalsIgnoreCase(fallbackRole);
    }

    /**
     * 主厨自测：给自己发一条「下单成功通知」。
     * 这样不用真的找第二个人下单，也能验证模板字段、授权、token 是否都通。
     */
    public Map<String, Object> sendTestToChef() {
        String openId = UserContext.get();
        Long familyId = UserContext.getFamily();
        User me = openId == null ? null : userMapper.selectById(openId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (me == null) {
            out.put("ok", false);
            out.put("reason", "用户不存在");
            return out;
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("thing2", truncate(familyName(familyId), 10));
        data.put("character_string3", "TEST0001");
        data.put("thing6", "测试推送，收到说明配置正确");
        data.put("time7", LocalDateTime.now().format(TIME_FMT));
        data.put("name8", safeName(me.getNickname()));

        out.put("templateId", templateNewOrder);
        out.put("dryRun", pushDryRun);
        out.put("remaining", quotaOf(me.getOpenId()));
        if (isBlank(templateNewOrder)) {
            out.put("ok", false);
            out.put("reason", "未配置 wechat.template.new-order，请先在微信公众平台申请模板");
            return out;
        }
        if (pushDryRun) {
            saveLog(me.getOpenId(), me.getOpenId(), familyId, null, TYPE_NEW_ORDER, 1,
                    "dry-run(test)", toJson(data));
            out.put("ok", true);
            out.put("reason", "dry-run 模式，只记录了日志没有真发；把 PUSH_DRY_RUN 改成 false 才会真推");
            return out;
        }
        // quotaTaken=false：自测不占用户额度。
        // 以前这里走的是会 consumeQuota 的路径，主厨点一次「测试推送」就烧掉一份
        // 真实额度——而一次授权只换一条通知，测两次之后真实下单就再也推不出去了。
        sendTemplate(me, templateNewOrder, data, null, familyId, TYPE_NEW_ORDER, false);
        out.put("remaining", quotaOf(me.getOpenId())); // 自测不扣，回传原值供前端显示
        out.put("ok", true);
        out.put("reason", "已提交微信接口，稍等几秒看手机；若没收到请看 logs 里的 errcode");
        out.put("logs", recentLogs(familyId, 1));
        return out;
    }

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** 绑定 / 取消推送授权（同步 t_user.allow_push） */
    public Result<Void> bindPushAuth(String userId, boolean allow) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error(404, "用户不存在");
        user.setAllowPush(allow ? 1 : 0);
        userMapper.updateById(user);
        if (!allow) clearQuota(userId);
        return Result.ok();
    }

    // ==================================================================
    //  工具
    // ==================================================================

    private User resolveChef(Long familyId) {
        if (familyId != null) {
            List<FamilyMember> chefs = familyMemberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getFamilyId, familyId)
                            .eq(FamilyMember::getRole, "CHEF")
                            .last("LIMIT 1"));
            if (chefs != null && !chefs.isEmpty()) {
                User chef = userMapper.selectById(chefs.get(0).getUserId());
                if (chef != null) return chef;
            }
        }
        // 回退：全局主厨（is_chef=1）
        List<User> global = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getIsChef, 1).last("LIMIT 1"));
        return global == null || global.isEmpty() ? null : global.get(0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 按「字符」截断（微信限制的是字数不是字节） */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }
}
