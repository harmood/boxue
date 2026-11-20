package com.boxue.api.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {

    private final JdbcTemplate jdbc;

    public QuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuestionRow> findQuestions(UUID ownerId, UUID bankId, String type, String status,
                                           String q, UUID tagId, UUID knowledgePointId,
                                           Instant cursorTime, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, owner_id, bank_id, type, status, stem_md, explanation_md, difficulty,
                   payload::text AS payload, source, version, created_at, updated_at
            FROM qb_question
            WHERE owner_id = ? AND deleted_at IS NULL
            """);
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        if (bankId != null) {
            sql.append(" AND bank_id = ?");
            args.add(bankId);
        }
        appendFilters(sql, args, "", type, status, q, tagId, knowledgePointId);
        appendCursor(sql, args, "updated_at", "id", cursorTime, cursorId);
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), questionMapper(), args.toArray());
    }

    public Optional<QuestionRow> findQuestion(UUID ownerId, UUID questionId) {
        List<QuestionRow> rows = jdbc.query("""
            SELECT id, owner_id, bank_id, type, status, stem_md, explanation_md, difficulty,
                   payload::text AS payload, source, version, created_at, updated_at
            FROM qb_question
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, questionMapper(), questionId, ownerId);
        return rows.stream().findFirst();
    }

    public Optional<AdminQuestionRow> findAdminQuestion(UUID questionId) {
        List<AdminQuestionRow> rows = jdbc.query("""
            SELECT q.id, q.owner_id, u.email AS owner_email, q.bank_id, b.name AS bank_name,
                   q.type, q.status, q.stem_md, q.explanation_md, q.difficulty,
                   q.payload::text AS payload, q.source, q.version, q.created_at, q.updated_at
            FROM qb_question q
            JOIN app_user u ON u.id = q.owner_id
            JOIN qb_bank b ON b.id = q.bank_id
            WHERE q.id = ? AND q.deleted_at IS NULL
            """, adminMapper(), questionId);
        return rows.stream().findFirst();
    }

    public List<AdminQuestionRow> findAdminQuestions(String q, UUID bankId, UUID ownerId,
                                                      String type, String status,
                                                      Instant cursorTime, UUID cursorId, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT q.id, q.owner_id, u.email AS owner_email, q.bank_id, b.name AS bank_name,
                   q.type, q.status, q.stem_md, q.explanation_md, q.difficulty,
                   q.payload::text AS payload, q.source, q.version, q.created_at, q.updated_at
            FROM qb_question q
            JOIN app_user u ON u.id = q.owner_id
            JOIN qb_bank b ON b.id = q.bank_id
            WHERE q.deleted_at IS NULL
            """);
        List<Object> args = new ArrayList<>();
        if (bankId != null) {
            sql.append(" AND q.bank_id = ?");
            args.add(bankId);
        }
        if (ownerId != null) {
            sql.append(" AND q.owner_id = ?");
            args.add(ownerId);
        }
        appendFilters(sql, args, "q", type, status, q, null, null);
        appendCursor(sql, args, "q.updated_at", "q.id", cursorTime, cursorId);
        sql.append(" ORDER BY q.updated_at DESC, q.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), adminMapper(), args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String alias, String type, String status,
                               String q, UUID tagId, UUID knowledgePointId) {
        String prefix = alias.isEmpty() ? "" : alias + ".";
        if (type != null && !type.isBlank()) {
            sql.append(" AND ").append(prefix).append("type = ?");
            args.add(type);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND ").append(prefix).append("status = ?");
            args.add(status);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND ").append(prefix).append("search_text ILIKE ?");
            args.add("%" + q.trim() + "%");
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM qb_question_tag t WHERE t.question_id = ")
                .append(prefix).append("id AND t.tag_id = ?)");
            args.add(tagId);
        }
        if (knowledgePointId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM qb_question_knowledge_point k WHERE k.question_id = ")
                .append(prefix).append("id AND k.knowledge_point_id = ?)");
            args.add(knowledgePointId);
        }
    }

    private void appendCursor(StringBuilder sql, List<Object> args, String timeColumn, String idColumn,
                              Instant cursorTime, UUID cursorId) {
        if (cursorTime != null) {
            sql.append(" AND (").append(timeColumn).append(" < ? OR (")
                .append(timeColumn).append(" = ? AND ").append(idColumn).append(" < ?))");
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(java.sql.Timestamp.from(cursorTime));
            args.add(cursorId);
        }
    }

    public boolean bankExists(UUID ownerId, UUID bankId) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM qb_bank WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, Integer.class, bankId, ownerId);
        return count != null && count > 0;
    }

    public boolean tagsExist(UUID ownerId, List<UUID> tagIds) {
        for (UUID tagId : tagIds) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM qb_tag WHERE id = ? AND owner_id = ?", Integer.class, tagId, ownerId);
            if (count == null || count == 0) {
                return false;
            }
        }
        return true;
    }

    public boolean knowledgePointsExist(UUID ownerId, List<UUID> ids) {
        for (UUID id : ids) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM qb_knowledge_point WHERE id = ? AND owner_id = ?",
                Integer.class, id, ownerId);
            if (count == null || count == 0) {
                return false;
            }
        }
        return true;
    }

    public void insertQuestion(UUID id, UUID ownerId, UUID bankId, String type, String status,
                               String stemMd, String explanationMd, int difficulty, String payloadJson,
                               String searchText, String source) {
        jdbc.update("""
            INSERT INTO qb_question (id, owner_id, bank_id, type, status, stem_md, explanation_md,
                                     difficulty, payload, search_text, source, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 1)
            """, id, ownerId, bankId, type, status, stemMd, explanationMd, difficulty,
            payloadJson, searchText, source);
    }

    public int updateQuestion(UUID id, UUID ownerId, String status, String stemMd, String explanationMd,
                              int difficulty, String payloadJson, String searchText, String source,
                              int expectedVersion) {
        return jdbc.update("""
            UPDATE qb_question
            SET status = ?, stem_md = ?, explanation_md = ?, difficulty = ?, payload = ?::jsonb,
                search_text = ?, source = ?, version = version + 1, updated_at = now()
            WHERE id = ? AND owner_id = ? AND version = ? AND deleted_at IS NULL
            """, status, stemMd, explanationMd, difficulty, payloadJson, searchText, source,
            id, ownerId, expectedVersion);
    }

    public void softDeleteQuestion(UUID id, UUID ownerId) {
        jdbc.update("""
            UPDATE qb_question SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, id, ownerId);
    }

    public void insertVersion(UUID questionId, int versionNo, String snapshotJson, UUID createdBy) {
        jdbc.update("""
            INSERT INTO qb_question_version (id, question_id, version_no, snapshot, created_by)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """, UUID.randomUUID(), questionId, versionNo, snapshotJson, createdBy);
    }

    public List<VersionRow> findVersions(UUID questionId) {
        return jdbc.query("""
            SELECT version_no, created_by, created_at
            FROM qb_question_version
            WHERE question_id = ?
            ORDER BY version_no DESC
            """, (rs, rowNum) -> new VersionRow(
            rs.getInt("version_no"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant()
        ), questionId);
    }

    public void replaceTags(UUID questionId, List<UUID> tagIds) {
        jdbc.update("DELETE FROM qb_question_tag WHERE question_id = ?", questionId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO qb_question_tag (question_id, tag_id) VALUES (?, ?)",
            tagIds.stream().map(tagId -> new Object[] {questionId, tagId}).toList());
    }

    public void replaceKnowledgePoints(UUID questionId, List<UUID> ids) {
        jdbc.update("DELETE FROM qb_question_knowledge_point WHERE question_id = ?", questionId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
            INSERT INTO qb_question_knowledge_point (question_id, knowledge_point_id) VALUES (?, ?)
            """, ids.stream().map(id -> new Object[] {questionId, id}).toList());
    }

    public void replaceMedia(UUID questionId, List<UUID> mediaIds) {
        jdbc.update("DELETE FROM qb_question_media WHERE question_id = ?", questionId);
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        int[] order = {0};
        jdbc.batchUpdate("""
            INSERT INTO qb_question_media (question_id, media_id, sort_order) VALUES (?, ?, ?)
            """, mediaIds.stream().map(id -> new Object[] {questionId, id, order[0]++}).toList());
    }

    public List<UUID> findMediaIds(UUID questionId) {
        return jdbc.queryForList("""
            SELECT media_id FROM qb_question_media WHERE question_id = ? ORDER BY sort_order, media_id
            """, UUID.class, questionId);
    }

    public List<UUID> findTagIds(UUID questionId) {
        return jdbc.queryForList(
            "SELECT tag_id FROM qb_question_tag WHERE question_id = ? ORDER BY tag_id",
            UUID.class, questionId);
    }

    public List<UUID> findKnowledgePointIds(UUID questionId) {
        return jdbc.queryForList("""
            SELECT knowledge_point_id FROM qb_question_knowledge_point
            WHERE question_id = ? ORDER BY knowledge_point_id
            """, UUID.class, questionId);
    }

    public void insertSyncChange(UUID ownerId, UUID questionId, int version, String payloadJson) {
        jdbc.update("""
            INSERT INTO sync_change (owner_id, entity_type, entity_id, operation, entity_version, payload)
            VALUES (?, 'QUESTION', ?, 'UPSERT', ?, ?::jsonb)
            """, ownerId, questionId, version, payloadJson);
    }

    public void insertSyncTombstone(UUID ownerId, UUID questionId, String payloadJson) {
        jdbc.update("""
            INSERT INTO sync_change (owner_id, entity_type, entity_id, operation, payload)
            VALUES (?, 'QUESTION', ?, 'DELETE', ?::jsonb)
            """, ownerId, questionId, payloadJson);
    }

    private RowMapper<QuestionRow> questionMapper() {
        return (rs, rowNum) -> new QuestionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getObject("bank_id", UUID.class),
            rs.getString("type"),
            rs.getString("status"),
            rs.getString("stem_md"),
            rs.getString("explanation_md"),
            rs.getInt("difficulty"),
            rs.getString("payload"),
            rs.getString("source"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RowMapper<AdminQuestionRow> adminMapper() {
        return (rs, rowNum) -> new AdminQuestionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_email"),
            rs.getObject("bank_id", UUID.class),
            rs.getString("bank_name"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getString("stem_md"),
            rs.getString("explanation_md"),
            rs.getInt("difficulty"),
            rs.getString("payload"),
            rs.getString("source"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record QuestionRow(
        UUID id,
        UUID ownerId,
        UUID bankId,
        String type,
        String status,
        String stemMd,
        String explanationMd,
        int difficulty,
        String payloadJson,
        String source,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AdminQuestionRow(
        UUID id,
        UUID ownerId,
        String ownerEmail,
        UUID bankId,
        String bankName,
        String type,
        String status,
        String stemMd,
        String explanationMd,
        int difficulty,
        String payloadJson,
        String source,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record VersionRow(int versionNo, UUID createdBy, Instant createdAt) {
    }
}
