package com.sanad.platform.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.dto.SelfRegistrationRequest;
import com.sanad.platform.security.dto.SelfRegistrationResponse;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.exception.SelfRegistrationRateLimitException;
import com.sanad.platform.security.notification.PasswordRecoveryNotificationCoordinator;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Creates a tenant, its primary organization, and the first administrator safely. */
@Service
public class SelfRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SelfRegistrationService.class);
    private static final int MAX_REGISTRATIONS_PER_IP_PER_HOUR = 3;
    private static final int MAX_REGISTRATIONS_PER_EMAIL_PER_HOUR = 2;
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
            "www", "api", "app", "admin", "auth", "login", "support", "help",
            "status", "mail", "billing", "security", "system", "root", "snad", "sanad");

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final UserRoleGrantRepository roleGrantRepository;
    private final PasswordRecoveryNotificationCoordinator recoveryCoordinator;
    private final Cache<String, Integer> registrationAttempts;

    public SelfRegistrationService(
            TenantRepository tenantRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository roleGrantRepository,
            PasswordRecoveryNotificationCoordinator recoveryCoordinator
    ) {
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.recoveryCoordinator = recoveryCoordinator;
        this.registrationAttempts = Caffeine.newBuilder()
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
        String displayName = request.getDisplayName().trim();
        String organizationName = request.getOrganizationName().trim();
        String subdomain = request.getSubdomain().trim().toLowerCase(Locale.ROOT);
        String clientIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();

        enforceRateLimit("registration:ip:" + clientIp, MAX_REGISTRATIONS_PER_IP_PER_HOUR);
        enforceRateLimit("registration:email:" + email, MAX_REGISTRATIONS_PER_EMAIL_PER_HOUR);

        if (RESERVED_SUBDOMAINS.contains(subdomain)) {
            throw new RegistrationConflictException("رمز مساحة العمل محجوز. اختر رمزًا آخر.");
        }
        if (tenantRepository.existsBySubdomain(subdomain)) {
            throw new RegistrationConflictException("رمز مساحة العمل مستخدم بالفعل. اختر رمزًا آخر.");
        }

        try {
            Tenant tenant = tenantRepository.save(new Tenant(
                    organizationName,
                    subdomain,
                    TenantStatus.ACTIVE));

            Organization organization = organizationRepository.save(new Organization(
                    tenant,
                    organizationName,
                    "Primary organization created by secure self-registration",
                    OrganizationStatus.ACTIVE));

            User administrator = new User(
                    tenant.getId(),
                    email,
                    displayName,
                    UserStatus.ACTIVE);
            administrator.setMustChangePassword(false);
            administrator = userRepository.save(administrator);

            OrganizationMembership membership = new OrganizationMembership(
                    tenant.getId(),
                    organization.getId(),
                    email,
                    displayName,
                    MembershipStatus.ACTIVE);
            membership.setUserId(administrator.getId());
            membershipRepository.save(membership);

            Role adminRole = roleRepository.save(new Role(
                    tenant.getId(),
                    "ADMIN",
                    "Administrator",
                    "Tenant-wide administrative access"));
            roleGrantRepository.save(new UserRoleGrant(
                    tenant.getId(),
                    administrator.getId(),
                    adminRole.getId(),
                    null));

            recoveryCoordinator.createAdministrativeResetLink(
                    administrator.getTenantId(),
                    administrator.getId(),
                    normalizeLocale(locale),
                    clientIp);

            log.info("AUDIT: self-registration completed tenantId={} userId={} subdomain={} ip={}",
                    tenant.getId(), administrator.getId(), subdomain, clientIp);

            return new SelfRegistrationResponse(
                    "تم إنشاء مساحة العمل. تحقق من بريدك لتعيين كلمة المرور وتفعيل الدخول.",
                    subdomain,
                    true);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationConflictException(
                    "تعذر إنشاء مساحة العمل لأن البريد أو الرمز مستخدم بالفعل.");
        }
    }

    private void enforceRateLimit(String key, int maximum) {
        int attempts = registrationAttempts.asMap().merge(key, 1, Integer::sum);
        if (attempts > maximum) {
            throw new SelfRegistrationRateLimitException(
                    "تم تجاوز عدد محاولات إنشاء الحساب. حاول مرة أخرى لاحقًا.");
        }
    }

    private String normalizeLocale(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "ar";
    }
}
