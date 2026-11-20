package com.boxue.api.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {

    private final JdbcTemplate jdbc;

    public MediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MediaRow> findByOwnerAndSha(UUID ownerId, String sha256) {
        List<MediaRow> rows = jdbc.query("""
            SELECT id, owner_id, sha256, mime_type, size_bytes, width, height,
                   storage_provider, storage_key, alt_text, created_at, deleted_at
            FROM qb_media
            WHERE owner_id = ? AND sha256 = ?
            """, mapper(), ownerId, sha256);
        return rows.stream().findFirst();
    }

    public Optional<MediaRow> findByIdAndOwner(UUID mediaId, UUID ownerId) {
        List<MediaRow> rows = jdbc.query("""
            SELECT id, owner_id, sha256, mime_type, size_bytes, width, height,
                   storage_provider, storage_key, alt_text, created_at, deleted_at
            FROM qb_media
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, mapper(), mediaId, ownerId);
        return rows.stream().findFirst();
    }

    public void insert(UUID id, UUID ownerId, String sha256, String mimeType, long sizeBytes,
                       Integer width, Integer height, String storageKey) {
        jdbc.update("""
            INSERT INTO qb_media (id, owner_id, sha256, mime_type, size_bytes, width, height,
                                  storage_provider, storage_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'S3', ?)
            """, id, ownerId, sha256, mimeType, sizeBytes, width, height, storageKey);
    }

    public void revive(UUID id, String mimeType, long sizeBytes, Integer width, Integer height,
                       String storageKey) {
        jdbc.update("""
            UPDATE qb_media
            SET mime_type = ?, size_bytes = ?, width = ?, height = ?, storage_key = ?, deleted_at = NULL
            WHERE id = ?
            """, mimeType, sizeBytes, width, height, storageKey, id);
    }

    public void softDelete(UUID id, UUID ownerId) {
        jdbc.update("""
            UPDATE qb_media SET deleted_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """, id, ownerId);
    }

    public long countQuestionReferences(UUID mediaId) {
        Long count = jdbc.queryForObject("""
            SELECT count(*) FROM qb_question_media m
            JOIN qb_question q ON q.id = m.question_id
            WHERE m.media_id = ? AND q.deleted_at IS NULL
            """, Long.class, mediaId);
        return count == null ? 0 : count;
    }

    private RowMapper<MediaRow> mapper() {
        return (rs, rowNum) -> new MediaRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("sha256"),
            rs.getString("mime_type"),
            rs.getLong("size_bytes"),
            rs.getObject("width", Integer.class),
            rs.getObject("height", Integer.class),
            rs.getString("storage_provider"),
            rs.getString("storage_key"),
            rs.getString("alt_text"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
        );
    }

    public record MediaRow(
        UUID id,
        UUID ownerId,
        String sha256,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        String storageProvider,
        String storageKey,
        String altText,
        Instant createdAt,
        Instant deletedAt
    ) {
    }
}
