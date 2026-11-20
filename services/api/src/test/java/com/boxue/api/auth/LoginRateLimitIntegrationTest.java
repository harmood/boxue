package com.boxue.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.boxue.api.bootstrap.AdminBootstrapRunner;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "boxue.auth.rate-limit.window-seconds=60",
        "boxue.auth.rate-limit.login-per-ip=100",
        "boxue.auth.rate-limit.login-per-account=3",
        "boxue.auth.rate-limit.admin-per-ip=100",
        "boxue.auth.rate-limit.admin-per-account=2"
    })
@Testcontainers(disabledWithoutDocker = true)
class LoginRateLimitIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_login_rate_limit")
        .withUsername("boxue")
        .withPassword("boxue_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    MailService mailService;

    @Test
    void blocksAccountAfterRepeatedAttemptsAndKeepsOtherAccountsUsable() {
        String email = "rate-user@boxue.local";
        String password = "RateUser-2026!";
        register(email, "rateuser", password);

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> failed = login(email, "wrong-password", "WEB");
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> blocked = login(email, password, "WEB");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("code")).isEqualTo("AUTH_RATE_LIMITED");
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();

        String otherEmail = "rate-other@boxue.local";
        String otherPassword = "RateOther-2026!";
        register(otherEmail, "rateother", otherPassword);
        ResponseEntity<Map> otherLogin = login(otherEmail, otherPassword, "WEB");
        assertThat(otherLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void separatelyLimitsAdminLoginByEmail() {
        new AdminBootstrapRunner(
            jdbc, passwordEncoder, "rate-admin@boxue.local", "rateadmin",
            "RateAdmin-2026!", "Rate Admin").run();

        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map> failed = adminLogin("rate-admin@boxue.local", "wrong-password");
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> blocked = adminLogin("rate-admin@boxue.local", "RateAdmin-2026!");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("code")).isEqualTo("AUTH_RATE_LIMITED");
    }

    private void register(String email, String username, String password) {
        ResponseEntity<Map> response = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", username,
            "password", password,
            "displayName", username,
            "acceptedTerms", true
        ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map> login(String login, String password, String platform) {
        return post("/api/v1/auth/login", Map.of(
            "login", login,
            "password", password,
            "device", device(platform)
        ));
    }

    private ResponseEntity<Map> adminLogin(String email, String password) {
        return post("/api/v1/admin/auth/login", Map.of(
            "email", email,
            "password", password,
            "device", device("ADMIN")
        ));
    }

    private Map<String, Object> device(String platform) {
        return Map.of(
            "deviceId", java.util.UUID.randomUUID().toString(),
            "deviceName", "Rate Limit Test",
            "platform", platform,
            "appVersion", "0.1.0"
        );
    }

    private ResponseEntity<Map> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
