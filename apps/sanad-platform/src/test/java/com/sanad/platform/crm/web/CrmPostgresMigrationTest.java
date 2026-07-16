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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmPostgresMigrationTest {
    private static final String MAIN_SCHEMA_VERSION = "20260629.2";
    private static final String CRM_CORE_VERSION = "20260702.1";
    private static final String RECONCILER_VERSION = "20260702.2";
    private static final String CRM_COMPLETION_VERSION = "20260702.3";
    private static final String TENANT_QUOTA_VERSION = "20260706.1";
    private static final String SUBSCRIPTION_CHANGE_EVENTS_VERSION = "20260711.1";
    private static final String CRM_IDEMPOTENCY_VERSION = "20260713.1";
    private static final String CRM_PIPELINE_VERSION_COLUMN = "20260713.2";
    private static final String CRM_TASKS_VERSION = "20260716.1";
    private static final String CRM_NOTES_VERSION = "20260716.2";
    private static final String CRM_TAGS_VERSION = "20260716.3";
    private static final String CRM_CUSTOMER_MASTER_VERSION = "20260716.4";
    private static final String CRM_CONTACT_RELATIONSHIP_VERSION = "20260717.1";
    private static final String CRM_CONTACT_RELATIONSHIP_RBAC_VERSION = "20260717.2";

    private static final List<String> CRM_CORE_TABLES = List.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_pipeline_stages", "crm_opportunities", "crm_opportunity_stage_history",
            "crm_activities", "crm_timeline_events", "crm_import_jobs",
            "crm_custom_field_definitions");

    private static final List<String> CRM_COMPLETION_TABLES = List.of(
            "crm_import_files", "crm_import_errors", "crm_custom_field_values");

    private static final List<String> CRM_G2_TABLES = List.of("crm_idempotency_records");
    private static final List<String> CRM_TASKS_TABLES = List.of("crm_tasks");
    private static final List<String> CRM_NOTES_TABLES = List.of("crm_notes");
    private static final List<String> CRM_TAGS_TABLES = List.of("crm_tags", "crm_tag_assignments");
    private static final List<String> CRM_CUSTOMER_MASTER_TABLES = List.of(
            "crm_account_addresses", "crm_account_identifiers", "crm_account_relationships",
            "crm_account_status_history", "crm_account_merge_history");
    private static final List<String> CRM_CONTACT_RELATIONSHIP_TABLES = List.of(
            "crm_contact_relationship_roles", "crm_contact_account_relationships",
            "crm_contact_relationship_history", "crm_contact_ownership_history");

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
                "Docker is not available — skipping CrmPostgresMigrationTest. " +
                        "Run on a CI runner with Docker to exercise PostgreSQL migrations.");
    }

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
                        MigrationVersion.fromVersion(SUBSCRIPTION_CHANGE_EVENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_IDEMPOTENCY_VERSION),
                        MigrationVersion.fromVersion(CRM_PIPELINE_VERSION_COLUMN),
                        MigrationVersion.fromVersion(CRM_TASKS_VERSION),
                        MigrationVersion.fromVersion(CRM_NOTES_VERSION),
                        MigrationVersion.fromVersion(CRM_TAGS_VERSION),
                        MigrationVersion.fromVersion(CRM_CUSTOMER_MASTER_VERSION),
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_VERSION),
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_RBAC_VERSION));
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
        assertThat(existingTables(jdbc)).doesNotContainAnyElementsOf(CRM_G2_TABLES);

        Flyway completion = flyway(null);
        assertThat(Arrays.stream(completion.info().pending()).map(MigrationInfo::getVersion))
                .containsExactly(
                        MigrationVersion.fromVersion(RECONCILER_VERSION),
                        MigrationVersion.fromVersion(CRM_COMPLETION_VERSION),
                        MigrationVersion.fromVersion(TENANT_QUOTA_VERSION),
                        MigrationVersion.fromVersion(SUBSCRIPTION_CHANGE_EVENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_IDEMPOTENCY_VERSION),
                        MigrationVersion.fromVersion(CRM_PIPELINE_VERSION_COLUMN),
                        MigrationVersion.fromVersion(CRM_TASKS_VERSION),
                        MigrationVersion.fromVersion(CRM_NOTES_VERSION),
                        MigrationVersion.fromVersion(CRM_TAGS_VERSION),
                        MigrationVersion.fromVersion(CRM_CUSTOMER_MASTER_VERSION),
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_VERSION),
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_RBAC_VERSION));
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
        assertMigration(jdbc, CRM_IDEMPOTENCY_VERSION, "SQL", "create crm idempotency records");
        assertMigration(jdbc, CRM_PIPELINE_VERSION_COLUMN, "SQL", "add pipeline version column");
        assertMigration(jdbc, CRM_TASKS_VERSION, "SQL", "create crm tasks");
        assertMigration(jdbc, CRM_NOTES_VERSION, "SQL", "create crm notes");
        assertMigration(jdbc, CRM_TAGS_VERSION, "SQL", "create crm tags");
        assertMigration(jdbc, CRM_CUSTOMER_MASTER_VERSION, "SQL", "crm enterprise account customer master");
        assertMigration(jdbc, CRM_CONTACT_RELATIONSHIP_VERSION, "SQL", "crm contact relationship model");
        assertMigration(jdbc, CRM_CONTACT_RELATIONSHIP_RBAC_VERSION, "SQL", "crm contact relationship capabilities");

        assertThat(latestVersion(jdbc)).isEqualTo(CRM_CONTACT_RELATIONSHIP_RBAC_VERSION);
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(allCrmTables());
        assertNoDuplicateVersions(jdbc);

        assertThat(constraintExists(jdbc, "fk_crm_contacts_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_import_files_job_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_import_errors_job_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_custom_field_values_definition_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "ck_crm_custom_field_value_exactly_one")).isTrue();
        assertThat(constraintExists(jdbc, "crm_idempotency_records_unique")).isTrue();
        assertThat(constraintExists(jdbc, "chk_crm_account_relationship_self")).isTrue();
        assertThat(constraintExists(jdbc, "chk_crm_account_merge_distinct")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_contact_relationship_contact_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_contact_relationship_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "uk_crm_contact_account_relationship_primary")).isTrue();
        assertThat(constraintExists(jdbc, "ck_crm_contact_relationship_dates")).isTrue();

        assertThat(columnExists(jdbc, "crm_idempotency_records", "response_headers_json")).isTrue();
        assertThat(columnExists(jdbc, "crm_idempotency_records", "content_type")).isTrue();
        assertThat(columnExists(jdbc, "crm_pipelines", "version")).isTrue();
        assertThat(columnExists(jdbc, "crm_accounts", "legal_name")).isTrue();
        assertThat(columnExists(jdbc, "crm_accounts", "registration_number")).isTrue();
        assertThat(columnExists(jdbc, "crm_accounts", "tax_number")).isTrue();
        assertThat(columnExists(jdbc, "crm_accounts", "data_quality_score")).isTrue();
        assertThat(columnExists(jdbc, "crm_accounts", "merged_into_account_id")).isTrue();
        assertThat(columnExists(jdbc, "crm_account_merge_history", "addresses_moved")).isTrue();
        assertThat(columnExists(jdbc, "crm_account_merge_history", "identifiers_moved")).isTrue();
        assertThat(columnExists(jdbc, "crm_account_merge_history", "relationships_moved")).isTrue();
        assertThat(columnExists(jdbc, "crm_contacts", "legal_name")).isTrue();
        assertThat(columnExists(jdbc, "crm_contacts", "preferred_name")).isTrue();
        assertThat(columnExists(jdbc, "crm_contacts", "middle_name")).isTrue();
        assertThat(columnExists(jdbc, "crm_contacts", "pronouns")).isTrue();
        assertThat(columnExists(jdbc, "crm_contacts", "source")).isTrue();

        // CRM-005 baseline: 24 active CRM capabilities. CRM-006 adds five.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(29L);
    }

    private List<String> allCrmTables() {
        return Stream.of(
                        CRM_CORE_TABLES,
                        CRM_COMPLETION_TABLES,
                        CRM_G2_TABLES,
                        CRM_TASKS_TABLES,
                        CRM_NOTES_TABLES,
                        CRM_TAGS_TABLES,
                        CRM_CUSTOMER_MASTER_TABLES,
                        CRM_CONTACT_RELATIONSHIP_TABLES)
                .flatMap(List::stream)
                .sorted()
                .toList();
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
                "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL " +
                        "GROUP BY version HAVING COUNT(*) > 1) duplicates",
                Long.class)).isZero();
    }

    private String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private List<String> existingTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' " +
                        "AND table_name LIKE 'crm_%' ORDER BY table_name",
                String.class);
    }

    private boolean constraintExists(JdbcTemplate jdbc, String constraint) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE constraint_schema='public' AND constraint_name=?",
                Long.class, constraint);
        return count != null && count == 1L;
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' " +
                        "AND table_name=? AND column_name=?",
                Long.class, table, column);
        return count != null && count == 1L;
    }
}
