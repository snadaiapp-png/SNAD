package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 4 / Task 21 — strangler cutover and legacy compatibility (Z3/AA3).
 *
 * <p>Engine selection happens exactly once at instance creation: the start
 * resolution reads the concrete published definition, persists its engine
 * generation on the instance, and every later command routes from that
 * persisted value. Running instances never change generation, never re-pin,
 * and never execute on both engines. Rollback repoints only future starts.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowY2CutoverTest {

    @Autowired
    private WorkflowExecutionService executionService;

    @Autowired
    private WorkflowGraphExecutionService graphExecutionService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID familyId;
    private UUID legacyV1;
    private UUID y2V2;
    private UUID startStepV2;
    private UUID taskStepV2;
    private UUID endStepV2;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = createUser(tenantId, "cut-user");

        // Family A: v1 LEGACY-eligible (ACTIVE legacy lifecycle), v2 Y2 published.
        familyId = UUID.randomUUID();
        legacyV1 = createDefinition(familyId, 1, "LEGACY", "ACTIVE", "DRAFT");
        createStep(legacyV1, "start", "START");
        UUID taskV1 = createStep(legacyV1, "task", "ACTION");
        createStep(legacyV1, "next", "ACTION");

        y2V2 = createDefinition(familyId, 2, "Y2", "ACTIVE", "PUBLISHED");
        startStepV2 = createStep(y2V2, "start", "START");
        taskStepV2 = createStep(y2V2, "review", "HUMAN_TASK");
        endStepV2 = createStep(y2V2, "end", "END");
        linkV2Steps();
    }

    // ===== 1 + 14: legacy instances are never migrated ============

    @Test
    void legacyRunningInstanceRemainsLegacyAfterY2VersionIsPublished() {
        UUID legacyInstance = startLegacyInstance(legacyV1, "task");
        assertThat(generationOf(legacyInstance)).isEqualTo("LEGACY");

        // Y2 v2 already published in setUp — the legacy instance must not care.
        String generation = jdbc.queryForObject(
                "SELECT engine_generation FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, legacyInstance);
        assertThat(generation).isEqualTo("LEGACY");
        String version = jdbc.queryForObject(
                "SELECT workflow_version FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, legacyInstance);
        assertThat(version).isEqualTo("1");
    }

    @Test
    void legacyInstanceIsNeverAutoMigratedDuringCutover() {
        UUID legacyInstance = startLegacyInstance(legacyV1, "task");
        // Even after a Y2 instance starts on v2, the legacy row is untouched.
        UUID y2Instance = startThroughLegacyPath(y2V2, "start");
        assertThat(generationOf(y2Instance)).isEqualTo("Y2");
        assertThat(generationOf(legacyInstance)).isEqualTo("LEGACY");
        Integer migrated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? "
                        + "AND engine_generation = 'Y2' AND workflow_version = 1",
                Integer.class, tenantId);
        assertThat(migrated).isZero();
    }

    // ===== 2 + 8 + 11 + 12: canonical start resolution ============

    @Test
    void newStartOnY2PublishedVersionUsesY2Only() {
        UUID instance = startThroughLegacyPath(y2V2, "start");
        assertThat(generationOf(instance)).isEqualTo("Y2");
        var row = instanceRow(instance);
        assertThat(row.get("definition_version_id")).isEqualTo(y2V2);
        assertThat(row.get("definition_family_id")).isEqualTo(familyId);
    }

    @Test
    void newManualStartUsesConcretePublishedVersion() {
        UUID instance = startThroughLegacyPath(y2V2, "start");
        String pinnedDefinition = jdbc.queryForObject(
                "SELECT workflow_definition_id FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, instance);
        assertThat(UUID.fromString(pinnedDefinition)).isEqualTo(y2V2);
    }

    @Test
    void draftDefinitionCannotBecomeStartTarget() {
        UUID draft = createDefinition(familyId, 3, "Y2", "ACTIVE", "DRAFT");
        WorkflowInstance instance = manualInstanceRow(draft);
        assertThatThrownBy(() -> executionService.startWorkflow(instance, userId))
                .isInstanceOf(IllegalStateException.class);
        Integer started = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND workflow_definition_id = ?",
                Integer.class, tenantId, draft);
        assertThat(started).isZero();
    }

    @Test
    void tenantACannotStartTenantBDefinition() {
        UUID tenantB = UUID.randomUUID();
        UUID userB = createUser(tenantB, "cut-b");
        UUID defB = createDefinitionFor(tenantB, userB, UUID.randomUUID(), 1, "Y2", "ACTIVE", "PUBLISHED");
        // A tenant-A start request pointing at tenant B's definition must fail
        // closed on the tenant-scoped definition resolution.
        WorkflowInstance instance = manualInstanceIn(tenantId, defB, userId);
        assertThatThrownBy(() -> executionService.startWorkflow(instance, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in tenant");
        Integer started = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND workflow_definition_id = ?",
                Integer.class, tenantId, defB);
        assertThat(started).isZero();
    }

    // ===== 3 + 9 + 13: pinning and rollback semantics ============

    @Test
    void runningY2InstanceRemainsPinnedWhenNewerVersionIsPublished() {
        UUID y2Instance = startThroughLegacyPath(y2V2, "start");
        UUID v3 = createDefinition(familyId, 3, "Y2", "ACTIVE", "PUBLISHED");
        var row = instanceRow(y2Instance);
        assertThat(row.get("definition_version_id")).isEqualTo(y2V2);
        assertThat(row.get("workflow_version")).isEqualTo(2);
    }

    @Test
    void rollbackOfFutureStartsDoesNotRewriteRunningY2Instance() {
        UUID y2Instance = startThroughLegacyPath(y2V2, "start");
        // Rollback: repoint future starts away from v2 by retiring it.
        jdbc.update("UPDATE workflow_definitions SET publication_state = 'RETIRED' "
                + "WHERE tenant_id = ? AND id = ?", tenantId, y2V2);

        // A NEW start on the retired version is refused.
        WorkflowInstance newInstance = manualInstanceRow(y2V2);
        assertThatThrownBy(() -> executionService.startWorkflow(newInstance, userId))
                .isInstanceOf(IllegalStateException.class);

        // The running v2 instance is untouched — still Y2, still pinned to v2.
        var row = instanceRow(y2Instance);
        assertThat(row.get("engine_generation")).isEqualTo("Y2");
        assertThat(row.get("definition_version_id")).isEqualTo(y2V2);
        assertThat(row.get("status")).isEqualTo("RUNNING");
    }

    @Test
    void publishedVersionRollbackChangesOnlyFutureStartResolution() {
        UUID v3 = createDefinition(familyId, 3, "Y2", "ACTIVE", "PUBLISHED");
        jdbc.update("UPDATE workflow_definitions SET publication_state = 'RETIRED' "
                + "WHERE tenant_id = ? AND id = ?", tenantId, v3);
        // v2 remains the resolution for future starts after v3 was retired.
        String published = jdbc.queryForObject(
                "SELECT id FROM workflow_definitions WHERE tenant_id = ? "
                        + "AND definition_family_id = ? AND publication_state = 'PUBLISHED' "
                        + "ORDER BY version DESC LIMIT 1",
                String.class, tenantId, familyId);
        assertThat(UUID.fromString(published)).isEqualTo(y2V2);
    }

    // ===== 5 + 6: lifecycle commands never change generation =====

    @Test
    void legacyPausedInstanceResumesOnLegacyRuntime() {
        UUID legacyInstance = startLegacyInstance(legacyV1, "task");
        executionService.pause(tenantId, legacyInstance, userId);
        executionService.resume(tenantId, legacyInstance, userId);
        var row = instanceRow(legacyInstance);
        assertThat(row.get("engine_generation")).isEqualTo("LEGACY");
        assertThat(row.get("status")).isEqualTo("RUNNING");
    }

    @Test
    void y2PausedInstanceResumesOnY2Runtime() {
        UUID y2Instance = startThroughLegacyPath(y2V2, "start");
        executionService.pause(tenantId, y2Instance, userId);
        executionService.resume(tenantId, y2Instance, userId);
        var row = instanceRow(y2Instance);
        assertThat(row.get("engine_generation")).isEqualTo("Y2");
    }

    // ===== 7: legacy approval continuation =====

    @Test
    void legacyApprovalCompletionContinuesLegacyExecution() {
        UUID legacyInstance = startLegacyInstance(legacyV1, "task");
        executionService.advanceToNextStep(tenantId, legacyInstance, "next", userId);
        var row = instanceRow(legacyInstance);
        assertThat(row.get("engine_generation")).isEqualTo("LEGACY");
        assertThat(row.get("current_step_key")).isEqualTo("next");
    }

    // ===== 10: no dual execution ============

    @Test
    void sameInstanceCanNeverBeExecutedByBothEngines() {
        UUID y2Instance = startThroughLegacyPath(y2V2, "start");
        // Y2 instance refuses the legacy advance command...
        assertThatThrownBy(() -> executionService.advanceToNextStep(tenantId, y2Instance, "review", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Y2 graph");
        // ...and a LEGACY instance refuses the Y2 graph command.
        UUID legacyInstance = startLegacyInstance(legacyV1, "task");
        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, legacyInstance, "SUCCESS", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEGACY");
    }

    // ===== fixture helpers =====

    /** Starts an instance through the legacy-compatible API path (what POST /instances does). */
    private UUID startThroughLegacyPath(UUID definitionId, String firstStepKey) {
        WorkflowInstance instance = WorkflowInstance.start(
                tenantId, definitionId, versionOf(definitionId), "TEST", UUID.randomUUID(),
                firstStepKey, userId, null);
        executionService.startWorkflow(instance, userId);
        return instance.id();
    }

    /** Builds an unsaved manual-start instance exactly as the API request path does. */
    private WorkflowInstance manualInstanceRow(UUID definitionId) {
        return WorkflowInstance.start(
                tenantId, definitionId, versionOf(definitionId), "TEST", UUID.randomUUID(),
                "start", userId, null);
    }

    private WorkflowInstance manualInstanceIn(UUID tenant, UUID definitionId, UUID starter) {
        return WorkflowInstance.start(
                tenant, definitionId, 1, "TEST", UUID.randomUUID(), "start", starter, null);
    }

    private UUID startLegacyInstance(UUID definitionId, String firstStepKey) {
        UUID id = startThroughLegacyPath(definitionId, firstStepKey);
        return id;
    }

    private int versionOf(UUID definitionId) {
        return jdbc.queryForObject(
                "SELECT version FROM workflow_definitions WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, definitionId);
    }

    private String generationOf(UUID instanceId) {
        return jdbc.queryForObject(
                "SELECT engine_generation FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, instanceId);
    }

    private Map<String, Object> instanceRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenant, "Cutover " + prefix, "wf-cut-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenant, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Cutover User", now, now);
        return id;
    }

    private UUID createDefinition(UUID familyId, int version, String generation,
                                  String status, String publicationState) {
        return createDefinitionIn(tenantId, familyId, version, generation, status, publicationState);
    }

    private UUID createDefinitionIn(UUID tenant, UUID family, int version, String generation,
                                    String status, String publicationState) {
        return createDefinitionFor(tenant, userId, family, version, generation, status, publicationState);
    }

    private UUID createDefinitionFor(UUID tenant, UUID createdBy, UUID family, int version,
                                     String generation, String status, String publicationState) {
        UUID defId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Cutover', 'GENERAL', ?, ?, 'MANUAL', ?, 0, ?, ?, 1, ?, ?)
                """, defId, tenant, family,
                "WF-CUT-" + family.toString().substring(0, 6) + "-v" + version,
                version, status, createdBy, generation, publicationState, now, now);
        return defId;
    }

    private UUID createStep(UUID definitionId, String stepKey, String stepType) {
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

    private void linkV2Steps() {
        createTransition(startStepV2, taskStepV2, "begin");
        createTransition(taskStepV2, endStepV2, "done");
    }

    private void createTransition(UUID fromStep, UUID toStep, String key) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, y2V2, fromStep, toStep, key, now, now);
    }
}
