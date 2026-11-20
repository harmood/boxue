package com.boxue.api.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.boxue.api.admin.AdminException;
import com.boxue.api.content.ContentException;
import com.boxue.api.auth.AuthException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法", details, request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> handleAuth(
        AuthException exception,
        HttpServletRequest request
    ) {
        return error(exception.status(), exception.code(), exception.getMessage(),
            exception.details(), request);
    }

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<ApiError> handleAdmin(
        AdminException exception,
        HttpServletRequest request
    ) {
        return error(exception.status(), exception.code(), exception.getMessage(),
            exception.details(), request);
    }

    @ExceptionHandler(ContentException.class)
    public ResponseEntity<ApiError> handleContent(
        ContentException exception,
        HttpServletRequest request
    ) {
        return error(exception.status(), exception.code(), exception.getMessage(),
            exception.details(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "资源冲突或重复", Map.of(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "服务端错误",
            Map.of(),
            request
        );
    }

    private ResponseEntity<ApiError> error(
        HttpStatus status,
        String code,
        String message,
        Map<String, Object> details,
        HttpServletRequest request
    ) {
        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status).body(new ApiError(
            code,
            message,
            details,
            traceId,
            Instant.now()
        ));
    }
}