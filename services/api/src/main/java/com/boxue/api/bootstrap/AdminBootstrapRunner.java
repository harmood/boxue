package com.boxue.api.bootstrap;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 一次性管理员引导命令。
 *
 * 运行方式：
 *   mvn spring-boot:run -Dspring-boot.run.profiles=bootstrap-admin
 *
 * 必填环境变量：
 *   BOXUE_BOOTSTRAP_ADMIN_EMAIL
 *   BOXUE_BOOTSTRAP_ADMIN_PASSWORD（至少 12 位）
 */
@Component
@Profile("bootstrap-admin")
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrapRunner(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Value("${boxue.bootstrap.admin.email:}") String email,
            @org.springframework.beans.factory.annotation.Value("${boxue.bootstrap.admin.username:admin}") String username,
            @org.springframework.beans.factory.annotation.Value("${boxue.bootstrap.admin.password:}") String password,
            @org.springframework.beans.factory.annotation.Value("${boxue.bootstrap.admin.display-name:Administrator}") String displayName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        validateInput();

        Integer adminCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM app_user u
                JOIN app_user_role ur ON ur.user_id = u.id
                JOIN app_role r ON r.id = ur.role_id
                WHERE r.code = 'ADMIN' AND u.deleted_at IS NULL
                """, Integer.class);
        if (adminCount != null && adminCount > 0) {
            log.info("Bootstrap skipped: an active ADMIN account already exists (count={}).", adminCount);
            return;
        }

        Integer conflictCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM app_user
                WHERE deleted_at IS NULL AND (email = ? OR username = ?)
                """, Integer.class, email, username);
        if (conflictCount != null && conflictCount > 0) {
            throw new IllegalStateException(
                    "Cannot create bootstrap admin: email or username already exists. "
                            + "Use a different BOXUE_BOOTSTRAP_ADMIN_EMAIL/USERNAME or grant ADMIN to the existing user.");
        }

        UUID adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN'", UUID.class);
        if (adminRoleId == null) {
            throw new IllegalStateException("ADMIN role is missing. Run Flyway migrations before bootstrap.");
        }

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, username, password_hash, status, email_verified_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', now())
                """, userId, email, username, passwordEncoder.encode(password));
        jdbcTemplate.update("""
                INSERT INTO app_user_profile (user_id, display_name, locale, timezone)
                VALUES (?, ?, 'zh-CN', 'Asia/Shanghai')
                """, userId, displayName);
        jdbcTemplate.update("""
                INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)
                """, userId, adminRoleId);

        log.info("Bootstrap admin created: id={}, email={}, username={}", userId, email, username);
        log.info("Please sign in and change the initial password immediately.");
    }

    private void validateInput() {
        if (!StringUtils.hasText(email)) {
            throw new IllegalStateException("BOXUE_BOOTSTRAP_ADMIN_EMAIL is required.");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("BOXUE_BOOTSTRAP_ADMIN_USERNAME is required.");
        }
        if (!StringUtils.hasText(password) || password.length() < 12) {
            throw new IllegalStateException("BOXUE_BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters.");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalStateException("BOXUE_BOOTSTRAP_ADMIN_EMAIL is not a valid email address.");
        }
        if (!username.matches("^[a-zA-Z0-9_-]{3,64}$")) {
            throw new IllegalStateException("BOXUE_BOOTSTRAP_ADMIN_USERNAME must match ^[a-zA-Z0-9_-]{3,64}$.");
        }
    }
}
