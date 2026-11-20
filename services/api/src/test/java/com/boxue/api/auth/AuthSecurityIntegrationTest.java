package com.boxue.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_auth_security")
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

    @Autowired
    JwtService jwtService;

    @MockitoBean
    MailService mailService;

    @Test
    void refreshTokensCannotCrossAudienceAndMalformedTokensAreRejected() {
        String email = "audience-user@boxue.local";
        String password = "AudienceUser-2026!";
        register(email, "audienceuser", password, "WEB");
        ResponseEntity<Map> clientLogin = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", device("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "DESKTOP")
        ), null);
        assertThat(clientLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String clientRefresh = (String) clientLogin.getBody().get("refreshToken");
        String clientAccess = (String) clientLogin.getBody().get("accessToken");
        String userId = (String) ((Map<?, ?>) clientLogin.getBody().get("user")).get("id");
        assertThat(clientRefresh).isNotBlank();

        String verificationToken = "disabled-verification-token";
        jdbc.update("""
            INSERT INTO app_email_verification (id, user_id, email, token_hash, expires_at)
            VALUES (?, ?::uuid, ?, ?, now() + interval '1 hour')
            """, UUID.randomUUID(), userId, email, jwtService.hashRefreshToken(verificationToken));
        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?::uuid", userId);
        ResponseEntity<Map> verifyDisabled = post("/api/v1/auth/verify-email",
            Map.of("token", verificationToken), null);
        assertThat(verifyDisabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
            "SELECT status FROM app_user WHERE id = ?::uuid", String.class, userId))
            .isEqualTo("DISABLED");

        ResponseEntity<Map> crossAudience = post("/api/v1/admin/auth/refresh",
            Map.of("refreshToken", clientRefresh), null);
        assertThat(crossAudience.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(crossAudience.getBody().get("code")).isEqualTo("AUTH_REFRESH_AUDIENCE_MISMATCH");

        new AdminBootstrapRunner(
            jdbc, passwordEncoder, "audience-admin@boxue.local", "audienceadmin",
            "AudienceAdmin-2026!", "Audience Admin").run();
        ResponseEntity<Map> adminLogin = post("/api/v1/admin/auth/login", Map.of(
            "email", "audience-admin@boxue.local",
            "password", "AudienceAdmin-2026!",
            "device", device("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "ADMIN")
        ), null);
        assertThat(adminLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminRefresh = cookieValue(adminLogin, "refresh_token");
        assertThat(adminRefresh).isNotBlank();

        ResponseEntity<Map> adminToClient = post("/api/v1/auth/refresh",
            Map.of("refreshToken", adminRefresh), null);
        assertThat(adminToClient.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(adminToClient.getBody().get("code")).isEqualTo("AUTH_REFRESH_AUDIENCE_MISMATCH");

        HttpHeaders forgedHeaders = new HttpHeaders();
        forgedHeaders.setBearerAuth("not-a-valid-jwt");
        ResponseEntity<Map> forged = rest.exchange("/api/v1/me", HttpMethod.GET,
            new HttpEntity<>(forgedHeaders), Map.class);
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders clientHeaders = new HttpHeaders();
        clientHeaders.setBearerAuth(clientAccess);
        ResponseEntity<Map> badUuid = rest.exchange("/api/v1/questions/not-a-uuid", HttpMethod.GET,
            new HttpEntity<>(clientHeaders), Map.class);
        assertThat(badUuid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badUuid.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    private void register(String email, String username, String password, String deviceId) {
        ResponseEntity<Map> response = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", username,
            "password", password,
            "displayName", username,
            "acceptedTerms", true
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String cookieValue(ResponseEntity<?> response, String name) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (setCookie == null) {
            return null;
        }
        for (String part : setCookie.split(";")) {
            String value = part.trim();
            if (value.startsWith(name + "=")) {
                return value.substring(name.length() + 1);
            }
        }
        return null;
    }

    private Map<String, Object> device(String id, String platform) {
        return Map.of(
            "deviceId", id,
            "deviceName", "Auth Security Test",
            "platform", platform,
            "appVersion", "0.1.0"
        );
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
