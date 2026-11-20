package com.boxue.api.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AdminBootstrapRunnerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_bootstrap")
        .withUsername("boxue")
        .withPassword("boxue_test");

    @Test
    void createsAdminOnlyOnce() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            jdbc,
            new BCryptPasswordEncoder(),
            "bootstrap@boxue.local",
            "bootstrapadmin",
            "Bootstrap-Test-2026!",
            "Bootstrap Admin");

        runner.run();
        runner.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM app_user u " +
            "JOIN app_user_role ur ON ur.user_id = u.id " +
            "JOIN app_role r ON r.id = ur.role_id WHERE r.code = 'ADMIN'",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user", String.class))
            .startsWith("$2");
    }
}
