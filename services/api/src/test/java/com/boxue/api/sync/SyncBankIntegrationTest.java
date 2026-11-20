package com.boxue.api.sync;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.boxue.api.auth.MailService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SyncBankIntegrationTest {

    private static final String DEVICE_ID = "99999999-9999-9999-9999-999999999999";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_sync")
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

    @MockitoBean
    MailService mailService;

    @Test
    void pushesPullsAndResolvesBankChanges() {
        String email = "sync-user@boxue.local";
        String password = "SyncUser-2026!";
        register(email, "syncuser", password);
        String token = login(email, password, DEVICE_ID);

        ResponseEntity<Map> created = post("/api/v1/banks", Map.of(
            "name", "同步题库",
            "description", "创建于服务端",
            "visibility", "PRIVATE"
        ), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String serverBankId = (String) created.getBody().get("id");

        ResponseEntity<Map> afterCreate = pull(token, 0L);
        assertThat(afterCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> initialChanges = (List<?>) afterCreate.getBody().get("changes");
        assertThat(initialChanges).isNotEmpty();
        Map<?, ?> firstChange = (Map<?, ?>) initialChanges.get(0);
        assertThat(firstChange.get("entityType")).isEqualTo("BANK");
        assertThat(firstChange.get("operation")).isEqualTo("UPSERT");
        assertThat(firstChange.get("entityId")).isEqualTo(serverBankId);
        long cursor = ((Number) afterCreate.getBody().get("nextCursor")).longValue();

        String clientBankId = UUID.randomUUID().toString();
        Map<String, Object> createChange = change(UUID.randomUUID().toString(), clientBankId, "CREATE", null,
            Map.of("name", "离线创建题库", "description", "来自客户端", "visibility", "UNLISTED"));
        ResponseEntity<Map> pushCreate = push(token, createChange);
        assertThat(pushCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> accepted = (Map<?, ?>) ((List<?>) pushCreate.getBody().get("accepted")).get(0);
        long createSeq = ((Number) accepted.get("serverSeq")).longValue();
        assertThat(accepted.get("entityVersion")).isEqualTo(1);

        ResponseEntity<Map> afterPush = pull(token, cursor);
        List<?> pushedChanges = (List<?>) afterPush.getBody().get("changes");
        assertThat(pushedChanges).isNotEmpty();
        assertThat(((Map<?, ?>) pushedChanges.get(0)).get("entityId")).isEqualTo(clientBankId);

        ResponseEntity<Map> duplicate = push(token, createChange);
        Map<?, ?> duplicateAccepted = (Map<?, ?>) ((List<?>) duplicate.getBody().get("accepted")).get(0);
        assertThat(((Number) duplicateAccepted.get("serverSeq")).longValue()).isEqualTo(createSeq);

        Map<String, Object> updateChange = change(UUID.randomUUID().toString(), clientBankId, "UPSERT", 1,
            Map.of("name", "离线创建题库-改", "description", "客户端更新", "visibility", "UNLISTED", "status", "ARCHIVED"));
        ResponseEntity<Map> pushUpdate = push(token, updateChange);
        assertThat(pushUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> updateAccepted = (Map<?, ?>) ((List<?>) pushUpdate.getBody().get("accepted")).get(0);
        assertThat(updateAccepted.get("entityVersion")).isEqualTo(2);

        Map<String, Object> staleUpdate = change(UUID.randomUUID().toString(), clientBankId, "UPSERT", 1,
            Map.of("name", "过期更新", "description", "不应成功", "visibility", "PRIVATE", "status", "ACTIVE"));
        ResponseEntity<Map> pushStale = push(token, staleUpdate);
        List<?> rejected = (List<?>) pushStale.getBody().get("rejected");
        assertThat(rejected).hasSize(1);
        assertThat(((Map<?, ?>) rejected.get(0)).get("code")).isEqualTo("BANK_VERSION_CONFLICT");
        assertThat(((Map<?, ?>) rejected.get(0)).get("conflict").toString()).contains("currentVersion");

        Map<String, Object> deleteChange = change(UUID.randomUUID().toString(), clientBankId, "DELETE", 2, null);
        ResponseEntity<Map> pushDelete = push(token, deleteChange);
        assertThat((List<?>) pushDelete.getBody().get("accepted")).hasSize(1);

        ResponseEntity<Map> status = get("/api/v1/sync/status", token);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) status.getBody().get("serverCursor")).longValue()).isGreaterThanOrEqualTo(createSeq);
        assertThat(status.getBody().get("lastPulledAt")).isNotNull();
        assertThat(status.getBody().get("lastPushedAt")).isNotNull();

        String otherEmail = "sync-other@boxue.local";
        register(otherEmail, "syncother", "SyncOther-2026!");
        String otherToken = login(otherEmail, "SyncOther-2026!", "88888888-8888-8888-8888-888888888888");
        ResponseEntity<Map> otherPull = pull(otherToken, 0L);
        assertThat((List<?>) otherPull.getBody().get("changes")).isEmpty();
    }

    private Map<String, Object> change(String clientChangeId, String entityId, String operation,
                                       Integer baseVersion, Map<String, Object> payload) {
        Map<String, Object> change = new java.util.HashMap<>();
        change.put("clientChangeId", clientChangeId);
        change.put("entityType", "BANK");
        change.put("entityId", entityId);
        change.put("operation", operation);
        if (baseVersion != null) {
            change.put("baseVersion", baseVersion);
        }
        if (payload != null) {
            change.put("payload", payload);
        }
        return change;
    }

    private ResponseEntity<Map> push(String token, Map<String, Object> change) {
        return post("/api/v1/sync/push", Map.of(
            "deviceId", DEVICE_ID,
            "changes", List.of(change)
        ), token);
    }

    private ResponseEntity<Map> pull(String token, long cursor) {
        return get("/api/v1/sync/pull?cursor=" + cursor + "&limit=100", token);
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

    private String login(String email, String password, String deviceId) {
        ResponseEntity<Map> response = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", Map.of(
                "deviceId", deviceId,
                "deviceName", "Sync Test",
                "platform", "DESKTOP",
                "appVersion", "0.1.0")
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
