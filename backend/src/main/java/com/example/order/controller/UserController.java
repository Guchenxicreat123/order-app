package com.example.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.JwtUtil;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.User;
import com.example.order.mapper.UserMapper;
import com.example.order.service.ChefService;
import com.example.order.service.PushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 微信登录 + 主厨相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired private UserMapper userMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ChefService chefService;
    @Autowired private PushService pushService;
    @Autowired private com.example.order.service.FamilyService familyService;
    @Autowired private com.example.order.service.NicknameSyncService nicknameSyncService;

    private final RestTemplate RT = new RestTemplate();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.force-real:false}")
    private boolean forceReal;

    /** 管理后台 admin 账号对应的用户 openId */
    @Value("${app.admin-openid:}")
    private String adminOpenId;

    // ==================== 微信登录 ====================

    /**
     * 解析当前请求对应的 openId（微信 code / 开发模式 deviceCode）。
     * 只做身份解析，不碰数据库 —— 供「查用户是否存在」和「登录」共用，
     * 保证两处用的是同一套身份规则。
     * 解析失败时返回 null，错误信息放在 err[0]。
     */
    private String resolveOpenId(String code, String deviceCode, String[] err) {
        // ===== mock 开发模式：WECHAT_SECRET 未配置且非强制真实微信 =====
        // 同一设备每次上报持久 deviceCode（dev_xxx），后端拼 mock_ 前缀做 openid，
        // 与历史 mock 数据（mock_dev_...）完全兼容，可复用老用户不产生新用户。
        boolean mockMode = (secret == null || secret.isBlank()) && !forceReal;
        if (mockMode) {
            String local = null;
            if (deviceCode != null && !deviceCode.isBlank()) {
                local = deviceCode;
            } else if (code != null
                    && (code.startsWith("dev_") || code.startsWith("mock_") || code.startsWith("test_"))) {
                local = code; // 兼容旧客户端：直接把本地风格 code 当身份
            }
            if (local == null) {
                err[0] = "服务器未配置 WECHAT_SECRET，无法完成微信登录（开发模式请携带 deviceCode）";
                return null;
            }
            return local.startsWith("mock_") ? local : "mock_" + local;
        }

        if (appid == null || appid.isBlank()) {
            err[0] = "服务器未配置 WECHAT_APPID，无法登录小程序";
            return null;
        }
        try {
            return fetchOpenId(code);
        } catch (Exception e) {
            log.error("[WxLogin] jscode2session 失败 codePrefix={}", code == null ? "null" : code.substring(0, Math.min(4, code.length())), e);
            err[0] = "微信登录失败：" + e.getMessage();
            return null;
        }
    }

    /**
     * 只判断「这个用户是否已经在库里」，**绝不写入数据库**。
     * 小程序刚打开时调用：
     *   exists=true  → 老用户，直接登录进小程序
     *   exists=false → 新用户，等用户点「立即登录」；不点就不在库里留下任何记录
     */
    @PostMapping("/wx/exists")
    public Result<Map<String, Object>> wxExists(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        String deviceCode = body == null ? null : body.get("deviceCode");
        if ((code == null || code.isBlank()) && (deviceCode == null || deviceCode.isBlank())) {
            return Result.error("code 不能为空");
        }
        String[] err = new String[1];
        String openId = resolveOpenId(code, deviceCode, err);
        if (openId == null) {
            return Result.error(500, err[0] == null ? "无法识别用户身份" : err[0]);
        }
        User user = userMapper.selectById(openId);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("exists", user != null);
        data.put("openId", openId);
        if (user != null) {
            data.put("nickname", user.getNickname());
            data.put("avatarUrl", user.getAvatarUrl());
            data.put("isChef", user.getIsChef() != null && user.getIsChef() == 1);
        }
        return Result.ok(data);
    }

    @PostMapping("/wx/login")
    public Result<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Result.error("code 不能为空");
        }
        String deviceCode = body.get("deviceCode");

        String[] err = new String[1];
        String openId = resolveOpenId(code, deviceCode, err);
        if (openId == null) {
            return Result.error(500, err[0] == null ? "无法识别用户身份" : err[0]);
        }
        // 用户同意登录 → 这里才会真正写入数据库（新用户建行）
        return wxLoginAs(openId);
    }

    /** 按 openId 查/建用户并签发 token（真实微信登录与 mock 登录共用） */
    private Result<Map<String, Object>> wxLoginAs(String openId) {
        User user = userMapper.selectById(openId);
        boolean isNewUser = user == null;
        if (user == null) {
            user = new User();
            user.setOpenId(openId);
            // 默认昵称取 openId 后 6 位（之前是前 6 位：前端 safeName() 校验 name 类型
            // 字段时，汉字+字母+数字混合都会被降级成「家人」，所以推送里看不到这个昵称；
            // 改成后 6 位后，至少在「我的」页面 / 订单卡片里看起来有差异。要在推送里展示，
            // 得让用户手动改一次纯汉字昵称——safeName 不会把字母数字自动去掉。
            user.setNickname("用户" + openId.substring(Math.max(0, openId.length() - 6)));
            // 头像保持 NULL：新用户需要自己选一个头像（前端据此判断是否弹「完善资料」卡）。
            // 不要在这里预置默认头像，否则前端无法区分「没选过」和「选过了」。
            user.setAvatarUrl(null);
            user.setIsChef(0);
            user.setAllowPush(0);
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }

        // 决定 token 类型
        String token = user.getIsChef() != null && user.getIsChef() == 1
                ? jwtUtil.generateChefToken(user.getOpenId(), ver(user))
                : jwtUtil.generateUserToken(user.getOpenId(), ver(user));

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getOpenId()); // 返回 openId 替代原数字 id
        data.put("openId", user.getOpenId());
        data.put("nickname", user.getNickname());
        data.put("avatarUrl", user.getAvatarUrl());  // 头像 key，前端拼 /static/avatars/X.svg
        data.put("allowPush", user.getAllowPush() != null && user.getAllowPush() == 1);
        data.put("isChef", user.getIsChef() != null && user.getIsChef() == 1);
        // 本次请求是否新建了用户：前端据此弹「使用微信昵称」引导。
        // 微信不允许静默获取昵称（getUserProfile 已废弃），只能靠 <input type="nickname">
        // 让用户点一下确认，所以只能"引导"，不能"自动设置"。
        data.put("isNewUser", isNewUser);
        // 附带家庭信息（含自动加入默认家庭）
        var fam = familyService.loginFamilies(user.getOpenId());
        data.putAll(fam);

        return Result.ok(data);
    }

    private String fetchOpenId(String code) throws Exception {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, secret, code);
        // 微信对错误响应返回 text/plain 的 JSON body，RestTemplate 直接转 Map 会因
        // Content-Type 不匹配而失败，故先取 String 再手动解析，错误信息更友好。
        String raw = RT.getForObject(url, String.class);
        if (raw == null || raw.isBlank()) throw new RuntimeException("微信接口返回为空");
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> resp;
        try {
            resp = om.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("微信返回无法解析：" + raw.substring(0, Math.min(100, raw.length())));
        }
        Object errcode = resp.get("errcode");
        if (errcode != null && !errcode.equals(0)) {
            throw new RuntimeException("微信错误：" + resp.get("errmsg") + " (errcode=" + errcode + ")");
        }
        Object openId = resp.get("openid");
        if (openId == null) throw new RuntimeException("微信未返回 openid");
        return openId.toString();
    }

    // ==================== 主厨接口 ====================

    /** 主厨状态查询（公开） */
    @GetMapping("/me/chef-status")
    public Result<?> chefStatus() {
        return chefService.chefStatus(UserContext.getFamily());
    }

    /** 绑定推送授权（同步 t_user.allow_push，0 只表示「没授权过」不表示拒收） */
    @PostMapping("/push/auth")
    public Result<?> bindPushAuth(@RequestBody Map<String, Boolean> body) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");
        boolean allow = body.getOrDefault("allow", false);
        return pushService.bindPushAuth(openId, allow);
    }

    /**
     * 订阅授权上报。小程序端 {@code requestSubscribeMessage} 拿到 "accept" 后调用。
     *
     * <p>微信只告诉我们「用户点了允许」，不告诉我们还能推几条、也不提供查询接口，
     * 所以由客户端在小程序里点一次报一次，服务端记账。body 形如：
     * <pre>{"templateId":"xxx","count":1,"role":"CHEF"}</pre>
     */
    @PostMapping("/push/subscribe-grant")
    public Result<?> subscribeGrant(@RequestBody Map<String, Object> body) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");
        String templateId = body.get("templateId") == null ? "" : body.get("templateId").toString();
        int count = 1;
        Object c = body.get("count");
        if (c instanceof Number) count = ((Number) c).intValue();
        if (templateId.isBlank()) return Result.error(400, "templateId 不能为空");
        int remain = pushService.reportSubscribeGrant(openId, templateId, count);
        return Result.ok(Map.of("remain", remain));
    }

    /** 推送配置：模板 ID + 当前剩余额度（客户端决定要不要弹授权框） */
    @GetMapping("/push/config")
    public Result<?> pushConfig(@RequestParam(value = "role", required = false) String role) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");
        return Result.ok(pushService.pushConfig(openId, role));
    }

    /** 推送测试（仅主厨）：给自己真发一条「下单成功通知」，用于验证模板是否生效 */
    @PostMapping("/push/test")
    public Result<?> pushTest() {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");
        if (!chefService.isChef(openId)) return Result.error(403, "需要主厨权限");
        return Result.ok(pushService.sendTestToChef());
    }

    /** 推送概况 + 最近推送记录（仅主厨），出问题时能直接在手机上看到原因 */
    @GetMapping("/push/log")
    public Result<?> pushLog(@RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");
        if (!chefService.isChef(openId)) return Result.error(403, "需要主厨权限");
        Long familyId = UserContext.getFamily();
        return Result.ok(Map.of(
                "stats", pushService.stats(familyId),
                "logs", pushService.recentLogs(familyId, limit)
        ));
    }

    // ====== 管理后台登录（仅平台管理员账号）======
    /**
     * admin-web 桌面后台登录。
     * 只接受固定管理员账号 admin（映射到配置的 APP_ADMIN_OPENID 用户），
     * 普通家庭主厨不能登录后台——他们的管理操作在小程序端完成。
     */
    @PostMapping("/chef/login")
    public Result<?> chefLogin(@RequestBody Map<String, String> body) {
        String userIdStr = body.get("userId");
        String pin = body.get("pin");
        if (userIdStr == null || pin == null) {
            return Result.error("userId 和 pin 不能为空");
        }
        // 仅允许固定管理员账号 admin
        if (!"admin".equalsIgnoreCase(userIdStr.trim())) {
            return Result.error(403, "该账号无后台管理权限，家庭管理请使用小程序");
        }
        String openId = adminOpenId;
        if (openId == null || openId.isBlank()) {
            return Result.error(500, "服务器未配置 APP_ADMIN_OPENID");
        }
        User user = userMapper.selectById(openId);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }
        if (user.getIsChef() == null || user.getIsChef() != 1) {
            return Result.error(403, "该账号不是主厨，无法登录后台");
        }
        String expectedPin = user.getChefPin() != null ? user.getChefPin() : "123456";
        if (!expectedPin.equals(pin)) {
            return Result.error(401, "PIN 错误");
        }
        int tv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        // 后台登录签发 ADMIN token（区别于小程序主厨的 CHEF token）
        String token = jwtUtil.generateAdminToken(openId, tv);
        return Result.ok(java.util.Map.of(
                "token", token,
                "userId", openId,
                "nickname", user.getNickname() != null ? user.getNickname() : "管理员"
        ));
    }

    /** 读取用户当前 token 版本号（null 视为 0） */
    private static int ver(User u) {
        return u == null || u.getTokenVersion() == null ? 0 : u.getTokenVersion();
    }

    /**
     * 修改当前用户昵称。
     * 小程序「我的」页编辑用户名的接口；改后 t_user.nickname 即时生效。
     * 同时把 t_family_member / t_order / t_menu_item 里冗余的快照昵称
     * 全部刷新为最新值，保证家庭成员名、主厨名、订单下单者名全站一致。
     */
    @PutMapping("/me/nickname")
    public Result<Map<String, Object>> updateNickname(@RequestBody Map<String, String> body) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");

        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return Result.error(400, "昵称不能为空");
        }
        nickname = nickname.trim();
        if (nickname.length() > 20) {
            return Result.error(400, "昵称最长 20 个字符");
        }
        // 去掉控制字符（换行等）
        nickname = nickname.replaceAll("[\\r\\n\\t]", " ").trim();

        try {
            nicknameSyncService.changeNicknameAndSync(openId, nickname);
        } catch (Exception e) {
            log.error("[User] 昵称保存失败 openId={}", openId, e);
            return Result.error(500, "昵称保存失败：" + e.getMessage());
        }

        return Result.ok(java.util.Map.of(
                "nickname", nickname,
                "userId", openId
        ));
    }

    /**
     * 修改当前用户的头像 key（前端预制 SVG 标识）。
     * avatarKey 取值范围 avatar-1 ~ avatar-6，前端拼成 /static/avatars/avatar-X.svg 展示。
     * 同时把 t_family_member / t_order / t_menu_item 里冗余的快照 avatar 全部刷新。
     */
    @PutMapping("/me/avatar")
    public Result<Map<String, Object>> updateAvatar(@RequestBody Map<String, String> body) {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "请先登录");

        String avatarKey = body.get("avatarKey");
        if (avatarKey == null || avatarKey.isBlank()) {
            return Result.error(400, "avatarKey 不能为空");
        }
        // 白名单校验：只允许 avatar-1 ~ avatar-6
        if (!avatarKey.matches("^avatar-[1-6]$")) {
            return Result.error(400, "avatarKey 非法，应为 avatar-1 ~ avatar-6");
        }

        User u = userMapper.selectById(openId);
        if (u == null) return Result.error(404, "用户不存在");
        u.setAvatarUrl(avatarKey);  // 复用 avatarUrl 字段存 key
        u.setUpdatedAt(LocalDateTime.now());
        try {
            userMapper.updateById(u);
        } catch (Exception e) {
            log.error("[User] 头像保存失败 openId={}", openId, e);
            return Result.error(500, "头像保存失败：" + e.getMessage());
        }
        // 同步家庭成员快照
        try {
            nicknameSyncService.changeNicknameAndSync(openId, u.getNickname());
        } catch (Exception e) {
            // 昵称同步失败不影响头像保存
            log.warn("[User] 头像同步后昵称刷新失败 openId={} msg={}", openId, e.getMessage());
        }

        return Result.ok(java.util.Map.of(
                "avatarKey", avatarKey,
                "userId", openId
        ));
    }

    /**
     * 主动退出登录：递增当前用户 token_version，所有已签发的 token 立刻失效。
     * 前端调用后清掉本地加密存储即可。
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        String openId = UserContext.get();
        if (openId == null) return Result.error(401, "未登录");
        User u = userMapper.selectById(openId);
        if (u == null) return Result.error(404, "用户不存在");
        int cur = u.getTokenVersion() == null ? 0 : u.getTokenVersion();
        u.setTokenVersion(cur + 1);
        userMapper.updateById(u);
        return Result.ok();
    }
}