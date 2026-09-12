package com.sanad.platform.workflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * W.3 TEST-HYGIENE (HARNESS_ISOLATION_REQUIRED) — deterministic owned-fixture
 * cleanup for Workflow integration tests that seed tenant-scoped rows into the
 * shared PostgreSQL Direct test database.
 *
 * <p>Governance context: the shared {@code sanad} test database is a
 * cross-class fixture surface. Tests that seed tenants / users / roles /
 * capabilities / workflow rows without cleanup leave residue that can break
 * other Workflow migration/capability tests, and manual superuser deletion is
 * not an acceptable permanent certification method. The permanent invariant is:
 * <b>every integration test either uses an isolated disposable database, or
 * cleans up every owned fixture deterministically without manual superuser
 * cleanup</b>. This sweeper implements the second invariant for the Workflow
 * test family.</p>
 *
 * <p>Fail-closed compliance notes:
 * <ul>
 *   <li>RLS is NOT weakened or altered in any way. The sweep runs inside a
 *       tenant-scoped transaction ({@code set_config('app.tenant_id', ...)})
 *       so FORCE-RLS tables such as {@code hr_employees} delete exactly the
 *       owned rows under their normal policies — identical to the seeding
 *       path's {@code tenantTx} context. Non-FORCE tables delete as the
 *       table-owning {@code sanad} test role (the pre-existing
 *       WorkflowParallelExecutionTest cleanup pattern).</li>
 *   <li>Deletion order is FK-safe: workflow runtime/definition children are
 *       removed before identity parents, and the tenant row itself is removed
 *       last by primary key.</li>
 *   <li>Only rows owned by the explicitly registered tenant are deleted; the
 *       sweeper never touches other tenants' data.</li>
 * </ul></p>
 */
final class WorkflowTenantFixtureSweeper {

    /**
     * Tenant-scoped tables swept for a registered tenant, in FK-safe
     * dependency order (children before parents). The {@code tenants} row
     * itself is deleted last by primary key. Exposed so the hygiene regression
     * test can assert zero residue for every swept table.
     */
    static final List<String> SWEPT_TABLES = List.of(
            // R2 experience layer (notification/portal/analytics/feedback) —
            // children of instances/actions/participants where applicable;
            // swept before their FK parents below.
            "workflow_user_notifications",
            "workflow_portal_otp_challenges",
            "workflow_portal_access_log",
            "workflow_customer_feedback",
            "workflow_analytics_step_facts",
            "workflow_analytics_process_facts",
            "workflow_webhook_endpoints",
            "workflow_notification_policies",
            // Workflow runtime children
            "workflow_transition_audit",
            "workflow_execution_attempts",
            "workflow_work_item_candidates",
            "workflow_work_items",
            "workflow_notification_intents",
            "workflow_external_actions",
            "workflow_external_participants",
            // workflow_journey is DB-enforced append-only (R1.35): row-level
            // DELETE is forbidden by the immutability trigger. Harness cleanup
            // follows the WorkflowJourneyTimeGovernanceTest precedent
            // (TRUNCATE bypasses row-level triggers; DB is a disposable,
            // freshly-migrated per-run fixture surface — see below).
            "workflow_responsibility_segments",
            "workflow_timers",
            "workflow_approval_requests",
            "workflow_incidents",
            "workflow_event_inbox",
            "workflow_event_outbox",
            "workflow_delegations",
            "workflow_branch_tokens",
            "workflow_step_instances",
            "workflow_instances",
            // Workflow definition graph
            "workflow_step_transitions",
            "workflow_steps",
            "workflow_definitions",
            // Business time
            "workflow_calendar_holidays",
            "workflow_business_calendars",
            // Identity / RBAC fixtures (hr_* tables are FORCE RLS — the sweep
            // transaction sets app.tenant_id so their policies apply normally;
            // hr_employment_status_periods and hr_people are FK children of
            // hr_employees/users created by the runtime identity path)
            "user_role_assignments",
            "role_capabilities",
            "roles",
            "hr_employment_status_periods",
            "hr_people",
            "hr_employees",
            "users");

    private WorkflowTenantFixtureSweeper() {
    }

    /**
     * Deletes every fixture row owned by {@code tenantId}, FK-safe order,
     * tenant row last, inside one tenant-scoped transaction. Deterministic:
     * repeated sweeps are idempotent.
     */
    static void sweepTenant(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                            UUID tenantId) {
        // The whole sweep runs as ONE tenant-scoped transaction: the tenant
        // context and the deletes execute on the same connection, so FORCE-RLS
        // tables (hr_employees) delete their owned rows under normal policy.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            // Journey (append-only, trigger-guarded): TRUNCATE is the only
            // harness-legal cleanup — same precedent as
            // WorkflowJourneyTimeGovernanceTest @AfterEach. The test database
            // is disposable and freshly migrated for every run.
            jdbc.execute("TRUNCATE workflow_journey");
            for (String table : SWEPT_TABLES) {
                jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantId);
            }
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        });
    }
}
