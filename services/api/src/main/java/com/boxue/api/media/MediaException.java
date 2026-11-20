package com.boxue.api.media;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class MediaException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public MediaException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public MediaException(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(status, code, message, details, null);
    }

    public MediaException(HttpStatus status, String code, String message,
                          Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = details;
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
