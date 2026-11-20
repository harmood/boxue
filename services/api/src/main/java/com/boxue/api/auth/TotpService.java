package com.boxue.api.auth;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.boxue.api.auth.AuthDtos.TotpSetupResponse;
import com.boxue.api.auth.AuthDtos.TotpStatusResponse;
import com.boxue.api.auth.AuthRepository.UserRow;
import com.boxue.api.auth.TotpRepository.TotpRow;

@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    private final TotpRepository repository;
    private final TotpSecretCipher cipher;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    public TotpService(TotpRepository repository, TotpSecretCipher cipher,
                       AuthRepository authRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.cipher = cipher;
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public TotpStatusResponse status(UUID userId) {
        return repository.findByUserId(userId)
            .map(row -> new TotpStatusResponse(row.enabled(), row.confirmedAt()))
            .orElse(new TotpStatusResponse(false, null));
    }

    @Transactional
    public TotpSetupResponse setup(UUID userId, String email, String password) {
        UserRow user = authRepository.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "用户不存在"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        TotpRow existing = repository.findByUserId(userId).orElse(null);
        if (existing != null && existing.enabled()) {
            throw new AuthException(HttpStatus.CONFLICT, "TOTP_ALREADY_ENABLED",
                "双因素认证已启用，请先关闭后再重新设置");
        }
        String secret = generateSecret();
        repository.upsertSetup(userId, cipher.encrypt(secret));
        return new TotpSetupResponse(secret, otpauthUri(email, secret));
    }

    @Transactional
    public TotpStatusResponse enable(UUID userId, String code) {
        TotpRow row = repository.findByUserId(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "TOTP_NOT_SETUP",
                "请先获取 TOTP 密钥"));
        if (row.enabled()) {
            throw new AuthException(HttpStatus.CONFLICT, "TOTP_ALREADY_ENABLED", "双因素认证已启用");
        }
        if (!verifyCode(cipher.decrypt(row.secretCiphertext()), code)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TOTP_CODE_INVALID", "动态验证码不正确");
        }
        repository.enable(userId);
        return status(userId);
    }

    @Transactional
    public void disable(UUID userId, String password, String code) {
        UserRow user = authRepository.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "用户不存在"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        TotpRow row = repository.findByUserId(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "TOTP_NOT_SETUP", "双因素认证未启用"));
        if (!row.enabled() || !verifyCode(cipher.decrypt(row.secretCiphertext()), code)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TOTP_CODE_INVALID", "动态验证码不正确");
        }
        repository.disable(userId);
    }

    public boolean isEnabled(UUID userId) {
        return repository.findByUserId(userId).map(TotpRow::enabled).orElse(false);
    }

    @Transactional
    public boolean verifyLogin(UUID userId, String code) {
        TotpRow row = repository.findByUserId(userId).orElse(null);
        if (row == null || !row.enabled()) {
            return true;
        }
        if (!StringUtils.hasText(code)) {
            return false;
        }
        boolean valid = verifyCode(cipher.decrypt(row.secretCiphertext()), code);
        if (valid) {
            repository.touch(userId);
        }
        return valid;
    }

    public String currentCode(String secret) {
        return codeForCounter(decodeBase32(secret), Instant.now().getEpochSecond() / PERIOD_SECONDS);
    }

    private boolean verifyCode(String secret, String code) {
        if (!StringUtils.hasText(code) || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = decodeBase32(secret);
        long counter = Instant.now().getEpochSecond() / PERIOD_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            String expected = codeForCounter(key, counter + offset);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    private String codeForCounter(byte[] key, long counter) {
        try {
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (counter & 0xFF);
                counter >>>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP generation failed", ex);
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private byte[] decodeBase32(String secret) {
        String normalized = secret.replace("=", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char ch : normalized.toCharArray()) {
            int value = BASE32.indexOf(ch);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private String otpauthUri(String email, String secret) {
        String label = URLEncoder.encode("Boxue:" + email, StandardCharsets.UTF_8)
            .replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
            + "&issuer=Boxue&algorithm=SHA1&digits=6&period=30";
    }
}
