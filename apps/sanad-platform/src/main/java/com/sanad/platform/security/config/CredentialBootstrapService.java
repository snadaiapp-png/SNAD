package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Executes the explicit, one-time administrative credential enrollment. */
@Service
@Profile({"prod", "local"})
public class CredentialBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(CredentialBootstrapService.class);
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleGrantRepository userRoleGrantRepository;
    private final PasswordEncoder passwordEncoder;

    public CredentialBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleGrantRepository = userRoleGrantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Enrolls exactly one administrator credential in an existing ACTIVE tenant.
     * Re-running against an already enrolled account fails closed.
     */
    @Transactional
    public User bootstrap(
            boolean enabled,
            UUID tenantId,
            String adminEmail,
            String adminCredential,
            String displayName,
            String auditActor
    ) {
        if (!enabled) {
            log.info("Credential bootstrap is disabled; no enrollment performed.");
            return null;
        }

        if (tenantId == null) {
            throw new IllegalStateException("Bootstrap tenant id is required");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Bootstrap tenant does not exist: " + tenantId));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Bootstrap tenant must be ACTIVE: " + tenantId);
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        User adminUser = userRepository.findByTenantIdAndEmail(tenantId, normalizedEmail)
                .map(user -> prepareExistingUser(user, tenantId))
                .orElseGet(() -> new User(
                        tenantId,
                        normalizedEmail,
                        normalizeDisplayName(displayName),
                        UserStatus.ACTIVE));

        Instant enrolledAt = Instant.now();
        adminUser.setPasswordHash(passwordEncoder.encode(adminCredential));
        adminUser.setPasswordSetAt(enrolledAt);
        adminUser.setPasswordSetBy(auditActor);
        adminUser.setMustChangePassword(true);
        adminUser.setLastLoginAt(null);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);

        Role adminRole = roleRepository.findByTenantIdAndCode(tenantId, ADMIN_ROLE_CODE)
                .map(this::requireActiveAdminRole)
                .orElseGet(() -> roleRepository.save(new Role(
                        tenantId,
                        ADMIN_ROLE_CODE,
                        "Administrator",
                        "Tenant-wide administrative access")));

        UserRoleGrant grant = userRoleGrantRepository
                .findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                        tenantId, adminUser.getId(), adminRole.getId())
                .orElseGet(() -> new UserRoleGrant(
                        tenantId, adminUser.getId(), adminRole.getId(), null));
        if (grant.getStatus() != UserGrantStatus.ACTIVE) {
            grant.setStatus(UserGrantStatus.ACTIVE);
        }
        userRoleGrantRepository.save(grant);

        log.info("Credential bootstrap completed for userId={} tenantId={}; forced rotation enabled.",
                adminUser.getId(), tenantId);
        return adminUser;
    }

    private User prepareExistingUser(User user, UUID tenantId) {
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap account is already enrolled; refusing credential replacement for tenant "
                            + tenantId);
        }
        if (user.getPasswordSetAt() != null) {
            throw new IllegalStateException(
                    "Bootstrap account has prior enrollment audit state; refusing reuse for tenant "
                            + tenantId);
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "Bootstrap account must not be SUSPENDED or ARCHIVED for tenant " + tenantId);
        }
        return user;
    }

    private Role requireActiveAdminRole(Role role) {
        if (role.getStatus() != RoleStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Existing ADMIN role must be ACTIVE before credential bootstrap");
        }
        return role;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "SANAD Admin";
        }
        return displayName.trim();
    }
}
