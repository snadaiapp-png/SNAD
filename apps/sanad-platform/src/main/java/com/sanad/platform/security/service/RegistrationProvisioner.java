package com.sanad.platform.security.service;

import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.security.rls.ControlPlaneDataAccess;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Shared tenant/user provisioning component. */
@Component
public final class RegistrationProvisioner {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RegistrationProvisioner.class);

    private final TenantRepository tenants;
    private TenantRlsTransactionContext registrationTenantRlsContext;
    private final ControlPlaneDataAccess controlPlaneDataAccess;

    @org.springframework.beans.factory.annotation.Autowired
    void setRegistrationTenantRlsContext(TenantRlsTransactionContext context) {
        this.registrationTenantRlsContext = context;
    }
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;
    private final RoleRepository roles;
    private final UserRoleGrantRepository grants;
    private final AccessCapabilityRepository capabilities;
    private final RoleCapabilityRepository roleCapabilities;
    private final RoleTemplateProvisioner roleTemplateProvisioner;

    RegistrationProvisioner(
            TenantRepository tenants,
            OrganizationRepository organizations,
            UserRepository users,
            OrganizationMembershipRepository memberships,
            RoleRepository roles,
            UserRoleGrantRepository grants,
            AccessCapabilityRepository capabilities,
            RoleCapabilityRepository roleCapabilities,
            RoleTemplateProvisioner roleTemplateProvisioner,
            ControlPlaneDataAccess controlPlaneDataAccess
    ) {
        this.tenants = tenants;
        this.organizations = organizations;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.grants = grants;
        this.capabilities = capabilities;
        this.roleCapabilities = roleCapabilities;
        this.roleTemplateProvisioner = roleTemplateProvisioner;
        this.controlPlaneDataAccess = controlPlaneDataAccess;
    }

    public ProvisionedRegistration provision(
            String email,
            String displayName,
            String organizationName,
            String subdomain,
            String mobileNumber,
            String regionCode
    ) {
        // ORDER 11 (V20261118_1): tenant lifecycle DML is control-plane-only in
        // the separated topology. The INSERT runs on the DEDICATED control-plane
        // connection (independent identity — no runtime membership, no SET ROLE).
        // It commits on its own connection BEFORE the surrounding runtime
        // transaction completes, so a later runtime failure must be compensated:
        // a rollback-synchronized deletion removes the tenant row after the
        // runtime transaction (and its FK children) has rolled back, restoring
        // the pre-order single-transaction atomicity for the common failure
        // modes (constraint violations on user/org/role writes).
        UUID tenantId = controlPlaneDataAccess.inControlPlane(cp -> cp.queryForObject(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now()) RETURNING id",
                UUID.class, UUID.randomUUID(), organizationName, subdomain));
        registerTenantRollbackCompensation(tenantId);
        Tenant tenant = tenants.getReferenceById(tenantId);
        Organization organization = organizations.save(new Organization(
                tenant, organizationName, "Primary organization", OrganizationStatus.ACTIVE));

        // This tenant ID is freshly created by the backend, not supplied by
        // an anonymous caller. Scope the current write transaction before the
        // first insert into users when users RLS is eventually enabled.
        if (registrationTenantRlsContext != null) {
            registrationTenantRlsContext.applyForCurrentTransaction(tenant.getId());
        }
        User administrator = new User(tenant.getId(), email, displayName, UserStatus.ACTIVE);
        administrator.setMobileNumber(mobileNumber);
        administrator.setMobileRegion(regionCode);
        administrator.setMustChangePassword(false);
        administrator = users.save(administrator);

        OrganizationMembership membership = new OrganizationMembership(
                tenant.getId(), organization.getId(), email, displayName, MembershipStatus.ACTIVE);
        membership.setUserId(administrator.getId());
        memberships.save(membership);

        Role adminRole = roles.save(new Role(
                tenant.getId(), "ADMIN", "Administrator", "Tenant-wide administrative access"));
        grants.save(new UserRoleGrant(tenant.getId(), administrator.getId(), adminRole.getId(), null));
        UUID roleId = adminRole.getId();
        // Payroll permissions are privileged and must never be inherited from the
        // global ACTIVE catalog by newly provisioned tenant administrators.
        capabilities.findByStatusOrderByCodeAsc(CapabilityStatus.ACTIVE).stream()
                .filter(capability -> !capability.getCode().startsWith("HRM.PAYROLL."))
                .forEach(capability -> roleCapabilities.save(new RoleCapability(
                        tenant.getId(), roleId, capability.getId())));

        // Flush JPA-created tenant/role state before the JdbcTemplate-based
        // canonical template provisioner runs in the same transaction.
        tenants.flush();
        roles.flush();
        roleCapabilities.flush();

        roleTemplateProvisioner.provision(tenant.getId());

        return new ProvisionedRegistration(tenant.getId(), administrator.getId(), subdomain);
    }

    /**
     * ORDER 11 cross-connection atomicity compensation: the tenant row is
     * created on the dedicated control-plane connection (own transaction),
     * while organizations/users/roles/grants are written in the surrounding
     * runtime transaction. When that runtime transaction rolls back, the
     * already-committed tenant row would be orphaned and would permanently
     * consume its unique subdomain. This synchronization deletes the row on
     * the control-plane connection AFTER the runtime rollback (its FK
     * children are gone by then), restoring effective atomicity.
     *
     * <p>Best-effort: a failure of the compensating delete is logged loudly
     * (and the orphan is detectable by operations: an ACTIVE tenant with no
     * organization/user rows) — it must never mask the original rollback
     * exception.
     */
    private void registerTenantRollbackCompensation(UUID tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return; // no runtime transaction to compensate (standalone call)
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    Integer removed = controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                            "DELETE FROM tenants WHERE id = ?", tenantId));
                    if (removed != null && removed > 0) {
                        log.warn("Control-plane compensation: removed orphaned tenant id={} "
                                + "after runtime rollback", tenantId);
                    }
                } catch (RuntimeException ex) {
                    log.error("Control-plane compensation FAILED for tenant id={}: the row "
                            + "may be orphaned (ACTIVE tenant without organization/user). "
                            + "Manual cleanup required.", tenantId, ex);
                }
            }
        });
    }

    public record ProvisionedRegistration(UUID tenantId, UUID userId, String subdomain) {}
}
