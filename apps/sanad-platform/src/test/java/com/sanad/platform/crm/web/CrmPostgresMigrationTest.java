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
    private static final String CRM_SCHEMA_VERSION = "20260702.1";
    private static final String RECONCILER_SCHEMA_VERSION = "20260702.2";
    private static final List<String> CRM_TABLES = List.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_pipeline_stages", "crm_opportunities", "crm_opportunity_stage_history",
            "crm_activities", "crm_timeline_events", "crm_import_jobs",
            "crm_custom_field_definitions"
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingPlatformSchemaThroughCrmAndRbacReconciliation() {
        Flyway baseline = flyway(MigrationVersion.fromVersion(MAIN_SCHEMA_VERSION));
        baseline.clean();
        baseline.migrate();

        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo(MAIN_SCHEMA_VERSION);
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(CRM_TABLES);
        assertMigration(jdbc, "15", "JDBC", "seed rbac roles and capabilities");

        Flyway upgrade = flyway(null);
        // After adding V20260703_x forward migrations, pending includes:
        // CRM (20260702.1), reconciler (20260702.2), and all V20260703_x
        assertThat(Arrays.stream(upgrade.info().pending())
                .map(MigrationInfo::getVersion))
                .contains(MigrationVersion.fromVersion(CRM_SCHEMA_VERSION),
                        MigrationVersion.fromVersion(RECONCILER_SCHEMA_VERSION));

        upgrade.migrate();
        upgrade.validate();

        assertMigration(jdbc, CRM_SCHEMA_VERSION, "SQL", "create unified crm core");
        assertMigration(jdbc, RECONCILER_SCHEMA_VERSION, "SQL", "reconcile admin role and capabilities");
        // Latest version is now V20260703_15 (or higher) after forward migrations
        // The reconciler (V20260702.2) is no longer the latest — it is above CRM but below the new audit/idempotency migrations;
        assertCrmTables(jdbc);
        assertNoDuplicateVersions(jdbc);
        assertThat(constraintExists(jdbc, "fk_crm_contacts_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_opportunities_pipeline_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_leads_converted_opportunity_same_tenant")).isTrue();
    }

    @Test
    void installsCurrentSchemaOnCleanPostgresDatabase() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = jdbc();
        assertMigration(jdbc, "15", "JDBC", "seed rbac roles and capabilities");
        assertMigration(jdbc, CRM_SCHEMA_VERSION, "SQL", "create unified crm core");
        assertMigration(jdbc, RECONCILER_SCHEMA_VERSION, "SQL", "reconcile admin role and capabilities");
        // Latest version is now V20260703_15 (or higher) after forward migrations
        // The reconciler (V20260702.2) is no longer the latest — it is above CRM but below the new audit/idempotency migrations;
        assertCrmTables(jdbc);
        assertNoDuplicateVersions(jdbc);
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

    private void assertMigration(JdbcTemplate jdbc, String version, String type, String description) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND type=? AND description=? AND success=TRUE",
                Long.class, version, type, description)).isOne();
    }

    private void assertCrmTables(JdbcTemplate jdbc) {
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(CRM_TABLES);
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
