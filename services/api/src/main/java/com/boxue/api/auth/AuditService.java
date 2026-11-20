package com.boxue.api.auth;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuthRepository authRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuthRepository authRepository, ObjectMapper objectMapper) {
        this.authRepository = authRepository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID actorUserId, String action, String targetType, String targetId,
                    Map<String, Object> details, HttpServletRequest request) {
        String json = null;
        if (details != null && !details.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize audit details for action {}", action, ex);
            }
        }
        String ip = request == null ? null : request.getRemoteAddr();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        authRepository.audit(actorUserId, action, targetType, targetId, json, ip, userAgent);
    }
}
