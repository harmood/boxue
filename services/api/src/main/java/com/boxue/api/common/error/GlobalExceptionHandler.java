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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.boxue.api.admin.AdminException;
import com.boxue.api.content.ContentException;
import com.boxue.api.media.MediaException;
import com.boxue.api.auth.RateLimitException;
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

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
        Exception exception,
        HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法", Map.of(), request);
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

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(
        RateLimitException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(exception.retryAfterSeconds()))
            .body(new ApiError(
                exception.code(),
                exception.getMessage(),
                Map.of("retryAfterSeconds", exception.retryAfterSeconds()),
                traceId(request),
                Instant.now()
            ));
    }

    @ExceptionHandler(MediaException.class)
    public ResponseEntity<ApiError> handleMedia(
        MediaException exception,
        HttpServletRequest request
    ) {
        return error(exception.status(), exception.code(), exception.getMessage(),
            exception.details(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "上传文件过大", Map.of(), request);
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
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求参数不合法", Map.of(), request);
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
        return ResponseEntity.status(status).body(new ApiError(
            code,
            message,
            details,
            traceId(request),
            Instant.now()
        ));
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}