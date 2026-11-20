package com.boxue.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserListItem(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        List<String> roles,
        boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt
    ) {
    }

    public record AdminUserPage(
        List<AdminUserListItem> items,
        String nextCursor,
        boolean hasMore
    ) {
    }

    public record AdminUserDetail(
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
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt,
        List<AdminDtos.DeviceItem> devices,
        long bankCount,
        long questionCount,
        long storageBytes
    ) {
    }

    public record DeviceItem(
        UUID id,
        String deviceName,
        String platform,
        String appVersion,
        Instant lastSeenAt
    ) {
    }

    public record UpdateUserStatusRequest(
        @NotBlank String status,
        @Size(max = 500) String reason
    ) {
    }

    public record SetUserRolesRequest(
        @NotEmpty List<@NotBlank String> roleCodes
    ) {
    }

    public record PermissionRef(
        String code,
        String name
    ) {
    }

    public record Role(
        UUID id,
        String code,
        String name,
        String description,
        boolean isSystem,
        List<PermissionRef> permissions
    ) {
    }

    public record RoleListResponse(
        List<Role> items
    ) {
    }

    public record CreateRoleRequest(
        @NotBlank
        @Size(min = 2, max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]+$", message = "角色码必须为大写字母、数字和下划线")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotEmpty List<@NotBlank String> permissionCodes
    ) {
    }

    public record UpdateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotEmpty List<@NotBlank String> permissionCodes
    ) {
    }

    public record Permission(
        UUID id,
        String code,
        String name,
        String description
    ) {
    }

    public record PermissionListResponse(
        List<Permission> items
    ) {
    }

    public record AuditLogItem(
        long id,
        UUID actorUserId,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        String ip,
        String userAgent,
        Instant createdAt
    ) {
    }

    public record AuditLogPage(
        List<AuditLogItem> items,
        String nextCursor,
        boolean hasMore
    ) {
    }

    public record AdminMessageResponse(
        String message
    ) {
    }
}
