package com.sanad.platform.crm.ownership;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmOwnershipRbacPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static final UUID TENANT_A = UUID.fromString("10000000-0000-4000-8000-000000000901");
    private static final UUID TENANT_B = UUID.fromString("20000000-0000-4000-8000-000000000902");

    @BeforeAll
    static void setup() {
        boolean docker;
        try {
            docker = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            docker = false;
        }
        Assumptions.assumeTrue(docker, "Docker required for CRM-008 RBAC PostgreSQL acceptance");

        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .outOfOrder(true)
                .validateOnMigrate(true);

        configuration.target("20260722.7").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        seedTenantAndAdmin(TENANT_A, "rbac-a");
        seedTenantAndAdmin(TENANT_B, "rbac-b");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
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
            assertThat(roleCapabilityCount(tenantId, "SALES_MANAGER")).isEqualTo(11);
            assertThat(roleCapabilityCount(tenantId, "SALES_REPRESENTATIVE")).isEqualTo(8);
            assertThat(roleCapabilityCount(tenantId, "ADMIN")).isEqualTo(17);
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
