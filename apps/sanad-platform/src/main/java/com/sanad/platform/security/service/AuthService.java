package com.sanad.platform.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.RefreshToken;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenStatus;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.AmbiguousTenantException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.exception.LoginRateLimitException;
import com.sanad.platform.security.exception.RefreshTokenReplayException;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Core tenant-scoped authentication and session service. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final Cache<String, Integer> loginFailureCache;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;

        SecurityProperties.LoginRateLimit rateLimit = securityProperties.getLoginRateLimit();
        this.loginFailureCache = Caffeine.newBuilder()
                .expireAfterWrite(rateLimit.getWindow().toSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Rate limit by email only (no tenantId in key)
        String rateLimitKey = "email:" + normalizedEmail;
        Integer failures = loginFailureCache.getIfPresent(rateLimitKey);
        int maxAttempts = securityProperties.getLoginRateLimit().getMaxAttempts();
        if (failures != null && failures >= maxAttempts) {
            log.warn("Login rate limit exceeded for email={}", normalizedEmail);
            throw new LoginRateLimitException(
                    "تم تجاوز عدد محاولات الدخول المسموح بها. حاول لاحقًا.");
        }

        // Find user by email across all tenants (or scoped if tenantId provided)
        User user;
        if (request.getTenantId() != null) {
            // Login with explicit tenantId (backward compatibility)
            user = userRepository.findByTenantIdAndEmail(request.getTenantId(), normalizedEmail)
                    .orElse(null);
        } else {
            // Email-only login: find across all tenants
            List<User> users = userRepository.findAllByEmail(normalizedEmail);
            if (users.isEmpty()) {
                user = null;
            } else if (users.size() == 1) {
                user = users.get(0);
            } else {
                // Multiple tenants — return 409 with tenant list for selection
                log.warn("Login ambiguous: email={} found in {} tenants", normalizedEmail, users.size());
                throw new AmbiguousTenantException("البريد الإلكتروني موجود في عدة مستأجرين", users);
            }
        }

        if (user == null) {
            recordLoginFailure(rateLimitKey);
            log.warn("Login failed: user not found for email={}", normalizedEmail);
            throw new InvalidCredentialsException("بيانات الدخول غير صحيحة");
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(rateLimitKey);
            log.warn("Login failed: credential mismatch for userId={}", user.getId());
            throw new InvalidCredentialsException("بيانات الدخول غير صحيحة");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login blocked: user status={} userId={}", user.getStatus(), user.getId());
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        loginFailureCache.invalidate(rateLimitKey);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    /**
     * Rotates a refresh value atomically. Security-state changes intentionally
     * commit when a domain exception is returned so replay and expiry revocation
     * cannot be rolled back with the HTTP error response.
     */
    @Transactional(noRollbackFor = {
            InvalidCredentialsException.class,
            RefreshTokenReplayException.class,
            AccountInactiveException.class
    })
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (tokenOpt.isEmpty()) {
            log.warn("Refresh failed: token not found");
            throw new InvalidCredentialsException("رمز التحديث غير صالح");
        }

        RefreshToken refreshToken = tokenOpt.get();
        if (refreshToken.getStatus() == RefreshTokenStatus.USED) {
            log.warn("Refresh replay detected for userId={} tenantId={}",
                    refreshToken.getUserId(), refreshToken.getTenantId());
            refreshTokenRepository.revokeAllActive(
                    refreshToken.getTenantId(), refreshToken.getUserId());
            throw new RefreshTokenReplayException("تم اكتشاف إعادة استخدام رمز التحديث");
        }

        if (refreshToken.getStatus() == RefreshTokenStatus.REVOKED) {
            throw new InvalidCredentialsException("رمز التحديث ملغي");
        }

        if (refreshToken.isExpired()) {
            refreshToken.setStatus(RefreshTokenStatus.REVOKED);
            refreshTokenRepository.save(refreshToken);
            throw new InvalidCredentialsException("رمز التحديث منتهي الصلاحية");
        }

        User user = userRepository.findByTenantIdAndId(
                        refreshToken.getTenantId(), refreshToken.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("المستخدم غير موجود"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllActive(
                    refreshToken.getTenantId(), refreshToken.getUserId());
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        if (user.isMustChangePassword()) {
            refreshTokenRepository.revokeAllActive(
                    refreshToken.getTenantId(), refreshToken.getUserId());
            throw new InvalidCredentialsException("يلزم تغيير بيانات الاعتماد قبل تجديد الجلسة");
        }

        refreshToken.setStatus(RefreshTokenStatus.USED);
        refreshToken.setUsedAt(Instant.now());

        String newRefreshTokenValue = generateRefreshTokenValue();
        RefreshToken newRefreshToken = new RefreshToken(
                refreshToken.getTenantId(),
                refreshToken.getUserId(),
                hashToken(newRefreshTokenValue),
                Instant.now().plus(securityProperties.getRefresh().getRefreshTokenTtl()));
        refreshTokenRepository.save(newRefreshToken);

        refreshToken.setReplacedById(newRefreshToken.getId());
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtTokenProvider.mintAccessToken(
                user.getId(), user.getTenantId(), user.getEmail(), user.isMustChangePassword());
        log.info("Refresh successful for userId={} tenantId={}", user.getId(), user.getTenantId());

        return new AuthResponse(
                accessToken,
                newRefreshTokenValue,
                jwtTokenProvider.getAccessTokenExpiry(),
                new AuthResponse.AuthUser(
                        user.getId(),
                        user.getTenantId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getStatus().name()));
    }

    @Transactional
    public void logout(UUID tenantId, UUID userId) {
        int revoked = refreshTokenRepository.revokeAllActive(tenantId, userId);
        log.info("Logout: revoked {} refresh tokens for userId={} tenantId={}",
                revoked, userId, tenantId);
    }

    /** Rotates the authenticated account credential and terminates existing refresh sessions. */
    @Transactional
    public void changeCredential(UUID tenantId, UUID userId, ChangeCredentialRequest request) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new InvalidCredentialsException("المستخدم غير موجود"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getCurrentCredential(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("بيانات الاعتماد الحالية غير صحيحة");
        }
        if (passwordEncoder.matches(request.getNewCredential(), user.getPasswordHash())) {
            throw new IllegalArgumentException("يجب أن تختلف بيانات الاعتماد الجديدة عن الحالية");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewCredential()));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("self-service");
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllActive(tenantId, userId);

        log.info("Credential rotated and refresh sessions revoked for userId={} tenantId={}",
                userId, tenantId);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.mintAccessToken(
                user.getId(), user.getTenantId(), user.getEmail(), user.isMustChangePassword());
        String refreshTokenValue = generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken(
                user.getTenantId(),
                user.getId(),
                hashToken(refreshTokenValue),
                Instant.now().plus(securityProperties.getRefresh().getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        log.info("Tokens issued for userId={} tenantId={}", user.getId(), user.getTenantId());
        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                jwtTokenProvider.getAccessTokenExpiry(),
                new AuthResponse.AuthUser(
                        user.getId(),
                        user.getTenantId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getStatus().name()));
    }

    private void recordLoginFailure(String key) {
        Integer current = loginFailureCache.getIfPresent(key);
        loginFailureCache.put(key, (current == null ? 0 : current) + 1);
    }

    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Duration getRateLimitWindow() {
        return securityProperties.getLoginRateLimit().getWindow();
    }
}
