package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.application.WorkflowSystemActionAdapter;
import com.sanad.platform.workflow.application.WorkflowSystemActionService;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 2 / Task 13 — backoff arithmetic safety (O3).
 *
 * <p>Adversarial invariant: the retry backoff is derived from the CUMULATIVE
 * persisted attempt number, so the arithmetic must stay bounded and safe for
 * any persisted numbering — it must never overflow into a negative delay
 * (uncontrolled IllegalArgumentException), never sleep for unbounded
 * durations, and never let the escaping exception destroy the durability of
 * the attempt records.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowSystemActionBackoffSafetyTest {

    @Autowired
    private WorkflowSystemActionService systemActionService;

    @Autowired
    private WorkflowIncidentService incidentService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID instanceId;
    private UUID stepInstanceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Backoff Safety', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-bs-" + tenantId.toString().substring(0, 8), now, now);
        UUID userId = createUser("bs-user");
        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-BS', 'Backoff Fixture', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId = UUID.randomUUID(), tenantId, definitionId, userId, now, now, now);
        UUID stepDefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'act', 'Act', 'SYSTEM_ACTION', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepDefId, tenantId, definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'act', 'PENDING', 0, ?, ?)
                """, stepInstanceId = UUID.randomUUID(), tenantId, instanceId, stepDefId, now, now);
    }

    @Test
    void backoffRemainsBoundedAndSafeForHighPersistedAttemptNumbers() {
        // Pre-seed a high cumulative attempt numbering on this step instance —
        // the persisted numbering is the backoff's input, so the policy must be
        // safe for any value the attempt store may hold.
        for (int i = 1; i <= 62; i++) {
            jdbc.update("""
                    INSERT INTO workflow_execution_attempts (
                        id, tenant_id, workflow_instance_id, step_instance_id, attempt_number,
                        idempotency_key, outcome, diagnostics, started_at, finished_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', '{}', ?, NOW(), NOW())
                    """, UUID.randomUUID(), tenantId, instanceId, stepInstanceId, i,
                    "seed-" + i, now());
        }

        AtomicInteger calls = new AtomicInteger();
        var alwaysTransient = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "BS-DOWN"; }
            @Override public ActionResult execute(ActionRequest request) {
                calls.incrementAndGet();
                return ActionResult.transientFailure("TIMEOUT");
            }
        };

        long t0 = System.nanoTime();
        var result = systemActionService.execute(tenantId, instanceId, stepInstanceId,
                alwaysTransient, Map.of(), null, null, "bs-" + UUID.randomUUID(), 2);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        // Exhaustion is reported as a controlled failure with a durable incident.
        assertThat(result.success()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.incidentId()).isNotNull();
        assertThat(incidentService.find(tenantId, result.incidentId()).orElseThrow().status())
                .isEqualTo(WorkflowIncident.Status.OPEN);

        // Both attempts of this invocation are durably recorded — an escaping
        // runtime exception must not be able to destroy their durability.
        Integer durable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_execution_attempts
                WHERE step_instance_id = ? AND attempt_number IN (63, 64)
                """, Integer.class, stepInstanceId);
        assertThat(durable).isEqualTo(2);

        // The backoff must never sleep for unbounded durations.
        assertThat(elapsedMs).isLessThan(15_000);
    }

    private Timestamp now() { return Timestamp.from(Instant.now()); }

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenantId, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Backoff User", now, now);
        return id;
    }
}
