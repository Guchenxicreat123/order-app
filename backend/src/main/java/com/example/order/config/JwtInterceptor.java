package com.example.order.config;

import com.example.order.common.JwtUtil;
import com.example.order.common.JwtUtil.TokenInfo;
import com.example.order.common.UserContext;
import com.example.order.entity.User;
import com.example.order.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.example.order.mapper.FamilyMemberMapper familyMemberMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // ============= 平台管理员专属接口（仅 ADMIN token）=============
        // /api/admin/* 全平台管理（用户/家庭/公共库），只有后台 admin 账号可用
        // /api/public/dishes|ingredients 的写操作（POST/PUT/DELETE）同属平台级管理
        if (path.startsWith("/api/admin")
                || (path.startsWith("/api/public/dishes") && !"GET".equals(method))
                || (path.startsWith("/api/public/ingredients") && !"GET".equals(method))) {
            return requireAdmin(request, response);
        }

        // ============= 主厨专属接口（优先判断，避免被 isPublic 错误放行）=============
        // /api/chef/* 主厨功能
        // POST /api/dishes 新建菜谱
        // PUT|DELETE /api/dishes/{id} 更新/删除菜谱
        // POST|PUT|DELETE /api/ingredients
        // /api/ingredients/manage/* 主厨配菜管理
        // /api/menu/items/* 确认/撤销
        // /api/chef/login 公开；/api/chef/* 其他主厨功能需要 token
        boolean isChefPath =
                (path.startsWith("/api/chef/") && !path.equals("/api/chef/login"))
                || (path.equals("/api/dishes") && "POST".equals(method))
                || path.startsWith("/api/dishes/manage")          // 主厨管理菜谱列表
                || (path.startsWith("/api/dishes/") && !"GET".equals(method))
                || (path.equals("/api/ingredients") && !"GET".equals(method))
                || (path.startsWith("/api/ingredients/") && !"GET".equals(method))
                || path.startsWith("/api/ingredients/manage")
                || (path.startsWith("/api/ingredient-categories") && !"GET".equals(method))
                // 确认必须主厨；撤销由 service 层判断（下单人可撤自己的，主厨可撤任何人）
                || (path.startsWith("/api/menu/items/") && !"DELETE".equals(method));

        if (isChefPath) {
            return requireChef(request, response);
        }

        // ============= 公开接口 =============
        if (isPublic(path)) {
            return true;
        }

        // ============= 需登录的用户接口 =============
        return requireUser(request, response);
    }

    private boolean isPublic(String path) {
        return path.startsWith("/api/wx/login")
                || path.startsWith("/api/wx/exists")   // 只查用户是否存在，不写库、无需登录
                || path.equals("/api/chef/login")
                || path.startsWith("/hello")
                || path.startsWith("/api/categories")
                || path.startsWith("/api/rate/")
                || path.startsWith("/api/push/auth")
                || path.startsWith("/api/public");  // 公共菜品库，无需登录
    }

    /**
     * 平台管理员接口鉴权：仅接受 ADMIN 角色 token（后台 admin 账号登录签发）。
     * 普通家庭主厨（CHEF token）没有全平台管理权限。
     */
    private boolean requireAdmin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = extractToken(request);
        if (token == null) {
            return authFail(response, "请先登录", 401);
        }
        try {
            TokenInfo info = jwtUtil.parseToken(token);
            User user = loadAndVerify(info);
            if (!"ADMIN".equals(info.role())) {
                return authFail(response, "无后台管理权限", 403);
            }
            UserContext.setChef(info.openId());
            applyFamilyHeader(request, user);
            return true;
        } catch (RevokedException e) {
            log.warn("[Auth] token 已撤销 uri={}", request.getRequestURI());
            return authFail(response, "登录已失效，请重新登录", 401);
        } catch (Exception e) {
            log.warn("[Auth] token 解析失败 uri={} msg={}", request.getRequestURI(), e.getMessage());
            return authFail(response, "token 无效或已过期", 401);
        }
    }

    private boolean requireChef(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = extractToken(request);
        if (token == null) {
            return authFail(response, "请先登录", 401);
        }
        try {
            TokenInfo info = jwtUtil.parseToken(token);
            // 校验 token 版本（撤销检查），顺带拿到 user 与活跃家庭
            User user = loadAndVerify(info);

            if ("CHEF".equals(info.role())) {
                // 主厨 token（管理后台）
                UserContext.setChef(info.openId());
                applyFamilyHeader(request, user);   // 管理端可按 X-Family-Id 切换查看家庭
                return true;
            }
            if ("USER".equals(info.role())) {
                // 家庭模型：当前活跃家庭的主厨成员可管理
                UserContext.set(info.openId());
                Long familyId = UserContext.getFamily();
                if (familyId != null && isFamilyChef(info.openId(), familyId)) {
                    return true;
                }
                return authFail(response, "需要主厨权限", 403);
            }
            return authFail(response, "需要主厨权限", 403);
        } catch (RevokedException e) {
            log.warn("[Auth-CHEF] token 已撤销 uri={}", request.getRequestURI());
            return authFail(response, "登录已失效，请重新登录", 401);
        } catch (Exception e) {
            log.warn("[Auth-CHEF] token 解析失败 uri={} msg={}", request.getRequestURI(), e.getMessage());
            return authFail(response, "token 无效或已过期", 401);
        }
    }

    /** 判断用户是否为某家庭的主厨成员 */
    private boolean isFamilyChef(String openId, Long familyId) {
        try {
            var m = familyMemberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.order.entity.FamilyMember>()
                            .eq(com.example.order.entity.FamilyMember::getFamilyId, familyId)
                            .eq(com.example.order.entity.FamilyMember::getUserId, openId)
                            .eq(com.example.order.entity.FamilyMember::getRole, "CHEF"));
            return m != null;
        } catch (Exception e) {
            log.debug("[Auth-CHEF] isFamilyChef 查询异常 openId={} familyId={}", openId, familyId, e);
            return false;
        }
    }

    private boolean requireUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = extractToken(request);
        if (token == null) {
            return authFail(response, "请先登录", 401);
        }
        try {
            TokenInfo info = jwtUtil.parseToken(token);
            User user = loadAndVerify(info);
            UserContext.set(info.openId());
            applyFamilyHeader(request, user);
            return true;
        } catch (RevokedException e) {
            log.warn("[Auth-USER] token 已撤销 uri={}", request.getRequestURI());
            return authFail(response, "登录已失效，请重新登录", 401);
        } catch (Exception e) {
            log.warn("[Auth-USER] token 解析失败 uri={} msg={}", request.getRequestURI(), e.getMessage());
            return authFail(response, "token 无效或已过期", 401);
        }
    }

    /**
     * 加载用户并校验 token 版本号，撤销检查。
     * 副作用：从 user.active_family_id 同步到 UserContext。
     * 返回 user 便于复用（避免 applyFamilyHeader 再查一次）。
     */
    private User loadAndVerify(TokenInfo info) {
        User user = userMapper.selectById(info.openId());
        if (user == null) {
            throw new RevokedException("user not found");
        }
        int dbVer = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (dbVer != info.tokenVersion()) {
            throw new RevokedException("token version mismatch");
        }
        if (user.getActiveFamilyId() != null) {
            UserContext.setFamily(user.getActiveFamilyId());
        }
        return user;
    }

    /** token 已撤销（版本号不匹配 / 用户已删除）专用异常，用于区分提示文案 */
    private static class RevokedException extends RuntimeException {
        RevokedException(String msg) { super(msg); }
    }

    /**
     * 管理后台家庭切换：主厨 token（CHEF 角色）可在请求头 X-Family-Id 指定要查看的家庭。
     * 仅当该用户是全局主厨（is_chef=1，管理员）或该家庭主厨成员时允许切换，
     * 避免普通成员越权查看其它家庭数据。
     * user 参数由 loadAndVerify 传入，省一次 DB 查询。
     */
    private void applyFamilyHeader(HttpServletRequest request, User user) {
        try {
            String header = request.getHeader("X-Family-Id");
            if (header == null || header.isBlank()) return;
            Long target = Long.parseLong(header.trim());
            String openId = UserContext.get();
            if (openId == null || target == null) return;

            boolean globalChef = user != null && user.getIsChef() != null && user.getIsChef() == 1;
            boolean familyChef = isFamilyChef(openId, target);
            if (!globalChef && !familyChef) {
                return; // 无权限切换，保持默认家庭
            }
            UserContext.setFamily(target);
        } catch (Exception e) {
            log.debug("[Auth] applyFamilyHeader 异常 header={}", request.getHeader("X-Family-Id"), e);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean authFail(HttpServletResponse response, String msg, int status) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + msg + "\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}