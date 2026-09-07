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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 14 — real end-to-end fork/join and cycle-guard semantics.
 *
 * <p>Regression lock for T14-D1 (branch-token join destination), T14-D2 (real
 * arrival completion + waiting pointer routing) and T14-D3 (fail-closed cycle
 * depth bound): drives the REAL graph runtime — fork minting, branch
 * activation, branch completion, join arrival, join grant and graph advance —
 * without manually seeding the branch-token state exercised by the flow.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowParallelEndToEndTest {

    @Autowired private WorkflowGraphExecutionService graph;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID defId;
    private UUID startId;
    private UUID forkId;
    private UUID branchAId;
    private UUID branchBId;
    private UUID joinId;
    private UUID endId;
    private UUID instanceId;

    // START -> FORK -> {branch_a, branch_b} -> JOIN -> END
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                tenantId, "T14E2E", "t14e2e-" + tenantId.toString().substring(0, 8), "ACTIVE", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                userId, tenantId, "t14e2e-" + userId.toString().substring(0, 8) + "@test", "T14E2E", "ACTIVE", "dummy", now, now);
        defId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (id, tenant_id, definition_family_id, code, name, module,
                    version, status, trigger_type, created_by, version_lock, engine_generation,
                    publication_state, schema_version, created_at, updated_at)
                VALUES (?, ?, ?, 'WF-E2E', 'E2E', 'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 'Y2',
                        'PUBLISHED', 1, ?, ?)
                """, defId, tenantId, defId, userId, now, now);
        startId = step("start", "START");
        forkId = step("fork", "PARALLEL_FORK");
        branchAId = step("branch_a", "SYSTEM_ACTION");
        branchBId = step("branch_b", "SYSTEM_ACTION");
        joinId = step("join", "PARALLEL_JOIN");
        endId = step("end", "END");
        transition(startId, forkId, "begin");
        transition(forkId, branchAId, "branch_a");
        transition(forkId, branchBId, "branch_b");
        transition(branchAId, joinId, "a_to_join");
        transition(branchBId, joinId, "b_to_join");
        transition(joinId, endId, "joined");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM workflow_branch_tokens WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_step_instances WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_instances WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_step_transitions WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_steps WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM workflow_definitions WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    @Test
    void forkTokensCarryTheActualJoinStepId() {
        instance("start");
        graph.advance(tenantId, instanceId, "SUCCESS", userId); // START -> FORK
        List<Map<String, Object>> tokens = tokens();
        assertThat(tokens).hasSize(2);
        // every token's join_step_id must reference the PARALLEL_JOIN step the join runtime
        // resolves tokens by (findByJoin) — never the branch's first step
        assertThat(tokens).allSatisfy(t -> assertThat(t.get("join_step_id")).isEqualTo(joinId));
    }

    @Test
    void realForkBranchesJoinAdvancesToEndWithExactlyOneJoinGrant() {
        instance("start");
        // REAL fork
        graph.advance(tenantId, instanceId, "SUCCESS", userId);
        assertThat(tokens()).hasSize(2);
        assertThat(currentStepKey()).isEqualTo("fork");
        // REAL branch_a: activation, execution, arrival (token marked, pointer routed back to fork)
        graph.advance(tenantId, instanceId, "branch_a", userId);
        assertThat(currentStepKey()).isEqualTo("branch_a");
        graph.advance(tenantId, instanceId, null, userId); // branch_a chain -> join arrival
        assertThat(tokenStatus("branch_a")).isEqualTo("COMPLETED");
        assertThat(tokenStatus("branch_b")).isEqualTo("RUNNING");
        assertThat(currentStepKey()).isEqualTo("fork"); // waiting join parks pointer at the fork
        // REAL branch_b: still commandable, completes the join
        graph.advance(tenantId, instanceId, "branch_b", userId);
        assertThat(currentStepKey()).isEqualTo("branch_b");
        graph.advance(tenantId, instanceId, null, userId); // branch_b chain -> join arrival -> grant -> END
        assertThat(tokenStatus("branch_b")).isEqualTo("COMPLETED");
        // join granted exactly once; graph advanced past join to END; instance completed
        Integer joinAttempts = jdbc.queryForObject(
                "SELECT attempt_count FROM workflow_step_instances WHERE tenant_id=? AND workflow_instance_id=? AND step_key='join'",
                Integer.class, tenantId, instanceId);
        assertThat(joinAttempts).isEqualTo(1);
        // END reached and the instance is COMPLETED (complete() nulls the pointer by domain design)
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id=?", String.class, instanceId))
                .isEqualTo("COMPLETED");
        Integer endInstances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_step_instances WHERE tenant_id=? AND workflow_instance_id=? AND step_key='end'",
                Integer.class, tenantId, instanceId);
        assertThat(endInstances).isEqualTo(0); // completion handled by the join grant path
        // no orphan RUNNING tokens
        Integer running = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? AND status='RUNNING'",
                Integer.class, tenantId, instanceId);
        assertThat(running).isEqualTo(0);
        // both branch chains executed exactly one step each
        assertThat(branchCompletions("branch_a")).isEqualTo(1);
        assertThat(branchCompletions("branch_b")).isEqualTo(1);
    }

    @Test
    void partialJoin_doesNotAdvanceUntilLastBranchArrives() {
        instance("start");
        graph.advance(tenantId, instanceId, "SUCCESS", userId);
        graph.advance(tenantId, instanceId, "branch_a", userId);
        graph.advance(tenantId, instanceId, null, userId); // A arrives: 1 of 2
        assertThat(tokenStatus("branch_a")).isEqualTo("COMPLETED");
        assertThat(tokenStatus("branch_b")).isEqualTo("RUNNING");
        Integer joinAttempts = joinAttempts();
        assertThat(joinAttempts).isEqualTo(0); // JOIN_NOT_COMPLETED
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id=?", String.class, instanceId))
                .isEqualTo("RUNNING"); // INSTANCE_NOT_ADVANCED_PAST_JOIN
        // complete B -> join becomes eligible and the graph completes
        graph.advance(tenantId, instanceId, "branch_b", userId);
        graph.advance(tenantId, instanceId, null, userId);
        assertThat(joinAttempts()).isEqualTo(1); // JOIN_BECOMES_ELIGIBLE, exactly once
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id=?", String.class, instanceId))
                .isEqualTo("COMPLETED");
    }

    @Test
    void duplicateBranchArrival_isIdempotentAndNeverCountsTwice() {
        instance("start");
        graph.advance(tenantId, instanceId, "SUCCESS", userId);
        graph.advance(tenantId, instanceId, "branch_a", userId);
        graph.advance(tenantId, instanceId, null, userId); // first arrival of A: accepted
        assertThat(tokenStatus("branch_a")).isEqualTo("COMPLETED");
        // re-command the same branch: the fork routes to branch_a again, its step is already
        // terminal and the token is already completed — the arrival must be a controlled no-op
        graph.advance(tenantId, instanceId, "branch_a", userId);
        graph.advance(tenantId, instanceId, null, userId);
        assertThat(tokenStatus("branch_a")).isEqualTo("COMPLETED");
        assertThat(joinAttempts()).isEqualTo(0); // duplicate never grants the join
        // duplicate never counts as another branch: the token row stays single + COMPLETED
        Integer tokenRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? AND branch_key='branch_a'",
                Integer.class, tenantId, instanceId);
        assertThat(tokenRows).isEqualTo(1);
        Integer tokenVersion = jdbc.queryForObject(
                "SELECT version FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? AND branch_key='branch_a'",
                Integer.class, tenantId, instanceId);
        assertThat(tokenVersion).isEqualTo(1); // single RUNNING->COMPLETED bump, not corrupted
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRealArrivalsGrantTheJoinExactlyOnce_acrossRounds() throws Exception {
        int rounds = 20;
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int r = 0; r < rounds; r++) {
                instance("start");
                graph.advance(tenantId, instanceId, "SUCCESS", userId);
                // REAL arrival of branch_a first: marks A, creates the join step instance,
                // parks the pointer at the fork — all state produced by the real runtime
                graph.advance(tenantId, instanceId, "branch_a", userId);
                graph.advance(tenantId, instanceId, null, userId);
                UUID joinInstanceId = joinInstanceId();
                // two concurrent branch_b arrivals race the atomic join grant
                CountDownLatch ready = new CountDownLatch(1);
                var grant = (java.util.function.Supplier<Boolean>) () ->
                        graph.grantJoinIfComplete(tenantId, instanceId, joinInstanceId, joinId(), 2, "branch_b");
                Future<Boolean> f1 = pool.submit(() -> { ready.await(); return grant.get(); });
                Future<Boolean> f2 = pool.submit(() -> { ready.await(); return grant.get(); });
                ready.countDown();
                boolean w1 = f1.get(30, TimeUnit.SECONDS);
                boolean w2 = f2.get(30, TimeUnit.SECONDS);
                int winners = (w1 ? 1 : 0) + (w2 ? 1 : 0);
                int completed = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? AND status='COMPLETED'",
                        Integer.class, tenantId, instanceId);
                if (winners != 1 || joinAttempts() != 1 || completed != 2) failures.incrementAndGet();
                cleanupTenantData();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures.get()).as("race failures out of " + rounds).isEqualTo(0);
    }

    @Test
    void failedBranchTokenDoesNotFalselyCompleteTheJoin() {
        instance("start");
        graph.advance(tenantId, instanceId, "SUCCESS", userId);
        // A terminally failed at the token level (runtime marks FAILED on branch failure)
        jdbc.update("UPDATE workflow_branch_tokens SET status='FAILED' WHERE tenant_id=? AND workflow_instance_id=? AND branch_key='branch_a'",
                tenantId, instanceId);
        graph.advance(tenantId, instanceId, "branch_b", userId);
        graph.advance(tenantId, instanceId, null, userId); // B arrives; A is FAILED, not COMPLETED
        assertThat(tokenStatus("branch_b")).isEqualTo("COMPLETED");
        assertThat(joinAttempts()).isEqualTo(0); // FAILED_BRANCH_DOES_NOT_COUNT_AS_COMPLETED
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id=?", String.class, instanceId))
                .isEqualTo("RUNNING"); // JOIN_DOES_NOT_FALSE_ADVANCE
    }

    @Test
    void cycleThroughDeepAncestorChainIsRejected_failClosedAtDepthBound() {
        // A -> (chain of 17 ancestors) -> deep parent calling family A:
        // the bounded ancestor walk must FAIL CLOSED at its maximum depth, never permit silently
        UUID rootFamily = UUID.randomUUID();
        UUID rootVersion = publishedDefinition(rootFamily, 1);
        UUID prev = null;
        for (int i = 0; i < 17; i++) {
            UUID f = UUID.randomUUID();
            publishedDefinition(f, 1);
            prev = bareInstance(f, prev);
        }
        UUID deepDef = publishedDefinition(UUID.randomUUID(), 1);
        UUID deepStart = existingStart(deepDef);
        UUID deepCall = callStep(deepDef, "call_root", rootFamily, "LATEST", null);
        transition(deepDef, deepStart, deepCall, "call");
        UUID deepParent = y2InstanceAtStart(deepDef, deepStart, prev);
        int before = instanceCount();
        assertThatThrownBy(() -> graph.advance(tenantId, deepParent, "call", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle"); // DEEP_ANCESTOR_CYCLE=REJECTED (fail-closed at bound)
        assertThat(instanceCount()).isEqualTo(before); // NO_CHILD_CREATED
    }

    @Test
    void cycleWithinGuardDepth_isRejected() {
        UUID fa = UUID.randomUUID();
        UUID fb = UUID.randomUUID();
        UUID fc = UUID.randomUUID();
        publishedDefinition(fa, 1);
        publishedDefinition(fb, 1);
        UUID cDef = publishedDefinition(fc, 1);
        UUID cStart = existingStart(cDef);
        UUID cCall = callStep(cDef, "call_a", fa, "LATEST", null);
        transition(cDef, cStart, cCall, "call");
        UUID instA = bareInstance(fa, null);
        UUID instB = bareInstance(fb, instA);
        UUID instC = y2InstanceAtStart(cDef, cStart, instB);
        int before = instanceCount();
        assertThatThrownBy(() -> graph.advance(tenantId, instC, "call", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
        assertThat(instanceCount()).isEqualTo(before);
    }

    @Test
    void deepAcyclicChain_withinGuardDepth_startsChild() {
        UUID prev = null;
        for (int i = 0; i < 5; i++) {
            UUID f = UUID.randomUUID();
            publishedDefinition(f, 1);
            prev = bareInstance(f, prev);
        }
        UUID newFamily = UUID.randomUUID();
        UUID newVersion = publishedDefinition(newFamily, 1);
        UUID lastDef = jdbc.queryForObject(
                "SELECT workflow_definition_id FROM workflow_instances WHERE id=?", UUID.class, prev);
        UUID lastStart = existingStart(lastDef);
        UUID call = callStep(lastDef, "call_new", newFamily, "LATEST", null);
        transition(lastDef, lastStart, call, "call");
        UUID parent = y2InstanceAtStart(lastDef, lastStart, prev);
        graph.advance(tenantId, parent, "call", userId);
        assertThat(jdbc.queryForObject(
                "SELECT definition_version_id FROM workflow_instances WHERE parent_instance_id=?",
                UUID.class, parent)).isEqualTo(newVersion);
    }

    // ===== helpers =====

    private void instance(String stepKey) {
        instanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (id, tenant_id, workflow_definition_id, workflow_version,
                    business_entity_type, business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id, context_json,
                    context_schema_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, ?, 'Y2', ?, ?,
                        CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, defId, stepKey, userId, now, defId, defId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (id, tenant_id, workflow_instance_id, workflow_step_id,
                    step_key, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """, UUID.randomUUID(), tenantId, instanceId, startId, stepKey, now, now);
    }

    private String currentStepKey() {
        return jdbc.queryForObject("SELECT current_step_key FROM workflow_instances WHERE id=?",
                String.class, instanceId);
    }

    private List<Map<String, Object>> tokens() {
        return jdbc.queryForList(
                "SELECT * FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? ORDER BY branch_key",
                tenantId, instanceId);
    }

    private String tokenStatus(String key) {
        return jdbc.queryForObject(
                "SELECT status FROM workflow_branch_tokens WHERE tenant_id=? AND workflow_instance_id=? AND branch_key=?",
                String.class, tenantId, instanceId, key);
    }

    private int branchCompletions(String key) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_step_instances WHERE tenant_id=? AND workflow_instance_id=? AND step_key=? AND status='COMPLETED'",
                Integer.class, tenantId, instanceId, key);
        return n == null ? 0 : n;
    }

    private int joinAttempts() {
        Integer n = jdbc.queryForObject(
                "SELECT attempt_count FROM workflow_step_instances WHERE tenant_id=? AND workflow_instance_id=? AND step_key='join'",
                Integer.class, tenantId, instanceId);
        return n == null ? 0 : n;
    }

    private UUID joinInstanceId() {
        return jdbc.queryForObject(
                "SELECT id FROM workflow_step_instances WHERE tenant_id=? AND workflow_instance_id=? AND step_key='join'",
                UUID.class, tenantId, instanceId);
    }

    private UUID joinId() {
        return joinId;
    }

    private void cleanupTenantData() {
        jdbc.update("DELETE FROM workflow_branch_tokens WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM workflow_step_instances WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM workflow_instances WHERE tenant_id=?", tenantId);
    }

    private UUID publishedDefinition(UUID familyId, int version) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (id, tenant_id, definition_family_id, code, name, module,
                    version, status, trigger_type, created_by, version_lock, engine_generation,
                    publication_state, schema_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Child', 'GENERAL', ?, 'ACTIVE', 'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, id, tenantId, familyId, "WF-C-" + familyId.toString().substring(0, 8), version, userId, now, now);
        jdbc.update("""
                INSERT INTO workflow_steps (id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at)
                VALUES (?, ?, ?, 'start', 'Start', 'START', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, UUID.randomUUID(), tenantId, id, now, now);
        return id;
    }

    private UUID existingStart(UUID def) {
        return jdbc.queryForObject(
                "SELECT id FROM workflow_steps WHERE tenant_id=? AND workflow_definition_id=? AND step_type='START' LIMIT 1",
                UUID.class, tenantId, def);
    }

    private UUID callStep(UUID def, String key, UUID familyId, String mode, UUID pinnedVersion) {
        String config = "PINNED".equals(mode)
                ? "{\"definitionFamilyId\":\"" + familyId + "\",\"versionMode\":\"PINNED\",\"definitionVersionId\":\"" + pinnedVersion + "\"}"
                : "{\"definitionFamilyId\":\"" + familyId + "\",\"versionMode\":\"" + mode + "\"}";
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'CALL_WORKFLOW', 2, CAST(? AS jsonb), 0, ?, ?)
                """, id, tenantId, def, key, key, config, now, now);
        return id;
    }

    private UUID step(String key, String type) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, id, tenantId, defId, key, key, type, now, now);
        return id;
    }

    private void transition(UUID from, UUID to, String key) {
        transition(defId, from, to, key);
    }

    private void transition(UUID def, UUID from, UUID to, String key) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (id, tenant_id, workflow_definition_id, from_step_id,
                    to_step_id, transition_key, outcome, priority, metadata, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, def, from, to, key, now, now);
    }

    private UUID y2InstanceAtStart(UUID definitionId, UUID startStepId, UUID parentInstanceId) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (id, tenant_id, workflow_definition_id, workflow_version,
                    business_entity_type, business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id, parent_instance_id,
                    context_json, context_schema_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'start', ?, ?, 'Y2',
                        (SELECT definition_family_id FROM workflow_definitions WHERE id=?), ?, ?,
                        CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, id, tenantId, definitionId, userId, now, definitionId, definitionId,
                parentInstanceId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (id, tenant_id, workflow_instance_id, workflow_step_id,
                    step_key, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'start', 'PENDING', 0, ?, ?)
                """, UUID.randomUUID(), tenantId, id, startStepId, now, now);
        return id;
    }

    /** Minimal RUNNING Y2 ancestor row for chain tests. */
    private UUID bareInstance(UUID familyId, UUID parentInstanceId) {
        UUID def = jdbc.queryForObject(
                "SELECT id FROM workflow_definitions WHERE tenant_id=? AND definition_family_id=? LIMIT 1",
                UUID.class, tenantId, familyId);
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (id, tenant_id, workflow_definition_id, workflow_version,
                    business_entity_type, business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id, parent_instance_id,
                    context_json, context_schema_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'x', ?, ?, 'Y2', ?, ?, ?,
                        CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, id, tenantId, def, userId, now, familyId, def, parentInstanceId, now, now);
        return id;
    }

    private int instanceCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM workflow_instances WHERE tenant_id=?",
                Integer.class, tenantId);
    }
}
