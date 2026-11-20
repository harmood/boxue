package com.boxue.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.boxue.api.auth.MailService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class MediaIntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_media")
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

    @MockitoBean
    StorageService storageService;

    @Test
    void uploadsDeduplicatesServesAndGuardsMedia() {
        when(storageService.get(anyString())).thenReturn(PNG);

        String email = "media-owner@boxue.local";
        register(email, "mediaowner", "MediaOwner-2026!");
        String token = login(email, "MediaOwner-2026!", "77777777-7777-7777-7777-777777777777");

        ResponseEntity<Map> uploaded = upload(token, PNG, "pixel.png", "image/png");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uploaded.getBody().get("mimeType")).isEqualTo("image/png");
        assertThat(((Number) uploaded.getBody().get("width")).intValue()).isEqualTo(1);
        assertThat(((Number) uploaded.getBody().get("height")).intValue()).isEqualTo(1);
        assertThat(uploaded.getBody().get("deduped")).isEqualTo(false);
        assertThat((String) uploaded.getBody().get("sha256")).hasSize(64);
        String mediaId = (String) uploaded.getBody().get("id");

        ResponseEntity<Map> duplicate = upload(token, PNG, "same.png", "image/png");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getBody().get("id")).isEqualTo(mediaId);
        assertThat(duplicate.getBody().get("deduped")).isEqualTo(true);

        ResponseEntity<byte[]> content = rest.exchange("/api/v1/media/" + mediaId + "/content",
            HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(content.getBody()).isEqualTo(PNG);

        String otherEmail = "media-other@boxue.local";
        register(otherEmail, "mediaother", "MediaOther-2026!");
        String otherToken = login(otherEmail, "MediaOther-2026!", "88888888-8888-8888-8888-888888888888");
        ResponseEntity<Map> hidden = get("/api/v1/media/" + mediaId, otherToken);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> otherUpload = upload(otherToken, PNG, "pixel.png", "image/png");
        assertThat(otherUpload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(otherUpload.getBody().get("id")).isNotEqualTo(mediaId);

        ResponseEntity<Map> bank = post("/api/v1/banks", Map.of("name", "媒体题库"), token);
        String bankId = (String) bank.getBody().get("id");
        String questionId = createQuestion(token, bankId, mediaId);

        ResponseEntity<Map> question = get("/api/v1/questions/" + questionId, token);
        List<?> questionMediaIds = (List<?>) question.getBody().get("mediaIds");
        assertThat(questionMediaIds).hasSize(1);
        assertThat(questionMediaIds.get(0)).isEqualTo(mediaId);

        ResponseEntity<Map> inUse = delete("/api/v1/media/" + mediaId, token);
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inUse.getBody().get("code")).isEqualTo("MEDIA_IN_USE");

        ResponseEntity<Map> updated = patch("/api/v1/questions/" + questionId, Map.of(
            "mediaIds", List.of(),
            "version", 1), token);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) updated.getBody().get("mediaIds")).isEmpty();

        ResponseEntity<Map> deleted = delete("/api/v1/media/" + mediaId, token);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> gone = get("/api/v1/media/" + mediaId, token);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> textUpload = upload(token, "hello".getBytes(), "note.txt", "text/plain");
        assertThat(textUpload.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(textUpload.getBody().get("code")).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
    }

    private String createQuestion(String token, String bankId, String mediaId) {
        ResponseEntity<Map> response = post("/api/v1/banks/" + bankId + "/questions", Map.of(
            "type", "SINGLE_CHOICE",
            "stem", Map.of("format", "MARKDOWN_LATEX", "content", "看图作答 ![图](media://" + mediaId + ")"),
            "difficulty", 2,
            "mediaIds", List.of(mediaId),
            "payload", Map.of(
                "options", List.of(
                    Map.of("key", "A", "content", "正确", "mediaIds", List.of()),
                    Map.of("key", "B", "content", "错误", "mediaIds", List.of())),
                "answer", Map.of("correctOptionKeys", List.of("A")))
        ), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private ResponseEntity<Map> upload(String token, byte[] bytes, String filename, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/media", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
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
            "device", Map.of(
                "deviceId", deviceId,
                "deviceName", "Media Test",
                "platform", "WEB",
                "appVersion", "0.1.0")
        ), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> patch(String path, Object body, String token) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
