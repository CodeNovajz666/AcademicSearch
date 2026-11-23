package com.example.literaturesearch.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
<<<<<<< HEAD
import java.util.UUID;
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
import java.util.function.Function;

@Component
public class JwtUtil {

<<<<<<< HEAD
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "type";

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
<<<<<<< HEAD
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return createToken(claims, subjectOf(userDetails), expiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
<<<<<<< HEAD
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return createToken(claims, subjectOf(userDetails), refreshExpiration);
    }

    /**
<<<<<<< HEAD
     * 令牌类型。历史令牌（类型区分引入前签发）没有该 claim，返回 null——
     * 调用方按 access 处理以兼容存量登录态，refresh 端点同样接受 null。
     */
    public String extractType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class));
    }

    /** 令牌唯一 id（jti）。历史令牌没有该 claim 返回 null——登出吊销只对有 jti 的令牌生效。 */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
     * token 主题统一使用 email：UserDetails.getUsername() 返回的是注册用户名，
     * 而 JwtAuthenticationFilter 按邮箱查询用户，两者不一致会导致认证 403。
     */
    private static String subjectOf(UserDetails userDetails) {
        return userDetails instanceof com.example.literaturesearch.entity.User user
                ? user.getEmail()
                : userDetails.getUsername();
    }

    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
<<<<<<< HEAD
                // jti：登出吊销按该 id 拉黑 refresh 令牌
                .id(UUID.randomUUID().toString())
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String subject = extractEmail(token);
        return (subject.equals(subjectOf(userDetails)) && !isTokenExpired(token));
    }
}
