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
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

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
    private static final String CRM_TIMELINE_TENANT_LIFECYCLE_VERSION = "20260717.3";
    private static final String BUSINESS_PROCESS_BACKBONE_VERSION = "20260717.4";
    private static final String BUSINESS_PROCESS_RBAC_VERSION = "20260717.5";
    private static final String CRM_G1_EXTENSION_VERSION = "20260717.6";
    private static final String CRM_ADDRESS_COMMUNICATION_VERSION = "20260717.100";
    private static final String CRM_ADDRESS_COMMUNICATION_RBAC_VERSION = "20260717.101";
    private static final String VENDOR_RECONCILE_G1_VERSION = "20260718.1";
    private static final String VENDOR_RECONCILE_TAGS_VERSION = "20260718.2";
    private static final String VENDOR_RECONCILE_CONTACT_REL_VERSION = "20260721.1";
    private static final String VENDOR_RECONCILE_IDEMPOTENCY_VERSION = "20260721.2";
    private static final String CRM_008B_SALES_TEAMS_VERSION = "20260722.1";
    private static final String CRM_008B_QUEUES_VERSION = "20260722.2";
    private static final String CRM_008B_TERRITORIES_VERSION = "20260722.3";
    private static final String CRM_008B_ASSIGNMENT_RULES_VERSION = "20260722.4";
    private static final String CRM_008B_ASSIGNMENTS_VERSION = "20260722.5";
    private static final String CRM_008B_TRANSFER_REQUESTS_VERSION = "20260722.6";
    private static final String CRM_008B_OWNER_COLUMNS_VERSION = "20260722.7";
    private static final String CRM_008B_CAPABILITIES_VERSION = "20260722.8";
    private static final String CRM_008B_COUNTERS_VERSION = "20260722.9";
    private static final String CRM_009_INTEGRATION_VERSION = "20260723.1";
    private static final String CRM_009_COMMAND_EXECUTIONS_VERSION = "20260724.1";
    private static final String CRM_009_COMMAND_ARTIFACTS_VERSION = "20260724.2";
    private static final String CRM_010_INTELLIGENCE_VERSION = "20260729.1";
    private static final String CRM_010_SCORING_MODELS_VERSION = "20260729.2";
    private static final String CRM_008B_TEAM_MGMT_CAPABILITIES_VERSION = "20260728.1";
    private static final String CRM_RLS_ENABLE_VERSION = "20260730.1";
    // CRM-018: V20260730_2 (disable RLS) removed from Flyway forward path
    // under RECOVERY-CRM-022 R1. Terminal migration is V20260802_1 (re-enable).
    private static final String CRM_RLS_RE_ENABLE_VERSION = "20260802.1";
    private static final String CRM_RECONCILE_CUSTOM_FIELD_VERSION = "20260804.1";
    private static final String CRM_SHIFT_TEMPLATES_VERSION = "20260804.2";
    private static final String CRM_SHIFT_ASSIGNMENTS_VERSION = "20260804.3";
    private static final String CRM_STAFF_AVAILABILITY_VERSION = "20260804.4";
    private static final String CRM_STAFF_SKILLS_VERSION = "20260804.5";
    private static final String CRM_CAPACITY_PLANS_VERSION = "20260804.6";
    private static final String CRM_WORKLOAD_ASSIGNMENTS_VERSION = "20260804.7";
    private static final String CRM_SERVICE_ASSIGNMENTS_VERSION = "20260804.8";
    private static final String CRM_CASES_VERSION = "20260804.9";
    private static final String CRM_EMAIL_LOGS_VERSION = "20260805.1";
    private static final String CRM_REPORTING_CAPABILITIES_VERSION = "20260805.2";
    private static final String CRM_PORTAL_CAPABILITIES_VERSION = "20260805.3";
    private static final String CRM_EXECUTIVE_HEALTH_VERSION = "20260806.1";
    private static final String CRM_GRANT_CAPABILITIES_VERSION = "20260807.1";
    private static final String CRM_SEED_DEFAULT_PIPELINE_VERSION = "20260807.2";
    private static final String CRM_CASE_INSENSITIVE_TAG_INDEX_VERSION = "20260807.3";
    private static final String CRM_ACTIVITY_RESULT_VERSION = "20260807.4";
    // G7 Mobile Offline Sync (V20260812.1/2/3)
    private static final String G7_MOBILE_SYNC_TABLES_VERSION = "20260812.1";
    private static final String G7_MOBILE_SYNC_COLUMNS_VERSION = "20260812.2";
    private static final String G7_MOBILE_SYNC_RLS_VERSION = "20260812.3";
    // Mission 01: Control Plane Admin + Module Registry + Capabilities (V20260813.1, V20260814.1/2)
    private static final String MISSION_01_ADMIN_SEED_VERSION = "20260813.1";
    private static final String MISSION_01_MODULE_REGISTRY_VERSION = "20260814.1";
    private static final String MISSION_01_MODULE_CAPABILITIES_VERSION = "20260814.2";
    // Senior Management Operating Layer (V20260815.1/2)
    private static final String MGMT_KPI_ENGINE_VERSION = "20260815.1";
    private static final String MGMT_CAPABILITIES_VERSION = "20260815.2";
    // Senior Management Phase B+C (V20260815.3/4)
    private static final String MGMT_DECISION_RISK_ISSUE_VERSION = "20260815.3";
    private static final String MGMT_PHASE_BC_CAPABILITIES_VERSION = "20260815.4";
    // Senior Management Phase D-G (V20260815.5/6)
    private static final String MGMT_COMMAND_CENTER_VERSION = "20260815.5";
    private static final String MGMT_COMMAND_CENTER_CAPS_VERSION = "20260815.6";
    // SLA fields (V20260815.7)
    private static final String MGMT_SLA_FIELDS_VERSION = "20260815.7";
    private static final String MGMT_ALERT_NULLABLE_VERSION = "20260815.8";
    private static final String MGMT_AUDIT_NULLABLE_VERSION = "20260815.9";
    // Workflow Engine (V20260815.10/11)
    private static final String WORKFLOW_ENGINE_VERSION = "20260815.10";
    private static final String WORKFLOW_CAPABILITIES_VERSION = "20260815.11";
    // Latest migration after Senior Management development
    private static final String LATEST_MIGRATION_VERSION = WORKFLOW_CAPABILITIES_VERSION;

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
    private static final List<String> CRM_G1_REMAINING_TABLES = List.of(
            "crm_assignments", "crm_transfers", "crm_audit_logs", "crm_reports",
            "crm_phone_numbers", "crm_contact_lookup_index");
    private static final List<String> CRM_G1_EXTENSION_TABLES = List.of(
            "crm_tasks", "crm_assignments", "crm_transfers", "crm_notes",
            "crm_audit_logs", "crm_reports", "crm_phone_numbers", "crm_contact_lookup_index");
    private static final List<String> CRM_TAGS_TABLES = List.of("crm_tags", "crm_tag_assignments");
    private static final List<String> CRM_CUSTOMER_MASTER_TABLES = List.of(
            "crm_account_addresses", "crm_account_identifiers", "crm_account_relationships",
            "crm_account_status_history", "crm_account_merge_history");
    private static final List<String> CRM_CONTACT_RELATIONSHIP_TABLES = List.of(
            "crm_contact_relationship_roles", "crm_contact_account_relationships",
            "crm_contact_relationship_history", "crm_contact_ownership_history");
    private static final List<String> CRM_ADDRESS_COMMUNICATION_TABLES = List.of(
            "crm_party_addresses", "crm_party_address_history",
            "crm_communication_policies", "crm_communication_methods",
            "crm_communication_method_history");

    private static final List<String> CRM_008B_NEW_TABLES = List.of(
            "crm_sales_teams", "crm_team_memberships",
            "crm_queues", "crm_queue_memberships",
            "crm_territories", "crm_territory_closure", "crm_territory_assignments",
            "crm_assignment_rules", "crm_assignment_rule_versions",
            "crm_ownership_history",
            "crm_transfer_requests", "crm_transfer_steps",
            "crm_assignment_rule_counters");

    private static final List<String> CRM_009_NEW_TABLES = List.of(
            "crm_integration_requests", "crm_integration_outbox", "crm_integration_decisions",
            "crm_integration_command_executions", "crm_integration_command_artifacts");

    private static final List<String> CRM_010_NEW_TABLES = List.of(
            "crm_customer_scores", "crm_customer_score_history", "crm_scoring_models",
            "crm_customer_segments", "crm_segment_memberships",
            "crm_next_best_actions");

    private static final List<String> CRM_CASES_TABLES = List.of("crm_cases");

    private static final List<String> CRM_EMAIL_LOGS_TABLES = List.of("crm_email_logs");

    private static final List<String> CRM_OWNERSHIP_MGMT_TABLES = List.of(
            "crm_shift_templates", "crm_shift_assignments",
            "crm_staff_availability", "crm_staff_skills",
            "crm_capacity_plans", "crm_workload_assignments",
            "crm_service_assignments");


    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping CrmPostgresMigrationTest. " +
                        "Run with PostgreSQL Direct to exercise PostgreSQL migrations.");
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
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_RBAC_VERSION),
                        MigrationVersion.fromVersion(CRM_TIMELINE_TENANT_LIFECYCLE_VERSION),
                        MigrationVersion.fromVersion(BUSINESS_PROCESS_BACKBONE_VERSION),
                        MigrationVersion.fromVersion(BUSINESS_PROCESS_RBAC_VERSION),
                        MigrationVersion.fromVersion(CRM_G1_EXTENSION_VERSION),
                        MigrationVersion.fromVersion(CRM_ADDRESS_COMMUNICATION_VERSION),
                        MigrationVersion.fromVersion(CRM_ADDRESS_COMMUNICATION_RBAC_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_G1_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_TAGS_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_CONTACT_REL_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_IDEMPOTENCY_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_SALES_TEAMS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_QUEUES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TERRITORIES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_ASSIGNMENT_RULES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TRANSFER_REQUESTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_OWNER_COLUMNS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_COUNTERS_VERSION),
                        MigrationVersion.fromVersion(CRM_009_INTEGRATION_VERSION),
                        MigrationVersion.fromVersion(CRM_009_COMMAND_EXECUTIONS_VERSION),
                        MigrationVersion.fromVersion(CRM_009_COMMAND_ARTIFACTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TEAM_MGMT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_010_INTELLIGENCE_VERSION),
                        MigrationVersion.fromVersion(CRM_010_SCORING_MODELS_VERSION),
                        MigrationVersion.fromVersion(CRM_RLS_ENABLE_VERSION),
                        // V20260730_2 (disable RLS) removed from Flyway forward path
                        // under RECOVERY-CRM-022 R1 — skip to re-enable V20260802_1.
                        MigrationVersion.fromVersion(CRM_RLS_RE_ENABLE_VERSION),
                        MigrationVersion.fromVersion(CRM_RECONCILE_CUSTOM_FIELD_VERSION),
                        MigrationVersion.fromVersion(CRM_SHIFT_TEMPLATES_VERSION),
                        MigrationVersion.fromVersion(CRM_SHIFT_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_STAFF_AVAILABILITY_VERSION),
                        MigrationVersion.fromVersion(CRM_STAFF_SKILLS_VERSION),
                        MigrationVersion.fromVersion(CRM_CAPACITY_PLANS_VERSION),
                        MigrationVersion.fromVersion(CRM_WORKLOAD_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_SERVICE_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_CASES_VERSION),
                        MigrationVersion.fromVersion(CRM_EMAIL_LOGS_VERSION),
                        MigrationVersion.fromVersion(CRM_REPORTING_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_PORTAL_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_EXECUTIVE_HEALTH_VERSION),
                        MigrationVersion.fromVersion(CRM_GRANT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_SEED_DEFAULT_PIPELINE_VERSION),
                        MigrationVersion.fromVersion(CRM_CASE_INSENSITIVE_TAG_INDEX_VERSION),
                        MigrationVersion.fromVersion(CRM_ACTIVITY_RESULT_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_TABLES_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_COLUMNS_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_RLS_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_ADMIN_SEED_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_MODULE_REGISTRY_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_MODULE_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_KPI_ENGINE_VERSION),
                        MigrationVersion.fromVersion(MGMT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_DECISION_RISK_ISSUE_VERSION),
                        MigrationVersion.fromVersion(MGMT_PHASE_BC_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_COMMAND_CENTER_VERSION),
                        MigrationVersion.fromVersion(MGMT_COMMAND_CENTER_CAPS_VERSION),
                        MigrationVersion.fromVersion(MGMT_SLA_FIELDS_VERSION),
                        MigrationVersion.fromVersion(MGMT_ALERT_NULLABLE_VERSION),
                        MigrationVersion.fromVersion(MGMT_AUDIT_NULLABLE_VERSION),
                        MigrationVersion.fromVersion(WORKFLOW_ENGINE_VERSION),
                        MigrationVersion.fromVersion(WORKFLOW_CAPABILITIES_VERSION));
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
                        MigrationVersion.fromVersion(CRM_CONTACT_RELATIONSHIP_RBAC_VERSION),
                        MigrationVersion.fromVersion(CRM_TIMELINE_TENANT_LIFECYCLE_VERSION),
                        MigrationVersion.fromVersion(BUSINESS_PROCESS_BACKBONE_VERSION),
                        MigrationVersion.fromVersion(BUSINESS_PROCESS_RBAC_VERSION),
                        MigrationVersion.fromVersion(CRM_G1_EXTENSION_VERSION),
                        MigrationVersion.fromVersion(CRM_ADDRESS_COMMUNICATION_VERSION),
                        MigrationVersion.fromVersion(CRM_ADDRESS_COMMUNICATION_RBAC_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_G1_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_TAGS_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_CONTACT_REL_VERSION),
                        MigrationVersion.fromVersion(VENDOR_RECONCILE_IDEMPOTENCY_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_SALES_TEAMS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_QUEUES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TERRITORIES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_ASSIGNMENT_RULES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TRANSFER_REQUESTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_OWNER_COLUMNS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_COUNTERS_VERSION),
                        MigrationVersion.fromVersion(CRM_009_INTEGRATION_VERSION),
                        MigrationVersion.fromVersion(CRM_009_COMMAND_EXECUTIONS_VERSION),
                        MigrationVersion.fromVersion(CRM_009_COMMAND_ARTIFACTS_VERSION),
                        MigrationVersion.fromVersion(CRM_008B_TEAM_MGMT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_010_INTELLIGENCE_VERSION),
                        MigrationVersion.fromVersion(CRM_010_SCORING_MODELS_VERSION),
                        MigrationVersion.fromVersion(CRM_RLS_ENABLE_VERSION),
                        // V20260730_2 (disable RLS) removed from Flyway forward path
                        // under RECOVERY-CRM-022 R1 — skip to re-enable V20260802_1.
                        MigrationVersion.fromVersion(CRM_RLS_RE_ENABLE_VERSION),
                        MigrationVersion.fromVersion(CRM_RECONCILE_CUSTOM_FIELD_VERSION),
                        MigrationVersion.fromVersion(CRM_SHIFT_TEMPLATES_VERSION),
                        MigrationVersion.fromVersion(CRM_SHIFT_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_STAFF_AVAILABILITY_VERSION),
                        MigrationVersion.fromVersion(CRM_STAFF_SKILLS_VERSION),
                        MigrationVersion.fromVersion(CRM_CAPACITY_PLANS_VERSION),
                        MigrationVersion.fromVersion(CRM_WORKLOAD_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_SERVICE_ASSIGNMENTS_VERSION),
                        MigrationVersion.fromVersion(CRM_CASES_VERSION),
                        MigrationVersion.fromVersion(CRM_EMAIL_LOGS_VERSION),
                        MigrationVersion.fromVersion(CRM_REPORTING_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_PORTAL_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_EXECUTIVE_HEALTH_VERSION),
                        MigrationVersion.fromVersion(CRM_GRANT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(CRM_SEED_DEFAULT_PIPELINE_VERSION),
                        MigrationVersion.fromVersion(CRM_CASE_INSENSITIVE_TAG_INDEX_VERSION),
                        MigrationVersion.fromVersion(CRM_ACTIVITY_RESULT_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_TABLES_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_COLUMNS_VERSION),
                        MigrationVersion.fromVersion(G7_MOBILE_SYNC_RLS_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_ADMIN_SEED_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_MODULE_REGISTRY_VERSION),
                        MigrationVersion.fromVersion(MISSION_01_MODULE_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_KPI_ENGINE_VERSION),
                        MigrationVersion.fromVersion(MGMT_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_DECISION_RISK_ISSUE_VERSION),
                        MigrationVersion.fromVersion(MGMT_PHASE_BC_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(MGMT_COMMAND_CENTER_VERSION),
                        MigrationVersion.fromVersion(MGMT_COMMAND_CENTER_CAPS_VERSION),
                        MigrationVersion.fromVersion(MGMT_SLA_FIELDS_VERSION),
                        MigrationVersion.fromVersion(MGMT_ALERT_NULLABLE_VERSION),
                        MigrationVersion.fromVersion(MGMT_AUDIT_NULLABLE_VERSION),
                        MigrationVersion.fromVersion(WORKFLOW_ENGINE_VERSION),
                        MigrationVersion.fromVersion(WORKFLOW_CAPABILITIES_VERSION));
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

    /**
     * Regression test for the PostgreSQL catalog-reading bug where
     * information_schema.columns.data_type for a JSONB column was wrongly
     * asserted to be 'USER-DEFINED'. On PostgreSQL 16, the correct values
     * for a JSONB column are:
     *   data_type = 'jsonb'
     *   udt_name  = 'jsonb'
     * This test asserts BOTH fields for all 5 JSONB columns created by
     * V20260722_1..6, and is the canonical guard against future regressions.
     */
    @Test
    void jsonbColumnsHaveExactPostgresCatalogValues() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        JdbcTemplate jdbc = jdbc();

        // The 5 JSONB columns created by V20260722_* migrations.
        // Each row: {table, column, expectedNullable}
        String[][] jsonbColumns = {
                {"crm_team_memberships",          "metadata",          "NO"},   // V20260722_1
                {"crm_territories",               "rule_definition",   "NO"},   // V20260722_3
                {"crm_assignment_rule_versions",  "match_conditions",  "NO"},   // V20260722_4
                {"crm_assignments",               "workflow_result",   "YES"},  // V20260722_5 (nullable)
                {"crm_transfer_requests",         "record_ids",        "NO"},   // V20260722_6
        };

        for (String[] entry : jsonbColumns) {
            String table = entry[0];
            String column = entry[1];
            String expectedNullable = entry[2];

            // Verify data_type = 'jsonb' (the field that was previously mis-asserted as 'USER-DEFINED')
            Long dataTypeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' " +
                            "AND table_name=? AND column_name=? AND data_type='jsonb'",
                    Long.class, table, column);
            assertThat(dataTypeCount)
                    .as("%s.%s data_type must be 'jsonb' (regression: was wrongly asserted as 'USER-DEFINED' before)",
                            table, column)
                    .isEqualTo(1L);

            // Verify udt_name = 'jsonb'
            Long udtNameCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' " +
                            "AND table_name=? AND column_name=? AND udt_name='jsonb'",
                    Long.class, table, column);
            assertThat(udtNameCount)
                    .as("%s.%s udt_name must be 'jsonb'", table, column)
                    .isEqualTo(1L);

            // Verify is_nullable matches expected
            Long nullableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' " +
                            "AND table_name=? AND column_name=? AND is_nullable=?",
                    Long.class, table, column, expectedNullable);
            assertThat(nullableCount)
                    .as("%s.%s is_nullable must be %s", table, column, expectedNullable)
                    .isEqualTo(1L);
        }
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
        assertMigration(jdbc, CRM_TIMELINE_TENANT_LIFECYCLE_VERSION, "SQL", "crm timeline tenant lifecycle");
        assertMigration(jdbc, BUSINESS_PROCESS_BACKBONE_VERSION, "SQL", "create business process e2e backbone");
        assertMigration(jdbc, BUSINESS_PROCESS_RBAC_VERSION, "SQL", "grant business process capabilities");
        assertMigration(jdbc, CRM_G1_EXTENSION_VERSION, "SQL", "create crm g1 extension tables");
        assertMigration(jdbc, CRM_ADDRESS_COMMUNICATION_VERSION, "SQL", "crm addresses communication methods");
        assertMigration(jdbc, CRM_ADDRESS_COMMUNICATION_RBAC_VERSION, "SQL", "crm addresses communication capabilities");
        assertMigration(jdbc, VENDOR_RECONCILE_G1_VERSION, "SQL", "reconcile crm g1 after baseline gap");
        assertMigration(jdbc, VENDOR_RECONCILE_TAGS_VERSION, "SQL", "reconcile crm tags after baseline gap");
        assertMigration(jdbc, VENDOR_RECONCILE_CONTACT_REL_VERSION, "SQL", "reconcile crm contact relationship model after baseline gap");
        assertMigration(jdbc, VENDOR_RECONCILE_IDEMPOTENCY_VERSION, "SQL", "reconcile crm idempotency records after baseline gap");
        assertMigration(jdbc, CRM_008B_SALES_TEAMS_VERSION, "SQL", "create crm sales teams");
        assertMigration(jdbc, CRM_008B_QUEUES_VERSION, "SQL", "create crm queues");
        assertMigration(jdbc, CRM_008B_TERRITORIES_VERSION, "SQL", "create crm territories");
        assertMigration(jdbc, CRM_008B_ASSIGNMENT_RULES_VERSION, "SQL", "create crm assignment rules");
        assertMigration(jdbc, CRM_008B_ASSIGNMENTS_VERSION, "SQL", "upgrade crm assignments and create ownership history");
        assertMigration(jdbc, CRM_008B_TRANSFER_REQUESTS_VERSION, "SQL", "create crm transfer requests");
        assertMigration(jdbc, CRM_008B_OWNER_COLUMNS_VERSION, "SQL", "add owner team queue columns");
        assertMigration(jdbc, CRM_008B_CAPABILITIES_VERSION, "SQL", "seed crm ownership capabilities");
        assertMigration(jdbc, CRM_008B_COUNTERS_VERSION, "SQL", "create crm assignment rule counters");
        assertMigration(jdbc, CRM_009_INTEGRATION_VERSION, "SQL", "create crm integration requests");
        assertMigration(jdbc, CRM_009_COMMAND_EXECUTIONS_VERSION, "SQL", "create crm command executions ledger");
        assertMigration(jdbc, CRM_009_COMMAND_ARTIFACTS_VERSION, "SQL", "create crm command artifacts");
        assertMigration(jdbc, CRM_010_INTELLIGENCE_VERSION, "SQL", "create crm customer intelligence");
        assertMigration(jdbc, CRM_010_SCORING_MODELS_VERSION, "SQL", "seed default scoring models");
        assertMigration(jdbc, CRM_RLS_ENABLE_VERSION, "SQL", "enable crm row level security");
        // V20260730_2 (disable RLS) removed from Flyway forward path under RECOVERY-CRM-022 R1.
        assertMigration(jdbc, CRM_RECONCILE_CUSTOM_FIELD_VERSION, "SQL", "reconcile crm custom field and pipeline audit columns");
        assertMigration(jdbc, CRM_SHIFT_TEMPLATES_VERSION, "SQL", "create crm shift templates");
        assertMigration(jdbc, CRM_SHIFT_ASSIGNMENTS_VERSION, "SQL", "create crm shift assignments");
        assertMigration(jdbc, CRM_STAFF_AVAILABILITY_VERSION, "SQL", "create crm staff availability");
        assertMigration(jdbc, CRM_STAFF_SKILLS_VERSION, "SQL", "create crm staff skills");
        assertMigration(jdbc, CRM_CAPACITY_PLANS_VERSION, "SQL", "create crm capacity plans");
        assertMigration(jdbc, CRM_WORKLOAD_ASSIGNMENTS_VERSION, "SQL", "create crm workload assignments");
        assertMigration(jdbc, CRM_SERVICE_ASSIGNMENTS_VERSION, "SQL", "create crm service assignments");
        assertMigration(jdbc, CRM_CASES_VERSION, "SQL", "create crm cases");
        assertMigration(jdbc, CRM_EMAIL_LOGS_VERSION, "SQL", "create crm email logs");
        assertMigration(jdbc, CRM_REPORTING_CAPABILITIES_VERSION, "SQL", "create crm reporting capabilities");
        assertMigration(jdbc, CRM_PORTAL_CAPABILITIES_VERSION, "SQL", "create crm portal capabilities");
        assertMigration(jdbc, CRM_EXECUTIVE_HEALTH_VERSION, "SQL", "seed executive health capabilities");
        assertMigration(jdbc, CRM_GRANT_CAPABILITIES_VERSION, "SQL", "grant crm capabilities to non admin roles");
        assertMigration(jdbc, CRM_SEED_DEFAULT_PIPELINE_VERSION, "SQL", "seed default pipeline and accounts");
        assertMigration(jdbc, CRM_CASE_INSENSITIVE_TAG_INDEX_VERSION, "SQL", "add case insensitive tag unique index");
        assertMigration(jdbc, CRM_ACTIVITY_RESULT_VERSION, "SQL", "add activity result column and related type check");

        // G7 Mobile Offline Sync migrations
        assertMigration(jdbc, G7_MOBILE_SYNC_TABLES_VERSION, "SQL", "create mobile sync tables");
        assertMigration(jdbc, G7_MOBILE_SYNC_COLUMNS_VERSION, "SQL", "add sync columns to crm entities");
        assertMigration(jdbc, G7_MOBILE_SYNC_RLS_VERSION, "SQL", "force rls mobile sync tables");
        // Mission 01: Control Plane Admin + Module Registry + Capabilities
        assertMigration(jdbc, MISSION_01_ADMIN_SEED_VERSION, "SQL", "seed control plane admin and capabilities");
        assertMigration(jdbc, MISSION_01_MODULE_REGISTRY_VERSION, "SQL", "create module registry");
        assertMigration(jdbc, MISSION_01_MODULE_CAPABILITIES_VERSION, "SQL", "create module capabilities and plan entitlements");

        // Senior Management Operating Layer
        assertMigration(jdbc, MGMT_KPI_ENGINE_VERSION, "SQL", "create senior management kpi engine");
        assertMigration(jdbc, MGMT_CAPABILITIES_VERSION, "SQL", "add executive management capabilities");
        assertMigration(jdbc, MGMT_DECISION_RISK_ISSUE_VERSION, "SQL", "create decision risk issue escalation audit");
        assertMigration(jdbc, MGMT_PHASE_BC_CAPABILITIES_VERSION, "SQL", "add decision risk issue escalation capabilities");
        assertMigration(jdbc, MGMT_COMMAND_CENTER_VERSION, "SQL", "create command center alerts ai");
        assertMigration(jdbc, MGMT_COMMAND_CENTER_CAPS_VERSION, "SQL", "add command center alerts intelligence capabilities");
        assertMigration(jdbc, MGMT_SLA_FIELDS_VERSION, "SQL", "add sla fields");
        assertMigration(jdbc, MGMT_ALERT_NULLABLE_VERSION, "SQL", "make alert created by nullable");
        assertMigration(jdbc, MGMT_AUDIT_NULLABLE_VERSION, "SQL", "make audit actor nullable");
        assertMigration(jdbc, WORKFLOW_ENGINE_VERSION, "SQL", "create workflow engine");
        assertMigration(jdbc, WORKFLOW_CAPABILITIES_VERSION, "SQL", "add workflow capabilities");

        assertThat(latestVersion(jdbc)).isEqualTo(LATEST_MIGRATION_VERSION);
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrderElementsOf(allCrmTables());
        assertNoDuplicateVersions(jdbc);

        // CRM-008B table scope assertions
        // 13 new tables created + 1 existing table upgraded (crm_assignments) = 14 total scope
        for (String table : CRM_008B_NEW_TABLES) {
            assertThat(existingTables(jdbc)).contains(table);
            assertThat(columnExists(jdbc, table, "tenant_id")).as(table + " tenant_id").isTrue();
        }
        // crm_assignments is an existing G1 table that was upgraded, not newly created
        assertThat(existingTables(jdbc)).contains("crm_assignments");
        // Verify owner columns were added to crm_assignments by V20260722_5
        assertThat(columnExists(jdbc, "crm_assignments", "owner_type")).isTrue();
        assertThat(columnExists(jdbc, "crm_assignments", "owner_user_id")).isTrue();
        assertThat(columnExists(jdbc, "crm_assignments", "owner_team_id")).isTrue();
        assertThat(columnExists(jdbc, "crm_assignments", "owner_queue_id")).isTrue();
        assertThat(columnExists(jdbc, "crm_assignments", "record_type")).isTrue();
        assertThat(columnExists(jdbc, "crm_assignments", "record_id")).isTrue();
        // Verify owner columns were added to 6 CRM tables by V20260722_7
        for (String table : List.of("crm_accounts", "crm_contacts", "crm_leads", "crm_opportunities", "crm_activities", "crm_tasks")) {
            assertThat(columnExists(jdbc, table, "owner_team_id")).as(table + " owner_team_id").isTrue();
            assertThat(columnExists(jdbc, table, "owner_queue_id")).as(table + " owner_queue_id").isTrue();
        }
        // Verify 20 CRM-008B capabilities were seeded (17 from V20260722.8 + 3 from V20260728.1)
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.ASSIGNMENT.%' " +
                "OR code LIKE 'CRM.TRANSFER.%' OR code LIKE 'CRM.TEAM.%' OR code LIKE 'CRM.QUEUE.%' " +
                "OR code LIKE 'CRM.TERRITORY.%' OR code LIKE 'CRM.ASSIGNMENT_RULE.%' " +
                "OR code = 'CRM.OWNERSHIP_HISTORY.READ'",
                Long.class)).isEqualTo(20L);

        // CRM-008B JSONB column assertions (PostgreSQL-native invariants)
        assertThat(jsonbColumnExists(jdbc, "crm_team_memberships", "metadata")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_territories", "rule_definition")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_assignment_rule_versions", "match_conditions")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_transfer_requests", "record_ids")).isTrue();
        assertThat(jsonbColumnExists(jdbc, "crm_assignments", "workflow_result")).isTrue();

        // CRM-008B partial unique index assertions (predicates verified via pg_indexes)
        assertThat(partialIndexPredicateMatches(jdbc, "crm_team_memberships",
                "uk_team_memberships_active", "status = 'ACTIVE'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_team_memberships",
                "uk_team_memberships_primary", "status = 'ACTIVE'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_team_memberships",
                "uk_team_memberships_primary", "is_primary = true")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_queue_memberships",
                "uk_queue_memberships_active", "status = 'ACTIVE'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_territory_assignments",
                "uk_territory_assignments_active", "status = 'ACTIVE'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_territory_assignments",
                "uk_territory_assignments_active", "role = 'PRIMARY'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_assignment_rule_versions",
                "uk_rule_versions_active", "status = 'ACTIVE'")).isTrue();
        assertThat(partialIndexPredicateMatches(jdbc, "crm_assignments",
                "uk_assignments_active_per_record", "status = 'ACTIVE'")).isTrue();

        // CRM-008B SALES_MANAGER and SALES_REPRESENTATIVE roles must exist
        // (defined in V20260722_8 for every active tenant)
        Long activeTenantCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE'", Long.class);
        if (activeTenantCount != null && activeTenantCount > 0) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT tenant_id) FROM roles WHERE code = 'SALES_MANAGER' AND status = 'ACTIVE'",
                    Long.class)).isEqualTo(activeTenantCount);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT tenant_id) FROM roles WHERE code = 'SALES_REPRESENTATIVE' AND status = 'ACTIVE'",
                    Long.class)).isEqualTo(activeTenantCount);

            // Verify SALES_MANAGER has 11 capabilities, SALES_REPRESENTATIVE has 8
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role_capabilities rc " +
                    "JOIN roles r ON r.id = rc.role_id AND r.tenant_id = rc.tenant_id " +
                    "JOIN access_capabilities c ON c.id = rc.capability_id " +
                    "WHERE r.code = 'SALES_MANAGER' AND r.status = 'ACTIVE' " +
                    "AND c.code IN (" +
                    "  'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE'," +
                    "  'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE'," +
                    "  'CRM.TEAM.READ','CRM.QUEUE.READ','CRM.QUEUE.CLAIM'," +
                    "  'CRM.TERRITORY.READ','CRM.ASSIGNMENT_RULE.READ'," +
                    "  'CRM.OWNERSHIP_HISTORY.READ')", Long.class))
                    .as("SALES_MANAGER must have 11 CRM-008B capabilities per tenant")
                    .isEqualTo(activeTenantCount * 11L);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role_capabilities rc " +
                    "JOIN roles r ON r.id = rc.role_id AND r.tenant_id = rc.tenant_id " +
                    "JOIN access_capabilities c ON c.id = rc.capability_id " +
                    "WHERE r.code = 'SALES_REPRESENTATIVE' AND r.status = 'ACTIVE' " +
                    "AND c.code IN (" +
                    "  'CRM.ASSIGNMENT.READ'," +
                    "  'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST'," +
                    "  'CRM.TEAM.READ','CRM.QUEUE.READ','CRM.QUEUE.CLAIM'," +
                    "  'CRM.TERRITORY.READ','CRM.OWNERSHIP_HISTORY.READ')", Long.class))
                    .as("SALES_REPRESENTATIVE must have 8 CRM-008B capabilities per tenant")
                    .isEqualTo(activeTenantCount * 8L);
        }

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
        assertThat(constraintExists(jdbc, "fk_crm_phone_numbers_contact_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_contact_lookup_contact_same_tenant")).isTrue();

        CRM_G1_EXTENSION_TABLES.forEach(table ->
                assertThat(columnExists(jdbc, table, "tenant_id")).as(table + " tenant_id").isTrue());
        assertThat(g1TenantForeignKeyCount(jdbc)).isEqualTo(8L);
        assertThat(g1ExplicitIndexCount(jdbc)).isEqualTo(26L);
        assertThat(g1IndexesWithoutTenantPrefix(jdbc)).isZero();

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

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(83L); // 79 previous + 1 reporting + 2 portal + 2 executive health - 1 dedup
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'BUSINESS_PROCESS.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(2L);
    }

    private List<String> allCrmTables() {
        return Stream.of(
                        CRM_CORE_TABLES,
                        CRM_COMPLETION_TABLES,
                        CRM_G2_TABLES,
                        CRM_TASKS_TABLES,
                        CRM_NOTES_TABLES,
                        CRM_G1_REMAINING_TABLES,
                        CRM_TAGS_TABLES,
                        CRM_CUSTOMER_MASTER_TABLES,
                        CRM_CONTACT_RELATIONSHIP_TABLES,
                        CRM_ADDRESS_COMMUNICATION_TABLES,
                        CRM_008B_NEW_TABLES,
                        CRM_009_NEW_TABLES,
                        CRM_010_NEW_TABLES,
                        CRM_OWNERSHIP_MGMT_TABLES,
                        CRM_CASES_TABLES,
                        CRM_EMAIL_LOGS_TABLES)
                .flatMap(List::stream)
                .sorted()
                .toList();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
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

    private long g1TenantForeignKeyCount(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint constraint_row " +
                        "JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid " +
                        "WHERE constraint_row.contype='f' AND constraint_row.confrelid='tenants'::regclass " +
                        "AND table_row.relname IN ('crm_tasks','crm_assignments','crm_transfers','crm_notes'," +
                        "'crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index')",
                Long.class);
        return count == null ? 0L : count;
    }

    private long g1ExplicitIndexCount(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' " +
                        "AND tablename IN ('crm_tasks','crm_assignments','crm_transfers','crm_notes'," +
                        "'crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index') " +
                        "AND indexname LIKE 'idx_crm_%'",
                Long.class);
        return count == null ? 0L : count;
    }

    private long g1IndexesWithoutTenantPrefix(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' " +
                        "AND tablename IN ('crm_tasks','crm_assignments','crm_transfers','crm_notes'," +
                        "'crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index') " +
                        "AND indexname LIKE 'idx_crm_%' AND indexdef NOT LIKE '%(tenant_id,%'",
                Long.class);
        return count == null ? 0L : count;
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

    private boolean jsonbColumnExists(JdbcTemplate jdbc, String table, String column) {
        // PostgreSQL catalog records JSONB columns as:
        //   information_schema.columns.data_type = 'jsonb'
        //   information_schema.columns.udt_name  = 'jsonb'
        // (NOT 'USER-DEFINED' — that is the old value for some other types).
        // Assert BOTH fields to prevent regression of the catalog-reading bug.
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' " +
                        "AND table_name=? AND column_name=? " +
                        "AND data_type='jsonb' AND udt_name='jsonb'",
                Long.class, table, column);
        return count != null && count == 1L;
    }

    private boolean partialIndexPredicateMatches(JdbcTemplate jdbc, String table, String indexName, String predicateFragment) {
        // Use pg_get_expr(pg_index.indpred, pg_index.indrelid) for stable semantic check.
        // pg_indexes.indexdef representation can vary (e.g. 'ACTIVE'::character varying
        // vs 'ACTIVE'::text) between PostgreSQL versions; pg_get_expr returns the
        // canonical predicate expression.
        try {
            String predicate = jdbc.queryForObject(
                    "SELECT pg_get_expr(i.indpred, i.indrelid) " +
                            "FROM pg_index i " +
                            "JOIN pg_class c ON c.oid = i.indrelid " +
                            "JOIN pg_class ci ON ci.oid = i.indexrelid " +
                            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                            "WHERE n.nspname='public' AND c.relname=? AND ci.relname=?",
                    String.class, table, indexName);
            if (predicate == null) return false;
            // Semantic token-based check: extract identifiers and values from the
            // fragment (e.g. "status = 'ACTIVE'" -> ["status", "ACTIVE"]) and
            // verify each token appears in the predicate independently. This is
            // robust to cast representation differences.
            String[] tokens = predicateFragment.replaceAll("[=()'\"\\s]+", " ").trim().split("\\s+");
            for (String token : tokens) {
                if (token.isEmpty()) continue;
                if (!predicate.contains(token)) {
                    return false;
                }
            }
            return true;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return false;
        }
    }
}
