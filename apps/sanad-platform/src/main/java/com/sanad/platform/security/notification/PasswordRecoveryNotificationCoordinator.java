package com.sanad.platform.security.notification;

import com.sanad.platform.security.domain.PasswordResetToken;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.PasswordResetTokenStatus;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
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
import java.util.List;
import java.util.UUID;

@Service
public class PasswordRecoveryNotificationCoordinator {
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryNotificationCoordinator.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecurityNotificationService notificationService;

    public PasswordRecoveryNotificationCoordinator(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            SecurityNotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void deliverRequestedReset(String email, String rawToken, String locale) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        List<User> users = userRepository.findAllByEmail(email.trim().toLowerCase());
        if (users.isEmpty()) {
            return;
        }
        User user = users.stream()
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .findFirst()
                .orElse(users.get(0));
        try {
            notificationService.deliverResetLink(user, rawToken, locale, false);
        } catch (RuntimeException exception) {
            tokenRepository.revokeAllActive(user.getTenantId(), user.getId());
            log.error("Recovery notification failed; active reset values were revoked", exception);
        }
    }

    @Transactional
    public void deliverResetConfirmation(String rawToken, String locale) {
        tokenRepository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token ->
                userRepository.findByTenantIdAndId(token.getTenantId(), token.getUserId())
                        .ifPresent(user -> notificationService.deliverPasswordChanged(user, locale)));
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
            String ipAddress
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
