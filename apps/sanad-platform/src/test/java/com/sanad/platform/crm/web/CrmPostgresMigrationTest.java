package com.sanad.platform.crm.web;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmPostgresMigrationTest {

    private static final String MAIN_SCHEMA_VERSION = "20260629.2";
    private static final String CRM_SCHEMA_VERSION = "20260702.1";
    private static final List<String> CRM_TABLES = List.of(
            "crm_accounts",
            "crm_contacts",
            "crm_leads",
            "crm_pipelines",
            "crm_pipeline_stages",
            "crm_opportunities",
            "crm_opportunity_stage_history",
            "crm_activities",
            "crm_timeline_events",
            "crm_import_jobs",
            "crm_custom_field_definitions"
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_crm_migration")
            .withUsername("sanad")
            .withPassword("sanad_test_only");

    @Test
    void upgradesExistingPlatformSchemaToUnifiedCrmCore() {
        Flyway beforeCrm = flyway(MigrationVersion.fromVersion(MAIN_SCHEMA_VERSION));
        beforeCrm.clean();
        beforeCrm.migrate();

        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo(MAIN_SCHEMA_VERSION);
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(CRM_TABLES);

        Flyway crmUpgrade = flyway(null);
        MigrationInfo[] pending = crmUpgrade.info().pending();
        assertThat(pending).hasSize(1);
        assertThat(pending[0].getVersion()).isEqualTo(MigrationVersion.fromVersion(CRM_SCHEMA_VERSION));

        crmUpgrade.migrate();
        crmUpgrade.validate();

        assertCrmMigrationAppliedExactlyOnce(jdbc);
        assertCrmTables(jdbc);
        assertNoDuplicateVersions(jdbc);
        assertThat(constraintExists(jdbc, "fk_crm_contacts_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_opportunities_pipeline_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_leads_converted_opportunity_same_tenant")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(14L);
    }

    @Test
    void installsUnifiedCrmCoreOnCleanPostgresDatabase() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = jdbc();
        assertCrmMigrationAppliedExactlyOnce(jdbc);
        assertCrmTables(jdbc);
        assertNoDuplicateVersions(jdbc);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
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

    private void assertCrmMigrationAppliedExactlyOnce(JdbcTemplate jdbc) {
        // Stage 05A.2.9.1 — The CRM migration (V20260702_1) must be applied
        // exactly once. It does NOT need to be the latest version — the
        // reconciler migration (V20260702_2) runs after it.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",
                Long.class,
                CRM_SCHEMA_VERSION)).isEqualTo(1L);
    }

    private void assertCrmTables(JdbcTemplate jdbc) {
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(CRM_TABLES);
    }

    private void assertNoDuplicateVersions(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT version
                    FROM flyway_schema_history
                    WHERE version IS NOT NULL
                    GROUP BY version
                    HAVING COUNT(*) > 1
                ) duplicate_versions
                """, Long.class)).isZero();
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
                Long.class,
                constraint);
        return count != null && count == 1L;
    }
}
