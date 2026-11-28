package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Acceptance-only wiring for the credential bootstrap dependency required by
 * the internal bootstrap controller. This profile is never active in normal
 * development or production runtime.
 */
@Configuration
@Profile("crm-acceptance")
public class CrmAcceptanceBootstrapConfig {

    @Bean
    CredentialBootstrapService crmAcceptanceCredentialBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            PasswordEncoder passwordEncoder,
            com.sanad.platform.access.capability.AccessCapabilityRepository accessCapabilityRepository,
            com.sanad.platform.access.role.RoleCapabilityRepository roleCapabilityRepository,
            com.sanad.platform.security.rls.TenantRlsTransactionContext tenantRlsTransactionContext,
            com.sanad.platform.security.rls.ControlPlaneDataAccess controlPlaneRoleExecutor
    ) {
        return new CredentialBootstrapService(
                tenantRepository,
                userRepository,
                roleRepository,
                userRoleGrantRepository,
                organizationRepository,
                organizationMembershipRepository,
                passwordEncoder,
                accessCapabilityRepository,
                roleCapabilityRepository,
                tenantRlsTransactionContext,
                controlPlaneRoleExecutor
        );
    }

    /**
     * CRM endpoints that do not carry an organizationId are evaluated against
     * tenant-wide role grants. The deterministic acceptance seed initially links
     * users to their primary organization for membership coverage; normalize only
     * the five acceptance grants to tenant scope before browser tests execute.
     *
     * <p>Under V20261029_1 (FORCE fail-closed tenant RLS on the identity plane)
     * this startup write MUST attach a verified HMAC-signed tenant context
     * inside its transaction. ApplicationRunner has no authenticated request
     * context; a raw tenant GUC is deliberately not trusted after
     * V20261124_2, and would either be rejected or match zero RLS rows.</p>
     */
    @Bean
    ApplicationRunner crmAcceptanceTenantWideRoleGrants(
            JdbcTemplate jdbcTemplate,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            com.sanad.platform.security.rls.TenantRlsTransactionContext tenantRlsTransactionContext) {
        return args -> {
            var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            var tenantGrants = java.util.Map.of(
                    "11111111-1111-4111-8111-111111111111",
                    new String[] {
                        "a0000000-0000-4000-8000-000000000001",
                        "a0000000-0000-4000-8000-000000000002",
                        "a0000000-0000-4000-8000-000000000003",
                        "a0000000-0000-4000-8000-000000000004"},
                    "22222222-2222-4222-8222-222222222222",
                    new String[] {
                        "a0000000-0000-4000-8000-000000000005"});
            tenantGrants.forEach((tenantId, grantIds) -> txTemplate.executeWithoutResult(status -> {
                // Fixed, acceptance-only tenant IDs; use the verified HMAC-signed
                // transaction context rather than the legacy, forgeable raw GUC.
                tenantRlsTransactionContext.applyForCurrentTransaction(
                        java.util.UUID.fromString(tenantId));
                String inList = String.join(", ", java.util.Arrays.stream(grantIds)
                        .map(id -> "'" + id + "'").toList());
                int updated = jdbcTemplate.update(
                        "UPDATE user_role_assignments SET organization_id = NULL "
                                + "WHERE id IN (" + inList + ")");
                if (updated != grantIds.length) {
                    throw new IllegalStateException(
                            "CRM_ACCEPTANCE_TENANT_WIDE_GRANT_NORMALIZATION_FAILED: expected "
                                    + grantIds.length + " grants for tenant " + tenantId
                                    + " but updated " + updated);
                }
            }));
        };
    }
}
