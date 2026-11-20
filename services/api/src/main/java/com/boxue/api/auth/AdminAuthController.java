package com.boxue.api.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthDtos.AccessTokenResponse;
import com.boxue.api.auth.AuthDtos.AdminLoginRequest;
import com.boxue.api.auth.AuthDtos.AdminMe;
import com.boxue.api.auth.AuthDtos.AdminTokenResponse;
import com.boxue.api.auth.AuthDtos.RefreshTokenRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AdminAuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminTokenResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthService.AdminLoginResult result = authService.adminLogin(request, httpRequest);
        setRefreshCookie(response, result.refreshToken(), httpRequest.isSecure());
        AdminTokenResponse body = result.response();
        return ResponseEntity.ok(new AdminTokenResponse(
            body.accessToken(), body.expiresIn(), null, body.admin(), body.roles(), body.permissions()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @org.springframework.web.bind.annotation.CookieValue(value = "refresh_token", required = false) String cookieToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String bodyToken = request == null ? null : request.refreshToken();
        String rawToken = StringUtils.hasText(bodyToken) ? bodyToken : cookieToken;
        AuthService.RefreshResult result = authService.refresh(rawToken, "admin", httpRequest);
        setRefreshCookie(response, result.refreshToken(), httpRequest.isSecure());
        AccessTokenResponse body = result.response();
        return ResponseEntity.ok(new AccessTokenResponse(body.accessToken(), body.expiresIn(), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        authService.logout(principal, httpRequest);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        authService.logoutAll(principal, httpRequest);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public AdminMe me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.adminMe(principal.userId());
    }

    private void setRefreshCookie(HttpServletResponse response, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/v1/admin/auth")
            .maxAge(Duration.ofDays(jwtService.refreshTtlDays()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .sameSite("Lax")
            .path("/api/v1/admin/auth")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
