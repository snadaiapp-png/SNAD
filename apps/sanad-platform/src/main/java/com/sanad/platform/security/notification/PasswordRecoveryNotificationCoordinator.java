package com.sanad.platform.security.notification;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.domain.PasswordResetToken;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.PasswordResetTokenStatus;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.rls.AuthTokenTenantScopeResolver;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class PasswordRecoveryNotificationCoordinator {
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryNotificationCoordinator.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecurityNotificationService notificationService;
    private PlatformAuditWriter credentialAuditWriter;
    private final AuthTokenTenantScopeResolver authTokenTenantScopeResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordRecoveryNotificationCoordinator(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SecurityNotificationService notificationService,
            @org.springframework.lang.Nullable
            AuthTokenTenantScopeResolver authTokenTenantScopeResolver
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
        this.authTokenTenantScopeResolver = authTokenTenantScopeResolver;
    }

    /**
     * Backward-compatible constructor for legacy unit tests: no pre-auth
     * resolver (V20261028_2 contract disabled). Production Spring wiring
     * always supplies the resolver.
     */
    public PasswordRecoveryNotificationCoordinator(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SecurityNotificationService notificationService
    ) {
        this(userRepository, tokenRepository, notificationService, null);
    }

    /** Optional Phase 7 audit writer for administrative reset-link issuance. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCredentialAuditWriter(PlatformAuditWriter credentialAuditWriter) {
        this.credentialAuditWriter = credentialAuditWriter;
    }

    @Transactional
    public void deliverRequestedReset(String rawToken, String locale) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        // Bind delivery to the exact persisted reset token rather than
        // re-resolving by email. Email can be duplicated across tenants, while
        // the token carries the authoritative tenant/user tuple.
        // D6 (V20261028_2): scope the transaction from the audited pre-auth
        // hash mapping BEFORE the FORCE-RLS'd lookup (fail-closed).
        String tokenHash = hash(rawToken);
        if (authTokenTenantScopeResolver != null) {
            java.util.UUID scopedTenant = authTokenTenantScopeResolver
                    .resolveTokenTenant(AuthTokenTenantScopeResolver.KIND_PASSWORD_RESET, tokenHash)
                    .orElse(null);
            if (scopedTenant == null) {
                return;
            }
            applyTenantScope(scopedTenant);
        }
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(PasswordResetToken::isUsable)
                .orElse(null);
        if (token == null) {
            return;
        }

        User user = userRepository.findByTenantIdAndId(token.getTenantId(), token.getUserId())
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            token.setStatus(PasswordResetTokenStatus.REVOKED);
            tokenRepository.save(token);
            return;
        }

        try {
            notificationService.deliverResetLink(user, rawToken, locale, false);
        } catch (RuntimeException exception) {
            tokenRepository.revokeAllActive(user.getTenantId(), user.getId());
            log.error("Recovery notification failed; active reset values were revoked", exception);
        }
    }

    @Transactional
    public void deliverResetConfirmation(String rawToken, String locale) {
        // D6 (V20261028_2): pre-auth tenant discovery before the scoped lookup.
        String tokenHash = hash(rawToken);
        if (authTokenTenantScopeResolver != null) {
            java.util.UUID scopedTenant = authTokenTenantScopeResolver
                    .resolveTokenTenant(AuthTokenTenantScopeResolver.KIND_PASSWORD_RESET, tokenHash)
                    .orElse(null);
            if (scopedTenant == null) {
                return;
            }
            applyTenantScope(scopedTenant);
        }
        tokenRepository.findByTokenHashForUpdate(tokenHash).ifPresent(token ->
                userRepository.findByTenantIdAndId(token.getTenantId(), token.getUserId())
                        .ifPresent(user -> notificationService.deliverPasswordChanged(user, locale)));
    }

    /**
     * Applies the discovered tenant scope inside the current transaction.
     * Mirrors AuthService#applyTrustedUserScope without a hard dependency:
     * the coordinator may be used in test wirings without the RLS context bean.
     */
    private void applyTenantScope(java.util.UUID tenantId) {
        if (tenantRlsContext != null) {
            tenantRlsContext.applyForCurrentTransaction(tenantId);
        }
    }

    private TenantRlsTransactionContext tenantRlsContext;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTenantRlsContext(TenantRlsTransactionContext context) {
        this.tenantRlsContext = context;
    }

    @Transactional
    public void deliverCredentialChangeConfirmation(UUID tenantId, UUID userId, String locale) {
        userRepository.findByTenantIdAndId(tenantId, userId)
                .ifPresent(user -> notificationService.deliverPasswordChanged(user, locale));
    }

    @Transactional
    public String createAdministrativeResetLink(
            UUID tenantId,
            UUID userId,
            String locale,
            String ipAddress,
            UUID actorUserId,
            String issuedBy
    ) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new InvalidCredentialsException("المستخدم غير موجود"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        tokenRepository.revokeAllActive(tenantId, userId);
        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken(
                tenantId,
                userId,
                hash(rawToken),
                Instant.now().plus(RESET_TTL),
                ipAddress);
        tokenRepository.save(token);

        try {
            notificationService.deliverResetLink(user, rawToken, locale, true);
        } catch (RuntimeException exception) {
            token.setStatus(PasswordResetTokenStatus.REVOKED);
            tokenRepository.save(token);
            throw exception;
        }

        // Phase 7 audit: issuance fact with safe delivery metadata only — the
        // single-use link value and its secret are never audited. The issuer
        // attribution comes from the governed calling surface (administrator
        // console vs governed self-registration provisioning). Emitted only
        // after a successful delivery (no false SUCCESS).
        if (credentialAuditWriter != null) {
            credentialAuditWriter.writeSuccess(tenantId, actorUserId, tenantId,
                    "USER_RESET_LINK_ISSUED", "USER", userId.toString(),
                    "ACCOUNT_RECOVERY", null,
                    java.util.Map.of("userId", userId,
                            "deliveryChannel", "email",
                            "singleUse", true,
                            "ttlMinutes", RESET_TTL.toMinutes(),
                            "issuedBy", issuedBy),
                    null, Instant.now());
        }
        return rawToken;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
