package com.example.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 微信小程序全局接口调用凭证（access_token）管理。
 *
 * <p>微信规定 access_token 全局唯一，且「获取」和「刷新」共用同一个接口，
 * 两次调用间隔太近会让上一次的 token 失效。因此这里做了三层保护：
 * <ol>
 *   <li><b>进程内缓存</b>：拿到后缓存 7000 秒（微信给 7200 秒），提前 200 秒刷新；</li>
 *   <li><b>单飞（single-flight）</b>：多个线程同时过期时只放一个去换，其它等结果；</li>
 *   <li><b>失败退避</b>：换失败后 60 秒内不再重试，避免微信限频（45009）。</li>
 * </ol>
 *
 * <p>生产环境多实例部署时应改为把 token 存 Redis / 数据库，本项目单实例，进程内缓存足够。
 */
@Slf4j
@Service
public class WeChatTokenService {

    /** 微信返回的默认有效期 7200 秒，这里提前 200 秒刷新 */
    private static final long REFRESH_AHEAD_MS = 200 * 1000L;
    /** 换 token 失败后的退避时间，避免被微信 45009 限频 */
    private static final long FAIL_BACKOFF_MS = 60 * 1000L;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    private final RestTemplate rt = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    private final Object lock = new Object();
    private volatile String token = null;
    private final AtomicLong expiresAt = new AtomicLong(0);
    private final AtomicLong failUntil = new AtomicLong(0);
    private volatile String lastError = null;

    /**
     * 取一个可用的 access_token。
     *
     * @return 成功返回 token；未配置 appid/secret、或微信接口报错时返回 {@code null}（调用方应降级为只记日志）
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        String cached = token;
        if (cached != null && now < expiresAt.get()) return cached;

        synchronized (lock) {
            // 双检：可能已经被其它线程换好了
            cached = token;
            if (cached != null && System.currentTimeMillis() < expiresAt.get()) return cached;
            if (System.currentTimeMillis() < failUntil.get()) {
                log.debug("[WxToken] 处于失败退避期，跳过获取。lastError={}", lastError);
                return null;
            }
            return fetchLocked();
        }
    }

    /** 强制丢弃缓存，下次调用重新获取（微信返回 40001/42001 时用） */
    public void invalidate() {
        synchronized (lock) {
            token = null;
            expiresAt.set(0);
        }
        log.warn("[WxToken] 已失效本地缓存，下次调用将重新获取");
    }

    private String fetchLocked() {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            lastError = "未配置 wechat.appid / wechat.secret";
            log.warn("[WxToken] {}，订阅消息将走 mock（只写日志不真发）", lastError);
            failUntil.set(System.currentTimeMillis() + FAIL_BACKOFF_MS);
            return null;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + appid + "&secret=" + secret;
        try {
            String raw = rt.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) throw new IllegalStateException("微信接口返回为空");
            Map<String, Object> resp = om.readValue(raw, new TypeReference<Map<String, Object>>() {});
            Object t = resp.get("access_token");
            if (t == null || String.valueOf(t).isBlank()) {
                // 微信的经典坑：用同一个 secret 换 token 会让上一次的失效，别在这里刷屏重试
                lastError = "errcode=" + resp.get("errcode") + " errmsg=" + resp.get("errmsg");
                failUntil.set(System.currentTimeMillis() + FAIL_BACKOFF_MS);
                log.error("[WxToken] 获取失败 {}，{} 秒内不再重试", lastError, FAIL_BACKOFF_MS / 1000);
                return null;
            }
            token = String.valueOf(t);
            Object expiresIn = resp.get("expires_in");
            long ttlMs = expiresIn instanceof Number ? ((Number) expiresIn).longValue() * 1000L : 7200_000L;
            expiresAt.set(System.currentTimeMillis() + Math.max(ttlMs - REFRESH_AHEAD_MS, 60_000L));
            lastError = null;
            log.info("[WxToken] 获取成功，有效期 {} 秒，将于 {} 秒后自动刷新",
                    ttlMs / 1000, (ttlMs - REFRESH_AHEAD_MS) / 1000);
            return token;
        } catch (Exception e) {
            lastError = e.getMessage();
            failUntil.set(System.currentTimeMillis() + FAIL_BACKOFF_MS);
            log.error("[WxToken] 获取异常：{}", lastError, e);
            return null;
        }
    }

    /** 每 30 分钟主动续期一次，避免第一个真实请求撞上换 token 的延迟 */
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void warmUp() {
        String t = getAccessToken();
        if (t != null) log.debug("[WxToken] 定时续期完成");
    }

    /** 给后台/诊断接口用：当前状态（不返回 token 明文） */
    public Map<String, Object> status() {
        long exp = expiresAt.get();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("configured", appid != null && !appid.isBlank() && secret != null && !secret.isBlank());
        m.put("cached", token != null);
        m.put("expiresInMs", exp > System.currentTimeMillis() ? exp - System.currentTimeMillis() : 0);
        m.put("lastError", lastError);
        return m;
    }
}
