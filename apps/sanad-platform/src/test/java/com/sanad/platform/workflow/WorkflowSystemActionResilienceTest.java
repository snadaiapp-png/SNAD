package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.application.WorkflowSystemActionAdapter;
import com.sanad.platform.workflow.application.WorkflowSystemActionService;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import com.sanad.platform.workflow.domain.WorkflowSystemActionException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 13 — durable system-action resilience (O3/P3/AF3).
 *
 * <p>Proves transient failures retry with bounded backoff, business
 * validation failures never retry and open incidents, replayed idempotency
 * keys return the prior outcome without re-invoking the adapter, the
 * incident lifecycle enforces its state machine, and compensation failure
 * opens an incident instead of pretending success.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowSystemActionResilienceTest {

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
                        + "VALUES (?, 'System Action Resilience', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-sar-" + tenantId.toString().substring(0, 8), now, now);
        UUID userId = createUser("sar-user");
        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-SAR', 'Resilience Fixture', 'GENERAL', 1, 'ACTIVE',
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
    void transientFailureRetriesButBusinessValidationOpensNoRetryLoop() {
        AtomicInteger calls = new AtomicInteger();
        var flaky = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "FLAKY"; }
            @Override public ActionResult execute(ActionRequest request) {
                return calls.incrementAndGet() < 3
                        ? ActionResult.transientFailure("CONNECTION_RESET")
                        : ActionResult.ok("ext-1", Map.of("done", true));
            }
        };
        var transientResult = systemActionService.execute(tenantId, instanceId, stepInstanceId,
                flaky, Map.of(), null, null, "flaky-" + UUID.randomUUID(), 5);
        assertThat(transientResult.success()).isTrue();
        assertThat(transientResult.attemptCount()).isGreaterThan(1);

        AtomicInteger businessCalls = new AtomicInteger();
        var businessFailing = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "BUSINESS"; }
            @Override public ActionResult execute(ActionRequest request) {
                businessCalls.incrementAndGet();
                return ActionResult.permanentFailure("BUSINESS_VALIDATION");
            }
        };
        assertThatThrownBy(() -> systemActionService.execute(tenantId, instanceId, stepInstanceId,
                businessFailing, Map.of(), null, null, "biz-" + UUID.randomUUID(), 5))
                .isInstanceOf(WorkflowSystemActionException.class)
                .satisfies(e -> assertThat(((WorkflowSystemActionException) e).incidentId()).isNotNull());
        assertThat(businessCalls.get()).isEqualTo(1);
    }

    @Test
    void duplicateSystemActionIsIdempotent() {
        AtomicInteger calls = new AtomicInteger();
        var adapter = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "IDEM"; }
            @Override public ActionResult execute(ActionRequest request) {
                calls.incrementAndGet();
                return ActionResult.ok("ext-same", Map.of());
            }
        };
        String key = "idem-" + UUID.randomUUID();
        var first = systemActionService.execute(tenantId, instanceId, stepInstanceId,
                adapter, Map.of(), null, null, key, 3);
        var replay = systemActionService.execute(tenantId, instanceId, stepInstanceId,
                adapter, Map.of(), null, null, key, 3);
        assertThat(first.success()).isTrue();
        assertThat(replay.success()).isTrue();
        assertThat(replay.attemptCount()).isZero();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retryExhaustionOpensIncident() {
        var alwaysTransient = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "DEAD"; }
            @Override public ActionResult execute(ActionRequest request) {
                return ActionResult.transientFailure("TIMEOUT");
            }
        };
        var result = systemActionService.execute(tenantId, instanceId, stepInstanceId,
                alwaysTransient, Map.of(), null, null, "dead-" + UUID.randomUUID(), 2);
        assertThat(result.success()).isFalse();
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(result.incidentId()).isNotNull();
        assertThat(incidentService.find(tenantId, result.incidentId()).orElseThrow().status())
                .isEqualTo(WorkflowIncident.Status.OPEN);
    }

    @Test
    void incidentLifecycleEnforcesStateMachine() {
        var incident = incidentService.open(tenantId, instanceId, stepInstanceId,
                "TEST", WorkflowIncident.Severity.MEDIUM, "TEST_FAILURE");
        var acknowledged = incidentService.acknowledge(tenantId, incident.id(), UUID.randomUUID());
        assertThat(acknowledged.status()).isEqualTo(WorkflowIncident.Status.ACKNOWLEDGED);
        var resolved = incidentService.resolve(tenantId, incident.id(), UUID.randomUUID(), "fixed by restart");
        assertThat(resolved.status()).isEqualTo(WorkflowIncident.Status.RESOLVED);

        assertThatThrownBy(() -> incidentService.resolve(tenantId, incident.id(), UUID.randomUUID(), "again"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> incidentService.resolve(tenantId, incident.id(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compensationFailureOpensIncidentAndIsIdempotentOnReplay() {
        var failingCompensation = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "VOID"; }
            @Override public ActionResult execute(ActionRequest request) {
                return ActionResult.ok("ext", Map.of());
            }
            @Override public ActionResult compensate(ActionRequest request) {
                return ActionResult.permanentFailure("VOID_REJECTED");
            }
        };
        String key = "comp-" + UUID.randomUUID();
        var result = systemActionService.compensate(tenantId, instanceId, stepInstanceId,
                failingCompensation, Map.of(), key);
        assertThat(result.success()).isFalse();
        assertThat(result.incidentId()).isNotNull();

        var openIncidents = incidentService.findOpen(tenantId, 50);
        assertThat(openIncidents).extracting(i -> i.id()).contains(result.incidentId());

        // Replay of a FAILED compensation key is not silently treated as success.
        var replay = systemActionService.compensate(tenantId, instanceId, stepInstanceId,
                failingCompensation, Map.of(), key);
        assertThat(replay.success()).isFalse();
    }

    @Test
    void nonCompensatableActionSkipsCompensation() {
        var plain = new WorkflowSystemActionAdapter() {
            @Override public String type() { return "PLAIN"; }
            @Override public ActionResult execute(ActionRequest request) {
                return ActionResult.ok("ext", Map.of());
            }
        };
        var result = systemActionService.compensate(tenantId, instanceId, stepInstanceId,
                plain, Map.of(), "plain-" + UUID.randomUUID());
        assertThat(result.failureCategory()).isEqualTo("NOT_COMPENSATABLE");
    }

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenantId, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Resilience User", now, now);
        return id;
    }
}
