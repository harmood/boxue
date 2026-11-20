package com.boxue.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
class AdminManagementIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("boxue_admin")
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
    void adminManagesUsersRolesAndAudit() {
        String userEmail = "managed@boxue.local";
        ResponseEntity<Map> register = post("/api/v1/auth/register", Map.of(
            "email", userEmail,
            "username", "manageduser",
            "password", "ManagedPass-2026!",
            "displayName", "Managed User",
            "acceptedTerms", true
        ), null);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) ((Map<?, ?>) register.getBody().get("user")).get("id");

        ResponseEntity<Map> userLogin = post("/api/v1/auth/login", Map.of(
            "login", userEmail,
            "password", "ManagedPass-2026!",
            "device", device("55555555-5555-5555-5555-555555555555", "DESKTOP")
        ), null);
        String userToken = (String) userLogin.getBody().get("accessToken");

        new AdminBootstrapRunner(
            jdbc, passwordEncoder, "admin@boxue.local", "admin", "AdminPass-2026!", "Administrator").run();
        ResponseEntity<Map> adminLogin = post("/api/v1/admin/auth/login", Map.of(
            "email", "admin@boxue.local",
            "password", "AdminPass-2026!",
            "device", device("66666666-6666-6666-6666-666666666666", "ADMIN")
        ), null);
        assertThat(adminLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminToken = (String) adminLogin.getBody().get("accessToken");
        String adminId = (String) ((Map<?, ?>) adminLogin.getBody().get("admin")).get("id");

        ResponseEntity<Map> users = get("/api/v1/admin/users?q=managed&limit=20", adminToken);
        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) users.getBody().get("items")).isNotEmpty();

        ResponseEntity<Map> detail = get("/api/v1/admin/users/" + userId, adminToken);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("email")).isEqualTo(userEmail);

        ResponseEntity<Map> permissions = get("/api/v1/admin/permissions", adminToken);
        assertThat(permissions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) permissions.getBody().get("items")).isNotEmpty();

        ResponseEntity<Map> createdRole = post("/api/v1/admin/roles", Map.of(
            "code", "CONTENT_REVIEWER",
            "name", "内容审核员",
            "description", "审核公开题库",
            "permissionCodes", List.of("ADMIN_CONTENT_READ")
        ), adminToken);
        assertThat(createdRole.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String roleId = (String) createdRole.getBody().get("id");

        ResponseEntity<Map> assigned = put("/api/v1/admin/users/" + userId + "/roles",
            Map.of("roleCodes", List.of("USER", "CONTENT_REVIEWER")), adminToken);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> assignedRoles = ((List<?>) assigned.getBody().get("roles")).stream()
            .map(String::valueOf).toList();
        assertThat(assignedRoles).contains("CONTENT_REVIEWER");

        ResponseEntity<Map> roleInUse = delete("/api/v1/admin/roles/" + roleId, adminToken);
        assertThat(roleInUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> removed = put("/api/v1/admin/users/" + userId + "/roles",
            Map.of("roleCodes", List.of("USER")), adminToken);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> deletedRole = delete("/api/v1/admin/roles/" + roleId, adminToken);
        assertThat(deletedRole.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> forceLogout = postVoid("/api/v1/admin/users/" + userId + "/force-logout",
            Map.of(), adminToken);
        assertThat(forceLogout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> unauthorized = get("/api/v1/me", userToken);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> disabled = patch("/api/v1/admin/users/" + userId,
            Map.of("status", "DISABLED", "reason", "test"), adminToken);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody().get("status")).isEqualTo("DISABLED");

        ResponseEntity<Map> enabled = patch("/api/v1/admin/users/" + userId,
            Map.of("status", "ACTIVE"), adminToken);
        assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> reset = post("/api/v1/admin/users/" + userId + "/password-reset",
            Map.of(), adminToken);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(mailService, timeout(3000)).sendPasswordResetEmail(eq(userEmail), anyString());

        ResponseEntity<Map> lastAdminStatus = patch("/api/v1/admin/users/" + adminId,
            Map.of("status", "DISABLED"), adminToken);
        assertThat(lastAdminStatus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> lastAdminRole = put("/api/v1/admin/users/" + adminId + "/roles",
            Map.of("roleCodes", List.of("USER")), adminToken);
        assertThat(lastAdminRole.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> audit = get("/api/v1/admin/audit-logs?limit=50", adminToken);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) audit.getBody().get("items")).isNotEmpty();
    }

    private Map<String, Object> device(String id, String platform) {
        return Map.of(
            "deviceId", id,
            "deviceName", "Admin Test",
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

    private ResponseEntity<Void> postVoid(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), Void.class);
    }

    private ResponseEntity<Map> patch(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<Map> put(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<Map> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);
    }
}
