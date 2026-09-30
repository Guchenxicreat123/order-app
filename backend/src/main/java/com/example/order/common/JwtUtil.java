package com.example.order.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.user-expire-millis}")
    private long userExpireMillis;

    @Value("${jwt.admin-expire-millis}")
    private long adminExpireMillis;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 普通用户 token，subject = "u:" + openId */
    public String generateUserToken(String openId, int tokenVersion) {
        return buildToken("u:" + openId, openId, tokenVersion, userExpireMillis);
    }

    /** 家庭主厨 token，subject = "c:" + openId（isChef=1 的用户） */
    public String generateChefToken(String openId, int tokenVersion) {
        return buildToken("c:" + openId, openId, tokenVersion, adminExpireMillis);
    }

    /** 平台管理员 token，subject = "a:" + openId（仅后台 admin 账号签发） */
    public String generateAdminToken(String openId, int tokenVersion) {
        return buildToken("a:" + openId, openId, tokenVersion, adminExpireMillis);
    }

    /** 兼容旧签名：版本 0 */
    public String generateUserToken(String openId) {
        return generateUserToken(openId, 0);
    }

    public String generateChefToken(String openId) {
        return generateChefToken(openId, 0);
    }

    public String generateAdminToken(String openId) {
        return generateAdminToken(openId, 0);
    }

    private String buildToken(String subject, String uid, int tokenVersion, long expireMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("uid", uid)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 token，返回角色、openId、签发时的 token 版本号。
     * 缺 tv claim 的历史 token 按 0 处理，避免本次改动误伤已登录用户。
     */
    public TokenInfo parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String subject = claims.getSubject();
        if (subject == null || subject.length() < 2) {
            throw new IllegalArgumentException("invalid token subject");
        }
        String prefix = subject.substring(0, 2);
        String openId = claims.get("uid", String.class);
        Number tv = claims.get("tv", Number.class);
        int tokenVersion = tv == null ? 0 : tv.intValue();
        if ("a:".equals(prefix)) {
            return new TokenInfo("ADMIN", openId, tokenVersion);
        }
        if ("c:".equals(prefix)) {
            return new TokenInfo("CHEF", openId, tokenVersion);
        }
        return new TokenInfo("USER", openId, tokenVersion);
    }

    public record TokenInfo(String role, String openId, int tokenVersion) {}
}
