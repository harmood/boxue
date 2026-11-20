package com.boxue.api.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.boxue.api.auth.AuthDtos.AccessTokenResponse;
import com.boxue.api.auth.AuthDtos.AdminLoginRequest;
import com.boxue.api.auth.AuthDtos.AdminMe;
import com.boxue.api.auth.AuthDtos.AdminSummary;
import com.boxue.api.auth.AuthDtos.AdminTokenResponse;
import com.boxue.api.auth.AuthDtos.AuthUser;
import com.boxue.api.auth.AuthDtos.ChangePasswordRequest;
import com.boxue.api.auth.AuthDtos.CurrentUser;
import com.boxue.api.auth.AuthDtos.DeviceListResponse;
import com.boxue.api.auth.AuthDtos.DeviceRequest;
import com.boxue.api.auth.AuthDtos.DeviceResponse;
import com.boxue.api.auth.AuthDtos.EmailRequest;
import com.boxue.api.auth.AuthDtos.MessageResponse;
import com.boxue.api.auth.AuthDtos.ResetPasswordRequest;
import com.boxue.api.auth.AuthDtos.VerifyEmailRequest;
import com.boxue.api.auth.AuthDtos.VerifyEmailResponse;
import com.boxue.api.auth.AuthDtos.LoginRequest;
import com.boxue.api.auth.AuthDtos.RegisterRequest;
import com.boxue.api.auth.AuthDtos.RegisterResponse;
import com.boxue.api.auth.AuthDtos.TokenResponse;
import com.boxue.api.auth.AuthDtos.UpdateProfileRequest;
import com.boxue.api.auth.AuthRepository.ProfileRow;
import com.boxue.api.auth.AuthRepository.SessionRow;
import com.boxue.api.auth.AuthRepository.TokenRow;
import com.boxue.api.auth.AuthRepository.UserRow;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthRepository repository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MailService mailService;
    private final LoginRateLimiter loginRateLimiter;
    private final TotpService totpService;
    private final boolean requireEmailVerification;
    private final int maxFailedLogins;
    private final long lockoutMinutes;
    private final long emailVerificationTokenMinutes;
    private final long passwordResetTokenMinutes;
    private final long refreshRotationGraceSeconds;

    public AuthService(
            AuthRepository repository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            MailService mailService,
            LoginRateLimiter loginRateLimiter,
            TotpService totpService,
            @Value("${boxue.auth.require-email-verification:false}") boolean requireEmailVerification,
            @Value("${boxue.auth.max-failed-logins:5}") int maxFailedLogins,
            @Value("${boxue.auth.lockout-minutes:15}") long lockoutMinutes,
            @Value("${boxue.auth.email-verification-token-minutes:60}") long emailVerificationTokenMinutes,
            @Value("${boxue.auth.password-reset-token-minutes:30}") long passwordResetTokenMinutes,
            @Value("${boxue.auth.refresh-rotation-grace-seconds:30}") long refreshRotationGraceSeconds) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mailService = mailService;
        this.loginRateLimiter = loginRateLimiter;
        this.totpService = totpService;
        this.requireEmailVerification = requireEmailVerification;
        this.maxFailedLogins = maxFailedLogins;
        this.lockoutMinutes = lockoutMinutes;
        this.emailVerificationTokenMinutes = emailVerificationTokenMinutes;
        this.passwordResetTokenMinutes = passwordResetTokenMinutes;
        this.refreshRotationGraceSeconds = Math.max(0, Math.min(refreshRotationGraceSeconds, 300));
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (!request.acceptedTerms()) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "AUTH_TERMS_NOT_ACCEPTED",
                "必须接受用户协议");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();
        if (repository.existsByEmailOrUsername(email, username)) {
            throw new AuthException(HttpStatus.CONFLICT, "AUTH_ACCOUNT_EXISTS",
                "邮箱或用户名已被使用");
        }

        UUID userId = UUID.randomUUID();
        String status = requireEmailVerification ? "PENDING_VERIFICATION" : "ACTIVE";
        String displayName = StringUtils.hasText(request.displayName())
            ? request.displayName().trim()
            : username;

        repository.insertUser(
            userId,
            email,
            username,
            passwordEncoder.encode(request.password()),
            status,
            !requireEmailVerification);
        repository.insertProfile(userId, displayName);
        repository.assignRole(userId, "USER");
        if (requireEmailVerification) {
            createEmailVerification(userId, email);
        }

        auditService.log(userId, "USER_REGISTERED", "USER", userId.toString(),
            java.util.Map.of("email", email, "username", username), httpRequest);

        String message = requireEmailVerification
            ? "注册成功，验证邮件已发送"
            : "注册成功，请登录";

        return new RegisterResponse(
            new AuthUser(userId, email, username, displayName, status,
                !requireEmailVerification),
            message);
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimiter.checkLogin(clientIp(httpRequest), request.login());
        UserRow user = authenticate(request.login().trim(), request.password(), null, httpRequest);
        loginRateLimiter.resetLoginAccount(request.login());
        repository.ensureDevice(user.id(), request.device());

        List<String> roles = repository.findRoles(user.id());
        List<String> permissions = repository.findPermissions(user.id());
        SessionTokens tokens = createSession(user, request.device().deviceId(), "client", roles, permissions, httpRequest);

        auditService.log(user.id(), "LOGIN_SUCCEEDED", "USER", user.id().toString(),
            java.util.Map.of("platform", request.device().platform().name()), httpRequest);

        return new LoginResult(
            new TokenResponse(
                tokens.accessToken(),
                jwtService.accessTtlSeconds(),
                tokens.refreshToken(),
                toAuthUser(user),
                roles,
                permissions),
            tokens.refreshToken(),
            request.device().platform() == DevicePlatform.WEB);
    }

    @Transactional
    public AdminLoginResult adminLogin(AdminLoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimiter.checkAdminLogin(clientIp(httpRequest), request.email());
        UserRow user = authenticate(request.email().trim(), request.password(), "ADMIN", httpRequest);
        boolean totpEnabled = totpService.isEnabled(user.id());
        if (totpEnabled && !totpService.verifyLogin(user.id(), request.totpCode())) {
            if (!StringUtils.hasText(request.totpCode())) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_TOTP_REQUIRED", "请输入动态验证码");
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_TOTP_INVALID", "动态验证码不正确");
        }
        loginRateLimiter.resetAdminAccount(request.email());
        repository.ensureDevice(user.id(), request.device());

        List<String> roles = repository.findRoles(user.id());
        List<String> permissions = repository.findPermissions(user.id());
        SessionTokens tokens = createSession(user, request.device().deviceId(), "admin", roles, permissions, httpRequest);

        auditService.log(user.id(), "ADMIN_LOGIN_SUCCEEDED", "USER", user.id().toString(),
            java.util.Map.of("platform", request.device().platform().name()), httpRequest);

        return new AdminLoginResult(
            new AdminTokenResponse(
                tokens.accessToken(),
                jwtService.accessTtlSeconds(),
                tokens.refreshToken(),
                totpEnabled,
                new AdminSummary(user.id(), user.email(), user.username(),
                    profileDisplayName(user.id(), user.username())),
                roles,
                permissions),
            tokens.refreshToken());
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String audience, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REQUIRED", "缺少刷新令牌");
        }
        SessionRow session = repository.findSessionByHashForUpdate(jwtService.hashRefreshToken(rawRefreshToken))
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID",
                "刷新令牌无效"));
        if (!session.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_EXPIRED", "刷新令牌已失效");
        }
        if (!audience.equals(session.audience())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_AUDIENCE_MISMATCH",
                "刷新令牌与当前客户端类型不匹配");
        }

        UserRow user = repository.findById(session.userId())
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID",
                "刷新令牌无效"));
        if (!"ACTIVE".equals(user.status())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "账号不可用");
        }

        List<String> roles = repository.findRoles(user.id());
        List<String> permissions = repository.findPermissions(user.id());
        if ("admin".equals(audience) && !roles.contains("ADMIN")) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ADMIN_REQUIRED", "需要管理员权限");
        }

        if (session.revokedAt() != null) {
            return replayRotatedSession(session, audience, user, roles, permissions, httpRequest);
        }

        SessionTokens tokens = createSession(user, session.deviceId(), audience, roles, permissions, httpRequest);
        repository.revokeSessionReplacedBy(session.id(), tokens.sessionId());

        auditService.log(user.id(), "TOKEN_REFRESHED", "SESSION", session.id().toString(),
            java.util.Map.of("audience", audience), httpRequest);

        return new RefreshResult(
            new AccessTokenResponse(tokens.accessToken(), jwtService.accessTtlSeconds(), tokens.refreshToken()),
            tokens.refreshToken(), false);
    }

    private RefreshResult replayRotatedSession(SessionRow session, String audience, UserRow user,
                                               List<String> roles, List<String> permissions,
                                               HttpServletRequest httpRequest) {
        if (session.replacedBySessionId() == null || refreshRotationGraceSeconds == 0
            || session.revokedAt().plusSeconds(refreshRotationGraceSeconds).isBefore(Instant.now())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_EXPIRED", "刷新令牌已失效");
        }
        SessionRow replacement = repository.findSessionById(session.replacedBySessionId())
            .filter(row -> row.revokedAt() == null)
            .filter(row -> row.expiresAt().isAfter(Instant.now()))
            .filter(row -> audience.equals(row.audience()))
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_EXPIRED",
                "刷新令牌已失效"));
        String accessToken = jwtService.issueAccessToken(
            user.id(), replacement.id(), replacement.deviceId(), audience, roles, permissions);
        auditService.log(user.id(), "TOKEN_REFRESH_REPLAYED", "SESSION", session.id().toString(),
            java.util.Map.of("audience", audience), httpRequest);
        return new RefreshResult(
            new AccessTokenResponse(accessToken, jwtService.accessTtlSeconds(), null),
            null,
            true);
    }

    @Transactional
    public void logout(AuthPrincipal principal, HttpServletRequest httpRequest) {
        if (principal == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "未认证");
        }
        repository.revokeSession(principal.sessionId());
        auditService.log(principal.userId(), "LOGOUT", "SESSION", principal.sessionId().toString(),
            java.util.Map.of(), httpRequest);
    }

    @Transactional
    public void logoutAll(AuthPrincipal principal, HttpServletRequest httpRequest) {
        if (principal == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "未认证");
        }
        repository.revokeAllSessions(principal.userId());
        auditService.log(principal.userId(), "LOGOUT_ALL", "USER", principal.userId().toString(),
            java.util.Map.of(), httpRequest);
    }

    public CurrentUser me(UUID userId) {
        UserRow user = repository.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "用户不存在"));
        ProfileRow profile = repository.findProfile(userId).orElse(null);
        return new CurrentUser(
            user.id(), user.email(), user.username(),
            profile == null ? null : profile.displayName(),
            profile == null ? null : profile.avatarMediaId(),
            profile == null ? null : profile.bio(),
            profile == null ? "zh-CN" : profile.locale(),
            profile == null ? "Asia/Shanghai" : profile.timezone(),
            user.status(),
            repository.findRoles(userId),
            repository.findPermissions(userId),
            user.emailVerifiedAt() != null);
    }

    @Transactional
    public CurrentUser updateProfile(UUID userId, UpdateProfileRequest request) {
        if (request.avatarMediaId() != null && !repository.ownsMedia(userId, request.avatarMediaId())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDIA_NOT_FOUND",
                "头像图片不存在或不属于当前用户");
        }
        repository.updateProfile(userId, request);
        return me(userId);
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        UserRow user = repository.findById(principal.userId())
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "用户不存在"));
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_INVALID",
                "当前密码不正确");
        }
        repository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        if (request.revokeOtherSessions() == null || request.revokeOtherSessions()) {
            repository.revokeOtherSessions(user.id(), principal.sessionId());
        }
        auditService.log(user.id(), "PASSWORD_CHANGED", "USER", user.id().toString(),
            java.util.Map.of(), httpRequest);
    }

    public DeviceListResponse listDevices(UUID userId, UUID currentDeviceId) {
        List<DeviceResponse> items = repository.listDevices(userId).stream()
            .map(device -> new DeviceResponse(
                device.id(), device.deviceName(), device.platform(), device.appVersion(),
                device.lastSeenAt(), device.id().equals(currentDeviceId)))
            .toList();
        return new DeviceListResponse(items);
    }

    @Transactional
    public void removeDevice(UUID userId, UUID deviceId) {
        repository.removeDevice(userId, deviceId);
    }

    public AdminMe adminMe(UUID userId) {
        UserRow user = repository.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "用户不存在"));
        List<String> roles = repository.findRoles(userId);
        if (!roles.contains("ADMIN")) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ADMIN_REQUIRED", "需要管理员权限");
        }
        return new AdminMe(user.id(), user.email(), user.username(),
            profileDisplayName(user.id(), user.username()), totpService.isEnabled(userId), roles,
            repository.findPermissions(userId));
    }

    @Transactional
    public MessageResponse resendVerification(EmailRequest request, HttpServletRequest httpRequest) {
        repository.findByLogin(request.email().trim())
            .filter(user -> "PENDING_VERIFICATION".equals(user.status()) && user.emailVerifiedAt() == null)
            .ifPresent(user -> {
                createEmailVerification(user.id(), user.email());
                auditService.log(user.id(), "EMAIL_VERIFICATION_RESENT", "USER", user.id().toString(),
                    java.util.Map.of("email", user.email()), httpRequest);
            });
        return new MessageResponse("如果该邮箱存在，验证邮件将发送");
    }

    @Transactional
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request, HttpServletRequest httpRequest) {
        TokenRow token = repository.findEmailVerificationByHash(jwtService.hashRefreshToken(request.token()))
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "AUTH_VERIFICATION_TOKEN_INVALID",
                "验证链接无效"));
        if (token.consumedAt() != null || !token.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_VERIFICATION_TOKEN_EXPIRED",
                "验证链接已失效");
        }

        repository.consumeEmailVerification(token.id());
        repository.markEmailVerified(token.userId());

        UserRow user = repository.findById(token.userId())
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "AUTH_VERIFICATION_TOKEN_INVALID",
                "验证链接无效"));
        if (!"ACTIVE".equals(user.status())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        List<String> roles = repository.findRoles(user.id());
        List<String> permissions = repository.findPermissions(user.id());
        SessionTokens session = createSession(user, null, "client", roles, permissions, httpRequest);

        auditService.log(user.id(), "EMAIL_VERIFIED", "USER", user.id().toString(),
            java.util.Map.of("email", user.email()), httpRequest);

        return new VerifyEmailResponse(
            toAuthUser(user), session.accessToken(), jwtService.accessTtlSeconds(), session.refreshToken());
    }

    @Transactional
    public MessageResponse forgotPassword(EmailRequest request, HttpServletRequest httpRequest) {
        repository.findByLogin(request.email().trim())
            .filter(user -> !"DISABLED".equals(user.status()) && !"DELETED".equals(user.status()))
            .ifPresent(user -> {
                createPasswordReset(user);
                auditService.log(user.id(), "PASSWORD_RESET_REQUESTED", "USER", user.id().toString(),
                    java.util.Map.of("email", user.email()), httpRequest);
            });
        return new MessageResponse("如果该邮箱存在，重置邮件将发送");
    }

    @Transactional
    public MessageResponse triggerPasswordReset(UUID userId, HttpServletRequest httpRequest) {
        UserRow user = repository.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        createPasswordReset(user);
        return new MessageResponse("密码重置邮件已发送");
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        TokenRow token = repository.findPasswordResetByHash(jwtService.hashRefreshToken(request.token()))
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "AUTH_RESET_TOKEN_INVALID",
                "重置链接无效"));
        if (token.consumedAt() != null || !token.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_RESET_TOKEN_EXPIRED",
                "重置链接已失效");
        }

        UserRow user = repository.findById(token.userId())
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "AUTH_RESET_TOKEN_INVALID",
                "重置链接无效"));

        repository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        repository.consumePasswordReset(token.id());
        repository.consumeAllPasswordResets(user.id());
        repository.revokeAllSessions(user.id());

        auditService.log(user.id(), "PASSWORD_RESET_COMPLETED", "USER", user.id().toString(),
            java.util.Map.of(), httpRequest);
    }

    private void createEmailVerification(UUID userId, String email) {
        String token = jwtService.newRefreshToken();
        repository.consumeAllEmailVerifications(userId);
        repository.insertEmailVerification(
            UUID.randomUUID(), userId, email, jwtService.hashRefreshToken(token),
            Instant.now().plus(emailVerificationTokenMinutes, ChronoUnit.MINUTES));
        try {
            mailService.sendVerificationEmail(email, token);
        } catch (RuntimeException ex) {
            log.warn("Failed to send verification email to {}: {}", email, ex.getMessage());
        }
    }

    private void createPasswordReset(UserRow user) {
        String token = jwtService.newRefreshToken();
        repository.consumeAllPasswordResets(user.id());
        repository.insertPasswordReset(
            UUID.randomUUID(), user.id(), jwtService.hashRefreshToken(token),
            Instant.now().plus(passwordResetTokenMinutes, ChronoUnit.MINUTES));
        try {
            mailService.sendPasswordResetEmail(user.email(), token);
        } catch (RuntimeException ex) {
            log.warn("Failed to send password reset email to {}: {}", user.email(), ex.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        return request == null ? "unknown" : request.getRemoteAddr();
    }

    private UserRow authenticate(String login, String password, String requiredRole,
                                 HttpServletRequest httpRequest) {
        UserRow user = repository.findByLogin(login).orElse(null);
        if (user == null) {
            auditService.log(null, "LOGIN_FAILED", "USER", null,
                java.util.Map.of("login", login, "reason", "USER_NOT_FOUND"), httpRequest);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "邮箱/用户名或密码错误");
        }

        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            throw new AuthException(HttpStatus.LOCKED, "AUTH_ACCOUNT_LOCKED", "账号已临时锁定，请稍后再试");
        }
        if ("PENDING_VERIFICATION".equals(user.status())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_EMAIL_NOT_VERIFIED", "请先验证邮箱");
        }
        if ("DISABLED".equals(user.status()) || "LOCKED".equals(user.status())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "账号不可用");
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int nextCount = user.failedLoginCount() + 1;
            Instant lockedUntil = nextCount >= maxFailedLogins
                ? Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES)
                : null;
            repository.recordLoginFailure(user.id(), nextCount, lockedUntil);
            auditService.log(user.id(), "LOGIN_FAILED", "USER", user.id().toString(),
                java.util.Map.of("reason", "INVALID_PASSWORD", "failedCount", nextCount), httpRequest);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "邮箱/用户名或密码错误");
        }

        if (requiredRole != null && !repository.findRoles(user.id()).contains(requiredRole)) {
            auditService.log(user.id(), "LOGIN_FORBIDDEN", "USER", user.id().toString(),
                java.util.Map.of("requiredRole", requiredRole), httpRequest);
            throw new AuthException(HttpStatus.FORBIDDEN, "AUTH_ADMIN_REQUIRED", "需要管理员权限");
        }

        repository.recordLoginSuccess(user.id());
        return user;
    }

    private SessionTokens createSession(UserRow user, UUID deviceId, String audience,
                                        List<String> roles, List<String> permissions,
                                        HttpServletRequest httpRequest) {
        String refreshToken = jwtService.newRefreshToken();
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(jwtService.refreshTtlDays(), ChronoUnit.DAYS);
        String ip = httpRequest == null ? null : httpRequest.getRemoteAddr();
        String userAgent = httpRequest == null ? null : httpRequest.getHeader("User-Agent");

        repository.createSession(sessionId, user.id(), deviceId, audience,
            jwtService.hashRefreshToken(refreshToken), expiresAt, userAgent, ip);

        String accessToken = jwtService.issueAccessToken(
            user.id(), sessionId, deviceId, audience, roles, permissions);

        return new SessionTokens(accessToken, refreshToken, sessionId);
    }

    private AuthUser toAuthUser(UserRow user) {
        return new AuthUser(
            user.id(),
            user.email(),
            user.username(),
            profileDisplayName(user.id(), user.username()),
            user.status(),
            user.emailVerifiedAt() != null);
    }

    private String profileDisplayName(UUID userId, String fallback) {
        return repository.findProfile(userId)
            .map(ProfileRow::displayName)
            .filter(StringUtils::hasText)
            .orElse(fallback);
    }

    public record SessionTokens(String accessToken, String refreshToken, UUID sessionId) {
    }

    public record LoginResult(TokenResponse response, String refreshToken, boolean webClient) {
    }

    public record AdminLoginResult(AdminTokenResponse response, String refreshToken) {
    }

    public record RefreshResult(AccessTokenResponse response, String refreshToken, boolean replayed) {
    }
}
