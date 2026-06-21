package com.sanad.platform.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.RefreshToken;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenStatus;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.exception.AccountInactiveException;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core authentication service.
 *
 * <p>Handles login, refresh token rotation, logout, and brute-force
 * protection. All operations are tenant-scoped via the {@code tenantId}
 * in the login request.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    /** In-memory cache for login rate limiting. Key: "tenantId:email", value: failure count. */
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

        SecurityProperties.LoginRateLimit rl = securityProperties.getLoginRateLimit();
        this.loginFailureCache = Caffeine.newBuilder()
                .expireAfterWrite(rl.getWindow().toSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * Authenticate a user and issue access + refresh tokens.
     *
     * @param request login credentials (tenantId, email, password)
     * @return auth response with tokens and user identity
     * @throws InvalidCredentialsException if email not found or password mismatch
     * @throws AccountInactiveException    if user status is not ACTIVE
     * @throws LoginRateLimitException     if too many failed attempts
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String rateLimitKey = request.getTenantId() + ":" + request.getEmail().trim().toLowerCase();

        // Check rate limit
        Integer failures = loginFailureCache.getIfPresent(rateLimitKey);
        int maxAttempts = securityProperties.getLoginRateLimit().getMaxAttempts();
        if (failures != null && failures >= maxAttempts) {
            log.warn("Login rate limit exceeded for tenantId={} email={}",
                    request.getTenantId(), request.getEmail());
            throw new LoginRateLimitException("تم تجاوز عدد محاولات الدخول المسموح بها. حاول لاحقًا.");
        }

        // Find user
        Optional<User> userOpt = userRepository.findByTenantIdAndEmail(
                request.getTenantId(),
                request.getEmail().trim().toLowerCase()
        );

        if (userOpt.isEmpty()) {
            recordLoginFailure(rateLimitKey);
            log.warn("Login failed: user not found for tenantId={} email={}",
                    request.getTenantId(), request.getEmail());
            throw new InvalidCredentialsException("بيانات الدخول غير صحيحة");
        }

        User user = userOpt.get();

        // Check password
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(rateLimitKey);
            log.warn("Login failed: password mismatch for userId={}", user.getId());
            throw new InvalidCredentialsException("بيانات الدخول غير صحيحة");
        }

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login blocked: user status is {} for userId={}", user.getStatus(), user.getId());
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        // Success — clear failure count
        loginFailureCache.invalidate(rateLimitKey);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Issue tokens
        String accessToken = jwtTokenProvider.mintAccessToken(user.getId(), user.getTenantId(), user.getEmail());
        String refreshTokenValue = generateRefreshTokenValue();
        String refreshTokenHash = hashToken(refreshTokenValue);

        RefreshToken refreshToken = new RefreshToken(
                user.getTenantId(),
                user.getId(),
                refreshTokenHash,
                Instant.now().plus(securityProperties.getRefresh().getRefreshTokenTtl())
        );
        refreshTokenRepository.save(refreshToken);

        log.info("Login successful for userId={} tenantId={}", user.getId(), user.getTenantId());

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                jwtTokenProvider.getAccessTokenExpiry(),
                new AuthResponse.AuthUser(
                        user.getId(),
                        user.getTenantId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getStatus().name()
                )
        );
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * <p>The old refresh token is marked USED and a new one is issued.
     * If a USED token is presented again, all refresh tokens for that
     * user are revoked (replay protection).</p>
     *
     * @param request contains the refresh token
     * @return new auth response with fresh tokens
     * @throws InvalidCredentialsException    if token not found or expired
     * @throws RefreshTokenReplayException    if a USED token is presented (replay attack)
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            log.warn("Refresh failed: token not found");
            throw new InvalidCredentialsException("رمز التحديث غير صالح");
        }

        RefreshToken refreshToken = tokenOpt.get();

        // Replay detection: USED token presented again
        if (refreshToken.getStatus() == RefreshTokenStatus.USED) {
            log.warn("Refresh token replay detected for userId={} tenantId={}",
                    refreshToken.getUserId(), refreshToken.getTenantId());
            // Revoke ALL tokens for this user (family invalidation)
            refreshTokenRepository.revokeAllActive(refreshToken.getTenantId(), refreshToken.getUserId());
            throw new RefreshTokenReplayException("تم اكتشاف إعادة استخدام رمز التحديث");
        }

        if (refreshToken.getStatus() == RefreshTokenStatus.REVOKED) {
            log.warn("Refresh failed: token revoked for userId={}", refreshToken.getUserId());
            throw new InvalidCredentialsException("رمز التحديث ملغي");
        }

        if (refreshToken.isExpired()) {
            log.warn("Refresh failed: token expired for userId={}", refreshToken.getUserId());
            refreshToken.setStatus(RefreshTokenStatus.REVOKED);
            refreshTokenRepository.save(refreshToken);
            throw new InvalidCredentialsException("رمز التحديث منتهي الصلاحية");
        }

        // Mark old token as USED
        refreshToken.setStatus(RefreshTokenStatus.USED);
        refreshToken.setUsedAt(Instant.now());

        // Issue new refresh token
        String newRefreshTokenValue = generateRefreshTokenValue();
        String newRefreshTokenHash = hashToken(newRefreshTokenValue);

        RefreshToken newRefreshToken = new RefreshToken(
                refreshToken.getTenantId(),
                refreshToken.getUserId(),
                newRefreshTokenHash,
                Instant.now().plus(securityProperties.getRefresh().getRefreshTokenTtl())
        );
        refreshTokenRepository.save(newRefreshToken);

        // Link old → new
        refreshToken.setReplacedById(newRefreshToken.getId());
        refreshTokenRepository.save(refreshToken);

        // Load user for the new access token
        Optional<User> userOpt = userRepository.findByTenantIdAndId(
                refreshToken.getTenantId(),
                refreshToken.getUserId()
        );
        if (userOpt.isEmpty()) {
            log.error("Refresh: user not found for userId={} tenantId={}",
                    refreshToken.getUserId(), refreshToken.getTenantId());
            throw new InvalidCredentialsException("المستخدم غير موجود");
        }
        User user = userOpt.get();

        String accessToken = jwtTokenProvider.mintAccessToken(user.getId(), user.getTenantId(), user.getEmail());

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
                        user.getStatus().name()
                )
        );
    }

    /**
     * Logout: revoke all active refresh tokens for the user.
     *
     * <p>The access JWT is stateless and cannot be revoked. It will
     * expire naturally (15 minutes). The frontend should discard it
     * immediately on logout.</p>
     *
     * @param tenantId the authenticated user's tenant ID
     * @param userId   the authenticated user's ID
     */
    @Transactional
    public void logout(UUID tenantId, UUID userId) {
        int revoked = refreshTokenRepository.revokeAllActive(tenantId, userId);
        log.info("Logout: revoked {} refresh tokens for userId={} tenantId={}",
                revoked, userId, tenantId);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private void recordLoginFailure(String key) {
        Integer current = loginFailureCache.getIfPresent(key);
        loginFailureCache.put(key, (current == null ? 0 : current) + 1);
    }

    /**
     * Generate a cryptographically secure opaque refresh token (256 bits, URL-safe Base64).
     */
    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash a refresh token with SHA-256. The raw token is never persisted;
     * only this hash is stored.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Expose the rate-limit window for test/inspection. */
    public Duration getRateLimitWindow() {
        return securityProperties.getLoginRateLimit().getWindow();
    }
}
