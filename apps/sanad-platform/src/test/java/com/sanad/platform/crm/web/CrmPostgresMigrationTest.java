package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canonical PostgreSQL migration regression gate for the CRM schema.
 *
 * The inventory is intentionally explicit: every new CRM migration must extend
 * EXPECTED_VERSIONS and the schema assertions below. This prevents a migration
 * from silently changing the production schema without updating its acceptance
 * evidence.
 */
@Testcontainers
class CrmPostgresMigrationTest {
    private static final String MAIN_SCHEMA_VERSION = "20260629.2";
    private static final String CRM_CORE_VERSION = "20260702.1";
    private static final String CRM_009_INTEGRATION_VERSION = "20260723.1";

    private static final List<String> EXPECTED_VERSIONS = List.of(
            "20260702.1", "20260702.2", "20260702.3",
            "20260706.1", "20260711.1", "20260713.1", "20260713.2",
            "20260716.1", "20260716.2", "20260716.3", "20260716.4",
            "20260717.1", "20260717.2", "20260717.3", "20260717.4",
            "20260717.5", "20260717.6", "20260717.100", "20260717.101",
            "20260718.1", "20260721.1", "20260721.2",
            "20260722.1", "20260722.2", "20260722.3", "20260722.4",
            "20260722.5", "20260722.6", "20260722.7", "20260722.8",
            "20260722.9", CRM_009_INTEGRATION_VERSION);

    private static final List<String> CRM_CORE_TABLES = List.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_pipeline_stages", "crm_opportunities", "crm_opportunity_stage_history",
            "crm_activities", "crm_timeline_events", "crm_import_jobs",
            "crm_custom_field_definitions");

    private static final List<String> CRM_009_TABLES = List.of("crm_integration_requests");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is not available — PostgreSQL migration verification requires Docker");
    }

    @Test
    void upgradesExistingPlatformThroughLatestCrmMigration() {
        Flyway baseline = flyway(MigrationVersion.fromVersion(MAIN_SCHEMA_VERSION));
        baseline.clean();
        baseline.migrate();
        JdbcTemplate jdbc = jdbc();

        assertThat(latestVersion(jdbc)).isEqualTo(MAIN_SCHEMA_VERSION);
        assertMigration(jdbc, "15", "JDBC", "seed rbac roles and capabilities");

        Flyway upgrade = flyway(null);
        assertPendingVersions(upgrade, EXPECTED_VERSIONS);
        upgrade.migrate();
        upgrade.validate();

        assertCompletedSchema(jdbc);
    }

    @Test
    void upgradesUnifiedCrmCoreThroughLatestCrmMigration() {
        Flyway core = flyway(MigrationVersion.fromVersion(CRM_CORE_VERSION));
        core.clean();
        core.migrate();
        JdbcTemplate jdbc = jdbc();

        assertThat(latestVersion(jdbc)).isEqualTo(CRM_CORE_VERSION);
        assertThat(existingTables(jdbc)).containsAll(CRM_CORE_TABLES);
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(CRM_009_TABLES);

        Flyway completion = flyway(null);
        assertPendingVersions(completion, EXPECTED_VERSIONS.subList(1, EXPECTED_VERSIONS.size()));
        completion.migrate();
        completion.validate();

        assertCompletedSchema(jdbc);
    }

    @Test
    void installsCompletedCrmOnCleanPostgresDatabase() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        assertCompletedSchema(jdbc());
    }

    @Test
    void crm009JsonbColumnsHaveNativePostgresTypes() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        JdbcTemplate jdbc = jdbc();

        for (String column : List.of("payload", "result_payload")) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema='public' AND table_name='crm_integration_requests' " +
                            "AND column_name=? AND data_type='jsonb' AND udt_name='jsonb'",
                    Long.class, column)).as("crm_integration_requests.%s must be native JSONB", column)
                    .isEqualTo(1L);
        }
    }

    private void assertCompletedSchema(JdbcTemplate jdbc) {
        assertThat(latestVersion(jdbc)).isEqualTo(CRM_009_INTEGRATION_VERSION);
        assertMigration(jdbc, CRM_009_INTEGRATION_VERSION, "SQL", "create crm integration requests");
        assertNoDuplicateVersions(jdbc);

        assertThat(existingTables(jdbc)).containsAll(CRM_CORE_TABLES);
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(existingTables(jdbc));
        assertThat(tableExists(jdbc, "crm_integration_requests")).isTrue();

        for (String column : List.of(
                "id", "tenant_id", "actor_id", "integration_type", "contract_name",
                "contract_version", "correlation_id", "causation_id", "idempotency_key",
                "source_entity_type", "source_entity_id", "source_entity_version",
                "required_capability", "data_classification", "payload", "result_payload",
                "status", "external_reference", "error_code", "requested_at", "expires_at",
                "completed_at", "created_at", "updated_at")) {
            assertThat(columnExists(jdbc, "crm_integration_requests", column))
                    .as("crm_integration_requests.%s", column).isTrue();
        }

        assertThat(constraintExists(jdbc, "crm_integration_expiry_ck")).isTrue();
        assertThat(constraintExists(jdbc, "crm_integration_tenant_idempotency_uq")).isTrue();
        assertThat(indexExists(jdbc, "crm_integration_tenant_status_idx")).isTrue();
        assertThat(indexExists(jdbc, "crm_integration_correlation_idx")).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code IN ('CRM.WORKFLOW.EXECUTE','CRM.AI.READ') " +
                        "AND status='ACTIVE'", Long.class)).isEqualTo(2L);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(57L);
    }

    private void assertPendingVersions(Flyway flyway, List<String> expected) {
        assertThat(Arrays.stream(flyway.info().pending())
                .map(MigrationInfo::getVersion)
                .map(MigrationVersion::getVersion)
                .toList()).containsExactlyElementsOf(expected);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }

    private void assertMigration(JdbcTemplate jdbc, String version, String type, String description) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history " +
                        "WHERE version=? AND type=? AND description=? AND success=TRUE",
                Long.class, version, type, description)).isOne();
    }

    private void assertNoDuplicateVersions(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history " +
                        "WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*) > 1) duplicates",
                Long.class)).isZero();
    }

    private String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=TRUE " +
                        "AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private List<String> existingTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' " +
                        "AND table_name LIKE 'crm_%' ORDER BY table_name",
                String.class);
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM information_schema.tables " +
                        "WHERE table_schema='public' AND table_name=?", Boolean.class, table);
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM information_schema.columns " +
                        "WHERE table_schema='public' AND table_name=? AND column_name=?",
                Boolean.class, table, column);
    }

    private boolean constraintExists(JdbcTemplate jdbc, String constraint) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM information_schema.table_constraints " +
                        "WHERE constraint_schema='public' AND constraint_name=?",
                Boolean.class, constraint);
    }

    private boolean indexExists(JdbcTemplate jdbc, String index) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM pg_indexes WHERE schemaname='public' AND indexname=?",
                Boolean.class, index);
    }
}
