package com.sanad.platform.commerce;

import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.infrastructure.LocalEmailAdapter;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.config.CredentialBootstrapService;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test-only wiring for the {@code pg-acceptance} Spring Boot test context.
 *
 * <p>The {@code pg-acceptance} profile (used exclusively by
 * {@link CommerceOrderPostgresConcurrencyTest}) loads the FULL
 * {@code SanadPlatformApplication} context via {@code @SpringBootTest}.
 * Several production adapters are gated by {@code @Profile} annotations
 * that explicitly enumerate the test/acceptance profiles they support
 * — and {@code pg-acceptance} is not yet in those enumerations.</p>
 *
 * <p>Rather than widening production {@code @Profile} lists (which would
 * also need updates to the static
 * {@code ProductionCommandAdapterGuardTest} assertions and risks
 * accidentally enabling real production adapters in CI), this class
 * provides the missing beans directly using existing no-op/test
 * infrastructure. Each bean here is a deliberate choice to reuse an
 * existing local/stub adapter — no business logic is duplicated.</p>
 *
 * <h3>Bean inventory</h3>
 * <ul>
 *   <li>{@link EmailPort} — provided by reusing the existing no-op
 *       {@link LocalEmailAdapter} (the same adapter auto-configured
 *       under the {@code local}, {@code test}, {@code perf-test},
 *       and {@code crm-acceptance} profiles via
 *       {@code @Profile({"local","test","perf-test","crm-acceptance"})}).
 *       The adapter is constructed via its default constructor and
 *       exposed as a Spring bean — equivalent to what the production
 *       {@code @Component} scan would produce under those profiles.</li>
 *   <li>{@link CredentialBootstrapService} — administrative credential
 *       enrollment service, gated by {@code @Profile({"prod","local"})}.
 *       It is loaded by {@code ControlPlaneBootstrapController →
 *       ControlPlaneBootstrapService} but is NOT invoked by the
 *       commerce concurrency tests. We instantiate it via its
 *       production constructor signature (autowiring all 9 repository
 *       + PasswordEncoder dependencies from the existing context) so
 *       it behaves identically to production wiring under the
 *       {@code local} profile. No business logic is duplicated.</li>
 * </ul>
 *
 * <p>This class is {@code @TestConfiguration} (lives in
 * {@code src/test/java}, NOT packaged into production JAR/Docker image).
 * Activate via {@code @Import(PgAcceptanceWiringConfig.class)} on the
 * test class.</p>
 */
@TestConfiguration
public class PgAcceptanceWiringConfig {

    /**
     * Provide the {@link EmailPort} bean by reusing the existing
     * no-op {@link LocalEmailAdapter}.
     *
     * <p>Rationale: the production {@link LocalEmailAdapter} is gated
     * by {@code @Profile({"local","test","perf-test","crm-acceptance"})},
     * which excludes {@code pg-acceptance}. Instead of widening that
     * production {@code @Profile} annotation (which would also require
     * updating the static architectural assertions in
     * {@code ProductionCommandAdapterGuardTest} and risks unintended
     * production-adapter activation), we directly instantiate the same
     * no-op adapter here. The bean behaves identically to what
     * production wiring would produce under the {@code local} profile:
     * silently discards email send requests, returns a synthetic
     * success result.</p>
     *
     * @return a {@link LocalEmailAdapter} instance, satisfying the
     *         {@code EmailController → EmailUseCases → EmailPort}
     *         dependency chain in the pg-acceptance context.
     */
    @Bean
    public EmailPort emailPort() {
        return new LocalEmailAdapter();
    }

    /**
     * Provide the {@link CredentialBootstrapService} bean by reusing
     * the existing production service via its constructor.
     *
     * <p>Rationale: the production {@link CredentialBootstrapService} is
     * gated by {@code @Profile({"prod","local"})}, which excludes
     * {@code pg-acceptance}. The service is loaded by
     * {@code ControlPlaneBootstrapController → ControlPlaneBootstrapService}
     * (both unconditional {@code @Component}/{@code @Service} beans in
     * the application scan), so its absence breaks the Spring context
     * for any test that loads the full application. The commerce
     * concurrency tests do NOT invoke this service; we only need the
     * bean to exist so the context loads. Constructing it via the
     * production constructor with autowired dependencies is equivalent
     * to what {@code @Profile("local")} would produce.</p>
     *
     * @param tenantRepository autowired from existing context
     * @param userRepository autowired from existing context
     * @param roleRepository autowired from existing context
     * @param userRoleGrantRepository autowired from existing context
     * @param organizationRepository autowired from existing context
     * @param organizationMembershipRepository autowired from existing context
     * @param passwordEncoder autowired from existing context
     * @param accessCapabilityRepository autowired from existing context
     * @param roleCapabilityRepository autowired from existing context
     * @return a {@link CredentialBootstrapService} instance satisfying the
     *         {@code ControlPlaneBootstrapService → CredentialBootstrapService}
     *         dependency chain.
     */
    @Bean
    public CredentialBootstrapService credentialBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            PasswordEncoder passwordEncoder,
            AccessCapabilityRepository accessCapabilityRepository,
            RoleCapabilityRepository roleCapabilityRepository
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
                roleCapabilityRepository
        );
    }
}
