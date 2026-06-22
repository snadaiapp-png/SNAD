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
import java.util.Optional;
import java.util.UUID;

/**
 * Executes the explicit, one-time administrative credential enrollment.
 *
 * <p>Supports two tenant-resolution modes:</p>
 * <ul>
 *   <li><b>Explicit tenant-id</b>: {@code tenantId != null} — the tenant must already
 *       exist and be ACTIVE. Use this for re-enrolling into an established tenant.</li>
 *   <li><b>Auto-create tenant</b>: {@code tenantId == null} and both {@code tenantName}
 *       and {@code tenantSubdomain} are non-blank — the service looks up the tenant by
 *       subdomain; if absent, creates a new ACTIVE tenant. Use this for first-run
 *       bootstrap on a fresh database.</li>
 * </ul>
 */
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
     * Enrolls exactly one administrator credential.
     *
     * <p>Re-running against an already-enrolled account fails closed.</p>
     *
     * @param enabled          when false, logs and returns null without writing
     * @param tenantId         explicit tenant UUID, or null to use auto-create mode
     * @param tenantName       required when {@code tenantId == null}; ignored otherwise
     * @param tenantSubdomain  required when {@code tenantId == null}; ignored otherwise
     * @param adminEmail       the admin login email (lowercased)
     * @param adminPassword    plaintext password (BCrypt-hashed before storage)
     * @param displayName      display name for the admin user
     * @param auditActor       audit identity recorded on the password-set audit columns
     * @return the enrolled admin user, or null when disabled
     */
    @Transactional
    public User bootstrap(
            boolean enabled,
            UUID tenantId,
            String tenantName,
            String tenantSubdomain,
            String adminEmail,
            String adminPassword,
            String displayName,
            String auditActor
    ) {
        if (!enabled) {
            log.info("Credential bootstrap is disabled; no enrollment performed.");
            return null;
        }

        Tenant tenant = resolveTenant(tenantId, tenantName, tenantSubdomain);
        UUID resolvedTenantId = tenant.getId();

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        User candidate = userRepository.findByTenantIdAndEmail(resolvedTenantId, normalizedEmail)
                .map(user -> prepareExistingUser(user, resolvedTenantId))
                .orElseGet(() -> new User(
                        resolvedTenantId,
                        normalizedEmail,
                        normalizeDisplayName(displayName),
                        UserStatus.ACTIVE));

        candidate.setPasswordHash(passwordEncoder.encode(adminPassword));
        candidate.setPasswordSetAt(Instant.now());
        candidate.setPasswordSetBy(auditActor);
        candidate.setMustChangePassword(true);
        candidate.setLastLoginAt(null);
        candidate.setStatus(UserStatus.ACTIVE);
        final User adminUser = userRepository.save(candidate);

        Role adminRole = roleRepository.findByTenantIdAndCode(resolvedTenantId, ADMIN_ROLE_CODE)
                .map(this::requireActiveAdminRole)
                .orElseGet(() -> roleRepository.save(new Role(
                        resolvedTenantId,
                        ADMIN_ROLE_CODE,
                        "Administrator",
                        "Tenant-wide administrative access")));

        UserRoleGrant grant = userRoleGrantRepository
                .findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                        resolvedTenantId, adminUser.getId(), adminRole.getId())
                .orElseGet(() -> new UserRoleGrant(
                        resolvedTenantId, adminUser.getId(), adminRole.getId(), null));
        if (grant.getStatus() != UserGrantStatus.ACTIVE) {
            grant.setStatus(UserGrantStatus.ACTIVE);
        }
        userRoleGrantRepository.save(grant);

        log.info("Credential bootstrap completed for userId={} tenantId={}; forced rotation enabled.",
                adminUser.getId(), resolvedTenantId);
        return adminUser;
    }

    /**
     * Resolves the bootstrap tenant.
     *
     * <p>If {@code tenantId} is non-null, looks it up and requires ACTIVE status.</p>
     * <p>If {@code tenantId} is null, looks up by {@code tenantSubdomain}; if absent,
     * creates a new ACTIVE tenant with the provided name and subdomain.</p>
     */
    private Tenant resolveTenant(UUID tenantId, String tenantName, String tenantSubdomain) {
        if (tenantId != null) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Bootstrap tenant does not exist: " + tenantId));
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Bootstrap tenant must be ACTIVE: " + tenantId);
            }
            return tenant;
        }

        if (tenantName == null || tenantName.isBlank()
                || tenantSubdomain == null || tenantSubdomain.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap requires either tenant-id or (tenant-name + tenant-subdomain)");
        }

        String normalizedSubdomain = tenantSubdomain.trim().toLowerCase(Locale.ROOT);
        Optional<Tenant> existing = tenantRepository.findBySubdomain(normalizedSubdomain);
        if (existing.isPresent()) {
            Tenant tenant = existing.get();
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Bootstrap tenant must be ACTIVE: " + tenant.getId());
            }
            log.info("Bootstrap reusing existing tenant id={} subdomain={}",
                    tenant.getId(), normalizedSubdomain);
            return tenant;
        }

        Tenant created = tenantRepository.save(new Tenant(
                tenantName.trim(),
                normalizedSubdomain,
                TenantStatus.ACTIVE));
        log.info("Bootstrap created new tenant id={} name='{}' subdomain='{}'",
                created.getId(), created.getName(), created.getSubdomain());
        return created;
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
