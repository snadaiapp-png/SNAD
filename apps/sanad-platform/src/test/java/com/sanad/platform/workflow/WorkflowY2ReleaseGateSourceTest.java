package com.sanad.platform.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level regression guard for the Workflow Y2 production schema incident
 * (2026-09-04).
 *
 * <p>Incident summary: Workflow Y2 application code shipped to production while
 * the Y2 migration wave {@code V20260902_1..7} was still PENDING in the
 * production {@code flyway_schema_history} (production schema version stayed at
 * {@code 20260901.1}). The release gate declared "Flyway PASS" because
 * {@code scripts/production/verify-flyway.sh} only asserted migrations from
 * July 2026 and had no knowledge of the Y2 wave, and
 * {@code production-release.yml} never verified the Flyway runtime invariants
 * ({@code FLYWAY_ENABLED=true}, {@code FLYWAY_OUT_OF_ORDER=false}).
 *
 * <p>These tests fail on any tree where the gate loses the Y2 sentinels or the
 * release pipeline loses the Flyway invariant checks — the exact blind spot
 * that let the incident reach production.
 */
class WorkflowY2ReleaseGateSourceTest {

    private static final Path MODULE_DIR = Path.of("").toAbsolutePath();
    private static final Path REPO_ROOT = MODULE_DIR.getParent().getParent();

    /** User-mandated Y2 sentinels that must never disappear from the gate. */
    private static final String[] FLYWAY_SCRIPT_SENTINELS = {
            // Y2 definition graph metadata on workflow_definitions
            "definition_family_id",
            // Fine-grained Y2 capability catalog rows
            "WORKFLOW.TASK_EXECUTE",
            "WORKFLOW.MONITOR",
            "WORKFLOW.INCIDENT_MANAGE",
            // Central Y2 execution tables
            "workflow_work_items",
            "workflow_incidents",
            // Reliable events (transactional outbox + idempotent inbox)
            "workflow_event_outbox",
            "workflow_event_inbox",
    };

    private static String readRepoFile(String relativePath) throws IOException {
        Path path = REPO_ROOT.resolve(relativePath);
        assertThat(path).as("repository file %s must exist", relativePath).exists();
        return Files.readString(path);
    }

    @Test
    void verifyFlywayScriptGuardsEveryY2Sentinel() throws IOException {
        String script = readRepoFile("scripts/production/verify-flyway.sh");
        for (String sentinel : FLYWAY_SCRIPT_SENTINELS) {
            assertThat(script)
                    .as("scripts/production/verify-flyway.sh must guard the Y2 sentinel '%s' "
                            + "so a pending Y2 migration wave can never again be reported as Flyway PASS",
                            sentinel)
                    .contains(sentinel);
        }
    }

    @Test
    void verifyFlywayScriptFailsOnPendingAndIncompleteHistory() throws IOException {
        String script = readRepoFile("scripts/production/verify-flyway.sh");
        // The gate must detect pending/incomplete history, not only failed rows:
        // the incident shipped with 0 failed migrations while 7 Y2 rows were Pending.
        assertThat(script)
                .as("verify-flyway.sh must fail when any migration is still pending")
                .contains("pending");
        assertThat(script)
                .as("verify-flyway.sh must compare the production schema version "
                        + "against the newest migration shipped in the repository")
                .contains("max(version)");
    }

    @Test
    void productionReleaseGateEnforcesFlywayRuntimeInvariants() throws IOException {
        String workflow = readRepoFile(".github/workflows/production-release.yml");
        assertThat(workflow)
                .as("production-release.yml must explicitly verify FLYWAY_ENABLED=true "
                        + "in the normal release path (incident: runtime Flyway was disabled "
                        + "while the blueprint claimed true)")
                .contains("FLYWAY_ENABLED");
        assertThat(workflow)
                .as("production-release.yml must explicitly verify FLYWAY_OUT_OF_ORDER=false "
                        + "in the normal release path")
                .contains("FLYWAY_OUT_OF_ORDER");
        assertThat(workflow)
                .as("production-release.yml must read the live service configuration "
                        + "from the deployment provider, not from the blueprint file")
                .contains("env-vars");
    }

    @Test
    void reconciliationMigrationIsForwardOnlyAboveProductionHead() throws IOException {
        // The reconciliation migration must be forward-only: never edit a
        // historical migration; add one above both the production head
        // (20260901.1) and the repository head (20260902.7).
        String reconciliation = readRepoFile(
                "apps/sanad-platform/src/main/resources/db/migration/"
                        + "V20260904_1__workflow_y2_production_reconciliation.sql");
        assertThat(reconciliation).isNotBlank();
        assertThat(reconciliation)
                .as("reconciliation migration must be documented as forward-only")
                .contains("forward-only");
        // Every DDL statement must be re-runnable: the file is executed again
        // by the regression suite against an already-reconciled schema.
        assertThat(reconciliation)
                .as("reconciliation must be idempotent by construction (IF NOT EXISTS guards)")
                .contains("IF NOT EXISTS");
        // It must complete the full Y2 capability catalog, not only the schema.
        assertThat(reconciliation)
                .as("reconciliation must seed the 13 Y2 capabilities for all tenants' ADMIN role")
                .contains("WORKFLOW.SELF_APPROVAL_OVERRIDE");
    }
}
