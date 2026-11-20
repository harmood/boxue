package com.boxue.api.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

import com.boxue.api.auth.MailService;
import com.boxue.api.bootstrap.AdminBootstrapRunner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class QuestionCrudIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_questions")
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
    void userCreatesValidatesUpdatesPublishesAndDeletesFourQuestionTypes() {
        String email = "question-owner@boxue.local";
        register(email, "questionowner", "QuestionOwner-2026!");
        String token = login(email, "QuestionOwner-2026!", "44444444-4444-4444-4444-444444444444");
        String ownerId = jdbc.queryForObject(
            "SELECT id::text FROM app_user WHERE email = ?", String.class, email);

        ResponseEntity<Map> bank = post("/api/v1/banks", Map.of("name", "题型测试"), token);
        assertThat(bank.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bankId = (String) bank.getBody().get("id");

        String singleId = createQuestion(bankId, token, Map.of(
            "type", "SINGLE_CHOICE",
            "stem", doc("已知 $x^2 + 1 = 0$，求 $x$ 的取值范围。"),
            "explanation", doc("在实数范围内无解。"),
            "difficulty", 3,
            "payload", Map.of(
                "options", List.of(
                    option("A", "有实数解"),
                    option("B", "无实数解")),
                "answer", Map.of("correctOptionKeys", List.of("B")))));

        ResponseEntity<Map> invalidSingle = post("/api/v1/banks/" + bankId + "/questions", Map.of(
            "type", "SINGLE_CHOICE",
            "stem", doc("单选题只能有一个答案"),
            "payload", Map.of(
                "options", List.of(option("A", "1"), option("B", "2")),
                "answer", Map.of("correctOptionKeys", List.of("A", "B")))), token);
        assertThat(invalidSingle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidSingle.getBody().get("code")).isEqualTo("QUESTION_PAYLOAD_INVALID");

        ResponseEntity<Map> unknownOption = post("/api/v1/banks/" + bankId + "/questions", Map.of(
            "type", "SINGLE_CHOICE",
            "stem", doc("正确选项不存在"),
            "payload", Map.of(
                "options", List.of(option("A", "1"), option("B", "2")),
                "answer", Map.of("correctOptionKeys", List.of("Z")))), token);
        assertThat(unknownOption.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String multipleId = createQuestion(bankId, token, Map.of(
            "type", "MULTIPLE_CHOICE",
            "stem", doc("下列哪些是质数？"),
            "difficulty", 2,
            "payload", Map.of(
                "options", List.of(option("A", "2"), option("B", "4"), option("C", "5")),
                "answer", Map.of("correctOptionKeys", List.of("A", "C")))));

        String trueFalseId = createQuestion(bankId, token, Map.of(
            "type", "TRUE_FALSE",
            "stem", doc("若 $a > b$，则 $a^2 > b^2$。"),
            "payload", Map.of("answer", Map.of("value", false))));

        ResponseEntity<Map> cleared = patch("/api/v1/questions/" + trueFalseId, Map.of(
            "explanation", doc(""),
            "source", "",
            "version", 1), token);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("explanation")).isNull();
        assertThat(cleared.getBody().get("source")).isNull();
        assertThat(cleared.getBody().get("version")).isEqualTo(2);

        String fillBlankId = createQuestion(bankId, token, Map.of(
            "type", "FILL_BLANK",
            "stem", doc("函数 $f(x)=x^2$ 的导数是 ____。"),
            "payload", Map.of("blanks", List.of(
                Map.of("id", "blank-1", "acceptedAnswers", List.of("2x", "2*x"),
                    "caseSensitive", false, "trimWhitespace", true)))));

        ResponseEntity<Map> invalidTrueFalse = post("/api/v1/banks/" + bankId + "/questions", Map.of(
            "type", "TRUE_FALSE",
            "stem", doc("缺少答案"),
            "payload", Map.of()), token);
        assertThat(invalidTrueFalse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> invalidBlank = post("/api/v1/banks/" + bankId + "/questions", Map.of(
            "type", "FILL_BLANK",
            "stem", doc("空的填空答案"),
            "payload", Map.of("blanks", List.of(Map.of("id", "blank-1", "acceptedAnswers", List.of())))), token);
        assertThat(invalidBlank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> all = get("/api/v1/banks/" + bankId + "/questions?limit=20", token);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) all.getBody().get("items")).hasSize(4);

        ResponseEntity<Map> filtered = get(
            "/api/v1/banks/" + bankId + "/questions?type=SINGLE_CHOICE&status=DRAFT&q=取值范围&limit=20", token);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) filtered.getBody().get("items")).hasSize(1);

        ResponseEntity<Map> detail = get("/api/v1/questions/" + fillBlankId, token);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("type")).isEqualTo("FILL_BLANK");
        assertThat(detail.getBody().get("version")).isEqualTo(1);
        assertThat(detail.getBody().get("stem").toString()).contains("导数");

        ResponseEntity<Map> updated = patch("/api/v1/questions/" + singleId, Map.of(
            "stem", doc("更新后的题干：$x^2 + 1 = 0$ 有实数解吗？"),
            "difficulty", 4,
            "payload", Map.of(
                "options", List.of(option("A", "有"), option("B", "没有")),
                "answer", Map.of("correctOptionKeys", List.of("B"))),
            "version", 1), token);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("version")).isEqualTo(2);
        assertThat(updated.getBody().get("difficulty")).isEqualTo(4);

        ResponseEntity<Map> stale = patch("/api/v1/questions/" + singleId, Map.of(
            "stem", doc("过期更新"), "version", 1), token);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("QUESTION_VERSION_CONFLICT");

        ResponseEntity<Map> published = post("/api/v1/questions/" + singleId + "/publish", Map.of(), token);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().get("status")).isEqualTo("PUBLISHED");
        assertThat(published.getBody().get("version")).isEqualTo(3);

        ResponseEntity<Map> versions = get("/api/v1/questions/" + singleId + "/versions", token);
        assertThat(versions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) versions.getBody().get("items")).hasSize(3);

        ResponseEntity<Map> duplicated = post("/api/v1/questions/" + singleId + "/duplicate",
            Map.of("targetBankId", bankId), token);
        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicated.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(duplicated.getBody().get("id")).isNotEqualTo(singleId);

        String otherEmail = "question-other@boxue.local";
        register(otherEmail, "questionother", "QuestionOther-2026!");
        String otherToken = login(otherEmail, "QuestionOther-2026!", "55555555-5555-5555-5555-555555555555");
        ResponseEntity<Map> hidden = get("/api/v1/questions/" + singleId, otherToken);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> hiddenBank = get("/api/v1/banks/" + bankId + "/questions", otherToken);
        assertThat(hiddenBank.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        new AdminBootstrapRunner(
            jdbc, passwordEncoder, "question-admin@boxue.local", "questionadmin", "QuestionAdmin-2026!", "Q Admin").run();
        String adminToken = loginAdmin("question-admin@boxue.local", "QuestionAdmin-2026!",
            "66666666-6666-6666-6666-666666666666");
        ResponseEntity<Map> adminList = get(
            "/api/v1/admin/questions?type=FILL_BLANK&status=DRAFT&limit=20", adminToken);
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) adminList.getBody().get("items")).hasSize(1);
        Map<?, ?> adminItem = (Map<?, ?>) ((List<?>) adminList.getBody().get("items")).get(0);
        assertThat(adminItem.get("ownerEmail")).isEqualTo(email);
        assertThat(adminItem.get("bankId")).isEqualTo(bankId);
        ResponseEntity<Map> adminDetail = get("/api/v1/admin/questions/" + fillBlankId, adminToken);
        assertThat(adminDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminDetail.getBody().get("payload").toString()).contains("acceptedAnswers");

        ResponseEntity<Void> deleted = delete("/api/v1/questions/" + multipleId, token);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Integer tombstones = jdbc.queryForObject("""
            SELECT count(*) FROM sync_change
            WHERE entity_type = 'QUESTION' AND entity_id = ?::uuid AND operation = 'DELETE'
            """, Integer.class, multipleId);
        assertThat(tombstones).isEqualTo(1);

        ResponseEntity<Map> bankAfterDelete = get("/api/v1/banks/" + bankId, token);
        assertThat(bankAfterDelete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) bankAfterDelete.getBody().get("questionCount")).intValue()).isEqualTo(4);

        Integer versionRows = jdbc.queryForObject("""
            SELECT count(*) FROM qb_question_version WHERE question_id = ?::uuid
            """, Integer.class, singleId);
        assertThat(versionRows).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM sync_change WHERE owner_id = ?::uuid AND entity_type = 'QUESTION'
            """, Integer.class, ownerId)).isGreaterThanOrEqualTo(8);
    }

    private String createQuestion(String bankId, String token, Map<String, Object> body) {
        ResponseEntity<Map> response = post("/api/v1/banks/" + bankId + "/questions", body, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(response.getBody().get("version")).isEqualTo(1);
        return (String) response.getBody().get("id");
    }

    private Map<String, Object> doc(String content) {
        return Map.of("format", "MARKDOWN_LATEX", "content", content);
    }

    private Map<String, Object> option(String key, String content) {
        return Map.of("key", key, "content", content, "mediaIds", List.of());
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
            "deviceName", "Question Test",
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

    private ResponseEntity<Void> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Void.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
