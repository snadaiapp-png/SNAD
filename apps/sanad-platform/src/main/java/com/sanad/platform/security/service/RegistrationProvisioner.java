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
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Shared transactional tenant provisioning component. */
@Component
public final class RegistrationProvisioner {

    private final TenantRepository tenants;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;
    private final RoleRepository roles;
    private final UserRoleGrantRepository grants;
    private final AccessCapabilityRepository capabilities;
    private final RoleCapabilityRepository roleCapabilities;

    RegistrationProvisioner(
            TenantRepository tenants,
            OrganizationRepository organizations,
            UserRepository users,
            OrganizationMembershipRepository memberships,
            RoleRepository roles,
            UserRoleGrantRepository grants,
            AccessCapabilityRepository capabilities,
            RoleCapabilityRepository roleCapabilities
    ) {
        this.tenants = tenants;
        this.organizations = organizations;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.grants = grants;
        this.capabilities = capabilities;
        this.roleCapabilities = roleCapabilities;
    }

    public ProvisionedRegistration provision(
            String email,
            String displayName,
            String organizationName,
            String subdomain,
            String mobileNumber,
            String regionCode
    ) {
        Tenant tenant = tenants.save(new Tenant(organizationName, subdomain, TenantStatus.ACTIVE));
        Organization organization = organizations.save(new Organization(
                tenant, organizationName, "Primary organization", OrganizationStatus.ACTIVE));

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
        capabilities.findByStatusOrderByCodeAsc(CapabilityStatus.ACTIVE)
                .forEach(capability -> roleCapabilities.save(new RoleCapability(
                        tenant.getId(), roleId, capability.getId())));

        // Required before a caller performs direct JDBC metadata updates in the
        // same transaction. JpaRepository.flush() flushes the shared persistence context.
        tenants.flush();

        return new ProvisionedRegistration(tenant.getId(), administrator.getId(), subdomain);
    }

    public record ProvisionedRegistration(UUID tenantId, UUID userId, String subdomain) {}
}
