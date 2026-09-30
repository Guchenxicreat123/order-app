package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.MenuItem;
import com.example.order.entity.Order;
import com.example.order.entity.User;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    @Autowired private OrderMapper orderMapper;
    @Autowired private MenuItemMapper menuItemMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private FamilyMemberMapper memberMapper;
    @Autowired private PushService pushService;
    @Autowired private ChefService chefService;

    // ==================== 创建订单（cart items → order + menu items） ====================

    /**
     * 把一组 cart items 创建为一个订单。
     * @param cartItems 一组购物车菜品（已选中的）
     * @param remark 订单备注（可选）
     * @return 新订单信息（包含 items）
     */
    @Transactional
    public Result<Map<String, Object>> createOrderFromCart(List<com.example.order.entity.Cart> cartItems, String remark) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (cartItems == null || cartItems.isEmpty()) return Result.error(400, "请先选择菜品");

        // 取第一个 cart 的 familyId（购物车项必定属于同一家庭同一用户）
        Long familyId = cartItems.get(0).getFamilyId();

        // 检查是否需要主厨
        if (!chefService.hasChef(familyId)) {
            return Result.error(400, "当前家庭还没有主厨，请先认领主厨");
        }

        User user = userMapper.selectById(userId);
        String nickname = user != null ? user.getNickname() : "";

        // 计算总价和菜品总数（按 quantity 累计）
        BigDecimal total = BigDecimal.ZERO;
        int totalCount = 0;
        for (com.example.order.entity.Cart c : cartItems) {
            int qty = c.getQuantity() != null ? c.getQuantity() : 1;
            total = total.add(c.getPrice().multiply(BigDecimal.valueOf(qty)));
            totalCount += qty;
        }

        // 创建订单
        Order order = new Order();
        order.setFamilyId(familyId);
        order.setUserId(userId);
        order.setUserNickname(nickname);
        order.setTotalAmount(total);
        order.setItemCount(totalCount);
        order.setStatus(0);
        order.setRemark(remark);
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        // 把 cart 转为 menu items（按 quantity 拆开），关联到 order
        List<Long> createdItemIds = new ArrayList<>();
        for (com.example.order.entity.Cart cart : cartItems) {
            int qty = cart.getQuantity() != null ? cart.getQuantity() : 1;
            for (int i = 0; i < qty; i++) {
                MenuItem mi = new MenuItem();
                mi.setOrderId(order.getId());
                mi.setUserId(userId);
                mi.setFamilyId(familyId);
                mi.setUserNickname(nickname);
                mi.setDishId(cart.getDishId());
                mi.setDishName(cart.getDishName());
                mi.setDishEmoji(cart.getDishEmoji());
                mi.setCustomIngs(cart.getCustomIngs());
                mi.setSpiceLevel(cart.getSpiceLevel());
                mi.setPrice(cart.getPrice());
                mi.setRemark(cart.getRemark());
                mi.setStatus(0);
                mi.setVersion(0);
                mi.setCreatedAt(LocalDateTime.now());
                menuItemMapper.insert(mi);
                createdItemIds.add(mi.getId());
            }
        }

        // 推送主厨：按「订单」推送一次（整单菜品合并成一条，避免把主厨的订阅额度一条条烧光）
        final Long newOrderId = order.getId();
        pushService.pushAfterCommit(() -> pushService.pushNewOrderToChef(newOrderId));

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("itemCount", totalCount);
        data.put("totalAmount", total);
        return Result.ok(data);
    }

    // ==================== 查询 ====================

    /** 当前家庭全部订单（按时间倒序），含每个订单的 item 数、确认数、合计 */
    public Result<List<Map<String, Object>>> listFamilyOrders() {
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        return Result.ok(listOrdersByFamily(familyId, null));
    }

    /** 当前家庭今日订单 */
    public Result<List<Map<String, Object>>> listTodayOrders() {
        Long familyId = UserContext.getFamily();
        if (familyId == null) return Result.ok(new ArrayList<>());
        return Result.ok(listOrdersByFamily(familyId, LocalDate.now()));
    }

    private List<Map<String, Object>> listOrdersByFamily(Long familyId, LocalDate date) {
        LambdaQueryWrapper<Order> q = new LambdaQueryWrapper<Order>()
                .eq(Order::getFamilyId, familyId)
                .orderByDesc(Order::getCreatedAt);
        if (date != null) {
            q.ge(Order::getCreatedAt, date.atStartOfDay())
                    .lt(Order::getCreatedAt, date.plusDays(1).atStartOfDay());
        }
        List<Order> orders = orderMapper.selectList(q);
        if (orders.isEmpty()) return new ArrayList<>();

        // 一次取所有 menu items
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<MenuItem>> itemsByOrder = menuItemMapper.selectList(
                        new LambdaQueryWrapper<MenuItem>().in(MenuItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(MenuItem::getOrderId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            List<MenuItem> items = itemsByOrder.getOrDefault(o.getId(), new ArrayList<>());
            int confirmedCount = 0;
            int rejectedCount = 0;
            for (MenuItem mi : items) {
                if (mi.getStatus() != null && mi.getStatus() == 1) confirmedCount++;
                else if (mi.getStatus() != null && mi.getStatus() == 2) rejectedCount++;
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("orderId", o.getId());
            e.put("familyId", o.getFamilyId());
            e.put("userId", o.getUserId());
            e.put("userNickname", o.getUserNickname());
            e.put("totalAmount", o.getTotalAmount());
            e.put("itemCount", o.getItemCount());
            e.put("confirmedCount", confirmedCount);
            e.put("rejectedCount", rejectedCount);
            e.put("status", o.getStatus());
            e.put("remark", o.getRemark());
            e.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
            e.put("confirmedAt", o.getConfirmedAt() != null ? o.getConfirmedAt().toString() : "");
            // 附带菜品明细（含状态），让每个家庭成员都能直接看到每道菜 确认/驳回/待确认
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (MenuItem mi : items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("itemId", mi.getId());
                im.put("dishId", mi.getDishId());
                im.put("dishName", mi.getDishName());
                im.put("dishEmoji", mi.getDishEmoji());
                im.put("spiceLevel", mi.getSpiceLevel());
                im.put("price", mi.getPrice());
                im.put("status", mi.getStatus());
                im.put("userNickname", mi.getUserNickname());
                im.put("createdAt", mi.getCreatedAt() != null ? mi.getCreatedAt().toString() : "");
                itemList.add(im);
            }
            e.put("items", itemList);
            result.add(e);
        }
        return result;
    }

    /** 单个订单详情（含菜品列表） */
    public Result<Map<String, Object>> getOrderDetail(Long orderId) {
        if (orderId == null) return Result.error(400, "orderId 不能为空");
        Order order = orderMapper.selectById(orderId);
        if (order == null) return Result.error(404, "订单不存在");

        // 权限校验：当前用户的活跃家庭必须等于订单的家庭
        Long curFamily = UserContext.getFamily();
        if (curFamily == null || !curFamily.equals(order.getFamilyId())) {
            return Result.error(403, "无权查看该订单");
        }

        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getOrderId, orderId)
                        .orderByAsc(MenuItem::getId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("familyId", order.getFamilyId());
        data.put("userId", order.getUserId());
        data.put("userNickname", order.getUserNickname());
        data.put("totalAmount", order.getTotalAmount());
        data.put("itemCount", order.getItemCount());
        // 统计菜品状态（已确认 / 已驳回）
        int confirmedCount = 0;
        int rejectedCount = 0;
        for (MenuItem mi : items) {
            if (mi.getStatus() != null && mi.getStatus() == 1) confirmedCount++;
            else if (mi.getStatus() != null && mi.getStatus() == 2) rejectedCount++;
        }
        data.put("confirmedCount", confirmedCount);
        data.put("rejectedCount", rejectedCount);
        data.put("status", order.getStatus());
        data.put("remark", order.getRemark());
        data.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
        data.put("confirmedAt", order.getConfirmedAt() != null ? order.getConfirmedAt().toString() : "");

        // 当前用户是否 OWNER/CHEF（决定能否显示确认按钮）；全局管理员也允许
        boolean canConfirm = false;
        String curUserId = UserContext.get();
        if (curUserId != null) {
            User curUser = userMapper.selectById(curUserId);
            if (curUser != null && curUser.getIsChef() != null && curUser.getIsChef() == 1) {
                canConfirm = true;
            } else {
                com.example.order.entity.FamilyMember me = memberMapper.selectOne(
                        new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                                .eq(com.example.order.entity.FamilyMember::getFamilyId, order.getFamilyId())
                                .eq(com.example.order.entity.FamilyMember::getUserId, curUserId));
                canConfirm = me != null && ("OWNER".equals(me.getRole()) || "CHEF".equals(me.getRole()));
            }
        }
        data.put("canConfirm", canConfirm);

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (MenuItem mi : items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("itemId", mi.getId());
            im.put("dishId", mi.getDishId());
            im.put("dishName", mi.getDishName());
            im.put("dishEmoji", mi.getDishEmoji());
            im.put("customIngs", mi.getCustomIngs());
            im.put("spiceLevel", mi.getSpiceLevel());
            im.put("price", mi.getPrice());
            im.put("remark", mi.getRemark());
            im.put("status", mi.getStatus());
            im.put("userNickname", mi.getUserNickname());
            im.put("createdAt", mi.getCreatedAt() != null ? mi.getCreatedAt().toString() : "");
            itemList.add(im);
        }
        data.put("items", itemList);
        return Result.ok(data);
    }

    // ==================== 确认 ====================

    /** 一键确认整个订单（主厨） */
    @Transactional
    public Result<Void> confirmOrder(Long orderId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        Order order = orderMapper.selectById(orderId);
        if (order == null) return Result.error(404, "订单不存在");

        // 必须本家庭成员，且必须是 OWNER/CHEF
        com.example.order.entity.FamilyMember me = memberMapper.selectOne(
                new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                        .eq(com.example.order.entity.FamilyMember::getFamilyId, order.getFamilyId())
                        .eq(com.example.order.entity.FamilyMember::getUserId, userId));
        if (me == null) return Result.error(403, "无权操作");
        if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
            return Result.error(403, "只有创建者或主厨才能确认");
        }

        if (order.getStatus() != null && order.getStatus() == 1) {
            return Result.error(400, "订单已确认");
        }

        LocalDateTime now = LocalDateTime.now();
        // 更新所有菜品状态（排除已撤销和已驳回的）
        menuItemMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MenuItem>()
                        .eq(MenuItem::getOrderId, orderId)
                        .notIn(MenuItem::getStatus, -1, 2)
                        .set(MenuItem::getStatus, 1)
                        .set(MenuItem::getConfirmedBy, userId)
                        .set(MenuItem::getConfirmedAt, now));

        // 同步订单状态：让 syncOrderStatus 决定（防止硬升级覆盖有驳回菜的情况）
        syncOrderStatus(orderId);
        // 通知下单人：整单已确认（只发一条，避免 N 个菜发 N 条）
        final Long notifyOrderId = orderId;
        pushService.pushAfterCommit(() -> pushService.pushOrderStatusChanged(notifyOrderId));
        return Result.ok();
    }

    /** 单菜确认（主厨） */
    @Transactional
    public Result<Void> confirmItem(Long itemId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        MenuItem item = menuItemMapper.selectById(itemId);
        if (item == null) return Result.error(404, "菜品不存在");

        // 全局管理员（is_chef=1）允许操作；否则需家庭内 OWNER/CHEF
        User operator = userMapper.selectById(userId);
        boolean isGlobalAdmin = operator != null && operator.getIsChef() != null && operator.getIsChef() == 1;
        if (!isGlobalAdmin) {
            com.example.order.entity.FamilyMember me = memberMapper.selectOne(
                    new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, item.getFamilyId())
                            .eq(com.example.order.entity.FamilyMember::getUserId, userId));
            if (me == null) return Result.error(403, "无权操作");
            if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
                return Result.error(403, "只有创建者或主厨才能确认");
            }
        }

        // 已撤销（-1）的菜品不能修改状态；其他状态（0/1/2）允许切换
        if (item.getStatus() != null && item.getStatus() == -1) {
            return Result.error(400, "菜品已撤销，不能修改状态");
        }
        item.setStatus(1);
        item.setConfirmedBy(userId);
        item.setConfirmedAt(LocalDateTime.now());
        menuItemMapper.updateById(item);

        // 检查订单内是否所有菜都已确认，若是则更新订单状态
        syncOrderStatus(item.getOrderId());
        // 通知下单人：这道菜好了（按菜品推送，只说他这道菜的状态）
        final Long notifyItemId = itemId;
        pushService.pushAfterCommit(() -> pushService.pushStatusChanged(notifyItemId));
        return Result.ok();
    }

    /** 单菜驳回（主厨） */
    @Transactional
    public Result<Void> rejectItem(Long itemId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        MenuItem item = menuItemMapper.selectById(itemId);
        if (item == null) return Result.error(404, "菜品不存在");

        // 全局管理员（is_chef=1）允许操作；否则需家庭内 OWNER/CHEF
        User operator = userMapper.selectById(userId);
        boolean isGlobalAdmin = operator != null && operator.getIsChef() != null && operator.getIsChef() == 1;
        if (!isGlobalAdmin) {
            com.example.order.entity.FamilyMember me = memberMapper.selectOne(
                    new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, item.getFamilyId())
                            .eq(com.example.order.entity.FamilyMember::getUserId, userId));
            if (me == null) return Result.error(403, "无权操作");
            if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
                return Result.error(403, "只有创建者或主厨才能驳回");
            }
        }

        // 已撤销（-1）的菜品不能修改状态；其他状态（0/1/2）允许切换
        if (item.getStatus() != null && item.getStatus() == -1) {
            return Result.error(400, "菜品已撤销，不能修改状态");
        }
        item.setStatus(2);
        item.setConfirmedBy(userId);
        item.setConfirmedAt(LocalDateTime.now());
        menuItemMapper.updateById(item);
        // 同步订单状态：把整笔订单同步重新计算（驳回会让已确认的订单降级为待确认）
        syncOrderStatus(item.getOrderId());
        // 单菜驳回也要让下单人知道（按菜品推送，只说他这道菜的状态）
        final Long notifyItemId = itemId;
        pushService.pushAfterCommit(() -> pushService.pushStatusChanged(notifyItemId));
        return Result.ok();
    }

    // ==================== 管理端：修改/删除订单 ====================

    /** 修改订单（管理端）: userId / totalAmount / remark 可改（订单状态由菜品状态自动同步，不允许手动改） */
    @Transactional
    public Result<Void> updateOrder(Long orderId, Map<String, Object> body) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (orderId == null) return Result.error(400, "orderId 不能为空");
        Order order = orderMapper.selectById(orderId);
        if (order == null) return Result.error(404, "订单不存在");

        if (body == null) return Result.error(400, "请求内容为空");

        // 下单者：只能改为本家庭（订单所属家庭）内的成员，不可改成其他家庭的成员
        if (body.get("userId") != null) {
            String newUserId;
            try {
                newUserId = body.get("userId").toString().trim();
            } catch (Exception e) {
                log.warn("[Order] userId 转换失败 body={}", body.get("userId"), e);
                return Result.error(400, "userId 格式不正确");
            }
            User newOwner = userMapper.selectById(newUserId);
            if (newOwner == null) return Result.error(404, "用户不存在");
            // 必须是订单所属家庭的成员
            com.example.order.entity.FamilyMember mem = memberMapper.selectOne(
                    new LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, order.getFamilyId())
                            .eq(com.example.order.entity.FamilyMember::getUserId, newUserId));
            if (mem == null) {
                return Result.error(403, "只能选择当前家庭内的成员作为下单者");
            }
            String nickname = mem.getNickname() != null && !mem.getNickname().isBlank()
                    ? mem.getNickname() : newOwner.getNickname();
            order.setUserId(newUserId);
            order.setUserNickname(nickname);
            // 订单内的菜品一并同步为新的下单者
            menuItemMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MenuItem>()
                            .eq(MenuItem::getOrderId, orderId)
                            .set(MenuItem::getUserId, newUserId)
                            .set(MenuItem::getUserNickname, nickname));
        }

        // 订单价格（总价）
        if (body.get("totalAmount") != null) {
            BigDecimal amt;
            try {
                amt = new BigDecimal(body.get("totalAmount").toString());
            } catch (Exception e) {
                log.warn("[Order] totalAmount 转换失败 body={}", body.get("totalAmount"), e);
                return Result.error(400, "价格格式不正确");
            }
            if (amt.compareTo(BigDecimal.ZERO) < 0) {
                return Result.error(400, "价格不能为负数");
            }
            order.setTotalAmount(amt);
        }

        // 备注
        if (body.containsKey("remark")) {
            Object r = body.get("remark");
            order.setRemark(r == null ? null : r.toString());
        }

        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return Result.ok();
    }

    /** 删除订单（管理端）: 级联删除菜单项 */
    @Transactional
    public Result<Void> deleteOrder(Long orderId) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (orderId == null) return Result.error(400, "orderId 不能为空");
        Order order = orderMapper.selectById(orderId);
        if (order == null) return Result.error(404, "订单不存在");

        menuItemMapper.delete(new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getOrderId, orderId));
        orderMapper.deleteById(orderId);
        return Result.ok();
    }

    /** 检查订单内全部菜品状态，同步更新订单状态（双向同步）
 *  规则：任一菜品 status==0（待确认）→ 订单=0（待确认）；否则（全部已确认/已驳回）→ 订单=1（已确认） */
    private void syncOrderStatus(Long orderId) {
        if (orderId == null) return;
        List<MenuItem> items = menuItemMapper.selectList(
                new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getOrderId, orderId));
        if (items.isEmpty()) return;
        int pending = 0;
        for (MenuItem mi : items) {
            if (mi.getStatus() == null || mi.getStatus() == 0) pending++;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) return;
        // 已撤销的订单不再改变状态
        if (order.getStatus() != null && order.getStatus() == -1) return;

        LocalDateTime now = LocalDateTime.now();
        if (pending > 0) {
            // 还有待确认菜品 → 订单为待确认
            if (order.getStatus() == null || order.getStatus() != 0) {
                order.setStatus(0);
                order.setUpdatedAt(now);
                orderMapper.updateById(order);
            }
        } else {
            // 所有菜品都已结案（已确认 / 已驳回） → 订单升级为已确认
            if (order.getStatus() == null || order.getStatus() != 1) {
                order.setStatus(1);
                order.setUpdatedAt(now);
                if (order.getConfirmedAt() == null) order.setConfirmedAt(now);
                orderMapper.updateById(order);
            }
        }
    }
}
