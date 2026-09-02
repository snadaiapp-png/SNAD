package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 14 — controlled parallelism primitives and sub-workflows
 * (R3/W3).
 *
 * <p>Proves a fork mints durable branch tokens, that the join grant is
 * atomic under a concurrent race (exactly one arrival wins, attempt_count
 * stays 1), and that CALL_WORKFLOW resolves child versions with parent
 * linkage while rejecting self-cycles before any child instance exists.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowParallelExecutionTest {

    @Autowired
    private WorkflowGraphExecutionService graphExecutionService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID definitionId;
    private UUID startStepId;
    private UUID forkStepId;
    private UUID endStepId;
    private UUID instanceId;

    // START -> FORK (branch_a + branch_b) ; FORK -> JOIN -> END
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Parallel Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-par-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Parallel User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-par-" + userId.toString().substring(0, 8) + "@test", now, now);

        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-PAR', 'Parallel', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);

        startStepId = createStep("start", "START");
        forkStepId = createStep("fork", "PARALLEL_FORK");
        UUID branchA = createStep("branch_a", "SYSTEM_ACTION");
        UUID branchB = createStep("branch_b", "SYSTEM_ACTION");
        UUID joinStepId = createStep("join", "PARALLEL_JOIN");
        endStepId = createStep("end", "END");
        createTransition(startStepId, forkStepId, "begin");
        createTransition(forkStepId, branchA, "branch_a");
        createTransition(forkStepId, branchB, "branch_b");
        createTransition(joinStepId, endStepId, "joined");
    }

    @AfterEach
    void cleanupRaceFixtures() {
        // The NOT_SUPPORTED race test commits its own rows; remove them.
        jdbc.update("DELETE FROM workflow_branch_tokens WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_step_instances WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_instances WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_step_transitions WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_steps WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_definitions WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM hr_employees WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void forkMintsDurableBranchTokens() {
        createY2Instance("start");
        graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId); // START -> FORK

        int tokenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id = ? AND workflow_instance_id = ?",
                Integer.class, tenantId, instanceId);
        assertThat(tokenCount).isEqualTo(2);
        int running = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id = ? "
                        + "AND workflow_instance_id = ? AND status = 'RUNNING'",
                Integer.class, tenantId, instanceId);
        assertThat(running).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentJoinGrantsAdvanceExactlyOnce() throws Exception {
        createY2Instance("start");

        // Prepare the join step instance and two completed branch tokens.
        UUID joinStepId = jdbc.queryForObject(
                "SELECT id FROM workflow_steps WHERE tenant_id = ? AND step_key = 'join'",
                UUID.class, tenantId, definitionId);
        UUID joinInstanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'join', 'PENDING', 0, ?, ?)
                """, joinInstanceId, tenantId, instanceId, joinStepId, now, now);
        for (String key : new String[] {"branch_a", "branch_b"}) {
            jdbc.update("""
                    INSERT INTO workflow_branch_tokens (
                        id, tenant_id, workflow_instance_id, fork_step_instance_id, branch_key,
                        status, join_step_id, version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, 0, ?, ?)
                    """, UUID.randomUUID(), tenantId, instanceId,
                    UUID.randomUUID(), key, joinStepId, now, now);
        }

        // Two concurrent arrivals race the grant; exactly one must win.
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        java.util.function.Supplier<Boolean> grant = () ->
                graphExecutionService.grantJoinIfComplete(tenantId, instanceId,
                        joinInstanceId, joinStepId, 2, "branch_b");
        Future<Boolean> a = pool.submit(() -> {
            ready.await();
            return grant.get();
        });
        Future<Boolean> b = pool.submit(() -> {
            ready.await();
            return grant.get();
        });
        ready.countDown();
        boolean winnerA = a.get(30, TimeUnit.SECONDS);
        boolean winnerB = b.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(winnerA ^ winnerB).isTrue();
        Integer joinAdvances = jdbc.queryForObject(
                "SELECT attempt_count FROM workflow_step_instances WHERE id = ?",
                Integer.class, joinInstanceId);
        assertThat(joinAdvances).isEqualTo(1);
        int completedTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id = ? "
                        + "AND workflow_instance_id = ? AND status = 'COMPLETED'",
                Integer.class, tenantId, instanceId);
        assertThat(completedTokens).isEqualTo(2);
    }

    @Test
    void childWorkflowStartsWithParentLinkAndPinnedVersion() {
        UUID childFamily = UUID.randomUUID();
        UUID childV1 = createDefinition(childFamily, 1);
        UUID childV2 = createDefinition(childFamily, 2);

        UUID callStepId = createCallStep("call_child", childFamily, childV1);
        createTransition(startStepId, callStepId, "call");
        jdbc.update("DELETE FROM workflow_step_transitions WHERE transition_key = 'begin'");

        createY2Instance("start");
        graphExecutionService.advance(tenantId, instanceId, "call", userId);

        Integer childCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND parent_instance_id = ?",
                Integer.class, tenantId, instanceId);
        assertThat(childCount).isEqualTo(1);
        UUID childId = jdbc.queryForObject(
                "SELECT id FROM workflow_instances WHERE tenant_id = ? AND parent_instance_id = ?",
                UUID.class, tenantId, instanceId);
        var child = jdbc.queryForMap("SELECT * FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                tenantId, childId);
        assertThat(child.get("definition_version_id")).isEqualTo(childV1);
        assertThat(child.get("engine_generation")).isEqualTo("Y2");

        // The second version exists but must not be used: instances are pinned.
        assertThat(childV2).isNotEqualTo(child.get("definition_version_id"));
    }

    @Test
    void selfCycleCallIsRejected() {
        UUID ownFamily = jdbc.queryForObject(
                "SELECT definition_family_id FROM workflow_definitions WHERE tenant_id = ? AND id = ?",
                UUID.class, tenantId, definitionId);
        UUID callStepId = createCallStep("call_self", ownFamily, null);
        createTransition(startStepId, callStepId, "call");
        jdbc.update("DELETE FROM workflow_step_transitions WHERE transition_key = 'begin'");

        createY2Instance("start");
        int instancesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ?", Integer.class, tenantId);
        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, instanceId, "call", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
        int instancesAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(instancesAfter).isEqualTo(instancesBefore);
    }

    // ===== fixture helpers =====

    private void createY2Instance(String currentStepKey) {
        instanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?,
                          ?, 'Y2', ?, ?, CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, definitionId, currentStepKey, userId, now,
                definitionId, definitionId, now, now);
        UUID stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'start', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenantId, instanceId, startStepId, now, now);
    }

    private UUID createStep(String stepKey, String stepType) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, stepType, now, now);
        return stepId;
    }

    private UUID createDefinition(UUID familyId, int version) {
        UUID defId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-CHILD', 'Child', 'GENERAL', ?, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, defId, tenantId, familyId, version, userId, now, now);
        UUID start = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'start', 'Start', 'START', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, start, tenantId, defId, now, now);
        return defId;
    }

    private UUID createCallStep(String stepKey, UUID familyId, UUID pinnedVersionId) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        String config = pinnedVersionId != null
                ? "{\"definitionFamilyId\":\"" + familyId + "\",\"versionMode\":\"PINNED\","
                + "\"definitionVersionId\":\"" + pinnedVersionId + "\"}"
                : "{\"definitionFamilyId\":\"" + familyId + "\",\"versionMode\":\"LATEST\"}";
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'CALL_WORKFLOW', 2, CAST(? AS jsonb), 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, config, now, now);
        return stepId;
    }

    private void createTransition(UUID fromStep, UUID toStep, String key) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, definitionId, fromStep, toStep, key, now, now);
    }
}
