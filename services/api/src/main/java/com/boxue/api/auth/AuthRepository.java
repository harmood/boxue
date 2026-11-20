package com.boxue.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByLogin(String login) {
        return first(jdbc.query("""
            SELECT id, email, username, password_hash, status, email_verified_at,
                   locked_until, failed_login_count
            FROM app_user
            WHERE deleted_at IS NULL AND (email = ? OR username = ?)
            """, userMapper(), login, login));
    }

    public Optional<UserRow> findById(UUID userId) {
        return first(jdbc.query("""
            SELECT id, email, username, password_hash, status, email_verified_at,
                   locked_until, failed_login_count
            FROM app_user
            WHERE id = ? AND deleted_at IS NULL
            """, userMapper(), userId));
    }

    public boolean existsByEmailOrUsername(String email, String username) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM app_user
            WHERE deleted_at IS NULL AND (email = ? OR username = ?)
            """, Integer.class, email, username);
        return count != null && count > 0;
    }

    public void insertUser(UUID id, String email, String username, String passwordHash,
                           String status, boolean emailVerified) {
        jdbc.update("""
            INSERT INTO app_user (id, email, username, password_hash, status, email_verified_at)
            VALUES (?, ?, ?, ?, ?, CASE WHEN ?::boolean THEN now() ELSE NULL END)
            """, id, email, username, passwordHash, status, emailVerified);
    }

    public void insertProfile(UUID userId, String displayName) {
        jdbc.update("""
            INSERT INTO app_user_profile (user_id, display_name, locale, timezone)
            VALUES (?, ?, 'zh-CN', 'Asia/Shanghai')
            ON CONFLICT (user_id) DO UPDATE
            SET display_name = EXCLUDED.display_name, updated_at = now()
            """, userId, displayName);
    }

    public void assignRole(UUID userId, String roleCode) {
        jdbc.update("""
            INSERT INTO app_user_role (user_id, role_id)
            SELECT ?, id FROM app_role WHERE code = ?
            ON CONFLICT (user_id, role_id) DO NOTHING
            """, userId, roleCode);
    }

    public List<String> findRoles(UUID userId) {
        return jdbc.queryForList("""
            SELECT DISTINCT r.code
            FROM app_role r
            JOIN app_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = ?
            ORDER BY r.code
            """, String.class, userId);
    }

    public List<String> findPermissions(UUID userId) {
        return jdbc.queryForList("""
            SELECT DISTINCT p.code
            FROM app_permission p
            JOIN app_role_permission rp ON rp.permission_id = p.id
            JOIN app_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = ?
            ORDER BY p.code
            """, String.class, userId);
    }

    public void recordLoginSuccess(UUID userId) {
        jdbc.update("""
            UPDATE app_user
            SET last_login_at = now(), failed_login_count = 0, locked_until = NULL, updated_at = now()
            WHERE id = ?
            """, userId);
    }

    public void recordLoginFailure(UUID userId, int failedCount, Instant lockedUntil) {
        jdbc.update("""
            UPDATE app_user
            SET failed_login_count = ?, locked_until = ?, updated_at = now()
            WHERE id = ?
            """, failedCount, lockedUntil == null ? null : java.sql.Timestamp.from(lockedUntil), userId);
    }

    public void updatePassword(UUID userId, String passwordHash) {
        jdbc.update("""
            UPDATE app_user SET password_hash = ?, updated_at = now() WHERE id = ?
            """, passwordHash, userId);
    }

    public Optional<ProfileRow> findProfile(UUID userId) {
        return first(jdbc.query("""
            SELECT user_id, display_name, avatar_media_id, bio, locale, timezone
            FROM app_user_profile WHERE user_id = ?
            """, (rs, rowNum) -> new ProfileRow(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getObject("avatar_media_id", UUID.class),
                rs.getString("bio"),
                rs.getString("locale"),
                rs.getString("timezone")
            ), userId));
    }

    public boolean ownsMedia(UUID userId, UUID mediaId) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM qb_media WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, Integer.class, mediaId, userId);
        return count != null && count > 0;
    }

    public void updateProfile(UUID userId, AuthDtos.UpdateProfileRequest request) {
        jdbc.update("""
            UPDATE app_user_profile
            SET display_name = COALESCE(?, display_name),
                avatar_media_id = COALESCE(?, avatar_media_id),
                bio = COALESCE(?, bio),
                locale = COALESCE(?, locale),
                timezone = COALESCE(?, timezone),
                updated_at = now()
            WHERE user_id = ?
            """, request.displayName(), request.avatarMediaId(), request.bio(),
            request.locale(), request.timezone(), userId);
    }

    public void ensureDevice(UUID userId, AuthDtos.DeviceRequest device) {
        List<UUID> owners = jdbc.query(
            "SELECT user_id FROM app_device WHERE id = ?",
            (rs, rowNum) -> rs.getObject("user_id", UUID.class),
            device.deviceId());
        if (!owners.isEmpty() && !owners.get(0).equals(userId)) {
            throw new AuthException(HttpStatus.CONFLICT, "DEVICE_OWNED_BY_ANOTHER_USER",
                "该设备 ID 已绑定其他用户");
        }
        jdbc.update("""
            INSERT INTO app_device (id, user_id, device_name, platform, app_version, last_seen_at)
            VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (id) DO UPDATE
            SET device_name = EXCLUDED.device_name,
                platform = EXCLUDED.platform,
                app_version = EXCLUDED.app_version,
                last_seen_at = now(),
                updated_at = now()
            """, device.deviceId(), userId, device.deviceName(),
            device.platform().name(), device.appVersion());
    }

    public Optional<DeviceRow> findDevice(UUID deviceId) {
        return first(jdbc.query("""
            SELECT id, device_name, platform, app_version, last_seen_at
            FROM app_device WHERE id = ?
            """, (rs, rowNum) -> new DeviceRow(
                rs.getObject("id", UUID.class),
                rs.getString("device_name"),
                rs.getString("platform"),
                rs.getString("app_version"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant()
            ), deviceId));
    }

    public List<DeviceRow> listDevices(UUID userId) {
        return jdbc.query("""
            SELECT id, device_name, platform, app_version, last_seen_at
            FROM app_device
            WHERE user_id = ?
            ORDER BY last_seen_at DESC NULLS LAST
            """, (rs, rowNum) -> new DeviceRow(
                rs.getObject("id", UUID.class),
                rs.getString("device_name"),
                rs.getString("platform"),
                rs.getString("app_version"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant()
            ), userId);
    }

    public void removeDevice(UUID userId, UUID deviceId) {
        jdbc.update("""
            UPDATE app_user_session SET revoked_at = now()
            WHERE user_id = ? AND device_id = ? AND revoked_at IS NULL
            """, userId, deviceId);
        jdbc.update("DELETE FROM app_device WHERE id = ? AND user_id = ?", deviceId, userId);
    }

    public void createSession(UUID sessionId, UUID userId, UUID deviceId, String audience,
                              String refreshHash, Instant expiresAt, String userAgent, String ip) {
        jdbc.update("""
            INSERT INTO app_user_session
              (id, user_id, device_id, audience, refresh_token_hash, user_agent, ip, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?)
            """, sessionId, userId, deviceId, audience, refreshHash, userAgent, ip,
            java.sql.Timestamp.from(expiresAt));
    }

    public Optional<SessionRow> findSessionByHash(String refreshHash) {
        return first(jdbc.query("""
            SELECT id, user_id, device_id, audience, expires_at, revoked_at, replaced_by_session_id
            FROM app_user_session WHERE refresh_token_hash = ?
            """, (rs, rowNum) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("audience"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getObject("replaced_by_session_id", UUID.class)
            ), refreshHash));
    }

    public Optional<SessionRow> findSessionByHashForUpdate(String refreshHash) {
        return first(jdbc.query("""
            SELECT id, user_id, device_id, audience, expires_at, revoked_at, replaced_by_session_id
            FROM app_user_session WHERE refresh_token_hash = ? FOR UPDATE
            """, (rs, rowNum) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("audience"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getObject("replaced_by_session_id", UUID.class)
            ), refreshHash));
    }

    public Optional<SessionRow> findSessionById(UUID sessionId) {
        return first(jdbc.query("""
            SELECT id, user_id, device_id, audience, expires_at, revoked_at, replaced_by_session_id
            FROM app_user_session WHERE id = ?
            """, (rs, rowNum) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("audience"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getObject("replaced_by_session_id", UUID.class)
            ), sessionId));
    }

    public boolean isSessionActive(UUID sessionId) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM app_user_session
            WHERE id = ? AND revoked_at IS NULL AND expires_at > now()
            """, Integer.class, sessionId);
        return count != null && count > 0;
    }

    public void revokeSessionReplacedBy(UUID sessionId, UUID replacementSessionId) {
        jdbc.update("""
            UPDATE app_user_session
            SET revoked_at = now(), replaced_by_session_id = ?
            WHERE id = ? AND revoked_at IS NULL
            """, replacementSessionId, sessionId);
    }

    public void revokeSession(UUID sessionId) {
        jdbc.update("""
            UPDATE app_user_session SET revoked_at = now()
            WHERE id = ? AND revoked_at IS NULL
            """, sessionId);
    }

    public void revokeAllSessions(UUID userId) {
        jdbc.update("""
            UPDATE app_user_session SET revoked_at = now()
            WHERE user_id = ? AND revoked_at IS NULL
            """, userId);
    }

    public void revokeOtherSessions(UUID userId, UUID keepSessionId) {
        jdbc.update("""
            UPDATE app_user_session SET revoked_at = now()
            WHERE user_id = ? AND id <> ? AND revoked_at IS NULL
            """, userId, keepSessionId);
    }

    public Optional<UUID> findRoleId(String roleCode) {
        return first(jdbc.query("SELECT id FROM app_role WHERE code = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class), roleCode));
    }

    public void insertEmailVerification(UUID id, UUID userId, String email, String tokenHash, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO app_email_verification (id, user_id, email, token_hash, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """, id, userId, email, tokenHash, java.sql.Timestamp.from(expiresAt));
    }

    public Optional<TokenRow> findEmailVerificationByHash(String tokenHash) {
        return first(jdbc.query("""
            SELECT id, user_id, expires_at, consumed_at
            FROM app_email_verification WHERE token_hash = ?
            """, tokenMapper(), tokenHash));
    }

    public void consumeEmailVerification(UUID id) {
        jdbc.update("""
            UPDATE app_email_verification SET consumed_at = now()
            WHERE id = ? AND consumed_at IS NULL
            """, id);
    }

    public void consumeAllEmailVerifications(UUID userId) {
        jdbc.update("""
            UPDATE app_email_verification SET consumed_at = now()
            WHERE user_id = ? AND consumed_at IS NULL
            """, userId);
    }

    public void markEmailVerified(UUID userId) {
        jdbc.update("""
            UPDATE app_user
            SET status = CASE WHEN status = 'PENDING_VERIFICATION' THEN 'ACTIVE' ELSE status END,
                email_verified_at = COALESCE(email_verified_at, now()),
                updated_at = now()
            WHERE id = ?
            """, userId);
    }

    public void insertPasswordReset(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO app_password_reset (id, user_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?)
            """, id, userId, tokenHash, java.sql.Timestamp.from(expiresAt));
    }

    public Optional<TokenRow> findPasswordResetByHash(String tokenHash) {
        return first(jdbc.query("""
            SELECT id, user_id, expires_at, consumed_at
            FROM app_password_reset WHERE token_hash = ?
            """, tokenMapper(), tokenHash));
    }

    public void consumePasswordReset(UUID id) {
        jdbc.update("""
            UPDATE app_password_reset SET consumed_at = now()
            WHERE id = ? AND consumed_at IS NULL
            """, id);
    }

    public void consumeAllPasswordResets(UUID userId) {
        jdbc.update("""
            UPDATE app_password_reset SET consumed_at = now()
            WHERE user_id = ? AND consumed_at IS NULL
            """, userId);
    }

    public void audit(UUID actorUserId, String action, String targetType, String targetId,
                      String detailsJson, String ip, String userAgent) {
        jdbc.update("""
            INSERT INTO app_audit_log
              (actor_user_id, action, target_type, target_id, details, ip, user_agent)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::inet, ?)
            """, actorUserId, action, targetType, targetId, detailsJson, ip, userAgent);
    }

    private org.springframework.jdbc.core.RowMapper<TokenRow> tokenMapper() {
        return (rs, rowNum) -> new TokenRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
        );
    }

    private org.springframework.jdbc.core.RowMapper<UserRow> userMapper() {
        return (rs, rowNum) -> new UserRow(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getTimestamp("email_verified_at") == null ? null : rs.getTimestamp("email_verified_at").toInstant(),
            rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant(),
            rs.getInt("failed_login_count")
        );
    }

    private <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record TokenRow(
        UUID id,
        UUID userId,
        Instant expiresAt,
        Instant consumedAt
    ) {
    }

    public record UserRow(
        UUID id,
        String email,
        String username,
        String passwordHash,
        String status,
        Instant emailVerifiedAt,
        Instant lockedUntil,
        int failedLoginCount
    ) {
    }

    public record SessionRow(
        UUID id,
        UUID userId,
        UUID deviceId,
        String audience,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBySessionId
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

    public record ProfileRow(
        UUID userId,
        String displayName,
        UUID avatarMediaId,
        String bio,
        String locale,
        String timezone
    ) {
    }
}
