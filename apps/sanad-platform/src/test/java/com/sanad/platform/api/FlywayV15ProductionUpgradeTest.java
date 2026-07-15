package com.sanad.platform.api;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayV15ProductionUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Skip gracefully on machines without Docker (e.g. dev Windows boxes).
     * On CI runners with Docker, the test runs normally.
     */
    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping FlywayV15ProductionUpgradeTest. " +
                "Run on a CI runner with Docker to exercise the production upgrade path.");
    }

    @Test
    void preservesProductionDataAndCompletesAdminGrants() {
        Flyway throughV14 = flyway(MigrationVersion.fromVersion("14"));
        throughV14.clean();
        throughV14.migrate();

        JdbcTemplate jdbc = jdbc();
        UUID tenantId = UUID.randomUUID();
        UUID customRoleId = UUID.randomUUID();
        UUID customCapabilityId = UUID.randomUUID();
        UUID customAssignmentId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'Upgrade Sentinel', 'upgrade-sentinel', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId);
        jdbc.update(
                "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'CUSTOM_ROLE', 'Custom Role', 'Must survive upgrade', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                customRoleId, tenantId);
        jdbc.update(
                "INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at) "
                        + "VALUES (?, 'CUSTOM.CAPABILITY', 'Custom Capability', 'Must survive upgrade', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                customCapabilityId);
        jdbc.update(
                "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                customAssignmentId, tenantId, customRoleId, customCapabilityId);

        Flyway throughV15 = flyway(MigrationVersion.fromVersion("15"));
        throughV15.migrate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version='15' AND type='JDBC' "
                        + "AND description='seed rbac roles and capabilities' AND success=TRUE",
                Long.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE tenant_id=? "
                        + "AND code IN ('SUPER_ADMIN','ORG_ADMIN','MANAGER','MEMBER','VIEWER')",
                Long.class, tenantId)).isEqualTo(5L);

        Flyway current = flyway(null);
        current.migrate();
        current.validate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version='20260702.2' AND type='SQL' AND success=TRUE",
                Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?", Long.class, tenantId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE id=?", Long.class, customRoleId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE id=?",
                Long.class, customCapabilityId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_capabilities WHERE id=?",
                Long.class, customAssignmentId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE tenant_id=? AND code='ADMIN'",
                Long.class, tenantId)).isOne();

        Long activeCapabilities = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE status='ACTIVE'",
                Long.class);
        Long adminAssignments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_capabilities assignment "
                        + "JOIN roles role ON role.id=assignment.role_id "
                        + "AND role.tenant_id=assignment.tenant_id "
                        + "WHERE assignment.tenant_id=? AND role.code='ADMIN'",
                Long.class, tenantId);
        assertThat(adminAssignments).isEqualTo(activeCapabilities);

        int historyRows = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        current.migrate();
        current.validate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .isEqualTo(historyRows);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*) > 1) duplicates",
                Long.class)).isZero();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }
}
