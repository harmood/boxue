package com.boxue.api.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncRepository {

    private final JdbcTemplate jdbc;

    public SyncRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long appendChange(UUID ownerId, String entityType, UUID entityId, String operation,
                             Integer entityVersion, String payloadJson, UUID deviceId) {
        Long seq = jdbc.queryForObject("""
            INSERT INTO sync_change (owner_id, entity_type, entity_id, operation, entity_version, payload, device_id)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            RETURNING change_seq
            """, Long.class, ownerId, entityType, entityId, operation, entityVersion, payloadJson, deviceId);
        return seq == null ? 0 : seq;
    }

    public List<ChangeRow> findChanges(UUID ownerId, long cursor, int limit) {
        return jdbc.query("""
            SELECT change_seq, entity_type, entity_id, operation, entity_version, payload::text AS payload, created_at
            FROM sync_change
            WHERE owner_id = ? AND change_seq > ?
            ORDER BY change_seq ASC
            LIMIT ?
            """, (rs, rowNum) -> new ChangeRow(
            rs.getLong("change_seq"),
            rs.getString("entity_type"),
            rs.getObject("entity_id", UUID.class),
            rs.getString("operation"),
            rs.getObject("entity_version", Integer.class),
            rs.getString("payload"),
            rs.getTimestamp("created_at").toInstant()
        ), ownerId, cursor, limit);
    }

    public long maxSeq(UUID ownerId) {
        Long seq = jdbc.queryForObject(
            "SELECT COALESCE(max(change_seq), 0) FROM sync_change WHERE owner_id = ?",
            Long.class, ownerId);
        return seq == null ? 0 : seq;
    }

    public Optional<IdempotencyRow> findIdempotency(UUID ownerId, UUID clientChangeId) {
        return jdbc.query("""
            SELECT response::text AS response
            FROM sync_idempotency WHERE owner_id = ? AND client_change_id = ?
            """, (rs, rowNum) -> new IdempotencyRow(rs.getString("response")),
            ownerId, clientChangeId).stream().findFirst();
    }

    public void insertIdempotency(UUID ownerId, UUID clientChangeId, UUID deviceId, String responseJson) {
        jdbc.update("""
            INSERT INTO sync_idempotency (id, owner_id, client_change_id, device_id, response)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """, UUID.randomUUID(), ownerId, clientChangeId, deviceId, responseJson);
    }

    public boolean deviceOwned(UUID ownerId, UUID deviceId) {
        if (deviceId == null) {
            return true;
        }
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM app_device WHERE id = ? AND user_id = ?", Integer.class, deviceId, ownerId);
        return count != null && count > 0;
    }

    public Optional<DeviceSyncRow> findDeviceSync(UUID ownerId, UUID deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        return jdbc.query("""
            SELECT last_sync_pull_at, last_sync_push_at
            FROM app_device WHERE id = ? AND user_id = ?
            """, (rs, rowNum) -> new DeviceSyncRow(
            rs.getTimestamp("last_sync_pull_at") == null ? null : rs.getTimestamp("last_sync_pull_at").toInstant(),
            rs.getTimestamp("last_sync_push_at") == null ? null : rs.getTimestamp("last_sync_push_at").toInstant()
        ), deviceId, ownerId).stream().findFirst();
    }

    public void touchPull(UUID ownerId, UUID deviceId) {
        if (deviceId != null) {
            jdbc.update("""
                UPDATE app_device SET last_sync_pull_at = now(), last_seen_at = now(), updated_at = now()
                WHERE id = ? AND user_id = ?
                """, deviceId, ownerId);
        }
    }

    public void touchPush(UUID ownerId, UUID deviceId) {
        if (deviceId != null) {
            jdbc.update("""
                UPDATE app_device SET last_sync_push_at = now(), last_seen_at = now(), updated_at = now()
                WHERE id = ? AND user_id = ?
                """, deviceId, ownerId);
        }
    }

    public record ChangeRow(
        long seq,
        String entityType,
        UUID entityId,
        String operation,
        Integer entityVersion,
        String payloadJson,
        Instant createdAt
    ) {
    }

    public record IdempotencyRow(String responseJson) {
    }

    public record DeviceSyncRow(Instant lastPulledAt, Instant lastPushedAt) {
    }
}
