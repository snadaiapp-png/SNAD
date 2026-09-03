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
 * release tests. Activates only under the {@code workflow-e2e} profile.
 *
 * <p>The fixture deliberately provides multiple real identities so concurrency,
 * approval, reassignment and tenant-isolation scenarios never share one cached
 * ADMIN token. Production profiles never load this configuration.</p>
 */
@Configuration
@Profile("workflow-e2e")
public class WorkflowE2eBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowE2eBootstrapConfig.class);

    public static final UUID TENANT_A_ID = UUID.fromString("aaaaaaa1-0000-4000-8000-000000000001");
    public static final UUID TENANT_B_ID = UUID.fromString("bbbbbbb1-0000-4000-8000-000000000001");

    public static final UUID ADMIN_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000002");
    public static final UUID EXEC_A_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000011");
    public static final UUID EXEC_B_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000012");
    public static final UUID APPROVER_A_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000021");
    public static final UUID APPROVER_B_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000022");
    public static final UUID REASSIGNER_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000031");
    public static final UUID INCIDENT_USER_ID = UUID.fromString("aaaaaaa2-0000-4000-8000-000000000041");
    public static final UUID TENANT_B_USER_ID = UUID.fromString("bbbbbbb2-0000-4000-8000-000000000002");

    public static final UUID ADMIN_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000002");
    public static final UUID EXEC_A_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000011");
    public static final UUID EXEC_B_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000012");
    public static final UUID APPROVER_A_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000021");
    public static final UUID APPROVER_B_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000022");
    public static final UUID REASSIGNER_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000031");
    public static final UUID INCIDENT_EMPLOYEE_ID = UUID.fromString("aaaaaaa4-0000-4000-8000-000000000041");
    public static final UUID TENANT_B_EMPLOYEE_ID = UUID.fromString("bbbbbbb4-0000-4000-8000-000000000002");

    private static final UUID TENANT_A_ROLE_ID = UUID.fromString("aaaaaaa3-0000-4000-8000-000000000003");
    private static final UUID TENANT_B_ROLE_ID = UUID.fromString("bbbbbbb3-0000-4000-8000-000000000003");

    public static final String E2E_PASSWORD = "WfE2eTest!2026";
    public static final String E2E_ADMIN_EMAIL = "wf-e2e-admin@snad-e2e.example";
    public static final String E2E_EXEC_A_EMAIL = "wf-e2e-exec-a@snad-e2e.example";
    public static final String E2E_EXEC_B_EMAIL = "wf-e2e-exec-b@snad-e2e.example";
    public static final String E2E_APPROVER_A_EMAIL = "wf-e2e-approver-a@snad-e2e.example";
    public static final String E2E_APPROVER_B_EMAIL = "wf-e2e-approver-b@snad-e2e.example";
    public static final String E2E_REASSIGNER_EMAIL = "wf-e2e-reassigner@snad-e2e.example";
    public static final String E2E_INCIDENT_EMAIL = "wf-e2e-incident@snad-e2e.example";
    public static final String E2E_TENANT_B_EMAIL = "wf-e2e-tenant-b@snad-e2e.example";

    private record Actor(UUID userId, UUID employeeId, String employeeNumber, String email, String displayName) {}

    @Bean
    ApplicationRunner workflowE2eSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("WorkflowE2eBootstrap: seeding multi-actor E2E tenants and capabilities");
            var now = Timestamp.from(Instant.now());
            String passwordHash = passwordEncoder.encode(E2E_PASSWORD);

            seedTenant(jdbc, TENANT_A_ID, "Workflow E2E Tenant A", "wf-e2e-a", now);
            seedTenant(jdbc, TENANT_B_ID, "Workflow E2E Tenant B", "wf-e2e-b", now);

            var tenantAActors = List.of(
                    new Actor(ADMIN_USER_ID, ADMIN_EMPLOYEE_ID, "E2E-A-001", E2E_ADMIN_EMAIL, "WF E2E Admin"),
                    new Actor(EXEC_A_USER_ID, EXEC_A_EMPLOYEE_ID, "E2E-A-011", E2E_EXEC_A_EMAIL, "WF Executor A"),
                    new Actor(EXEC_B_USER_ID, EXEC_B_EMPLOYEE_ID, "E2E-A-012", E2E_EXEC_B_EMAIL, "WF Executor B"),
                    new Actor(APPROVER_A_USER_ID, APPROVER_A_EMPLOYEE_ID, "E2E-A-021", E2E_APPROVER_A_EMAIL, "WF Approver A"),
                    new Actor(APPROVER_B_USER_ID, APPROVER_B_EMPLOYEE_ID, "E2E-A-022", E2E_APPROVER_B_EMAIL, "WF Approver B"),
                    new Actor(REASSIGNER_USER_ID, REASSIGNER_EMPLOYEE_ID, "E2E-A-031", E2E_REASSIGNER_EMAIL, "WF Reassigner"),
                    new Actor(INCIDENT_USER_ID, INCIDENT_EMPLOYEE_ID, "E2E-A-041", E2E_INCIDENT_EMAIL, "WF Incident Manager")
            );
            for (var actor : tenantAActors) {
                seedActor(jdbc, TENANT_A_ID, actor, passwordHash, now);
            }
            seedActor(jdbc, TENANT_B_ID,
                    new Actor(TENANT_B_USER_ID, TENANT_B_EMPLOYEE_ID, "E2E-B-001", E2E_TENANT_B_EMAIL, "WF Tenant B Actor"),
                    passwordHash, now);

            seedWorkflowRole(jdbc, TENANT_A_ID, TENANT_A_ROLE_ID, "WF_E2E_ACTOR", now);
            seedWorkflowRole(jdbc, TENANT_B_ID, TENANT_B_ROLE_ID, "WF_E2E_ACTOR", now);

            for (var actor : tenantAActors) {
                assignRole(jdbc, TENANT_A_ID, actor.userId(), TENANT_A_ROLE_ID, now);
            }
            assignRole(jdbc, TENANT_B_ID, TENANT_B_USER_ID, TENANT_B_ROLE_ID, now);

            grantWorkflowCapabilities(jdbc, TENANT_A_ID, TENANT_A_ROLE_ID);
            grantWorkflowCapabilities(jdbc, TENANT_B_ID, TENANT_B_ROLE_ID);

            Integer tenantAUsers = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_A_ID);
            Integer tenantBUsers = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, TENANT_B_ID);
            log.info("WorkflowE2eBootstrap: seeded tenantAUsers={} tenantBUsers={}", tenantAUsers, tenantBUsers);
        };
    }

    private static void seedTenant(JdbcTemplate jdbc, UUID id, String name, String subdomain, Timestamp now) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
                """, id, name, subdomain, now, now);
    }

    private static void seedActor(JdbcTemplate jdbc, UUID tenantId, Actor actor,
                                  String passwordHash, Timestamp now) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                   SET email = EXCLUDED.email,
                       display_name = EXCLUDED.display_name,
                       password_hash = EXCLUDED.password_hash,
                       status = 'ACTIVE',
                       updated_at = NOW()
                """, actor.userId(), tenantId, actor.email(), actor.displayName(), passwordHash, now, now);

        jdbc.update("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number,
                                          first_name, last_name, display_name,
                                          employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Workflow', 'E2E', ?, 'FULL_TIME', 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO UPDATE
                   SET user_id = EXCLUDED.user_id,
                       display_name = EXCLUDED.display_name,
                       status = 'ACTIVE',
                       updated_at = NOW()
                """, actor.employeeId(), tenantId, actor.userId(), actor.employeeNumber(), actor.displayName(), now, now);
    }

    private static void seedWorkflowRole(JdbcTemplate jdbc, UUID tenantId, UUID roleId,
                                         String code, Timestamp now) {
        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Workflow E2E Actor', 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
                """, roleId, tenantId, code, now, now);
    }

    private static void assignRole(JdbcTemplate jdbc, UUID tenantId, UUID userId,
                                   UUID roleId, Timestamp now) {
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT DO NOTHING
                """, tenantId, userId, roleId, now, now);
    }

    private static void grantWorkflowCapabilities(JdbcTemplate jdbc, UUID tenantId, UUID roleId) {
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, ac.id, NOW()
                FROM access_capabilities ac
                WHERE ac.code LIKE 'WORKFLOW.%'
                  AND ac.status = 'ACTIVE'
                ON CONFLICT DO NOTHING
                """, tenantId, roleId);
    }
}
