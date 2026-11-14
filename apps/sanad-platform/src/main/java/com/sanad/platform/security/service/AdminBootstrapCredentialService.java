package com.sanad.platform.security.service;

import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.dto.AdminBootstrapCredentialRequest;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time initial credential bootstrap for an account that does not yet have
 * a credential. Tenant scoping is derived from the authenticated administrator,
 * never from request input.
 */
@Service
public class AdminBootstrapCredentialService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapCredentialService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionVersionCache sessionVersionCache;

    public AdminBootstrapCredentialService(
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
    public void bootstrap(
            UUID tenantId,
            UUID actorUserId,
            UUID targetUserId,
            AdminBootstrapCredentialRequest request
    ) {
        User user = userRepository.findByTenantIdAndId(tenantId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المستخدم غير موجود"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "حساب المستخدم غير نشط");
        }

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "تم تعيين بيانات اعتماد لهذا المستخدم مسبقًا");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewCredential()));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("admin-bootstrap");
        user.setMustChangePassword(true);
        user.incrementSessionVersion();
        userRepository.save(user);
        sessionVersionCache.invalidate(tenantId, targetUserId);

        int revokedRefreshTokens = refreshTokenRepository.revokeAllActive(tenantId, targetUserId);
        int revokedResetTokens = passwordResetTokenRepository.revokeAllActive(tenantId, targetUserId);

        log.info(
                "AUDIT: Initial credential bootstrapped actorUserId={} targetUserId={} tenantId={} revokedRefreshTokens={} revokedResetTokens={}",
                actorUserId, targetUserId, tenantId, revokedRefreshTokens, revokedResetTokens);
    }
}
