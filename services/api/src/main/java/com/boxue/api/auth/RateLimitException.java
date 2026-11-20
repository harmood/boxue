package com.boxue.api.auth;

public class RateLimitException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public RateLimitException(String code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
