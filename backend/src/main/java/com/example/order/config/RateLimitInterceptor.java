package com.example.order.config;

import com.example.order.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录接口限流器（基于 IP 的滑动窗口）
 *
 * 实现要点：
 *   - 每个 IP 独立计数；按"路径前缀"分组（同一个 IP 在不同路径的计数互不影响）
 *   - 窗口长度 60 秒；超过阈值直接 429
 *   - 内存 ConcurrentHashMap；不需要持久化、不需要分布式一致
 *     （单机部署够用；多实例可换 Redis 限流）
 *   - 进程重启清零；冷启动不会误伤
 *
 * 默认阈值：
 *   - /api/wx/login       : 10 次/分钟（普通用户登录）
 *   - /api/admin/login    : 5 次/分钟（管理后台登录）
 *   - 其他路径            : 不限
 *
 * 通过环境变量覆盖：
 *   RATE_LIMIT_LOGIN=20        把 /api/wx/login 阈值调到 20
 *   RATE_LIMIT_ADMIN=3         把 /api/admin/login 阈值调到 3
 *   RATE_LIMIT_ENABLED=false   关闭整个限流器
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 单 IP 在窗口内允许的最大请求数（按路径前缀分桶） */
    private static final int DEFAULT_LOGIN_LIMIT = Integer.parseInt(
            System.getenv().getOrDefault("RATE_LIMIT_LOGIN", "10"));
    private static final int DEFAULT_ADMIN_LIMIT = Integer.parseInt(
            System.getenv().getOrDefault("RATE_LIMIT_ADMIN", "5"));
    private static final boolean ENABLED = !Boolean.parseBoolean(
            System.getenv().getOrDefault("RATE_LIMIT_ENABLED", "true"));

    /** key = ip + "|" + bucket, value = 当前窗口的计数 */
    private final ConcurrentHashMap<String, AtomicInteger> window = new ConcurrentHashMap<>();

    /** key = ip + "|" + bucket, value = 窗口起始时间（毫秒） */
    private final ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!ENABLED) return true;
        String path = request.getRequestURI();
        String bucket = bucketOf(path);
        if (bucket == null) return true; // 非限流路径直接放行
        int limit = limitOf(bucket);

        String key = clientIp(request) + "|" + bucket;
        long now = System.currentTimeMillis();

        // 滑动窗口：检查是否需要重置
        Long start = windowStart.get(key);
        if (start == null || now - start > WINDOW_MS) {
            windowStart.put(key, now);
            window.put(key, new AtomicInteger(0));
        }

        int count = window.get(key).incrementAndGet();
        if (count > limit) {
            log.warn("[RateLimit] 触发限流 ip={} path={} count={}/{} windowMs={}",
                    clientIp(request), path, count, limit, WINDOW_MS);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }
        return true;
    }

    private String bucketOf(String path) {
        if (path.startsWith("/api/admin/login")) return "admin";
        if (path.startsWith("/api/wx/login"))    return "wx";
        if (path.startsWith("/api/admin/logout")) return "admin"; // 限制暴力登出循环
        return null;
    }

    private int limitOf(String bucket) {
        return switch (bucket) {
            case "admin" -> DEFAULT_ADMIN_LIMIT;
            case "wx"    -> DEFAULT_LOGIN_LIMIT;
            default      -> Integer.MAX_VALUE;
        };
    }

    /** 客户端 IP：优先取 X-Forwarded-For（Cloudflare 隧道后会带），否则取 remoteAddr */
    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            // 形如 "client, proxy1, proxy2"，取第一个
            int comma = fwd.indexOf(',');
            return (comma < 0 ? fwd : fwd.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }

    /** 测试用：暴露当前内存用量 */
    public int size() {
        return window.size();
    }
}