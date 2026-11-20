package com.boxue.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String issuer;
    private final SecretKey clientKey;
    private final SecretKey adminKey;
    private final long accessTtlSeconds;
    private final long refreshTtlDays;

    public JwtService(
            @Value("${boxue.security.jwt.issuer:boxue}") String issuer,
            @Value("${boxue.security.jwt.client-secret:dev-client-secret-change-me-32-bytes}") String clientSecret,
            @Value("${boxue.security.jwt.admin-secret:dev-admin-secret-change-me-32-bytes}") String adminSecret,
            @Value("${boxue.security.jwt.access-token-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${boxue.security.jwt.refresh-token-ttl-days:30}") long refreshTtlDays) {
        this.issuer = issuer;
        this.clientKey = Keys.hmacShaKeyFor(clientSecret.getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(adminSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String issueAccessToken(
            UUID userId,
            UUID sessionId,
            UUID deviceId,
            String audience,
            List<String> roles,
            List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .audience().add(audience).and()
            .claim("sid", sessionId.toString())
            .claim("deviceId", deviceId == null ? null : deviceId.toString())
            .claim("roles", roles)
            .claim("permissions", permissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
            .signWith(keyFor(audience))
            .compact();
    }

    public Claims parse(String token, String audience) {
        return Jwts.parser()
            .verifyWith(keyFor(audience))
            .requireIssuer(issuer)
            .requireAudience(audience)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Claims tryParse(String token) {
        try {
            return parse(token, "client");
        } catch (JwtException | IllegalArgumentException ex) {
            try {
                return parse(token, "admin");
            } catch (JwtException | IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long refreshTtlDays() {
        return refreshTtlDays;
    }

    private SecretKey keyFor(String audience) {
        return "admin".equals(audience) ? adminKey : clientKey;
    }
}
