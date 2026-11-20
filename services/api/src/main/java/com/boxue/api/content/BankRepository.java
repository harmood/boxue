package com.boxue.api.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BankRepository {

    private final JdbcTemplate jdbc;

    public BankRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BankRow> findBanks(UUID ownerId, String status, String q,
                                   Instant cursorTime, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, owner_id, name, description_md, visibility, status, version,
                   created_at, updated_at
            FROM qb_bank
            WHERE owner_id = ? AND deleted_at IS NULL
            """);
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (name ILIKE ? OR description_md ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (cursorTime != null) {
            sql.append(" AND (updated_at < ? OR (updated_at = ? AND id < ?::uuid))");
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), bankMapper(), args.toArray());
    }

    public Optional<BankRow> findBank(UUID ownerId, UUID bankId) {
        List<BankRow> rows = jdbc.query("""
            SELECT id, owner_id, name, description_md, visibility, status, version,
                   created_at, updated_at
            FROM qb_bank
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, bankMapper(), bankId, ownerId);
        return rows.stream().findFirst();
    }

    public Optional<AdminBankRow> findAdminBank(UUID bankId) {
        List<AdminBankRow> rows = jdbc.query("""
            SELECT b.id, b.owner_id, u.email AS owner_email, b.name, b.description_md,
                   b.visibility, b.status, b.version, b.created_at, b.updated_at
            FROM qb_bank b
            JOIN app_user u ON u.id = b.owner_id
            WHERE b.id = ? AND b.deleted_at IS NULL
            """, adminBankMapper(), bankId);
        return rows.stream().findFirst();
    }

    public List<AdminBankRow> findAdminBanks(String q, UUID ownerId, String status, String visibility,
                                              Instant cursorTime, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT b.id, b.owner_id, u.email AS owner_email, b.name, b.description_md,
                   b.visibility, b.status, b.version, b.created_at, b.updated_at
            FROM qb_bank b
            JOIN app_user u ON u.id = b.owner_id
            WHERE b.deleted_at IS NULL
            """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (b.name ILIKE ? OR b.description_md ILIKE ? OR u.email ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (ownerId != null) {
            sql.append(" AND b.owner_id = ?");
            args.add(ownerId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND b.status = ?");
            args.add(status);
        }
        if (visibility != null && !visibility.isBlank()) {
            sql.append(" AND b.visibility = ?");
            args.add(visibility);
        }
        if (cursorTime != null) {
            sql.append(" AND (b.updated_at < ? OR (b.updated_at = ? AND b.id < ?::uuid))");
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY b.updated_at DESC, b.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), adminBankMapper(), args.toArray());
    }

    public boolean existsName(UUID ownerId, String name, UUID excludeId) {
        String sql = """
            SELECT count(*) FROM qb_bank
            WHERE owner_id = ? AND lower(name) = lower(?) AND deleted_at IS NULL
            """;
        Integer count;
        if (excludeId == null) {
            count = jdbc.queryForObject(sql, Integer.class, ownerId, name);
        } else {
            count = jdbc.queryForObject(sql + " AND id <> ?", Integer.class, ownerId, name, excludeId);
        }
        return count != null && count > 0;
    }

    public void insertBank(UUID id, UUID ownerId, String name, String description, String visibility) {
        jdbc.update("""
            INSERT INTO qb_bank (id, owner_id, name, description_md, visibility, status, version)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 1)
            """, id, ownerId, name, description, visibility);
    }

    public int updateBank(UUID id, UUID ownerId, String name, String description,
                          String status, int expectedVersion) {
        return jdbc.update("""
            UPDATE qb_bank
            SET name = ?, description_md = ?, status = ?, version = version + 1, updated_at = now()
            WHERE id = ? AND owner_id = ? AND version = ? AND deleted_at IS NULL
            """, name, description, status, id, ownerId, expectedVersion);
    }

    public long countQuestions(UUID bankId) {
        Long count = jdbc.queryForObject("""
            SELECT count(*) FROM qb_question WHERE bank_id = ? AND deleted_at IS NULL
            """, Long.class, bankId);
        return count == null ? 0 : count;
    }

    public void softDeleteBank(UUID id, UUID ownerId) {
        jdbc.update("""
            UPDATE qb_bank SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, id, ownerId);
    }

    public void softDeleteQuestions(UUID bankId) {
        jdbc.update("""
            UPDATE qb_question SET deleted_at = now(), updated_at = now()
            WHERE bank_id = ? AND deleted_at IS NULL
            """, bankId);
    }

    public void insertSyncTombstone(UUID ownerId, UUID bankId, String payloadJson) {
        jdbc.update("""
            INSERT INTO sync_change (owner_id, entity_type, entity_id, operation, payload)
            VALUES (?, 'BANK', ?, 'DELETE', ?::jsonb)
            """, ownerId, bankId, payloadJson);
    }

    private org.springframework.jdbc.core.RowMapper<BankRow> bankMapper() {
        return (rs, rowNum) -> new BankRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("name"),
            rs.getString("description_md"),
            rs.getString("visibility"),
            rs.getString("status"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private org.springframework.jdbc.core.RowMapper<AdminBankRow> adminBankMapper() {
        return (rs, rowNum) -> new AdminBankRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_email"),
            rs.getString("name"),
            rs.getString("description_md"),
            rs.getString("visibility"),
            rs.getString("status"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record BankRow(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String visibility,
        String status,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AdminBankRow(
        UUID id,
        UUID ownerId,
        String ownerEmail,
        String name,
        String description,
        String visibility,
        String status,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
