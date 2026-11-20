package com.boxue.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CsrfService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public CsrfService(
            @Value("${boxue.security.csrf.secret:}") String configuredSecret,
            @Value("${boxue.security.jwt.client-secret:dev-client-secret-change-me-32-bytes}") String fallbackSecret) {
        String material = StringUtils.hasText(configuredSecret) ? configuredSecret : fallbackSecret + ":csrf";
        this.key = new SecretKeySpec(sha256(material), HMAC_ALGORITHM);
    }

    public String issueToken() {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return payload + "." + sign(payload);
    }

    public boolean isValid(String headerToken, String cookieToken) {
        if (!StringUtils.hasText(headerToken) || !StringUtils.hasText(cookieToken)) {
            return false;
        }
        if (!MessageDigest.isEqual(headerToken.getBytes(StandardCharsets.UTF_8),
            cookieToken.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        String[] parts = headerToken.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        return MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8),
            parts[1].getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("CSRF token signing failed", ex);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
