package com.sanad.platform.workflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Workflow Y2 E2E bootstrap — profile-gated deterministic seed for browser
 * release tests. Activates only under the {@code workflow-e2e} Spring
 * profile. Uses Spring's {@link PasswordEncoder} for real password hashing.
 * Never activates in production or normal local startup.
 *
 * <p>Seeds: one tenant, an ADMIN bootstrap user (for API-level setup), and
 * role-capability bindings covering all WORKFLOW.* capabilities.</p>
 */
@Configuration
@Profile("workflow-e2e")
public class WorkflowE2eBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowE2eBootstrapConfig.class);

    static final UUID TENANT_A_ID = UUID.fromString("aaaaaaa1-0000-4000-8000-000000000001");
    static final UUID ADMIN_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000002");
    static final UUID ADMIN_ROLE_ID = UUID.fromString("aaaaaaa3-0000-4000-8000-000000000003");

    static final String E2E_ADMIN_EMAIL = "wf-e2e-admin@snad-e2e.example";
    static final String E2E_PASSWORD = "WfE2eTest!2026";

    @Bean
    ApplicationRunner workflowE2eSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("WorkflowE2eBootstrap: seeding E2E tenant, user, and capabilities");
            var now = Timestamp.from(Instant.now());

            // 1. Tenant
            jdbc.update("""
                    INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                    VALUES (?, 'Workflow E2E Tenant', 'wf-e2e', 'ACTIVE', ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, TENANT_A_ID, now, now);

            // 2. Admin user with real PasswordEncoder hash
            String passwordHash = passwordEncoder.encode(E2E_PASSWORD);
            jdbc.update("""
                    INSERT INTO users (id, tenant_id, email, display_name, status,
                                       password_hash, created_at, updated_at)
                    VALUES (?, ?, ?, 'WF E2E Admin', 'ACTIVE', ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                       SET password_hash = EXCLUDED.password_hash,
                           status = 'ACTIVE',
                           updated_at = NOW()
                    """, ADMIN_USER_ID, TENANT_A_ID, E2E_ADMIN_EMAIL, passwordHash, now, now);

            // 3. Employee linked to the admin user (required by WorkflowActionabilityService)
            jdbc.update("""
                    INSERT INTO hr_employees (id, tenant_id, user_id, employee_number,
                                              first_name, last_name, display_name,
                                              employment_type, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, 'E2E-001', 'E2E', 'Admin', 'E2E Admin',
                            'FULL_TIME', 'ACTIVE', ?, ?)
                    ON CONFLICT DO NOTHING
                    """, TENANT_A_ID, ADMIN_USER_ID, now, now);

            // 4. ADMIN role
            jdbc.update("""
                    INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at)
                    VALUES (?, ?, 'ADMIN', 'E2E Administrator', 'ACTIVE', ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, ADMIN_ROLE_ID, TENANT_A_ID, now, now);

            // 5. Bind ADMIN role to admin user
            jdbc.update("""
                    INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', ?, ?)
                    ON CONFLICT DO NOTHING
                    """, TENANT_A_ID, ADMIN_USER_ID, ADMIN_ROLE_ID, now, now);

            // 6. Grant all WORKFLOW.* capabilities to ADMIN role
            jdbc.update("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT gen_random_uuid(), ?, ?, ac.id, NOW()
                    FROM access_capabilities ac
                    WHERE ac.code LIKE 'WORKFLOW.%'
                      AND ac.status = 'ACTIVE'
                    ON CONFLICT DO NOTHING
                    """, TENANT_A_ID, ADMIN_ROLE_ID);

            // 7. Verify seeding
            Integer userCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_A_ID);
            Integer capCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role_capabilities WHERE tenant_id = ? AND role_id = ?",
                    Integer.class, TENANT_A_ID, ADMIN_ROLE_ID);
            log.info("WorkflowE2eBootstrap: seeded tenant={} users={} workflowCaps={}",
                    TENANT_A_ID, userCount, capCount);
        };
    }
}
