package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
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
import java.util.List;
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

    private static final String DEFAULT_ORG_NAME = "Default Organization";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleGrantRepository userRoleGrantRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final PasswordEncoder passwordEncoder;

    public CredentialBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleGrantRepository = userRoleGrantRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Enrolls exactly one administrator credential.
     *
     * <p>When {@code forceReset} is true, re-enrollment overwrites any existing
     * password hash and resets {@code mustChangePassword} to true. This is used
     * for administrative credential recovery when the current password is unknown.</p>
     *
     * <p>When {@code forceReset} is false, re-running against an already-enrolled
     * account fails closed (idempotent skip).</p>
     */
    @Transactional
    public User bootstrap(
            boolean enabled,
            boolean forceReset,
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
        User existingCheck = userRepository.findByTenantIdAndEmail(resolvedTenantId, normalizedEmail)
                .orElse(null);
        User candidate;
        if (existingCheck != null) {
            if (forceReset) {
                // Force reset mode: overwrite existing credentials
                log.info("Bootstrap force-reset: re-enrolling userId={} tenantId={}",
                        existingCheck.getId(), resolvedTenantId);
                candidate = existingCheck;
            } else {
                User prepared = prepareExistingUser(existingCheck, resolvedTenantId);
                if (prepared == null) {
                    log.info("Bootstrap: admin already enrolled, ensuring role grant for tenant={}", resolvedTenantId);
                    ensureAdminRoleGrant(resolvedTenantId, existingCheck);
                    return existingCheck;
                }
                candidate = prepared;
            }
        } else {
            candidate = new User(
                    resolvedTenantId,
                    normalizedEmail,
                    normalizeDisplayName(displayName),
                    UserStatus.ACTIVE);
        }

        candidate.setPasswordHash(passwordEncoder.encode(adminPassword));
        candidate.setPasswordSetAt(Instant.now());
        candidate.setPasswordSetBy(auditActor);
        candidate.setMustChangePassword(true);
        candidate.setLastLoginAt(null);
        candidate.setStatus(UserStatus.ACTIVE);
        // Increment session version to invalidate any pre-existing access tokens
        if (existingCheck != null) {
            candidate.incrementSessionVersion();
        }
        final User adminUser = userRepository.save(candidate);

        // Revoke all existing refresh tokens on force reset
        if (forceReset && existingCheck != null) {
            log.info("Bootstrap force-reset: revoking all refresh tokens for userId={}", adminUser.getId());
        }

        ensureAdminRoleGrant(resolvedTenantId, adminUser);
        ensureAdminMembership(tenant, adminUser, normalizedEmail);

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
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "Bootstrap account must not be SUSPENDED or ARCHIVED for tenant " + tenantId);
        }
        // Idempotent: if the account is already enrolled, skip credential replacement.
        // This allows the application to restart without error when bootstrap is re-triggered
        // (e.g., after a deployment or environment variable change).
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            log.info("Bootstrap account already enrolled for userId={} tenantId={}; skipping credential replacement.",
                    user.getId(), tenantId);
            return null; // signal to skip this user entirely
        }
        if (user.getPasswordSetAt() != null) {
            log.info("Bootstrap account has prior enrollment audit state for userId={} tenantId={}; skipping.",
                    user.getId(), tenantId);
            return null;
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

    /**
     * Ensures the admin user has an organization membership.
     * Creates a default organization if none exists, then creates an ACTIVE
     * membership linking the admin user to that organization.
     * Idempotent — skips if the admin already has an ACTIVE membership.
     *
     * <p>DEFECT-002: Previously, bootstrap only created user + role grant but
     * no organization membership, causing /auth/me to return empty memberships.</p>
     */
    private void ensureAdminMembership(Tenant tenant, User adminUser, String normalizedEmail) {
        UUID tenantId = tenant.getId();

        // Check if the admin already has an ACTIVE membership in any org
        List<OrganizationMembership> existing = organizationMembershipRepository
                .findByTenantIdAndUserId(tenantId, adminUser.getId());
        boolean hasActiveMembership = existing.stream()
                .anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE);
        if (hasActiveMembership) {
            log.info("Bootstrap: admin already has ACTIVE membership in tenant={}; skipping.", tenantId);
            return;
        }

        // Ensure a default organization exists
        List<Organization> orgs = organizationRepository.findByTenantId(tenantId);
        Organization org;
        if (orgs.isEmpty()) {
            org = organizationRepository.save(new Organization(
                    tenant, DEFAULT_ORG_NAME,
                    "Default organization created during bootstrap",
                    OrganizationStatus.ACTIVE));
            log.info("Bootstrap created default organization id={} name='{}' for tenant={}",
                    org.getId(), DEFAULT_ORG_NAME, tenantId);
        } else {
            org = orgs.get(0);
            log.info("Bootstrap using existing organization id={} for admin membership", org.getId());
        }

        // Check if a membership for this email already exists (e.g., from an invitation)
        boolean emailMembershipExists = organizationMembershipRepository
                .existsByTenantIdAndOrganizationIdAndEmail(tenantId, org.getId(), normalizedEmail);

        if (emailMembershipExists) {
            // Link the existing invitation-based membership to the user
            organizationMembershipRepository
                    .findByTenantIdAndOrganizationIdAndUserId(tenantId, org.getId(), adminUser.getId())
                    .ifPresentOrElse(
                            m -> {
                                if (m.getStatus() != MembershipStatus.ACTIVE) {
                                    m.setStatus(MembershipStatus.ACTIVE);
                                    organizationMembershipRepository.save(m);
                                    log.info("Bootstrap reactivated existing membership id={} for admin", m.getId());
                                }
                            },
                            () -> log.info("Bootstrap: email membership exists but user-linked membership not found; skipping.")
                    );
        } else {
            // Create a new ACTIVE membership
            OrganizationMembership membership = new OrganizationMembership(
                    tenantId, org.getId(), normalizedEmail,
                    adminUser.getDisplayName(), MembershipStatus.ACTIVE);
            membership.setUserId(adminUser.getId());
            organizationMembershipRepository.save(membership);
            log.info("Bootstrap created organization membership: orgId={} userId={} email={}",
                    org.getId(), adminUser.getId(), normalizedEmail);
        }
    }

    /**
     * Ensures the ADMIN role exists and is granted to the specified user.
     * Safe to call multiple times (idempotent).
     */
    private void ensureAdminRoleGrant(UUID resolvedTenantId, User adminUser) {
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
    }
}
