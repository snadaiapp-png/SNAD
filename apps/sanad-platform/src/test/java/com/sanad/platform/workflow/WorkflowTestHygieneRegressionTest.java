package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W.3 TEST-HYGIENE regression — proves that repeated execution of the Task 15
 * notification wiring fixture family (the seeding path named by the Phase W
 * hygiene directive) leaves NO residual tenant/role/capability state in the
 * shared PostgreSQL Direct test database, and therefore cannot contaminate
 * another Workflow migration/capability test.
 *
 * <p>Required protocol (contract W.3), encoded deterministically as two full
 * cycles inside one test method:
 * <pre>
 * RUN_TEST_A (seed + run + sweep) → RUN_TEST_B (capability invariant) → PASS
 * RUN_TEST_A_AGAIN                → RUN_TEST_B_AGAIN                  → PASS
 * </pre></p>
 *
 * <p>TEST_B is the capability-contamination invariant behind
 * {@code WorkflowY2CapabilityMigrationTest.adminCompatibilityMappingCoversEveryY2Capability}:
 * every tenant present in the shared database must hold full ADMIN coverage of
 * the Y2 capability set. A residue tenant without an ADMIN role is exactly the
 * state that breaks capability/migration-family tests; the invariant below
 * fails the hygiene regression the moment such residue survives a sweep.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowTestHygieneRegressionTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private WorkflowExecutionService executionService;
    @Autowired private WorkflowGraphExecutionService graphExecutionService;

    @Test
    void repeatedTask15FixtureExecutionLeavesNoResidueAndNoCapabilityContamination() {
        // Contamination baseline of the shared DB (other suites may legitimately
        // leave ADMIN-less tenants; this regression must only prove that OUR
        // execution adds ZERO new contamination — baseline-delta encoding).
        int baselineWithoutAdminCoverage = countTenantsWithoutAdminCoverage();
        int baselineWithoutCapabilityBindings = countTenantsWithoutCapabilityBindings();

        for (int cycle = 1; cycle <= 2; cycle++) {
            // ---- RUN_TEST_A (cycle N): identical seeding + runtime path ----
            WorkflowNotificationWiringFixtures fixtures =
                    new WorkflowNotificationWiringFixtures(jdbc, transactionManager);

            WorkflowNotificationWiringFixtures.Fixture pool =
                    fixtures.fixture("hygiene-pool-" + cycle, "HUMAN_TASK", true, 2);
            WorkflowNotificationWiringFixtures.Fixture direct =
                    fixtures.fixture("hygiene-direct-" + cycle, "APPROVAL", false, 1);

            runRuntimeCycle(pool);
            runRuntimeCycle(direct);

            // Deterministic owned-fixture cleanup (the permanent fix under test)
            fixtures.sweepCreated();

            // ---- RUN_TEST_B (cycle N): zero residue + capability invariant ----
            assertZeroResidue(pool.tenantId(), cycle);
            assertZeroResidue(direct.tenantId(), cycle);
            assertNoNewCapabilityContamination(baselineWithoutAdminCoverage,
                    baselineWithoutCapabilityBindings, cycle);
        }
    }

    /** Drives the real Y2 start + graph transition path (durable runtime rows). */
    private void runRuntimeCycle(WorkflowNotificationWiringFixtures.Fixture fx) {
        authenticate(fx.tenantId(), fx.actorUserId());
        WorkflowInstance instance = WorkflowInstance.startY2(
                fx.tenantId(), fx.definitionId(), fx.definitionId(), 1,
                "TEST", UUID.randomUUID(), "start", fx.actorUserId(), UUID.randomUUID(),
                "MANUAL", null, null, null, null);
        WorkflowInstance started = executionService.startWorkflow(instance, fx.actorUserId());
        graphExecutionService.advance(fx.tenantId(), started.id(), "SUCCESS", fx.actorUserId());
        SecurityContextHolder.clearContext();
    }

    /** Every swept table must contain zero rows owned by the swept tenant. */
    private void assertZeroResidue(UUID tenantId, int cycle) {
        for (String table : WorkflowTenantFixtureSweeper.SWEPT_TABLES) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?",
                    Integer.class, tenantId);
            assertThat(count)
                    .as("cycle %s: no residual rows in %s for swept tenant %s",
                            cycle, table, tenantId)
                    .isZero();
        }
        Integer tenantRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, tenantId);
        assertThat(tenantRows)
                .as("cycle %s: tenant row %s must be removed", cycle, tenantId)
                .isZero();
    }

    /**
     * After the sweep, the shared DB's capability-contamination surface must be
     * EXACTLY the pre-test baseline — our repeated execution added zero new
     * tenants that would break capability-family assertions (TEST_B encoding).
     */
    private void assertNoNewCapabilityContamination(int baselineWithoutAdminCoverage,
                                                    int baselineWithoutCapabilityBindings,
                                                    int cycle) {
        assertThat(countTenantsWithoutAdminCoverage())
                .as("cycle %s: swept execution must add zero new tenants lacking "
                        + "ADMIN coverage (capability-test contamination guard)", cycle)
                .isEqualTo(baselineWithoutAdminCoverage);
        assertThat(countTenantsWithoutCapabilityBindings())
                .as("cycle %s: swept execution must add zero new tenants lacking "
                        + "capability bindings (capability-test contamination guard)", cycle)
                .isEqualTo(baselineWithoutCapabilityBindings);
    }

    private int countTenantsWithoutAdminCoverage() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenants t
                WHERE NOT EXISTS (
                    SELECT 1 FROM roles r
                    WHERE r.tenant_id = t.id AND r.code = 'ADMIN'
                )
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private int countTenantsWithoutCapabilityBindings() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenants t
                WHERE NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc WHERE rc.tenant_id = t.id
                )
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private void authenticate(UUID tenantId, UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
