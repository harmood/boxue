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
        "boxue.auth.rate-limit.admin-per-ip=100",
        "boxue.auth.rate-limit.admin-per-account=100"
    })
@Testcontainers(disabledWithoutDocker = true)
class AdminTotpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_admin_totp")
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
    TotpService totpService;

    @MockitoBean
    MailService mailService;

    @Test
    void enrollsRequiresAndDisablesAdminTotp() {
        String email = "totp-admin@boxue.local";
        String password = "TotpAdmin-2026!";
        new AdminBootstrapRunner(
            jdbc, passwordEncoder, email, "totpadmin", password, "TOTP Admin").run();

        ResponseEntity<Map> firstLogin = adminLogin(email, password, null);
        assertThat(firstLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstLogin.getBody().get("totpEnabled")).isEqualTo(false);
        String token = (String) firstLogin.getBody().get("accessToken");

        ResponseEntity<Map> status = get("/api/v1/admin/auth/totp/status", token);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("enabled")).isEqualTo(false);

        ResponseEntity<Map> setup = post("/api/v1/admin/auth/totp/setup", Map.of("password", password), token);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secret = (String) setup.getBody().get("secret");
        assertThat(secret).isNotBlank();
        assertThat((String) setup.getBody().get("otpauthUri")).startsWith("otpauth://totp/");

        ResponseEntity<Map> enabled = post("/api/v1/admin/auth/totp/enable",
            Map.of("code", totpService.currentCode(secret)), token);
        assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(enabled.getBody().get("enabled")).isEqualTo(true);

        ResponseEntity<Map> me = get("/api/v1/admin/auth/me", token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("totpEnabled")).isEqualTo(true);

        ResponseEntity<Map> missingCode = adminLogin(email, password, null);
        assertThat(missingCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingCode.getBody().get("code")).isEqualTo("AUTH_TOTP_REQUIRED");

        ResponseEntity<Map> wrongCode = adminLogin(email, password, "000000");
        assertThat(wrongCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongCode.getBody().get("code")).isEqualTo("AUTH_TOTP_INVALID");

        String code = totpService.currentCode(secret);
        ResponseEntity<Map> secondLogin = adminLogin(email, password, code);
        assertThat(secondLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondLogin.getBody().get("totpEnabled")).isEqualTo(true);
        String secondToken = (String) secondLogin.getBody().get("accessToken");

        ResponseEntity<Map> badDisable = post("/api/v1/admin/auth/totp/disable", Map.of(
            "password", "wrong-password",
            "code", totpService.currentCode(secret)), secondToken);
        assertThat(badDisable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badDisable.getBody().get("code")).isEqualTo("AUTH_CURRENT_PASSWORD_INVALID");

        ResponseEntity<Void> disabled = postVoid("/api/v1/admin/auth/totp/disable", Map.of(
            "password", password,
            "code", totpService.currentCode(secret)), secondToken);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> disabledStatus = get("/api/v1/admin/auth/totp/status", secondToken);
        assertThat(disabledStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabledStatus.getBody().get("enabled")).isEqualTo(false);
    }

    private ResponseEntity<Map> adminLogin(String email, String password, String totpCode) {
        Map<String, Object> body = totpCode == null
            ? Map.of("email", email, "password", password, "device", device())
            : Map.of("email", email, "password", password, "totpCode", totpCode, "device", device());
        return post("/api/v1/admin/auth/login", body, null);
    }

    private Map<String, Object> device() {
        return Map.of(
            "deviceId", java.util.UUID.randomUUID().toString(),
            "deviceName", "TOTP Test",
            "platform", "ADMIN",
            "appVersion", "0.1.0"
        );
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<Void> postVoid(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), Void.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
