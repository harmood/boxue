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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.boxue.api.bootstrap.AdminBootstrapRunner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_auth")
        .withUsername("boxue")
        .withPassword("boxue_test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
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

    @Test
    void userAndAdminAuthenticationFlow() {
        String email = "alice@boxue.local";
        String username = "alice";
        String password = "StrongPass-2026!";
        String newPassword = "StrongPass-2027!";

        ResponseEntity<Map> register = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", username,
            "password", password,
            "displayName", "Alice",
            "acceptedTerms", true
        ), null);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(statusOf(register)).isEqualTo("ACTIVE");

        ResponseEntity<Map> login = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", device("11111111-1111-1111-1111-111111111111", "DESKTOP")
        ), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = (String) login.getBody().get("accessToken");
        String refresh = (String) login.getBody().get("refreshToken");
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        java.util.List<String> roles = ((java.util.List<?>) login.getBody().get("roles")).stream()
            .map(String::valueOf).toList();
        assertThat(roles).contains("USER");

        ResponseEntity<Map> me = get("/api/v1/me", access);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);

        ResponseEntity<Map> devices = get("/api/v1/devices", access);
        assertThat(devices.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) devices.getBody().get("items")).isNotEmpty();

        ResponseEntity<Map> userOnAdmin = get("/api/v1/admin/ping", access);
        assertThat(userOnAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> changed = post("/api/v1/me/change-password", Map.of(
            "currentPassword", password,
            "newPassword", newPassword,
            "revokeOtherSessions", true
        ), access);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> oldLogin = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", device("11111111-1111-1111-1111-111111111111", "DESKTOP")
        ), null);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> newLogin = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", newPassword,
            "device", device("11111111-1111-1111-1111-111111111111", "DESKTOP")
        ), null);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access2 = (String) newLogin.getBody().get("accessToken");
        String refresh2 = (String) newLogin.getBody().get("refreshToken");

        ResponseEntity<Map> refreshed = post("/api/v1/auth/refresh",
            Map.of("refreshToken", refresh2), null);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access3 = (String) refreshed.getBody().get("accessToken");
        String refresh3 = (String) refreshed.getBody().get("refreshToken");
        assertThat(access3).isNotBlank();
        assertThat(refresh3).isNotBlank();

        ResponseEntity<Void> logout = postVoid("/api/v1/auth/logout", Map.of(), access3);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> refreshAfterLogout = post("/api/v1/auth/refresh",
            Map.of("refreshToken", refresh3), null);
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        new AdminBootstrapRunner(
            jdbc, passwordEncoder,
            "admin@boxue.local", "admin", "AdminPass-2026!", "Administrator").run();

        ResponseEntity<Map> adminLogin = post("/api/v1/admin/auth/login", Map.of(
            "email", "admin@boxue.local",
            "password", "AdminPass-2026!",
            "device", device("22222222-2222-2222-2222-222222222222", "ADMIN")
        ), null);
        assertThat(adminLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminAccess = (String) adminLogin.getBody().get("accessToken");
        assertThat(adminAccess).isNotBlank();

        ResponseEntity<Map> adminMe = get("/api/v1/admin/auth/me", adminAccess);
        assertThat(adminMe.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> adminOnUser = get("/api/v1/me", adminAccess);
        assertThat(adminOnUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Map<String, Object> device(String deviceId, String platform) {
        return Map.of(
            "deviceId", deviceId,
            "deviceName", "Test Device",
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

    private ResponseEntity<Void> postVoid(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private String statusOf(ResponseEntity<Map> response) {
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        return (String) user.get("status");
    }
}
