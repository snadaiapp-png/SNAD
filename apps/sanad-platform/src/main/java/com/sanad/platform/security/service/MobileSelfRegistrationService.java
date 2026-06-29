package com.sanad.platform.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.security.dto.SelfRegistrationRequest;
import com.sanad.platform.security.dto.SelfRegistrationResponse;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.exception.SelfRegistrationRateLimitException;
import com.sanad.platform.security.notification.PasswordRecoveryNotificationCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class MobileSelfRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MobileSelfRegistrationService.class);
    private static final int MAX_PER_IP_PER_HOUR = 3;
    private static final int MAX_PER_EMAIL_PER_HOUR = 2;

    private final RegistrationIdentityFactory identityFactory;
    private final RegistrationProvisioner provisioner;
    private final PasswordRecoveryNotificationCoordinator recoveryCoordinator;
    private final Cache<String, Integer> attempts;

    public MobileSelfRegistrationService(
            RegistrationIdentityFactory identityFactory,
            RegistrationProvisioner provisioner,
            PasswordRecoveryNotificationCoordinator recoveryCoordinator
    ) {
        this.identityFactory = identityFactory;
        this.provisioner = provisioner;
        this.recoveryCoordinator = recoveryCoordinator;
        this.attempts = Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    @Transactional
    public SelfRegistrationResponse register(
            SelfRegistrationRequest request,
            String ipAddress,
            String locale
    ) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String clientIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();
        enforceRateLimit("registration:ip:" + clientIp, MAX_PER_IP_PER_HOUR);
        enforceRateLimit("registration:email:" + email, MAX_PER_EMAIL_PER_HOUR);

        String regionCode = request.getRegionCode().trim().toUpperCase(Locale.ROOT);
        String mobileNumber = identityFactory.normalizeMobileNumber(
                request.getCountryCode(), request.getMobileNumber());
        String subdomain = identityFactory.generateSubdomain(email);

        try {
            RegistrationProvisioner.ProvisionedRegistration registration = provisioner.provision(
                    email,
                    request.getDisplayName().trim(),
                    request.getOrganizationName().trim(),
                    subdomain,
                    mobileNumber,
                    regionCode);

            recoveryCoordinator.createAdministrativeResetLink(
                    registration.tenantId(),
                    registration.userId(),
                    normalizeLocale(locale),
                    clientIp);

            log.info(
                    "AUDIT: mobile self-registration completed tenantId={} userId={} region={} ip={}",
                    registration.tenantId(), registration.userId(), regionCode, clientIp);

            return new SelfRegistrationResponse(
                    "تم إنشاء الحساب. تحقق من بريدك لتعيين كلمة المرور وتفعيل الدخول.",
                    registration.subdomain(),
                    true);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationConflictException(
                    "تعذر إنشاء الحساب لأن البريد الإلكتروني مستخدم بالفعل.");
        }
    }

    private void enforceRateLimit(String key, int maximum) {
        int count = attempts.asMap().merge(key, 1, Integer::sum);
        if (count > maximum) {
            throw new SelfRegistrationRateLimitException(
                    "تم تجاوز عدد محاولات إنشاء الحساب. حاول مرة أخرى لاحقًا.");
        }
    }

    private String normalizeLocale(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "ar";
    }
}
