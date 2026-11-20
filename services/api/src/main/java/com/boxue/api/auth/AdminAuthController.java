package com.boxue.api.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthDtos.AccessTokenResponse;
import com.boxue.api.auth.AuthDtos.CsrfTokenResponse;
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

    private static final String CSRF_COOKIE = "boxue_admin_csrf";

    private final AuthService authService;
    private final JwtService jwtService;
    private final CsrfService csrfService;

    public AdminAuthController(AuthService authService, JwtService jwtService, CsrfService csrfService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.csrfService = csrfService;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest httpRequest, HttpServletResponse response) {
        String token = csrfService.issueToken();
        setCsrfCookie(response, token, httpRequest.isSecure());
        return new CsrfTokenResponse(token);
    }

    @PostMapping("/login")
    public ResponseEntity<AdminTokenResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthService.AdminLoginResult result = authService.adminLogin(request, httpRequest);
        setRefreshCookie(response, result.refreshToken(), httpRequest.isSecure());
        setCsrfCookie(response, csrfService.issueToken(), httpRequest.isSecure());
        AdminTokenResponse body = result.response();
        return ResponseEntity.ok(new AdminTokenResponse(
            body.accessToken(), body.expiresIn(), null, body.totpEnabled(), body.admin(), body.roles(), body.permissions()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(value = "refresh_token", required = false) String cookieToken,
            @CookieValue(value = CSRF_COOKIE, required = false) String csrfCookie,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfHeader,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String bodyToken = request == null ? null : request.refreshToken();
        boolean usingCookie = !StringUtils.hasText(bodyToken) && StringUtils.hasText(cookieToken);
        if (usingCookie && !csrfService.isValid(csrfHeader, csrfCookie)) {
            throw new AuthException(org.springframework.http.HttpStatus.FORBIDDEN,
                "AUTH_CSRF_INVALID", "CSRF 校验失败，请刷新页面后重试");
        }
        String rawToken = StringUtils.hasText(bodyToken) ? bodyToken : cookieToken;
        AuthService.RefreshResult result = authService.refresh(rawToken, "admin", httpRequest);
        if (StringUtils.hasText(result.refreshToken())) {
            setRefreshCookie(response, result.refreshToken(), httpRequest.isSecure());
            setCsrfCookie(response, csrfService.issueToken(), httpRequest.isSecure());
        }
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
        clearCsrfCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        authService.logoutAll(principal, httpRequest);
        clearRefreshCookie(response);
        clearCsrfCookie(response);
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

    private void setCsrfCookie(HttpServletResponse response, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, token)
            .httpOnly(false)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(jwtService.refreshTtlDays()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCsrfCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, "")
            .httpOnly(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
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
