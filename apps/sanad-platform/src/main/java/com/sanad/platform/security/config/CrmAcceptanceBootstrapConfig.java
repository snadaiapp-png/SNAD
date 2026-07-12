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
            PasswordEncoder passwordEncoder
    ) {
        return new CredentialBootstrapService(
                tenantRepository,
                userRepository,
                roleRepository,
                userRoleGrantRepository,
                organizationRepository,
                organizationMembershipRepository,
                passwordEncoder
        );
    }

    /**
     * CRM endpoints that do not carry an organizationId are evaluated against
     * tenant-wide role grants. The deterministic acceptance seed initially links
     * users to their primary organization for membership coverage; normalize only
     * the five acceptance grants to tenant scope before browser tests execute.
     */
    @Bean
    ApplicationRunner crmAcceptanceTenantWideRoleGrants(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.update("""
                UPDATE user_role_assignments
                   SET organization_id = NULL
                 WHERE id IN (
                     'a0000000-0000-4000-8000-000000000001',
                     'a0000000-0000-4000-8000-000000000002',
                     'a0000000-0000-4000-8000-000000000003',
                     'a0000000-0000-4000-8000-000000000004',
                     'a0000000-0000-4000-8000-000000000005'
                 )
                """);
    }
}
