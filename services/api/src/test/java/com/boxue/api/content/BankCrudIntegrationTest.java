package com.boxue.api.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

import com.boxue.api.auth.MailService;
import com.boxue.api.bootstrap.AdminBootstrapRunner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class BankCrudIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_banks")
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
    void userManagesBanksWithIsolationCascadeAndAdminVisibility() {
        String ownerEmail = "bank-owner@boxue.local";
        register(ownerEmail, "bankowner", "BankOwner-2026!");
        String ownerToken = login(ownerEmail, "BankOwner-2026!", "11111111-1111-1111-1111-111111111111");
        String ownerId = jdbc.queryForObject(
            "SELECT id::text FROM app_user WHERE email = ?", String.class, ownerEmail);

        ResponseEntity<Map> created = post("/api/v1/banks", Map.of(
            "name", "Java 基础",
            "description", "Java 语法与集合",
            "visibility", "PRIVATE"
        ), ownerToken);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name")).isEqualTo("Java 基础");
        assertThat(created.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(created.getBody().get("version")).isEqualTo(1);
        assertThat(((Number) created.getBody().get("questionCount")).intValue()).isZero();
        String bankId = (String) created.getBody().get("id");

        ResponseEntity<Map> duplicate = post("/api/v1/banks", Map.of(
            "name", "java 基础"
        ), ownerToken);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("BANK_NAME_EXISTS");

        post("/api/v1/banks", Map.of("name", "数据结构", "visibility", "UNLISTED"), ownerToken);

        ResponseEntity<Map> list = get("/api/v1/banks?q=Java&status=ACTIVE&limit=20", ownerToken);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) list.getBody().get("items")).hasSize(1);
        assertThat(list.getBody().get("hasMore")).isEqualTo(false);
        assertThat(list.getBody().get("nextCursor")).isNull();

        ResponseEntity<Map> detail = get("/api/v1/banks/" + bankId, ownerToken);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("description")).isEqualTo("Java 语法与集合");

        ResponseEntity<Map> updated = patch("/api/v1/banks/" + bankId, Map.of(
            "name", "Java 进阶",
            "description", "集合与并发",
            "status", "ARCHIVED",
            "version", 1
        ), ownerToken);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo("Java 进阶");
        assertThat(updated.getBody().get("status")).isEqualTo("ARCHIVED");
        assertThat(updated.getBody().get("version")).isEqualTo(2);

        ResponseEntity<Map> cleared = patch("/api/v1/banks/" + bankId, Map.of(
            "description", "",
            "version", 2), ownerToken);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("description")).isNull();
        assertThat(cleared.getBody().get("version")).isEqualTo(3);

        ResponseEntity<Map> stale = patch("/api/v1/banks/" + bankId, Map.of(
            "name", "过期更新",
            "version", 1
        ), ownerToken);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("BANK_VERSION_CONFLICT");

        String otherEmail = "bank-other@boxue.local";
        register(otherEmail, "bankother", "BankOther-2026!");
        String otherToken = login(otherEmail, "BankOther-2026!", "22222222-2222-2222-2222-222222222222");
        ResponseEntity<Map> hidden = get("/api/v1/banks/" + bankId, otherToken);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID questionId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO qb_question (id, owner_id, bank_id, type, status, stem_md)
            VALUES (?, ?::uuid, ?::uuid, 'SINGLE_CHOICE', 'PUBLISHED', 'Java 的父类是？')
            """, questionId, ownerId, bankId);

        ResponseEntity<Map> notEmpty = delete("/api/v1/banks/" + bankId + "?cascade=false", ownerToken);
        assertThat(notEmpty.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notEmpty.getBody().get("code")).isEqualTo("BANK_NOT_EMPTY");
        assertThat(((Map<?, ?>) notEmpty.getBody().get("details")).get("questionCount")).isEqualTo(1);

        new AdminBootstrapRunner(
            jdbc, passwordEncoder, "bank-admin@boxue.local", "bankadmin", "BankAdmin-2026!", "Bank Admin").run();
        String adminToken = loginAdmin("bank-admin@boxue.local", "BankAdmin-2026!",
            "33333333-3333-3333-3333-333333333333");

        ResponseEntity<Map> adminList = get(
            "/api/v1/admin/banks?q=Java&status=ARCHIVED&visibility=PRIVATE&limit=20", adminToken);
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) adminList.getBody().get("items")).hasSize(1);
        Map<?, ?> adminItem = (Map<?, ?>) ((List<?>) adminList.getBody().get("items")).get(0);
        assertThat(adminItem.get("ownerEmail")).isEqualTo(ownerEmail);
        assertThat(adminItem.get("id")).isEqualTo(bankId);

        ResponseEntity<Map> adminDetail = get("/api/v1/admin/banks/" + bankId, adminToken);
        assertThat(adminDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) adminDetail.getBody().get("questionCount")).intValue()).isEqualTo(1);

        ResponseEntity<Void> cascaded = deleteVoid("/api/v1/banks/" + bankId + "?cascade=true", ownerToken);
        assertThat(cascaded.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM qb_bank WHERE id = ?::uuid", Boolean.class, bankId))
            .isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM qb_question WHERE id = ?", Boolean.class, questionId))
            .isTrue();
        Integer tombstones = jdbc.queryForObject("""
            SELECT count(*) FROM sync_change
            WHERE entity_type = 'BANK' AND entity_id = ?::uuid AND operation = 'DELETE'
            """, Integer.class, bankId);
        assertThat(tombstones).isEqualTo(1);
        Integer questionTombstones = jdbc.queryForObject("""
            SELECT count(*) FROM sync_change
            WHERE entity_type = 'QUESTION' AND entity_id = ? AND operation = 'DELETE'
            """, Integer.class, questionId);
        assertThat(questionTombstones).isEqualTo(1);

        ResponseEntity<Map> gone = get("/api/v1/banks/" + bankId, ownerToken);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void register(String email, String username, String password) {
        ResponseEntity<Map> response = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", username,
            "password", password,
            "displayName", username,
            "acceptedTerms", true
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String login(String login, String password, String deviceId) {
        ResponseEntity<Map> response = post("/api/v1/auth/login", Map.of(
            "login", login,
            "password", password,
            "device", device(deviceId, "WEB")
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private String loginAdmin(String email, String password, String deviceId) {
        ResponseEntity<Map> response = post("/api/v1/admin/auth/login", Map.of(
            "email", email,
            "password", password,
            "device", device(deviceId, "ADMIN")
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private Map<String, Object> device(String id, String platform) {
        return Map.of(
            "deviceId", id,
            "deviceName", "Bank Test",
            "platform", platform,
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

    private ResponseEntity<Map> patch(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<Map> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<Void> deleteVoid(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
