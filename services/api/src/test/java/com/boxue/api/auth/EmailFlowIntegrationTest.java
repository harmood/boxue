package com.boxue.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class EmailFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_email")
        .withUsername("boxue")
        .withPassword("boxue_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("boxue.auth.require-email-verification", () -> true);
    }

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    MailService mailService;

    @Test
    void verifiesEmailAndResetsPassword() {
        String email = "verify@boxue.local";
        String password = "VerifyPass-2026!";
        String newPassword = "ResetPass-2027!";

        ResponseEntity<Map> register = post("/api/v1/auth/register", Map.of(
            "email", email,
            "username", "verifyuser",
            "password", password,
            "displayName", "Verify User",
            "acceptedTerms", true
        ));
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(statusOf(register)).isEqualTo("PENDING_VERIFICATION");

        ArgumentCaptor<String> verificationToken = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(3000)).sendVerificationEmail(eq(email), verificationToken.capture());

        ResponseEntity<Map> verifyResponse = post("/api/v1/auth/verify-email",
            Map.of("token", verificationToken.getValue()));
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) verifyResponse.getBody().get("user")).get("status")).isEqualTo("ACTIVE");

        ResponseEntity<Map> login = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", Map.of(
                "deviceId", "33333333-3333-3333-3333-333333333333",
                "deviceName", "Email Test",
                "platform", "DESKTOP",
                "appVersion", "0.1.0"
            )
        ));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> forgot = post("/api/v1/auth/forgot-password", Map.of("email", email));
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<String> resetToken = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(3000)).sendPasswordResetEmail(eq(email), resetToken.capture());

        ResponseEntity<Void> reset = rest.exchange("/api/v1/auth/reset-password", HttpMethod.POST,
            new HttpEntity<>(Map.of("token", resetToken.getValue(), "newPassword", newPassword), jsonHeaders()), Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> oldLogin = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", password,
            "device", Map.of(
                "deviceId", "33333333-3333-3333-3333-333333333333",
                "deviceName", "Email Test",
                "platform", "DESKTOP",
                "appVersion", "0.1.0"
            )
        ));
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> newLogin = post("/api/v1/auth/login", Map.of(
            "login", email,
            "password", newPassword,
            "device", Map.of(
                "deviceId", "33333333-3333-3333-3333-333333333333",
                "deviceName", "Email Test",
                "platform", "DESKTOP",
                "appVersion", "0.1.0"
            )
        ));
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
    }

    private String statusOf(ResponseEntity<Map> response) {
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        return (String) user.get("status");
    }
}
