package com.boxue.api.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbc;

    public AdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserRow> findUsers(String q, String status, String role, Instant createdFrom,
                                   Instant createdTo, Instant cursorTime, String cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT u.id, u.email, u.username, u.status, u.email_verified_at,
                   u.last_login_at, u.created_at,
                   p.display_name, p.avatar_media_id, p.bio, p.locale, p.timezone
            FROM app_user u
            LEFT JOIN app_user_profile p ON p.user_id = u.id
            WHERE u.deleted_at IS NULL
            """);
        List<Object> args = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            sql.append(" AND (u.email ILIKE ? OR u.username ILIKE ? OR p.display_name ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND u.status = ?");
            args.add(status);
        }
        if (role != null && !role.isBlank()) {
            sql.append("""
                 AND EXISTS (
                   SELECT 1 FROM app_user_role ur
                   JOIN app_role r ON r.id = ur.role_id
                   WHERE ur.user_id = u.id AND r.code = ?
                 )
                """);
            args.add(role);
        }
        if (createdFrom != null) {
            sql.append(" AND u.created_at >= ?");
            args.add(java.sql.Timestamp.from(createdFrom));
        }
        if (createdTo != null) {
            sql.append(" AND u.created_at <= ?");
            args.add(java.sql.Timestamp.from(createdTo));
        }
        if (cursorTime != null) {
            sql.append(" AND (u.created_at < ? OR (u.created_at = ? AND u.id < ?::uuid))");
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), userMapper(), args.toArray());
    }

    public Optional<UserRow> findUser(UUID userId) {
        List<UserRow> rows = jdbc.query("""
            SELECT u.id, u.email, u.username, u.status, u.email_verified_at,
                   u.last_login_at, u.created_at,
                   p.display_name, p.avatar_media_id, p.bio, p.locale, p.timezone
            FROM app_user u
            LEFT JOIN app_user_profile p ON p.user_id = u.id
            WHERE u.id = ? AND u.deleted_at IS NULL
            """, userMapper(), userId);
        return rows.stream().findFirst();
    }

    public List<String> findRoles(UUID userId) {
        return jdbc.queryForList("""
            SELECT DISTINCT r.code FROM app_role r
            JOIN app_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = ? ORDER BY r.code
            """, String.class, userId);
    }

    public List<String> findPermissions(UUID userId) {
        return jdbc.queryForList("""
            SELECT DISTINCT p.code FROM app_permission p
            JOIN app_role_permission rp ON rp.permission_id = p.id
            JOIN app_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = ? ORDER BY p.code
            """, String.class, userId);
    }

    public List<DeviceRow> findDevices(UUID userId) {
        return jdbc.query("""
            SELECT id, device_name, platform, app_version, last_seen_at
            FROM app_device WHERE user_id = ?
            ORDER BY last_seen_at DESC NULLS LAST
            """, (rs, rowNum) -> new DeviceRow(
                rs.getObject("id", UUID.class),
                rs.getString("device_name"),
                rs.getString("platform"),
                rs.getString("app_version"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant()
            ), userId);
    }

    public long countBanks(UUID userId) {
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM qb_bank WHERE owner_id = ? AND deleted_at IS NULL", Long.class, userId);
        return count == null ? 0 : count;
    }

    public long countQuestions(UUID userId) {
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM qb_question WHERE owner_id = ? AND deleted_at IS NULL", Long.class, userId);
        return count == null ? 0 : count;
    }

    public long sumStorageBytes(UUID userId) {
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(sum(size_bytes), 0) FROM qb_media WHERE owner_id = ? AND deleted_at IS NULL",
            Long.class, userId);
        return total == null ? 0 : total;
    }

    public int countActiveAdmins() {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM app_user u
            JOIN app_user_role ur ON ur.user_id = u.id
            JOIN app_role r ON r.id = ur.role_id
            WHERE r.code = 'ADMIN' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
            """, Integer.class);
        return count == null ? 0 : count;
    }

    public void updateStatus(UUID userId, String status) {
        jdbc.update("""
            UPDATE app_user SET status = ?, updated_at = now() WHERE id = ?
            """, status, userId);
    }

    public void replaceRoles(UUID userId, List<UUID> roleIds, UUID grantedBy) {
        jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", userId);
        if (roleIds.isEmpty()) {
            return;
        }
        List<Object[]> args = roleIds.stream()
            .map(roleId -> new Object[] { userId, roleId, grantedBy })
            .toList();
        jdbc.batchUpdate("""
            INSERT INTO app_user_role (user_id, role_id, granted_by) VALUES (?, ?, ?)
            """, args);
    }

    public Map<String, UUID> findRoleIdsByCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, code FROM app_role WHERE code IN (" + placeholders + ")", codes.toArray());
        Map<String, UUID> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("code"), (UUID) row.get("id"));
        }
        return result;
    }

    public Map<String, UUID> findPermissionIdsByCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, code FROM app_permission WHERE code IN (" + placeholders + ")", codes.toArray());
        Map<String, UUID> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("code"), (UUID) row.get("id"));
        }
        return result;
    }

    public List<RoleRow> findAllRoles() {
        return jdbc.query("""
            SELECT id, code, name, description, is_system
            FROM app_role ORDER BY is_system DESC, code
            """, roleMapper());
    }

    public Optional<RoleRow> findRole(UUID roleId) {
        List<RoleRow> rows = jdbc.query("""
            SELECT id, code, name, description, is_system FROM app_role WHERE id = ?
            """, roleMapper(), roleId);
        return rows.stream().findFirst();
    }

    public List<PermissionRow> findPermissionsByRole(UUID roleId) {
        return jdbc.query("""
            SELECT p.id, p.code, p.name, p.description
            FROM app_permission p
            JOIN app_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = ?
            ORDER BY p.code
            """, permissionMapper(), roleId);
    }

    public List<PermissionRow> findAllPermissions() {
        return jdbc.query("""
            SELECT id, code, name, description FROM app_permission ORDER BY code
            """, permissionMapper());
    }

    public boolean roleCodeExists(String code) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM app_role WHERE code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    public boolean roleInUse(UUID roleId) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM app_user_role WHERE role_id = ?", Integer.class, roleId);
        return count != null && count > 0;
    }

    public void createRole(UUID roleId, String code, String name, String description) {
        jdbc.update("""
            INSERT INTO app_role (id, code, name, description, is_system)
            VALUES (?, ?, ?, ?, false)
            """, roleId, code, name, description);
    }

    public void updateRole(UUID roleId, String name, String description) {
        jdbc.update("""
            UPDATE app_role SET name = ?, description = ?, updated_at = now() WHERE id = ?
            """, name, description, roleId);
    }

    public void deleteRole(UUID roleId) {
        jdbc.update("DELETE FROM app_role WHERE id = ?", roleId);
    }

    public void replaceRolePermissions(UUID roleId, List<UUID> permissionIds) {
        jdbc.update("DELETE FROM app_role_permission WHERE role_id = ?", roleId);
        if (permissionIds.isEmpty()) {
            return;
        }
        List<Object[]> args = permissionIds.stream()
            .map(permissionId -> new Object[] { roleId, permissionId })
            .toList();
        jdbc.batchUpdate("""
            INSERT INTO app_role_permission (role_id, permission_id) VALUES (?, ?)
            """, args);
    }

    public List<AuditRow> findAuditLogs(UUID actorId, String action, String targetType,
                                         Instant from, Instant to,
                                         Instant cursorTime, Long cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, actor_user_id, action, target_type, target_id, details, ip, user_agent, created_at
            FROM app_audit_log WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (actorId != null) {
            sql.append(" AND actor_user_id = ?");
            args.add(actorId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (targetType != null && !targetType.isBlank()) {
            sql.append(" AND target_type = ?");
            args.add(targetType);
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND created_at <= ?");
            args.add(java.sql.Timestamp.from(to));
        }
        if (cursorTime != null) {
            sql.append(" AND (created_at < ? OR (created_at = ? AND id < ?::bigint))");
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new AuditRow(
            rs.getLong("id"),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getString("target_id"),
            rs.getString("details"),
            rs.getString("ip"),
            rs.getString("user_agent"),
            rs.getTimestamp("created_at").toInstant()
        ), args.toArray());
    }

    private org.springframework.jdbc.core.RowMapper<UserRow> userMapper() {
        return (rs, rowNum) -> new UserRow(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("username"),
            rs.getString("status"),
            rs.getTimestamp("email_verified_at") == null ? null : rs.getTimestamp("email_verified_at").toInstant(),
            rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("display_name"),
            rs.getObject("avatar_media_id", UUID.class),
            rs.getString("bio"),
            rs.getString("locale"),
            rs.getString("timezone")
        );
    }

    private org.springframework.jdbc.core.RowMapper<RoleRow> roleMapper() {
        return (rs, rowNum) -> new RoleRow(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("is_system")
        );
    }

    private org.springframework.jdbc.core.RowMapper<PermissionRow> permissionMapper() {
        return (rs, rowNum) -> new PermissionRow(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description")
        );
    }

    public record UserRow(
        UUID id,
        String email,
        String username,
        String status,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        String displayName,
        UUID avatarMediaId,
        String bio,
        String locale,
        String timezone
    ) {
    }

    public record DeviceRow(
        UUID id,
        String deviceName,
        String platform,
        String appVersion,
        Instant lastSeenAt
    ) {
    }

    public record RoleRow(
        UUID id,
        String code,
        String name,
        String description,
        boolean isSystem
    ) {
    }

    public record PermissionRow(
        UUID id,
        String code,
        String name,
        String description
    ) {
    }

    public record AuditRow(
        long id,
        UUID actorUserId,
        String action,
        String targetType,
        String targetId,
        String detailsJson,
        String ip,
        String userAgent,
        Instant createdAt
    ) {
    }
}
