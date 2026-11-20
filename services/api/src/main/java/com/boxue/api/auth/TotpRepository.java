package com.boxue.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TotpRepository {

    private final JdbcTemplate jdbc;

    public TotpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TotpRow> findByUserId(UUID userId) {
        return jdbc.query("""
            SELECT user_id, secret_ciphertext, enabled, confirmed_at, last_used_at, created_at
            FROM app_user_totp WHERE user_id = ?
            """, (rs, rowNum) -> new TotpRow(
            rs.getObject("user_id", UUID.class),
            rs.getString("secret_ciphertext"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
            rs.getTimestamp("last_used_at") == null ? null : rs.getTimestamp("last_used_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
        ), userId).stream().findFirst();
    }

    public void upsertSetup(UUID userId, String secretCiphertext) {
        jdbc.update("""
            INSERT INTO app_user_totp (user_id, secret_ciphertext, enabled, confirmed_at)
            VALUES (?, ?, false, NULL)
            ON CONFLICT (user_id) DO UPDATE
            SET secret_ciphertext = EXCLUDED.secret_ciphertext,
                enabled = false,
                confirmed_at = NULL,
                last_used_at = NULL,
                updated_at = now()
            """, userId, secretCiphertext);
    }

    public void enable(UUID userId) {
        jdbc.update("""
            UPDATE app_user_totp
            SET enabled = true, confirmed_at = now(), last_used_at = now(), updated_at = now()
            WHERE user_id = ?
            """, userId);
    }

    public void disable(UUID userId) {
        jdbc.update("DELETE FROM app_user_totp WHERE user_id = ?", userId);
    }

    public void touch(UUID userId) {
        jdbc.update("""
            UPDATE app_user_totp SET last_used_at = now(), updated_at = now()
            WHERE user_id = ?
            """, userId);
    }

    public record TotpRow(
        UUID userId,
        String secretCiphertext,
        boolean enabled,
        Instant confirmedAt,
        Instant lastUsedAt,
        Instant createdAt
    ) {
    }
}
