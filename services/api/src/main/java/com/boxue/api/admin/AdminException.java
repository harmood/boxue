package com.boxue.api.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class AdminException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public AdminException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public AdminException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
