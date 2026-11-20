package com.boxue.api.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LoginRateLimiter {

    private final int windowSeconds;
    private final int loginPerIp;
    private final int loginPerAccount;
    private final int adminPerIp;
    private final int adminPerAccount;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public LoginRateLimiter(
            @Value("${boxue.auth.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${boxue.auth.rate-limit.login-per-ip:30}") int loginPerIp,
            @Value("${boxue.auth.rate-limit.login-per-account:10}") int loginPerAccount,
            @Value("${boxue.auth.rate-limit.admin-per-ip:10}") int adminPerIp,
            @Value("${boxue.auth.rate-limit.admin-per-account:5}") int adminPerAccount) {
        this.windowSeconds = Math.max(1, windowSeconds);
        this.loginPerIp = Math.max(0, loginPerIp);
        this.loginPerAccount = Math.max(0, loginPerAccount);
        this.adminPerIp = Math.max(0, adminPerIp);
        this.adminPerAccount = Math.max(0, adminPerAccount);
    }

    public void checkLogin(String ip, String login) {
        consume("login:ip:" + normalizeIp(ip), loginPerIp,
            "AUTH_RATE_LIMITED", "登录尝试过于频繁，请稍后再试");
        if (StringUtils.hasText(login)) {
            consume("login:account:" + normalizeAccount(login), loginPerAccount,
                "AUTH_RATE_LIMITED", "该账号登录尝试过于频繁，请稍后再试");
        }
    }

    public void checkAdminLogin(String ip, String email) {
        consume("admin:ip:" + normalizeIp(ip), adminPerIp,
            "AUTH_RATE_LIMITED", "管理员登录尝试过于频繁，请稍后再试");
        if (StringUtils.hasText(email)) {
            consume("admin:account:" + normalizeAccount(email), adminPerAccount,
                "AUTH_RATE_LIMITED", "该管理员账号登录尝试过于频繁，请稍后再试");
        }
    }

    public void resetLoginAccount(String login) {
        if (StringUtils.hasText(login)) {
            counters.remove("login:account:" + normalizeAccount(login));
        }
    }

    public void resetAdminAccount(String email) {
        if (StringUtils.hasText(email)) {
            counters.remove("admin:account:" + normalizeAccount(email));
        }
    }

    private void consume(String key, int limit, String code, String message) {
        if (limit <= 0) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        long window = now / windowSeconds;
        Counter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.window() != window) {
                return new Counter(window, 1);
            }
            return new Counter(window, existing.count() + 1);
        });
        cleanupIfNeeded(window);
        if (counter.count() > limit) {
            long retryAfter = windowSeconds - (now % windowSeconds);
            throw new RateLimitException(code, message, retryAfter);
        }
    }

    private void cleanupIfNeeded(long currentWindow) {
        if (calls.incrementAndGet() % 1024 != 0) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow - 1);
    }

    private String normalizeIp(String ip) {
        return StringUtils.hasText(ip) ? ip.trim() : "unknown";
    }

    private String normalizeAccount(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record Counter(long window, int count) {
    }
}
