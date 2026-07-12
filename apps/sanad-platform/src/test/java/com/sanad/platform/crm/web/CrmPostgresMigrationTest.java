package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmPostgresMigrationTest {
    private static final String MAIN_SCHEMA_VERSION = "20260629.2";
    private static final String CRM_CORE_VERSION = "20260702.1";
    private static final String RECONCILER_VERSION = "20260702.2";
    private static final String CRM_COMPLETION_VERSION = "20260702.3";
    /**
     * Stage 08 Sprint 1 — ST8-S1-002 tenant_quota migration.
     * Added in Stage 08 Sprint 1; must be reflected in migration order assertions.
     */
    private static final String TENANT_QUOTA_VERSION = "20260706.1";
    /**
     * Stage 29 verification compatibility — subscription change event migration.
     * This migration is outside the CRM table set but still advances the Flyway schema history.
     */
    private static final String SUBSCRIPTION_CHANGE_EVENTS_VERSION = "20260711.1";
    private static final List<String> CRM_CORE_TABLES = List.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_pipeline_stages", "crm_opportunities", "crm_opportunity_stage_history",
            "crm_activities", "crm_timeline_events", "crm_import_jobs",
            "crm_custom_field_definitions");
    private static final List<String> CRM_COMPLETION_TABLES = List.of(
            "crm_import_files", "crm_import_errors", "crm_custom_field_values");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingPlatformThroughCrmRbacAndCompletion() {
        Flyway baseline = flyway(MigrationVersion.fromVersion(MAIN_SCHEMA_VERSION));
        baseline.clean();
        baseline.migrate();
        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo(MAIN_SCHEMA_VERSION);
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(allCrmTables());
        assertMigration(jdbc, "15", "JDBC", "seed rbac roles and capabilities");

        Flyway upgrade = flyway(null);
        assertThat(Arrays.stream(upgrade.info().pending()).map(MigrationInfo::getVersion))
                .containsExactly(
                        MigrationVersion.fromVersion(CRM_CORE_VERSION),
                        MigrationVersion.fromVersion(RECONCILER_VERSION),
                        MigrationVersion.fromVersion(CRM_COMPLETION_VERSION),
                        MigrationVersion.fromVersion(TENANT_QUOTA_VERSION),
                        MigrationVersion.fromVersion(SUBSCRIPTION_CHANGE_EVENTS_VERSION));
        upgrade.migrate();
        upgrade.validate();
        assertCompletedSchema(jdbc);
    }

    @Test
    void upgradesUnifiedCrmCoreThroughReconciliationAndCompletion() {
        Flyway core = flyway(MigrationVersion.fromVersion(CRM_CORE_VERSION));
        core.clean();
        core.migrate();
        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo(CRM_CORE_VERSION);
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(CRM_CORE_TABLES);
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(CRM_COMPLETION_TABLES);

        Flyway completion = flyway(null);
        assertThat(Arrays.stream(completion.info().pending()).map(MigrationInfo::getVersion))
                .containsExactly(
                        MigrationVersion.fromVersion(RECONCILER_VERSION),
                        MigrationVersion.fromVersion(CRM_COMPLETION_VERSION),
                        MigrationVersion.fromVersion(TENANT_QUOTA_VERSION),
                        MigrationVersion.fromVersion(SUBSCRIPTION_CHANGE_EVENTS_VERSION));
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

    private void assertCompletedSchema(JdbcTemplate jdbc) {
        assertMigration(jdbc, "15", "JDBC", "seed rbac roles and capabilities");
        assertMigration(jdbc, CRM_CORE_VERSION, "SQL", "create unified crm core");
        assertMigration(jdbc, RECONCILER_VERSION, "SQL", "reconcile admin role and capabilities");
        assertMigration(jdbc, CRM_COMPLETION_VERSION, "SQL", "complete crm imports custom fields");
        assertMigration(jdbc, TENANT_QUOTA_VERSION, "SQL", "create tenant quota");
        assertMigration(jdbc, SUBSCRIPTION_CHANGE_EVENTS_VERSION, "SQL", "create subscription change events");
        assertThat(latestVersion(jdbc)).isEqualTo(SUBSCRIPTION_CHANGE_EVENTS_VERSION);
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(allCrmTables());
        assertNoDuplicateVersions(jdbc);
        assertThat(constraintExists(jdbc, "fk_crm_contacts_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_import_files_job_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_import_errors_job_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_custom_field_values_definition_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "ck_crm_custom_field_value_exactly_one")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(18L);
    }

    private List<String> allCrmTables() {
        return java.util.stream.Stream.concat(
                        CRM_CORE_TABLES.stream(), CRM_COMPLETION_TABLES.stream())
                .sorted().toList();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
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
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND type=? AND description=? AND success=TRUE",
                Long.class, version, type, description)).isOne();
    }

    private void assertNoDuplicateVersions(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*) > 1) duplicates",
                Long.class)).isZero();
    }

    private String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private List<String> existingTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'crm_%' ORDER BY table_name",
                String.class);
    }

    private boolean constraintExists(JdbcTemplate jdbc, String constraint) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='public' AND constraint_name=?",
                Long.class, constraint);
        return count != null && count == 1L;
    }
}
