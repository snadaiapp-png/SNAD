package com.sanad.platform.crm.web;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL acceptance coverage for the CRM-008B foundation after CRM-009
 * extends the platform migration chain. The tests retain the fail-closed,
 * preservation, JSONB, partial-index and clean-install guarantees while
 * distinguishing the CRM-008B terminal migration from the platform head.
 */
@Testcontainers
class Crm008bFoundationAcceptanceTest {

    private static final String CRM_008B_ASSIGNMENT_RULES_VERSION = "20260722.4";
    private static final String CRM_008B_ASSIGNMENTS_VERSION = "20260722.5";
    private static final String CRM_008B_COUNTERS_VERSION = "20260722.9";
    private static final String CRM_PLATFORM_HEAD_VERSION = "20260723.1";

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void requireDocker() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Docker required for CRM PostgreSQL acceptance tests");
    }

    @Test
    void crm008bBackfillPreservesLegacyAssignment() {
        Flyway upToRules = flyway(MigrationVersion.fromVersion(CRM_008B_ASSIGNMENT_RULES_VERSION));
        upToRules.clean();
        upToRules.migrate();
        JdbcTemplate jdbc = jdbc();
        seedTenant(jdbc);
        jdbc.update("INSERT INTO crm_assignments " +
                        "(id, tenant_id, version, subject_type, subject_id, assigned_user_id, " +
                        "assignment_role, status, starts_at, created_by, updated_by, created_at, updated_at) " +
                        "VALUES (?, ?, 0, 'ACCOUNT', ?, ?, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, USER_ID, USER_ID, USER_ID);

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=? " +
                        "AND subject_type=record_type AND subject_id=record_id " +
                        "AND assigned_user_id=owner_user_id AND owner_type='USER'",
                Long.class, TENANT_ID)).isEqualTo(1L);
    }

    @Test
    void crm008bBackfillFailsClosedAndRollsBack() {
        Flyway upToRules = flyway(MigrationVersion.fromVersion(CRM_008B_ASSIGNMENT_RULES_VERSION));
        upToRules.clean();
        upToRules.migrate();
        JdbcTemplate jdbc = jdbc();
        seedTenant(jdbc);
        jdbc.execute("ALTER TABLE crm_assignments ALTER COLUMN subject_id DROP NOT NULL");
        jdbc.update("INSERT INTO crm_assignments " +
                        "(id, tenant_id, version, subject_type, subject_id, assigned_user_id, assignment_role, status, starts_at, created_by, updated_by, created_at, updated_at) " +
                        "VALUES (?, ?, 0, 'ACCOUNT', NULL, ?, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), TENANT_ID, USER_ID, USER_ID, USER_ID);

        Flyway target = flyway(MigrationVersion.fromVersion(CRM_008B_ASSIGNMENTS_VERSION));
        assertThatThrownBy(target::migrate).hasMessageContaining("backfill");
        assertThat(tableExists(jdbc, "crm_ownership_history")).isFalse();
        assertThat(columnExists(jdbc, "crm_assignments", "owner_type")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",
                Long.class, CRM_008B_ASSIGNMENTS_VERSION)).isZero();
    }

    @Test
    void crm008bTerminalMigrationRemainsInstalledBeforeCrm009Head() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        JdbcTemplate jdbc = jdbc();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",
                Long.class, CRM_008B_COUNTERS_VERSION)).isOne();
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo(CRM_PLATFORM_HEAD_VERSION);
    }

    @Test
    void cleanInstallContainsCrm008bAndCrm009Schema() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        JdbcTemplate jdbc = jdbc();

        List<String> expected = List.of(
                "crm_sales_teams", "crm_team_memberships", "crm_queues", "crm_queue_memberships",
                "crm_territories", "crm_territory_closure", "crm_territory_assignments",
                "crm_assignment_rules", "crm_assignment_rule_versions", "crm_ownership_history",
                "crm_transfer_requests", "crm_transfer_steps", "crm_assignment_rule_counters",
                "crm_integration_requests");
        expected.forEach(table -> assertThat(tableExists(jdbc, table)).as(table).isTrue());

        assertThat(jsonbColumnExists(jdbc, "crm_team_memberships", "metadata")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_transfer_requests", "record_ids")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_integration_requests", "payload")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_integration_requests", "result_payload")).isTrue();
        assertThat(indexExists(jdbc, "crm_integration_tenant_status_idx")).isTrue();
        assertThat(indexExists(jdbc, "crm_integration_correlation_idx")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=FALSE", Long.class)).isZero();
    }

    @Test
    void partialUniqueIndexesRetainGovernedPredicates() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        JdbcTemplate jdbc = jdbc();
        assertThat(indexPredicateContains(jdbc, "crm_team_memberships", "uk_team_memberships_active", "status = 'ACTIVE'")).isTrue();
        assertThat(indexPredicateContains(jdbc, "crm_queue_memberships", "uk_queue_memberships_active", "status = 'ACTIVE'")).isTrue();
        assertThat(indexPredicateContains(jdbc, "crm_assignments", "uk_assignments_active_per_record", "status = 'ACTIVE'")).isTrue();
    }

    private Flyway flyway(MigrationVersion target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(false);
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(ds);
    }

    private void seedTenant(JdbcTemplate jdbc) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE id=?", Long.class, TENANT_ID) == 0) {
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                            "VALUES (?, 'Test Tenant', 'test-tenant-crm008b', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    TENANT_ID);
        }
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                Long.class, table) == 1L;
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND column_name=?",
                Long.class, table, column) == 1L;
    }

    private boolean jsonbColumnExists(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND column_name=? AND data_type='jsonb' AND udt_name='jsonb'",
                Long.class, table, column) == 1L;
    }

    private boolean indexExists(JdbcTemplate jdbc, String indexName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' AND indexname=?",
                Long.class, indexName) == 1L;
    }

    private boolean indexPredicateContains(JdbcTemplate jdbc, String table, String indexName, String fragment) {
        String predicate = jdbc.queryForObject(
                "SELECT pg_get_expr(i.indpred, i.indrelid) FROM pg_index i " +
                        "JOIN pg_class c ON c.oid=i.indrelid JOIN pg_class ci ON ci.oid=i.indexrelid " +
                        "JOIN pg_namespace n ON n.oid=c.relnamespace " +
                        "WHERE n.nspname='public' AND c.relname=? AND ci.relname=?",
                String.class, table, indexName);
        if (predicate == null) return false;
        for (String token : fragment.replaceAll("[=()'\"\\s]+", " ").trim().split("\\s+")) {
            if (!token.isEmpty() && !predicate.contains(token)) return false;
        }
        return true;
    }
}
