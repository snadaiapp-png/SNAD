package com.sanad.platform.workflow;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow Y2 production schema sentinels.
 *
 * <p>Incident (2026-09-04): Workflow Y2 code went live while the whole Y2
 * migration wave ({@code V20260902_1..7}) was still pending in production —
 * the production schema stayed at {@code 20260901.1}. These sentinels pin the
 * complete Y2 schema/capability surface so that:
 *
 * <ul>
 *   <li>any future migration-chain regression (a Y2 table, column, capability
 *       or ADMIN binding silently missing) fails the suite on real PostgreSQL
 *       (CI runs the Maven Test Suite on PostgreSQL 16);</li>
 *   <li>the forward-only reconciliation migration
 *       {@code V20260904_1__workflow_y2_production_reconciliation.sql} is
 *       executed a second time through the real Flyway engine against an
 *       already-reconciled schema — proving it is a safe no-op verifier for
 *       environments where the wave already applied, and that it stays
 *       re-runnable for the production reconciliation itself.</li>
 * </ul>
 *
 * <p>The tests are read-only with respect to logical data: they never delete
 * or mutate existing rows; the reconciliation re-execution only re-asserts
 * schema objects and inserts missing capability rows (none, on a migrated
 * schema).
 */
@SpringBootTest
class WorkflowY2SchemaSentinelTest {

    private static final String RECONCILIATION_RESOURCE =
            "/db/migration/V20260904_1__workflow_y2_production_reconciliation.sql";

    /** All 12 Y2 tables created by the V20260902 wave + verified by reconciliation. */
    private static final List<String> Y2_TABLES = List.of(
            "workflow_step_transitions",
            "workflow_work_items",
            "workflow_work_item_candidates",
            "workflow_branch_tokens",
            "workflow_business_calendars",
            "workflow_calendar_holidays",
            "workflow_delegations",
            "workflow_execution_attempts",
            "workflow_incidents",
            "workflow_event_inbox",
            "workflow_event_outbox",
            "workflow_notification_intents");

