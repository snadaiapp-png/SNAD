package com.sanad.platform.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetToken;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.PasswordResetTokenStatus;
import com.sanad.platform.security.domain.RefreshToken;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenStatus;
import com.sanad.platform.security.dto.AdminResetPasswordRequest;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.ForgotPasswordRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.dto.ResetPasswordRequest;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.AmbiguousTenantException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.exception.InvalidResetTokenException;
import com.sanad.platform.security.exception.LoginRateLimitException;
import com.sanad.platform.security.exception.PasswordResetRateLimitException;
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
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_RESET_REQUESTS_PER_HOUR = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final Cache<String, Integer> loginFailureCache;
    private final Cache<String, Integer> resetRequestCache;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;

        SecurityProperties.LoginRateLimit rateLimit = securityProperties.getLoginRateLimit();
        this.loginFailureCache = Caffeine.newBuilder()
                .expireAfterWrite(rateLimit.getWindow().toSeconds(), TimeUnit.SECONDS)
                .build();

        this.resetRequestCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
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
                user.getId(), user.getTenantId(), user.getEmail(), user.isMustChangePassword(), user.getSessionVersion());
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

        // Increment session version to invalidate all outstanding access tokens
        userRepository.findByTenantIdAndId(tenantId, userId).ifPresent(user -> {
            user.incrementSessionVersion();
            userRepository.save(user);
        });

        log.info("Logout: revoked {} refresh tokens and incremented session version for userId={} tenantId={}",
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
        user.incrementSessionVersion();
        userRepository.save(user);
        refreshTokenRepository.revokeAllActive(tenantId, userId);

        log.info("Credential rotated, session version incremented, and refresh sessions revoked for userId={} tenantId={}",
                userId, tenantId);
    }

    // =========================================================================
    // Password Recovery (AUTH-RECOVERY-001)
    // =========================================================================

    /**
     * Initiates a password reset flow for the given email.
     * Always returns success to prevent account enumeration.
     * If the email exists, a reset token is generated and a reset link is logged.
     * In production, this would trigger an email delivery service.
     *
     * @return the raw reset token (only for logging/dev; in production, sent via email)
     */
    @Transactional
    public String initiatePasswordReset(ForgotPasswordRequest request, String ipAddress) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Rate limit password reset requests by email (prevent abuse)
        String rateLimitKey = "reset:" + normalizedEmail;
        Integer count = resetRequestCache.getIfPresent(rateLimitKey);
        if (count != null && count >= MAX_RESET_REQUESTS_PER_HOUR) {
            log.warn("Password reset rate limit exceeded for email={}", normalizedEmail);
            throw new PasswordResetRateLimitException(
                    "تم تجاوز عدد طلبات إعادة تعيين كلمة المرور. حاول لاحقًا.");
        }
        resetRequestCache.put(rateLimitKey, (count == null ? 0 : count) + 1);

        // Find user by email — if not found, silently return (no account enumeration)
        List<User> users = userRepository.findAllByEmail(normalizedEmail);
        if (users.isEmpty()) {
            log.info("Password reset requested for non-existent email={}", normalizedEmail);
            return null;
        }

        // If multiple tenants, use the first active user (or the first one)
        User user = users.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .findFirst()
                .orElse(users.get(0));

        // Revoke any existing active reset tokens for this user
        passwordResetTokenRepository.revokeAllActive(user.getTenantId(), user.getId());

        // Generate a secure random token
        String rawTokenValue = generateSecureToken();
        String tokenHash = hashToken(rawTokenValue);

        PasswordResetToken resetToken = new PasswordResetToken(
                user.getTenantId(),
                user.getId(),
                tokenHash,
                Instant.now().plus(RESET_TOKEN_TTL),
                ipAddress);
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset token created for userId={} tenantId={} email={} (expires in {}min)",
                user.getId(), user.getTenantId(), normalizedEmail, RESET_TOKEN_TTL.toMinutes());

        // Audit log
        log.info("AUDIT: Password reset initiated for userId={} tenantId={} email={} ip={}",
                user.getId(), user.getTenantId(), normalizedEmail, ipAddress);

        // Return the raw token value so the controller can include it in the response
        // (In production, this would be sent via email instead)
        return rawTokenValue;
    }

    /**
     * Completes a password reset using a valid reset token.
     * Invalidates the token after use (one-time), revokes all sessions,
     * and updates the password.
     */
    @Transactional
    public void completePasswordReset(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.getToken());

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (tokenOpt.isEmpty()) {
            log.warn("Password reset failed: token not found");
            throw new InvalidResetTokenException("رمز إعادة التعيين غير صالح أو منتهي الصلاحية");
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (!resetToken.isUsable()) {
            // Mark as expired if it's past TTL
            if (resetToken.getStatus() == PasswordResetTokenStatus.ACTIVE && resetToken.isExpired()) {
                resetToken.setStatus(PasswordResetTokenStatus.EXPIRED);
                passwordResetTokenRepository.save(resetToken);
            }
            log.warn("Password reset failed: token status={} expired={}",
                    resetToken.getStatus(), resetToken.isExpired());
            throw new InvalidResetTokenException("رمز إعادة التعيين غير صالح أو منتهي الصلاحية");
        }

        // Load user
        User user = userRepository.findByTenantIdAndId(resetToken.getTenantId(), resetToken.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException("المستخدم غير موجود"));

        // Update password and invalidate all sessions
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("password-reset");
        user.setMustChangePassword(false);
        user.incrementSessionVersion();
        userRepository.save(user);

        // Mark token as used
        resetToken.setStatus(PasswordResetTokenStatus.USED);
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke all other active reset tokens for this user
        passwordResetTokenRepository.revokeAllActive(user.getTenantId(), user.getId());

        // Revoke all refresh tokens (force re-login on all devices)
        refreshTokenRepository.revokeAllActive(user.getTenantId(), user.getId());

        log.info("AUDIT: Password reset completed for userId={} tenantId={} email={}",
                user.getId(), user.getTenantId(), user.getEmail());
    }

    // =========================================================================
    // Administrative Password Reset (AUTH-ACCOUNT-001)
    // =========================================================================

    /**
     * Administratively resets a user's password.
     * This is used for bootstrap credential provisioning and account recovery
     * by an administrator. Sets mustChangePassword=true by default.
     *
     * All existing sessions and refresh tokens are revoked.
     * An audit event is recorded.
     */
    @Transactional
    public void adminResetPassword(UUID tenantId, UUID userId, AdminResetPasswordRequest request) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new InvalidCredentialsException("المستخدم غير موجود"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        // Update password and invalidate all sessions
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("admin-reset");
        user.setMustChangePassword(request.isForceChange());
        user.incrementSessionVersion();
        userRepository.save(user);

        // Revoke all refresh tokens (force re-login on all devices)
        int revokedTokens = refreshTokenRepository.revokeAllActive(tenantId, userId);

        // Revoke all active password reset tokens
        int revokedResets = passwordResetTokenRepository.revokeAllActive(tenantId, userId);

        log.info("AUDIT: Admin password reset for userId={} tenantId={} forceChange={} revokedRefreshTokens={} revokedResetTokens={}",
                userId, tenantId, request.isForceChange(), revokedTokens, revokedResets);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.mintAccessToken(
                user.getId(), user.getTenantId(), user.getEmail(), user.isMustChangePassword(), user.getSessionVersion());
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
        return generateSecureToken();
    }

    private String generateSecureToken() {
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
