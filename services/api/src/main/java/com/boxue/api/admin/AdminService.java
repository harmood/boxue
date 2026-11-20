package com.boxue.api.admin;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boxue.api.admin.AdminDtos.AdminMessageResponse;
import com.boxue.api.admin.AdminDtos.AdminUserDetail;
import com.boxue.api.admin.AdminDtos.AdminUserListItem;
import com.boxue.api.admin.AdminDtos.AdminUserPage;
import com.boxue.api.admin.AdminDtos.AuditLogItem;
import com.boxue.api.admin.AdminDtos.AuditLogPage;
import com.boxue.api.admin.AdminDtos.CreateRoleRequest;
import com.boxue.api.admin.AdminDtos.DeviceItem;
import com.boxue.api.admin.AdminDtos.Permission;
import com.boxue.api.admin.AdminDtos.PermissionListResponse;
import com.boxue.api.admin.AdminDtos.PermissionRef;
import com.boxue.api.admin.AdminDtos.Role;
import com.boxue.api.admin.AdminDtos.RoleListResponse;
import com.boxue.api.admin.AdminDtos.SetUserRolesRequest;
import com.boxue.api.admin.AdminDtos.UpdateRoleRequest;
import com.boxue.api.admin.AdminDtos.UpdateUserStatusRequest;
import com.boxue.api.admin.AdminRepository.AuditRow;
import com.boxue.api.admin.AdminRepository.PermissionRow;
import com.boxue.api.admin.AdminRepository.RoleRow;
import com.boxue.api.admin.AdminRepository.UserRow;
import com.boxue.api.auth.AuditService;
import com.boxue.api.auth.AuthPrincipal;
import com.boxue.api.auth.AuthRepository;
import com.boxue.api.auth.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminService {

    private final AdminRepository repository;
    private final AuthRepository authRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminService(
            AdminRepository repository,
            AuthRepository authRepository,
            AuthService authService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public AdminUserPage listUsers(String q, String status, String role, Instant createdFrom,
                                   Instant createdTo, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<UserRow> rows = repository.findUsers(
            q, status, role, createdFrom, createdTo,
            decoded == null ? null : decoded.time(),
            decoded == null ? null : decoded.id(),
            pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<UserRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<AdminUserListItem> items = page.stream().map(this::toListItem).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id().toString())
            : null;
        return new AdminUserPage(items, nextCursor, hasMore);
    }

    public AdminUserDetail getUser(UUID userId) {
        UserRow user = requireUser(userId);
        return toDetail(user);
    }

    @Transactional
    public AdminUserDetail updateStatus(AuthPrincipal principal, UUID userId,
                                        UpdateUserStatusRequest request,
                                        HttpServletRequest httpRequest) {
        UserRow user = requireUser(userId);
        String status = request.status().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED", "LOCKED").contains(status)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "ADMIN_INVALID_STATUS", "不支持的用户状态");
        }
        List<String> roles = repository.findRoles(userId);
        if (roles.contains("ADMIN") && !"ACTIVE".equals(status) && repository.countActiveAdmins() <= 1) {
            throw new AdminException(HttpStatus.CONFLICT, "ADMIN_LAST_ADMIN", "不能禁用或锁定最后一个管理员");
        }

        repository.updateStatus(userId, status);
        if (!"ACTIVE".equals(status)) {
            authRepository.revokeAllSessions(userId);
        }
        auditService.log(principal.userId(), "ADMIN_USER_STATUS_CHANGED", "USER", userId.toString(),
            Map.of("status", status, "reason", request.reason() == null ? "" : request.reason()), httpRequest);
        return toDetail(requireUser(userId));
    }

    @Transactional
    public AdminUserDetail setRoles(AuthPrincipal principal, UUID userId,
                                    SetUserRolesRequest request,
                                    HttpServletRequest httpRequest) {
        requireUser(userId);
        List<String> roleCodes = request.roleCodes().stream().distinct().toList();
        Map<String, UUID> roleIds = repository.findRoleIdsByCodes(roleCodes);
        if (roleIds.size() != roleCodes.size()) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_NOT_FOUND", "包含不存在的角色");
        }

        List<String> currentRoles = repository.findRoles(userId);
        if (currentRoles.contains("ADMIN") && !roleCodes.contains("ADMIN")
                && repository.countActiveAdmins() <= 1) {
            throw new AdminException(HttpStatus.CONFLICT, "ADMIN_LAST_ADMIN", "不能移除最后一个管理员的 ADMIN 角色");
        }

        repository.replaceRoles(userId, new ArrayList<>(roleIds.values()), principal.userId());
        if (!new HashSet<>(currentRoles).equals(new HashSet<>(roleCodes))) {
            authRepository.revokeAllSessions(userId);
        }
        auditService.log(principal.userId(), "ADMIN_USER_ROLES_CHANGED", "USER", userId.toString(),
            Map.of("roleCodes", roleCodes), httpRequest);
        return toDetail(requireUser(userId));
    }

    @Transactional
    public AdminMessageResponse triggerPasswordReset(AuthPrincipal principal, UUID userId,
                                                     HttpServletRequest httpRequest) {
        requireUser(userId);
        AdminMessageResponse response = new AdminMessageResponse(
            authService.triggerPasswordReset(userId, httpRequest).message());
        auditService.log(principal.userId(), "ADMIN_PASSWORD_RESET_TRIGGERED", "USER", userId.toString(),
            Map.of(), httpRequest);
        return response;
    }

    @Transactional
    public void forceLogout(AuthPrincipal principal, UUID userId, HttpServletRequest httpRequest) {
        requireUser(userId);
        authRepository.revokeAllSessions(userId);
        auditService.log(principal.userId(), "ADMIN_FORCE_LOGOUT", "USER", userId.toString(),
            Map.of(), httpRequest);
    }

    public RoleListResponse listRoles() {
        List<Role> roles = repository.findAllRoles().stream().map(this::toRole).toList();
        return new RoleListResponse(roles);
    }

    @Transactional
    public Role createRole(AuthPrincipal principal, CreateRoleRequest request, HttpServletRequest httpRequest) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.roleCodeExists(code)) {
            throw new AdminException(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS", "角色码已存在");
        }
        List<UUID> permissionIds = resolvePermissions(request.permissionCodes());
        UUID roleId = UUID.randomUUID();
        repository.createRole(roleId, code, request.name(), request.description());
        repository.replaceRolePermissions(roleId, permissionIds);
        auditService.log(principal.userId(), "ADMIN_ROLE_CREATED", "ROLE", roleId.toString(),
            Map.of("code", code, "permissionCodes", request.permissionCodes()), httpRequest);
        return toRole(requireRole(roleId));
    }

    @Transactional
    public Role updateRole(AuthPrincipal principal, UUID roleId, UpdateRoleRequest request,
                           HttpServletRequest httpRequest) {
        RoleRow existing = requireRole(roleId);
        List<UUID> permissionIds = resolvePermissions(request.permissionCodes());
        repository.updateRole(roleId, request.name(), request.description());
        repository.replaceRolePermissions(roleId, permissionIds);
        auditService.log(principal.userId(), "ADMIN_ROLE_UPDATED", "ROLE", roleId.toString(),
            Map.of("code", existing.code(), "permissionCodes", request.permissionCodes()), httpRequest);
        return toRole(requireRole(roleId));
    }

    @Transactional
    public void deleteRole(AuthPrincipal principal, UUID roleId, HttpServletRequest httpRequest) {
        RoleRow role = requireRole(roleId);
        if (role.isSystem()) {
            throw new AdminException(HttpStatus.CONFLICT, "ROLE_SYSTEM_PROTECTED", "系统角色不可删除");
        }
        if (repository.roleInUse(roleId)) {
            throw new AdminException(HttpStatus.CONFLICT, "ROLE_IN_USE", "角色仍被用户使用");
        }
        repository.deleteRole(roleId);
        auditService.log(principal.userId(), "ADMIN_ROLE_DELETED", "ROLE", roleId.toString(),
            Map.of("code", role.code()), httpRequest);
    }

    public PermissionListResponse listPermissions() {
        List<Permission> permissions = repository.findAllPermissions().stream()
            .map(this::toPermission).toList();
        return new PermissionListResponse(permissions);
    }

    public AuditLogPage listAuditLogs(UUID actorId, String action, String targetType,
                                      Instant from, Instant to, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<AuditRow> rows = repository.findAuditLogs(
            actorId, action, targetType, from, to,
            decoded == null ? null : decoded.time(),
            decoded == null ? null : Long.valueOf(decoded.id()),
            pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<AuditRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<AuditLogItem> items = page.stream().map(this::toAuditItem).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).createdAt(),
                String.valueOf(page.get(page.size() - 1).id()))
            : null;
        return new AuditLogPage(items, nextCursor, hasMore);
    }

    private AdminUserListItem toListItem(UserRow user) {
        return new AdminUserListItem(
            user.id(), user.email(), user.username(), user.displayName(), user.status(),
            repository.findRoles(user.id()), user.emailVerifiedAt() != null,
            user.lastLoginAt(), user.createdAt());
    }

    private AdminUserDetail toDetail(UserRow user) {
        List<DeviceItem> devices = repository.findDevices(user.id()).stream()
            .map(d -> new DeviceItem(d.id(), d.deviceName(), d.platform(), d.appVersion(), d.lastSeenAt()))
            .toList();
        return new AdminUserDetail(
            user.id(), user.email(), user.username(), user.displayName(),
            user.avatarMediaId(), user.bio(), user.locale(), user.timezone(), user.status(),
            repository.findRoles(user.id()), repository.findPermissions(user.id()),
            user.emailVerifiedAt() != null, user.createdAt(), user.lastLoginAt(), devices,
            repository.countBanks(user.id()), repository.countQuestions(user.id()),
            repository.sumStorageBytes(user.id()));
    }

    private Role toRole(RoleRow row) {
        List<PermissionRef> permissions = repository.findPermissionsByRole(row.id()).stream()
            .map(p -> new PermissionRef(p.code(), p.name())).toList();
        return new Role(row.id(), row.code(), row.name(), row.description(), row.isSystem(), permissions);
    }

    private Permission toPermission(PermissionRow row) {
        return new Permission(row.id(), row.code(), row.name(), row.description());
    }

    private AuditLogItem toAuditItem(AuditRow row) {
        Map<String, Object> details = Map.of();
        if (row.detailsJson() != null && !row.detailsJson().isBlank()) {
            try {
                details = objectMapper.readValue(row.detailsJson(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
                details = Map.of("raw", row.detailsJson());
            }
        }
        return new AuditLogItem(row.id(), row.actorUserId(), row.action(), row.targetType(),
            row.targetId(), details, row.ip(), row.userAgent(), row.createdAt());
    }

    private UserRow requireUser(UUID userId) {
        return repository.findUser(userId)
            .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private RoleRow requireRole(UUID roleId) {
        return repository.findRole(roleId)
            .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "角色不存在"));
    }

    private List<UUID> resolvePermissions(List<String> permissionCodes) {
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(permissionCodes));
        Map<String, UUID> ids = repository.findPermissionIdsByCodes(codes);
        if (ids.size() != codes.size()) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "PERMISSION_NOT_FOUND", "包含不存在的权限码");
        }
        return new ArrayList<>(ids.values());
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String encodeCursor(Instant time, String id) {
        String raw = time.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), parts[1]);
        } catch (RuntimeException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "分页游标无效");
        }
    }

    private record Cursor(Instant time, String id) {
    }
}