    private static final List<String> Y2_CAPABILITIES = List.of(
            "WORKFLOW.DESIGN", "WORKFLOW.VALIDATE", "WORKFLOW.PUBLISH",
            "WORKFLOW.START", "WORKFLOW.TASK_EXECUTE", "WORKFLOW.REASSIGN",
            "WORKFLOW.DELEGATE", "WORKFLOW.CANCEL", "WORKFLOW.INCIDENT_MANAGE",
            "WORKFLOW.MONITOR", "WORKFLOW.AUDIT_VIEW", "WORKFLOW.BREAK_GLASS",
            "WORKFLOW.SELF_APPROVAL_OVERRIDE");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allY2TablesExist() {
        for (String table : Y2_TABLES) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("Y2 table %s must exist", table).isEqualTo(1);
        }
    }

    @Test
    void y2DefinitionGraphColumnsExist() {
        List<String> columns = List.of(
                "definition_family_id", "engine_generation", "publication_state",
                "published_by", "published_at", "validated_at",
                "definition_checksum", "schema_version");
        for (String column : columns) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'workflow_definitions' "
                            + "AND column_name = ?",
                    Integer.class, column);
            assertThat(count)
                    .as("workflow_definitions.%s (Y2 publication metadata) must exist", column)
                    .isEqualTo(1);
        }
    }

    @Test
    void y2RuntimeContextColumnsExist() {
        List<String> columns = List.of(
                "engine_generation", "definition_family_id", "definition_version_id",
                "parent_instance_id", "trigger_type", "trigger_id", "idempotency_key",
                "causation_id", "context_json", "context_schema_version");
        for (String column : columns) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'workflow_instances' "
                            + "AND column_name = ?",
                    Integer.class, column);
            assertThat(count)
                    .as("workflow_instances.%s (Y2 runtime context) must exist", column)
                    .isEqualTo(1);
        }
    }

    @Test
    void y2ApprovalPolicyColumnsExist() {
        List<String> columns = List.of(
                "requested_from_employee_id", "approval_policy",
                "self_approval_policy", "policy_snapshot");
        for (String column : columns) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'workflow_approval_requests' "
                            + "AND column_name = ?",
                    Integer.class, column);
            assertThat(count)
                    .as("workflow_approval_requests.%s (Y2 approval policy) must exist", column)
                    .isEqualTo(1);
        }
    }

    @Test
    void allThirteenY2CapabilitiesAreActiveAndBoundToAdmin() {
        for (String code : Y2_CAPABILITIES) {
            Integer active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM access_capabilities WHERE code = ? AND status = 'ACTIVE'",
                    Integer.class, code);
            assertThat(active)
                    .as("capability %s must exist and be ACTIVE", code)
                    .isEqualTo(1);
        }

        // Every tenant's ADMIN role must be bound to every Y2 capability
        // (platform invariant: ADMIN gets all active capabilities).
        Integer missingBindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants t "
                        + "JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN' "
                        + "JOIN access_capabilities ac ON ac.status = 'ACTIVE' "
                        + "   AND ac.code IN ('WORKFLOW.DESIGN','WORKFLOW.VALIDATE',"
                        + "'WORKFLOW.PUBLISH','WORKFLOW.START','WORKFLOW.TASK_EXECUTE',"
                        + "'WORKFLOW.REASSIGN','WORKFLOW.DELEGATE','WORKFLOW.CANCEL',"
                        + "'WORKFLOW.INCIDENT_MANAGE','WORKFLOW.MONITOR',"
                        + "'WORKFLOW.AUDIT_VIEW','WORKFLOW.BREAK_GLASS',"
                        + "'WORKFLOW.SELF_APPROVAL_OVERRIDE') "
                        + "WHERE NOT EXISTS (SELECT 1 FROM role_capabilities rc "
                        + "  WHERE rc.tenant_id = t.id AND rc.role_id = r.id "
                        + "  AND rc.capability_id = ac.id)",
                Integer.class);
        assertThat(missingBindings)
                .as("every tenant ADMIN must hold every one of the 13 Y2 capabilities")
                .isEqualTo(0);
    }

    @Test
    void tenantIsolationPoliciesExistOnY2Tables() {
        for (String table : Y2_TABLES) {
            Integer policies = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_policies "
                            + "WHERE schemaname = 'public' AND tablename = ? "
                            + "AND policyname = 'tenant_isolation'",
                    Integer.class, table);
            assertThat(policies)
                    .as("table %s must carry the tenant_isolation RLS policy", table)
                    .isEqualTo(1);
        }
    }

    /**
     * Re-executes the production reconciliation migration through the real
     * Flyway engine against the already-reconciled schema. Proves the file
     * is syntactically valid PostgreSQL, applies cleanly, and is a safe no-op
     * when the Y2 wave is already present — exactly the production
     * reconciliation contract (forward-only, idempotent).
     */
    @Test
    void reconciliationMigrationReexecutesCleanlyOnReconciledSchema() throws IOException {
        String sql = Files.readString(
                Path.of("").toAbsolutePath().resolve("src/main/resources/db/migration")
                        .resolve("V20260904_1__workflow_y2_production_reconciliation.sql"));

        Path singleFileDir = Files.createTempDirectory("wfy2-recon");
        Files.writeString(singleFileDir.resolve(
                "V20260904_1__workflow_y2_production_reconciliation.sql"), sql);

        Flyway flyway = Flyway.configure()
                .dataSource(jdbc.getDataSource())
                .locations("filesystem:" + singleFileDir.toAbsolutePath())
                .table("flyway_y2_reconciliation_guard")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load();

        MigrateResult first = flyway.migrate();
        assertThat(first.success)
                .as("first guarded re-execution of the reconciliation must succeed")
                .isTrue();

        // Re-run after resetting the guarded history: full idempotency proof.
        jdbc.update("DELETE FROM flyway_y2_reconciliation_guard WHERE version = '20260904.1'");
        MigrateResult second = flyway.migrate();
        assertThat(second.success)
                .as("second guarded re-execution must also succeed (idempotent by construction)")
                .isTrue();

        jdbc.update("DROP TABLE IF EXISTS flyway_y2_reconciliation_guard");
        Files.walk(singleFileDir).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }
}
