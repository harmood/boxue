package com.boxue.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.boxue.api.common.error.ApiError;

class ApiErrorTest {

    @Test
    void exposesErrorFields() {
        ApiError error = new ApiError(
            "VALIDATION_ERROR",
            "请求参数不合法",
            Map.of("email", "邮箱格式不正确"),
            "trace-id",
            Instant.parse("2026-11-19T10:00:00Z")
        );

        assertEquals("VALIDATION_ERROR", error.code());
        assertEquals("trace-id", error.traceId());
        assertEquals("邮箱格式不正确", error.details().get("email"));
    }
}