package com.boxue.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 验证 Flyway 迁移可以在全新 PostgreSQL 上从零执行，并写入系统角色/权限/开关。
 * 本机没有 Docker 时自动跳过；CI 中必须真实执行。
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue")
        .withUsername("boxue")
        .withPassword("boxue_test");

    @Test
    void migrationsApplyAndSeedSystemData() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {

            assertThat(count(statement,
                "SELECT count(*) FROM app_role WHERE code IN ('USER','ADMIN')")).isEqualTo(2);
            assertThat(count(statement,
                "SELECT count(*) FROM app_permission WHERE code LIKE 'ADMIN_%'")).isGreaterThanOrEqualTo(8);
            assertThat(count(statement,
                "SELECT count(*) FROM app_role_permission")).isGreaterThanOrEqualTo(9);
            assertThat(count(statement,
                "SELECT count(*) FROM app_feature_flag WHERE enabled = false")).isEqualTo(4);
            assertThat(count(statement,
                "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema='public' AND table_name='qb_question'")).isEqualTo(1);
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
