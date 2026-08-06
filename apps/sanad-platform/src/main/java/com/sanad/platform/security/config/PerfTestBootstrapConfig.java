package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;

/**
 * Performance-test provisioning for the CRM-033 k6 benchmark.
 *
 * <p>Active only under the {@code perf-test} profile (see
 * {@code application-perf-test.yml}) — never in production, never in local
 * development. It removes the CRM-033 infrastructure blocker permanently:
 * automated performance tests authenticate with a real JWT issued by the
 * ordinary login flow against a deterministically seeded environment. No H2
 * console access, no manual SQL, no security bypass.</p>
 *
 * <p>Two beans are provided:</p>
 * <ul>
 *   <li><b>CredentialBootstrapService</b> — required by the internal
 *       control-plane bootstrap controller on any profile outside
 *       {@code prod}/{@code local} (same wiring as {@code crm-acceptance}).</li>
 *   <li><b>ApplicationRunner</b> — idempotently seeds the perf-test tenant,
 *       the perf-admin user (password from {@code PERF_TEST_ADMIN_PASSWORD}),
 *       its ADMIN role with every active capability, organization membership,
 *       and the CRM fixtures referenced by the k6 script. The seed uses plain
 *       JDBC (H2/PostgreSQL-compatible) and marks the admin as
 *       {@code must_change_password = FALSE} so the JWT filter's credential
 *       rotation restriction never blocks benchmark endpoints.</li>
 * </ul>
 *
 * <p>Deterministic identifiers (tenant {@code 40000000-...}) are mirrored by
 * {@code performance/k6/crm-performance-baseline.js}, and the JWT HMAC key is
 * taken from {@code PERF_TEST_JWT_SECRET}/{@code JWT_SECRET} so tokens minted
 * by the login endpoint are reproducible across benchmark runs.</p>
 */
