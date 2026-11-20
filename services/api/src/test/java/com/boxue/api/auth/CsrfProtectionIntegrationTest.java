package com.boxue.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        "boxue.auth.rate-limit.login-per-ip=100",
        "boxue.auth.rate-limit.login-per-account=100",
        "boxue.auth.rate-limit.admin-per-ip=100",
        "boxue.auth.rate-limit.admin-per-account=100"
    })
@Testcontainers(disabledWithoutDocker = true)
class CsrfProtectionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_csrf")
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
    void protectsCookieRefreshWithDoubleSubmitToken() {
        String email = "csrf-user@boxue.local";
        String password = "CsrfUser-2026!";
        register(email, "csrfuser", password);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Client-Platform", "web");
        ResponseEntity<Map> login = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "login", email,
                "password", password,
                "device", device("WEB")
            ), loginHeaders), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("refreshToken")).isNull();
        Map<String, String> cookies = cookies(login);
        assertThat(cookies).containsKeys("refresh_token", "boxue_csrf");

        ResponseEntity<Map> noCsrf = refreshWithCookies(cookies, null);
        assertThat(noCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(noCsrf.getBody().get("code")).isEqualTo("AUTH_CSRF_INVALID");

        ResponseEntity<Map> wrongCsrf = refreshWithCookies(cookies, "wrong-token");
        assertThat(wrongCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongCsrf.getBody().get("code")).isEqualTo("AUTH_CSRF_INVALID");

        ResponseEntity<Map> refreshed = refreshWithCookies(cookies, cookies.get("boxue_csrf"));
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> rotated = cookies(refreshed);
        assertThat(rotated).containsKeys("refresh_token", "boxue_csrf");
        assertThat(rotated.get("boxue_csrf")).isNotEqualTo(cookies.get("boxue_csrf"));

        ResponseEntity<Map> replay = refreshWithCookies(cookies, cookies.get("boxue_csrf"));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) replay.getBody().get("accessToken")).isNotBlank();
        assertThat(cookies(replay)).doesNotContainKey("refresh_token");

        ResponseEntity<Map> next = refreshWithCookies(rotated, rotated.get("boxue_csrf"));
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> csrfEndpoint = rest.exchange("/api/v1/auth/csrf", HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(csrfEndpoint.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) csrfEndpoint.getBody().get("csrfToken")).isNotBlank();
        assertThat(cookies(csrfEndpoint)).containsKey("boxue_csrf");
    }

    @Test
    void concurrentRefreshRequestsBothSucceedWithSingleRotation() throws Exception {
        String email = "csrf-concurrent@boxue.local";
        String password = "CsrfConcurrent-2026!";
        register(email, "csrfconcurrent", password);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Client-Platform", "web");
        ResponseEntity<Map> login = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "login", email,
                "password", password,
                "device", device("WEB")
            ), loginHeaders), Map.class);
        Map<String, String> cookies = cookies(login);
        String csrf = cookies.get("boxue_csrf");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<Map>> first = pool.submit(() -> {
                start.await();
                return refreshWithCookies(cookies, csrf);
            });
            Future<ResponseEntity<Map>> second = pool.submit(() -> {
                start.await();
                return refreshWithCookies(cookies, csrf);
            });
            start.countDown();
            ResponseEntity<Map> firstResponse = first.get();
            ResponseEntity<Map> secondResponse = second.get();

            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((String) firstResponse.getBody().get("accessToken")).isNotBlank();
            assertThat((String) secondResponse.getBody().get("accessToken")).isNotBlank();
            long rotations = List.of(cookies(firstResponse), cookies(secondResponse)).stream()
                .filter(map -> map.containsKey("refresh_token"))
                .count();
            assertThat(rotations).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nativeBodyRefreshDoesNotRequireCsrf() {
        String email = "csrf-native@boxue.local";
        String password = "CsrfNative-2026!";
        register(email, "csrfnative", password);

        ResponseEntity<Map> login = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", device("DESKTOP")
        ), new HttpHeaders());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshToken = (String) login.getBody().get("refreshToken");
        assertThat(refreshToken).isNotBlank();

        ResponseEntity<Map> refreshed = post("/api/v1/auth/refresh",
            Map.of("refreshToken", refreshToken), new HttpHeaders());
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectsAdminCookieRefresh() {
        String email = "csrf-admin@boxue.local";
        String password = "CsrfAdmin-2026!";
        new AdminBootstrapRunner(
            jdbc, passwordEncoder, email, "csrfadmin", password, "CSRF Admin").run();

        ResponseEntity<Map> login = post("/api/v1/admin/auth/login", Map.of(
            "email", email,
            "password", password,
            "device", device("ADMIN")
        ), new HttpHeaders());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> cookies = cookies(login);
        assertThat(cookies).containsKeys("refresh_token", "boxue_admin_csrf");

        HttpHeaders headers = cookieHeaders(cookies);
        ResponseEntity<Map> noCsrf = rest.exchange("/api/v1/admin/auth/refresh", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headers), Map.class);
        assertThat(noCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        headers.set("X-CSRF-Token", cookies.get("boxue_admin_csrf"));
        ResponseEntity<Map> refreshed = rest.exchange("/api/v1/admin/auth/refresh", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headers), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> refreshWithCookies(Map<String, String> cookies, String csrfHeader) {
        HttpHeaders headers = cookieHeaders(cookies);
        if (csrfHeader != null) {
            headers.set("X-CSRF-Token", csrfHeader);
        }
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headers), Map.class);
    }

    private HttpHeaders cookieHeaders(Map<String, String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Client-Platform", "web");
        headers.set(HttpHeaders.COOKIE, cookies.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "; " + right)
            .orElse(""));
        return headers;
    }

    private Map<String, String> cookies(ResponseEntity<?> response) {
        Map<String, String> result = new HashMap<>();
        for (String header : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
            String pair = header.split(";", 2)[0];
            int index = pair.indexOf('=');
            if (index > 0) {
                result.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        return result;
    }

    private void register(String email, String username, String password) {
        ResponseEntity<Map> response = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", username,
            "password", password,
            "displayName", username,
            "acceptedTerms", true
        ), new HttpHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Map<String, Object> device(String platform) {
        return Map.of(
            "deviceId", java.util.UUID.randomUUID().toString(),
            "deviceName", "CSRF Test",
            "platform", platform,
            "appVersion", "0.1.0"
        );
    }

    private ResponseEntity<Map> post(String path, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
