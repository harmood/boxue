package com.boxue.api.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthDtos.TotpCodeRequest;
import com.boxue.api.auth.AuthDtos.TotpDisableRequest;
import com.boxue.api.auth.AuthDtos.TotpSetupRequest;
import com.boxue.api.auth.AuthDtos.TotpSetupResponse;
import com.boxue.api.auth.AuthDtos.TotpStatusResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/auth/totp")
public class AdminTotpController {

    private final TotpService totpService;
    private final AuthRepository authRepository;
    private final AuditService auditService;

    public AdminTotpController(TotpService totpService, AuthRepository authRepository,
                               AuditService auditService) {
        this.totpService = totpService;
        this.authRepository = authRepository;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    public TotpStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return totpService.status(principal.userId());
    }

    @PostMapping("/setup")
    public TotpSetupResponse setup(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TotpSetupRequest request,
            HttpServletRequest httpRequest) {
        String email = authRepository.findById(principal.userId())
            .map(user -> user.email())
            .orElseThrow();
        TotpSetupResponse response = totpService.setup(principal.userId(), email, request.password());
        auditService.log(principal.userId(), "TOTP_SETUP_STARTED", "USER",
            principal.userId().toString(), Map.of(), httpRequest);
        return response;
    }

    @PostMapping("/enable")
    public TotpStatusResponse enable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TotpCodeRequest request,
            HttpServletRequest httpRequest) {
        TotpStatusResponse response = totpService.enable(principal.userId(), request.code());
        authRepository.revokeOtherSessions(principal.userId(), principal.sessionId());
        auditService.log(principal.userId(), "TOTP_ENABLED", "USER",
            principal.userId().toString(), Map.of(), httpRequest);
        return response;
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TotpDisableRequest request,
            HttpServletRequest httpRequest) {
        totpService.disable(principal.userId(), request.password(), request.code());
        authRepository.revokeOtherSessions(principal.userId(), principal.sessionId());
        auditService.log(principal.userId(), "TOTP_DISABLED", "USER",
            principal.userId().toString(), Map.of(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
