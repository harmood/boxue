package com.boxue.api.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthDtos.ChangePasswordRequest;
import com.boxue.api.auth.AuthDtos.CurrentUser;
import com.boxue.api.auth.AuthDtos.DeviceListResponse;
import com.boxue.api.auth.AuthDtos.UpdateProfileRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.userId());
    }

    @PatchMapping("/me")
    public CurrentUser updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(principal.userId(), request);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.changePassword(principal, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/devices")
    public DeviceListResponse devices(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Device-Id", required = false) UUID deviceId) {
        return authService.listDevices(principal.userId(), deviceId);
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> removeDevice(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID deviceId) {
        authService.removeDevice(principal.userId(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
