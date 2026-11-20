package com.boxue.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record DeviceRequest(
        @NotNull UUID deviceId,
        @Size(max = 100) String deviceName,
        @NotNull DevicePlatform platform,
        @Size(max = 50) String appVersion
    ) {
    }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "用户名只能包含字母、数字、下划线和连字符")
        String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 100) String displayName,
        boolean acceptedTerms
    ) {
    }

    public record LoginRequest(
        @NotBlank @Size(max = 320) String login,
        @NotBlank @Size(max = 128) String password,
        @NotNull @Valid DeviceRequest device
    ) {
    }

    public record RefreshTokenRequest(
        @Size(max = 512) String refreshToken
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        Boolean revokeOtherSessions
    ) {
    }

    public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        UUID avatarMediaId,
        @Size(max = 2000) String bio,
        @Size(max = 20) String locale,
        @Size(max = 64) String timezone
    ) {
    }

    public record AuthUser(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        boolean emailVerified
    ) {
    }

    public record RegisterResponse(
        AuthUser user,
        String message
    ) {
    }

    public record TokenResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        AuthUser user,
        List<String> roles,
        List<String> permissions
    ) {
    }

    public record AccessTokenResponse(
        String accessToken,
        long expiresIn,
        String refreshToken
    ) {
    }

    public record CurrentUser(
        UUID id,
        String email,
        String username,
        String displayName,
        UUID avatarMediaId,
        String bio,
        String locale,
        String timezone,
        String status,
        List<String> roles,
        List<String> permissions,
        boolean emailVerified
    ) {
    }

    public record DeviceResponse(
        UUID id,
        String deviceName,
        String platform,
        String appVersion,
        Instant lastSeenAt,
        boolean current
    ) {
    }

    public record DeviceListResponse(
        List<DeviceResponse> items
    ) {
    }

    public record AdminLoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 128) String password,
        @Pattern(regexp = "^[0-9]{6}$", message = "TOTP 必须是 6 位数字") String totpCode,
        @NotNull @Valid DeviceRequest device
    ) {
    }

    public record AdminSummary(
        UUID id,
        String email,
        String username,
        String displayName
    ) {
    }

    public record AdminTokenResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        AdminSummary admin,
        List<String> roles,
        List<String> permissions
    ) {
    }

    public record AdminMe(
        UUID id,
        String email,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions
    ) {
    }

    public record MessageResponse(
        String message
    ) {
    }
}
