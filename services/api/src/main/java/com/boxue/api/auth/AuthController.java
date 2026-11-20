package com.boxue.api.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthDtos.AccessTokenResponse;
import com.boxue.api.auth.AuthDtos.EmailRequest;
import com.boxue.api.auth.AuthDtos.MessageResponse;
import com.boxue.api.auth.AuthDtos.ResetPasswordRequest;
import com.boxue.api.auth.AuthDtos.VerifyEmailRequest;
import com.boxue.api.auth.AuthDtos.VerifyEmailResponse;
import com.boxue.api.auth.AuthDtos.LoginRequest;
import com.boxue.api.auth.AuthDtos.RefreshTokenRequest;
import com.boxue.api.auth.AuthDtos.RegisterRequest;
import com.boxue.api.auth.AuthDtos.RegisterResponse;
import com.boxue.api.auth.AuthDtos.TokenResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, httpRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request, httpRequest);
        boolean web = result.webClient() || "web".equalsIgnoreCase(platform);
        if (web) {
            setRefreshCookie(response, result.refreshToken(), "/api/v1/auth", httpRequest.isSecure());
            TokenResponse body = result.response();
            return ResponseEntity.ok(new TokenResponse(
                body.accessToken(), body.expiresIn(), null, body.user(), body.roles(), body.permissions()));
        }
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(value = "refresh_token", required = false) String cookieToken,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String bodyToken = request == null ? null : request.refreshToken();
        String rawToken = StringUtils.hasText(bodyToken) ? bodyToken : cookieToken;
        AuthService.RefreshResult result = authService.refresh(rawToken, "client", httpRequest);
        boolean web = "web".equalsIgnoreCase(platform) || (!StringUtils.hasText(bodyToken) && StringUtils.hasText(cookieToken));
        if (web) {
            setRefreshCookie(response, result.refreshToken(), "/api/v1/auth", httpRequest.isSecure());
            AccessTokenResponse body = result.response();
            return ResponseEntity.ok(new AccessTokenResponse(body.accessToken(), body.expiresIn(), null));
        }
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthPrincipal principal,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse response) {
        authService.logout(principal, httpRequest);
        clearRefreshCookie(response, "/api/v1/auth");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthPrincipal principal,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse response) {
        authService.logoutAll(principal, httpRequest);
        clearRefreshCookie(response, "/api/v1/auth");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        VerifyEmailResponse result = authService.verifyEmail(request, httpRequest);
        if ("web".equalsIgnoreCase(platform)) {
            setRefreshCookie(response, result.refreshToken(), "/api/v1/auth", httpRequest.isSecure());
            return ResponseEntity.ok(new VerifyEmailResponse(
                result.user(), result.accessToken(), result.expiresIn(), null));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(authService.resendVerification(request, httpRequest));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(authService.forgotPassword(request, httpRequest));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.resetPassword(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String token, String path, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(Duration.ofDays(jwtService.refreshTtlDays()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response, String path) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .sameSite("Lax")
            .path(path)
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
