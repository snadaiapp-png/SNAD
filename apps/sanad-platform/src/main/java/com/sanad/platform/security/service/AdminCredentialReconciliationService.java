package com.sanad.platform.security.service;

import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Narrow administrative recovery service for reconciling an already-existing
 * credential through application contracts. It never bypasses tenant scope and
 * invalidates all outstanding session/recovery material after rotation.
 */
@Service
public class AdminCredentialReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentialReconciliationService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionVersionCache sessionVersionCache;

    public AdminCredentialReconciliationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            SessionVersionCache sessionVersionCache
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionVersionCache = sessionVersionCache;
    }

    @Transactional
    public void reconcileCredential(
            UUID tenantId,
            UUID userId,
            String governedCredential,
            UUID actorUserId
    ) {
        if (governedCredential == null
                || governedCredential.isBlank()
                || governedCredential.length() < 8
                || governedCredential.length() > 256) {
            throw new IllegalArgumentException("Governed credential must be between 8 and 256 characters");
        }

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new InvalidCredentialsException("المستخدم غير موجود"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountInactiveException("حساب المستخدم غير نشط");
        }

        user.setPasswordHash(passwordEncoder.encode(governedCredential));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy(actorUserId == null
                ? "admin-reconcile"
                : "admin-reconcile:" + actorUserId);
        user.setMustChangePassword(false);
        user.incrementSessionVersion();
        userRepository.save(user);

        sessionVersionCache.invalidate(tenantId, userId);
        int revokedRefreshTokens = refreshTokenRepository.revokeAllActive(tenantId, userId);
        int revokedResetTokens = passwordResetTokenRepository.revokeAllActive(tenantId, userId);

        log.info(
                "AUDIT: Governed credential reconciliation completed actorUserId={} targetUserId={} tenantId={} revokedRefreshTokens={} revokedResetTokens={}",
                actorUserId,
                userId,
                tenantId,
                revokedRefreshTokens,
                revokedResetTokens);
    }
}
