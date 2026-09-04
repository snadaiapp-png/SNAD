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
import java.util.List;
import java.util.UUID;

/**
 * Workflow Y2 E2E bootstrap — profile-gated deterministic seed for browser
 * release tests. Activates only under the {@code workflow-e2e} Spring
 * profile. Uses Spring's {@link PasswordEncoder} for real password hashing.
 * Never activates in production or normal local startup.
 *
 * <p>Seeds two real tenants with the full multi-actor release fixture
 * (task §9): Tenant A designers/publishers/employees/approvers/reassigner/
 * incident-manager plus an ADMIN bootstrap user for API-level setup, and
 * Tenant B employees for true cross-tenant isolation proof (P12). Every
 * interactive user is linked to an ACTIVE Employee in the same tenant, as
 * required by WorkflowActionabilityService.</p>
 */
@Configuration
@Profile("workflow-e2e")
public class WorkflowE2eBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowE2eBootstrapConfig.class);

    static final UUID TENANT_A_ID = UUID.fromString("aaaaaaa1-0000-4000-8000-000000000001");
    static final UUID TENANT_B_ID = UUID.fromString("bbbbbbb1-0000-4000-8000-000000000001");
    static final UUID ADMIN_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000002");
    static final UUID ADMIN_ROLE_ID = UUID.fromString("aaaaaaa3-0000-4000-8000-000000000003");

    static final String E2E_ADMIN_EMAIL = "wf-e2e-admin@snad-e2e.example";
    static final String E2E_PASSWORD = "WfE2eTest!2026";

    /** Tenant A fixture actors (email local part, role code, capability list). */
    private record Actor(String emailLocalPart, String roleCode, List<String> capabilities) {}

    private static final List<Actor> TENANT_A_ACTORS = List.of(
            new Actor("wf-e2e-designer", "E2E_DESIGNER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.WRITE", "WORKFLOW.DESIGN", "WORKFLOW.VALIDATE")),
            new Actor("wf-e2e-publisher", "E2E_PUBLISHER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.PUBLISH")),
            new Actor("wf-e2e-employee-1", "E2E_EMPLOYEE",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.TASK_EXECUTE")),
            new Actor("wf-e2e-employee-2", "E2E_EMPLOYEE",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.TASK_EXECUTE")),
            new Actor("wf-e2e-approver-1", "E2E_APPROVER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.APPROVE", "WORKFLOW.TASK_EXECUTE")),
            new Actor("wf-e2e-approver-2", "E2E_APPROVER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.APPROVE", "WORKFLOW.TASK_EXECUTE")),
            new Actor("wf-e2e-reassigner", "E2E_REASSIGNER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.TASK_EXECUTE", "WORKFLOW.REASSIGN")),
            new Actor("wf-e2e-incident-manager", "E2E_INCIDENT_MANAGER",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.MONITOR", "WORKFLOW.INCIDENT_MANAGE")));
    private static final List<Actor> TENANT_B_ACTORS = List.of(
            new Actor("wf-e2e-tenant-b-employee", "E2E_TENANT_B_EMPLOYEE",
                    List.of("WORKFLOW.VIEW", "WORKFLOW.TASK_EXECUTE")));

    @Bean
    ApplicationRunner workflowE2eSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("WorkflowE2eBootstrap: seeding E2E tenants, multi-actor fixtures and capabilities");
            var now = Timestamp.from(Instant.now());

            // 1. Tenants
            jdbc.update("""
                    INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                    VALUES (?, 'Workflow E2E Tenant', 'wf-e2e', 'ACTIVE', ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, TENANT_A_ID, now, now);
            jdbc.update("""
                    INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                    VALUES (?, 'Workflow E2E Tenant B', 'wf-e2e-b', 'ACTIVE', ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, TENANT_B_ID, now, now);

            // 2. Admin user with real PasswordEncoder hash (API-level setup actor)
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

            // 6. Grant all WORKFLOW.* capabilities to ADMIN role (+ HR employee
            //    read and user lifecycle for P09 disabled-user semantics).
            jdbc.update("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT gen_random_uuid(), ?, ?, ac.id, NOW()
                    FROM access_capabilities ac
                    WHERE (ac.code LIKE 'WORKFLOW.%'
                           OR ac.code IN ('HR.EMPLOYEE.READ', 'USER.WRITE'))
                      AND ac.status = 'ACTIVE'
                    ON CONFLICT DO NOTHING
                    """, TENANT_A_ID, ADMIN_ROLE_ID);

            // 7. Multi-actor fixture (task §9): one user + linked ACTIVE employee
            //    + tenant role + capability bindings per actor.
            seedActors(jdbc, passwordEncoder, TENANT_A_ID, TENANT_A_ACTORS, "A", passwordHash, now);
            seedActors(jdbc, passwordEncoder, TENANT_B_ID, TENANT_B_ACTORS, "B", passwordHash, now);

            // 8. Verify seeding
            Integer userCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_A_ID);
            Integer capCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role_capabilities WHERE tenant_id = ? AND role_id = ?",
                    Integer.class, TENANT_A_ID, ADMIN_ROLE_ID);
            Integer employeeCountA = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_A_ID);
            Integer employeeCountB = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_B_ID);
            log.info("WorkflowE2eBootstrap: seeded tenantA={} (users={} employees={}) tenantB={} employees={} adminCaps={}",
                    TENANT_A_ID, userCount, employeeCountA, TENANT_B_ID, employeeCountB, capCount);
        };
    }

    private void seedActors(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, UUID tenantId,
                            List<Actor> actors, String tenantLabel, String passwordHash, Timestamp now) {
        for (Actor actor : actors) {
            String email = actor.emailLocalPart() + "@snad-e2e.example";
            UUID userId = deterministicId(tenantLabel, actor.emailLocalPart(), "user");
            UUID roleId = deterministicId(tenantLabel, actor.roleCode(), "role");

            jdbc.update("""
                    INSERT INTO users (id, tenant_id, email, display_name, status,
                                       password_hash, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                       SET password_hash = EXCLUDED.password_hash,
                           status = 'ACTIVE',
                           updated_at = NOW()
                    """, userId, tenantId, email, "E2E " + actor.roleCode(), passwordHash, now, now);

            jdbc.update("""
                    INSERT INTO hr_employees (id, tenant_id, user_id, employee_number,
                                              first_name, last_name, display_name,
                                              employment_type, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'E2E', ?, 'E2E ' || ?,
                            'FULL_TIME', 'ACTIVE', ?, ?)
                    ON CONFLICT DO NOTHING
                    """, tenantId, userId, actor.emailLocalPart().toUpperCase() + "-E", actor.roleCode(),
                    actor.roleCode(), now, now);

            jdbc.update("""
                    INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, roleId, tenantId, actor.roleCode(), actor.roleCode(), now, now);

            jdbc.update("""
                    INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', ?, ?)
                    ON CONFLICT DO NOTHING
                    """, tenantId, userId, roleId, now, now);

            jdbc.update("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT gen_random_uuid(), ?, ?, ac.id, NOW()
                    FROM access_capabilities ac
                    WHERE ac.code IN (SELECT unnest(?::text[]))
                      AND ac.status = 'ACTIVE'
                    ON CONFLICT DO NOTHING
                    """, tenantId, roleId, actor.capabilities().toArray(new String[0]));
        }
    }

    /**
     * Deterministic, stable v4-shaped UUIDs so the fixture is reproducible
     * across bootstrap runs and CI environments. Derived from SHA-256 of
     * (tenantLabel, naturalKey, kind) with the v4 variant bits set.
     */
    private static UUID deterministicId(String tenantLabel, String naturalKey, String kind) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((tenantLabel + "|" + naturalKey + "|" + kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] b = java.util.Arrays.copyOfRange(digest, 0, 16);
            b[6] = (byte) ((b[6] & 0x0F) | 0x40); // version 4
            b[8] = (byte) ((b[8] & 0x3F) | 0x80); // RFC 4122 variant
            return UUID.fromString(String.format(
                    "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                    b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
                    b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
