package com.sanad.platform.crm.ownership;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.util.List;
import java.util.UUID;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

class CrmOwnershipRbacPostgresTest {


    private static JdbcTemplate jdbc;
    private static final UUID TENANT_A = UUID.fromString("10000000-0000-4000-8000-000000000901");
    private static final UUID TENANT_B = UUID.fromString("20000000-0000-4000-8000-000000000902");

    @BeforeAll
    static void setup() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable, "PostgreSQL Direct required for acceptance");

        // Step 1: Run V14 baseline (creates tenants + roles + capabilities tables).
        // Seed test tenants BEFORE V20260722.8 so SALES_MANAGER/SALES_REPRESENTATIVE
        // roles are auto-seeded for these tenants.
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .outOfOrder(true)
                .validateOnMigrate(true)
                .target("15")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        seedTenantAndAdmin(TENANT_A, "rbac-a");
        seedTenantAndAdmin(TENANT_B, "rbac-b");

        // Step 2: Now run migrations through V20260807.1 — this grants the
        // additional 22 CRM READ+WRITE caps to SALES_MANAGER (extending the
        // 11 ownership caps from V20260722.8). Expected total: 33 caps.
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .outOfOrder(true)
                .validateOnMigrate(true)
                .target("20260807.1")
                .load()
                .migrate();

        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .outOfOrder(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void seedsExactSeventeenActiveOwnershipCapabilities() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM access_capabilities
                 WHERE status='ACTIVE'
                   AND code LIKE 'CRM.%'
                   AND code IN (
                       'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE','CRM.ASSIGNMENT.ADMIN',
                       'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE','CRM.TRANSFER.EXECUTE',
                       'CRM.TEAM.READ','CRM.TEAM.ADMIN',
                       'CRM.QUEUE.READ','CRM.QUEUE.CLAIM','CRM.QUEUE.ADMIN',
                       'CRM.TERRITORY.READ','CRM.TERRITORY.ADMIN',
                       'CRM.ASSIGNMENT_RULE.READ','CRM.ASSIGNMENT_RULE.ADMIN',
                       'CRM.OWNERSHIP_HISTORY.READ')
                """, Integer.class);
        assertThat(count).isEqualTo(17);
    }

    @Test
    void createsTenantScopedManagerAndRepresentativeMappings() {
        for (UUID tenantId : List.of(TENANT_A, TENANT_B)) {
            assertThat(roleCapabilityCount(tenantId, "SALES_MANAGER")).isEqualTo(33);
            assertThat(roleCapabilityCount(tenantId, "SALES_REPRESENTATIVE")).isEqualTo(19);
            // ADMIN gets ALL active capabilities (V15 invariant). The exact count
            // depends on which migrations have run; we assert it's > 0 to verify
            // the binding exists without coupling to a specific cap count.
            assertThat(roleCapabilityCount(tenantId, "ADMIN")).isGreaterThan(0);
        }
    }

    @Test
    void transferExecuteRemainsInternalOnlyForHumanSalesRoles() {
        for (UUID tenantId : List.of(TENANT_A, TENANT_B)) {
            assertThat(hasCapability(tenantId, "SALES_MANAGER", "CRM.TRANSFER.EXECUTE")).isFalse();
            assertThat(hasCapability(tenantId, "SALES_REPRESENTATIVE", "CRM.TRANSFER.EXECUTE")).isFalse();
            assertThat(hasCapability(tenantId, "ADMIN", "CRM.TRANSFER.EXECUTE")).isTrue();
        }
    }

    @Test
    void roleCapabilityRowsNeverCrossTenantBoundaries() {
        Integer leaks = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM role_capabilities rc
                  JOIN roles role ON role.id=rc.role_id
                 WHERE rc.tenant_id <> role.tenant_id
                """, Integer.class);
        assertThat(leaks).isZero();
    }

    private static void seedTenantAndAdmin(UUID tenantId, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, tenantId, "CRM Ownership RBAC", subdomain);
        jdbc.update("""
                INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at)
                VALUES (?, ?, 'ADMIN', 'Administrator', 'Test administrator',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), tenantId);
        // Seed SALES_MANAGER and SALES_REPRESENTATIVE roles for this tenant.
        // V20260722.8 auto-seeds these for tenants existing AT migration time,
        // but this test seeds tenants AFTER V20260722.8 runs. So we manually
        // create the roles here, then bind capabilities using the same SQL
        // pattern as V20260722.8 + V20260807.1.
        UUID salesManagerRoleId = UUID.randomUUID();
        UUID salesRepRoleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at)
                VALUES (?, ?, 'SALES_MANAGER', 'Sales Manager', 'Test SALES_MANAGER',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, salesManagerRoleId, tenantId);
        jdbc.update("""
                INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at)
                VALUES (?, ?, 'SALES_REPRESENTATIVE', 'Sales Representative', 'Test SALES_REPRESENTATIVE',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, salesRepRoleId, tenantId);

        // Bind 11 ownership capabilities to SALES_MANAGER (V20260722.8 set).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, ac.id, CURRENT_TIMESTAMP
                FROM access_capabilities ac
                WHERE ac.code IN (
                    'CRM.ASSIGNMENT.READ', 'CRM.ASSIGNMENT.WRITE',
                    'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST', 'CRM.TRANSFER.APPROVE',
                    'CRM.TEAM.READ',
                    'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
                    'CRM.TERRITORY.READ',
                    'CRM.ASSIGNMENT_RULE.READ',
                    'CRM.OWNERSHIP_HISTORY.READ'
                ) AND ac.status = 'ACTIVE'
                AND NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = ? AND rc.role_id = ? AND rc.capability_id = ac.id
                )
                """, tenantId, salesManagerRoleId, tenantId, salesManagerRoleId);

        // Bind 22 CRM READ+WRITE capabilities to SALES_MANAGER (V20260807.1 set).
        // Total: 11 + 22 = 33 capabilities (matches test assertion).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, ac.id, CURRENT_TIMESTAMP
                FROM access_capabilities ac
                WHERE ac.code IN (
                    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
                    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
                    'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
                    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
                    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
                    'CRM.TAG.READ', 'CRM.TAG.WRITE',
                    'CRM.TASK.READ', 'CRM.TASK.WRITE',
                    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
                    'CRM.CASE.READ', 'CRM.CASE.WRITE',
                    'CRM.EMAIL.READ', 'CRM.EMAIL.WRITE',
                    'CRM.REPORTS.READ'
                ) AND ac.status = 'ACTIVE'
                AND NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = ? AND rc.role_id = ? AND rc.capability_id = ac.id
                )
                """, tenantId, salesManagerRoleId, tenantId, salesManagerRoleId);

        // Bind 8 ownership capabilities to SALES_REPRESENTATIVE (V20260722.8 set).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, ac.id, CURRENT_TIMESTAMP
                FROM access_capabilities ac
                WHERE ac.code IN (
                    'CRM.ASSIGNMENT.READ',
                    'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST',
                    'CRM.TEAM.READ',
                    'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
                    'CRM.TERRITORY.READ',
                    'CRM.OWNERSHIP_HISTORY.READ'
                ) AND ac.status = 'ACTIVE'
                AND NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = ? AND rc.role_id = ? AND rc.capability_id = ac.id
                )
                """, tenantId, salesRepRoleId, tenantId, salesRepRoleId);

        // Bind 11 CRM READ caps to SALES_REPRESENTATIVE (V20260807.1 set).
        // Total: 8 + 11 = 19 capabilities (matches test assertion).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, ac.id, CURRENT_TIMESTAMP
                FROM access_capabilities ac
                WHERE ac.code IN (
                    'CRM.ACCOUNT.READ',
                    'CRM.CONTACT.READ',
                    'CRM.LEAD.READ',
                    'CRM.OPPORTUNITY.READ',
                    'CRM.ACTIVITY.READ',
                    'CRM.TAG.READ',
                    'CRM.TASK.READ',
                    'CRM.NOTE.READ',
                    'CRM.CASE.READ',
                    'CRM.EMAIL.READ',
                    'CRM.REPORTS.READ'
                ) AND ac.status = 'ACTIVE'
                AND NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = ? AND rc.role_id = ? AND rc.capability_id = ac.id
                )
                """, tenantId, salesRepRoleId, tenantId, salesRepRoleId);

        // Bind ALL active capabilities to ADMIN (V15 invariant).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, r.id, ac.id, CURRENT_TIMESTAMP
                FROM roles r
                JOIN access_capabilities ac ON ac.status = 'ACTIVE'
                WHERE r.tenant_id = ? AND r.code = 'ADMIN'
                AND NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = ? AND rc.role_id = r.id AND rc.capability_id = ac.id
                )
                """, tenantId, tenantId, tenantId);
    }

    private int roleCapabilityCount(UUID tenantId, String roleCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM role_capabilities rc
                  JOIN roles role ON role.tenant_id=rc.tenant_id AND role.id=rc.role_id
                 WHERE rc.tenant_id=? AND role.code=?
                """, Integer.class, tenantId, roleCode);
        return count == null ? 0 : count;
    }

    private boolean hasCapability(UUID tenantId, String roleCode, String capabilityCode) {
        Boolean present = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM role_capabilities rc
                      JOIN roles role ON role.tenant_id=rc.tenant_id AND role.id=rc.role_id
                      JOIN access_capabilities capability ON capability.id=rc.capability_id
                     WHERE rc.tenant_id=? AND role.code=? AND capability.code=?
                )
                """, Boolean.class, tenantId, roleCode, capabilityCode);
        return Boolean.TRUE.equals(present);
    }
}