@Configuration
@Profile("perf-test")
public class PerfTestBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(PerfTestBootstrapConfig.class);

    // ------------------------------------------------------------------
    // Deterministic identifiers — mirrored by performance/k6/crm-performance-baseline.js
    // ------------------------------------------------------------------
    static final UUID TENANT_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    static final UUID ORG_ID = UUID.fromString("40000000-0000-4000-8000-000000000002");
    static final UUID ADMIN_USER_ID = UUID.fromString("40000000-0000-4000-8000-000000000003");
    static final UUID ADMIN_ROLE_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    static final UUID MEMBERSHIP_ID = UUID.fromString("40000000-0000-4000-8000-000000000005");
    static final UUID GRANT_ID = UUID.fromString("40000000-0000-4000-8000-000000000006");

    static final UUID ACCOUNT_ID = UUID.fromString("40000000-0000-4000-8000-000000000010");
    static final UUID CONTACT_ID = UUID.fromString("40000000-0000-4000-8000-000000000011");
    static final UUID PIPELINE_ID = UUID.fromString("40000000-0000-4000-8000-000000000012");
    static final UUID STAGE_1_ID = UUID.fromString("40000000-0000-4000-8000-000000000013");
    static final UUID STAGE_2_ID = UUID.fromString("40000000-0000-4000-8000-000000000014");
    static final UUID OPPORTUNITY_ID = UUID.fromString("40000000-0000-4000-8000-000000000015");
    static final UUID LEAD_ID = UUID.fromString("40000000-0000-4000-8000-000000000020");

    /**
     * Acceptance-only wiring for the credential bootstrap dependency required by
     * the internal bootstrap controller (mirrors CrmAcceptanceBootstrapConfig).
     */
    @Bean
    CredentialBootstrapService perfTestCredentialBootstrapService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            PasswordEncoder passwordEncoder,
            com.sanad.platform.access.capability.AccessCapabilityRepository accessCapabilityRepository,
            com.sanad.platform.access.role.RoleCapabilityRepository roleCapabilityRepository
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

    /**
     * Seeds the deterministic perf-test environment once the context is up.
     * Fails fast when required environment secrets are missing so a benchmark
     * run can never silently fall back to ephemeral keys or empty credentials.
     */
    @Bean
    ApplicationRunner perfTestEnvironmentSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager,
            @Value("${PERF_TEST_ADMIN_EMAIL:perf-admin@sanad.local}") String adminEmail,
            @Value("${PERF_TEST_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${sanad.security.jwt.secret:}") String jwtSecret) {
        return args -> {
            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException(
                        "PERF_TEST_ADMIN_PASSWORD must be set when the 'perf-test' profile is active");
            }
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException(
                        "PERF_TEST_JWT_SECRET (or JWT_SECRET) must be set when the 'perf-test' profile is "
                                + "active so JWT generation is deterministic for automated tests");
            }

            String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
            String passwordHash = passwordEncoder.encode(adminPassword);

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> seedEnvironment(jdbcTemplate, passwordHash, normalizedEmail));
            log.info("Perf-test environment seeded: tenant={} admin={} account={} lead={}",
                    TENANT_ID, normalizedEmail, ACCOUNT_ID, LEAD_ID);
        };
    }

    /**
     * Idempotent whole-environment seed executed in a single transaction so a
     * crash mid-way leaves the database untouched and the next boot re-seeds.
     */
    private void seedEnvironment(JdbcTemplate jdbc, String passwordHash, String adminEmail) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, TENANT_ID);
        if (existing != null && existing > 0) {
            log.info("Perf-test environment already seeded for tenant={}; skipping.", TENANT_ID);
            return;
        }

        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, 'Perf Test Tenant', 'perf-test', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, TENANT_ID);

        jdbc.update("""
                INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at)
                VALUES (?, ?, 'Perf Test Organization',
                        'Primary organization for the CRM-033 performance benchmark',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ORG_ID, TENANT_ID);

        // must_change_password = FALSE is critical: the JWT filter forbids all
        // /api/** calls (except auth/me, change-credential, logout) while rotation
        // is pending. The perf-admin login must yield a fully usable token.
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash,
                                   must_change_password, platform_admin, session_version,
                                   created_at, updated_at)
                VALUES (?, ?, ?, 'Perf Test Admin', 'ACTIVE', ?, FALSE, TRUE, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_USER_ID, TENANT_ID, adminEmail, passwordHash);

        jdbc.update("""
                INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id,
                        email, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'Perf Test Admin', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, MEMBERSHIP_ID, TENANT_ID, ORG_ID, ADMIN_USER_ID, adminEmail);

        // The perf tenant is created at runtime (after migrations), so neither
        // V15 nor V20260702_2 has created its ADMIN role — create it here.
        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
                VALUES (?, ?, 'ADMIN', 'Administrator',
                        'Tenant-wide administrative access (perf-test seed)',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ROLE_ID, TENANT_ID);

        // Grant every ACTIVE capability to the ADMIN role, mirroring
        // CredentialBootstrapService.ensureAdminAllCapabilities.
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT RANDOM_UUID(), ?, ?, cap.id, CURRENT_TIMESTAMP
                  FROM access_capabilities cap
                 WHERE cap.status = 'ACTIVE'
                   AND NOT EXISTS (
                       SELECT 1 FROM role_capabilities existing
                        WHERE existing.tenant_id = ?
                          AND existing.role_id = ?
                          AND existing.capability_id = cap.id
                   )
                """, TENANT_ID, ADMIN_ROLE_ID, TENANT_ID, ADMIN_ROLE_ID);

        // Tenant-wide grant (organization_id NULL) so CRM endpoints without an
        // organizationId query parameter are authorized against this role.
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id,
                        status, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, GRANT_ID, TENANT_ID, ADMIN_USER_ID, ADMIN_ROLE_ID);

        seedCrmFixtures(jdbc);
    }

    private void seedCrmFixtures(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name,
                        account_type, lifecycle_status, primary_currency_code, preferred_locale,
                        time_zone, source, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 0, 'Perf Test Account', 'perf test account',
                        'BUSINESS', 'ACTIVE', 'SAR', 'ar-SA', 'Asia/Riyadh',
                        'PERF_SEED', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ACCOUNT_ID, TENANT_ID, ADMIN_USER_ID, ADMIN_USER_ID);

        jdbc.update("""
                INSERT INTO crm_contacts (id, tenant_id, version, account_id, given_name,
                        family_name, display_name, normalized_name, primary_email,
                        normalized_email, primary_phone, preferred_locale, time_zone,
                        lifecycle_status, owner_user_id, consent_summary,
                        created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 0, ?, 'Perf', 'Tester', 'Perf Tester', 'perf tester',
                        'perf.contact@sanad.local', 'perf.contact@sanad.local', '+966500000099',
                        'ar-SA', 'Asia/Riyadh', 'ACTIVE', ?, 'GRANTED',
                        ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, CONTACT_ID, TENANT_ID, ACCOUNT_ID, ADMIN_USER_ID, ADMIN_USER_ID, ADMIN_USER_ID);

        jdbc.update("""
                INSERT INTO crm_pipelines (id, tenant_id, name, currency_code, active,
                        created_by, created_at, updated_at)
                VALUES (?, ?, 'Perf Test Pipeline', 'SAR', TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PIPELINE_ID, TENANT_ID, ADMIN_USER_ID);

        jdbc.update("""
                INSERT INTO crm_pipeline_stages (id, tenant_id, pipeline_id, name, sequence,
                        probability, terminal_state, active)
                VALUES (?, ?, ?, 'New', 1, 10, NULL, TRUE)
                """, STAGE_1_ID, TENANT_ID, PIPELINE_ID);
        jdbc.update("""
                INSERT INTO crm_pipeline_stages (id, tenant_id, pipeline_id, name, sequence,
                        probability, terminal_state, active)
                VALUES (?, ?, ?, 'Won', 2, 100, 'WON', TRUE)
                """, STAGE_2_ID, TENANT_ID, PIPELINE_ID);

        jdbc.update("""
                INSERT INTO crm_opportunities (id, tenant_id, version, account_id, contact_id,
                        pipeline_id, stage_id, name, amount, currency_code, probability,
                        status, owner_user_id, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 0, ?, ?, ?, ?, 'Perf Test Opportunity',
                        25000.000000, 'SAR', 10.00, 'OPEN',
                        ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, OPPORTUNITY_ID, TENANT_ID, ACCOUNT_ID, CONTACT_ID, PIPELINE_ID, STAGE_1_ID,
                ADMIN_USER_ID, ADMIN_USER_ID, ADMIN_USER_ID);

        // Seeded as CONVERTED so the lead-conversion endpoint exercises its
        // deterministic idempotent replay path for the whole benchmark run —
        // no first-request write race, no version conflicts at 12.5 RPS.
        jdbc.update("""
                INSERT INTO crm_leads (id, tenant_id, version, display_name, normalized_name,
                        company_name, email, normalized_email, phone, source, status,
                        owner_user_id, score, converted_account_id, converted_contact_id,
                        converted_opportunity_id, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 0, 'Perf Test Lead', 'perf test lead', 'Perf Co.',
                        'perf.lead@sanad.local', 'perf.lead@sanad.local', '+966500000098',
                        'WEB_FORM', 'CONVERTED', ?, 50.000, ?, ?, ?,
                        ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, LEAD_ID, TENANT_ID, ADMIN_USER_ID, ACCOUNT_ID, CONTACT_ID, OPPORTUNITY_ID,
                ADMIN_USER_ID, ADMIN_USER_ID);
    }
}
