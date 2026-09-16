package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RED regression coverage for the 2026-09-16 Workflow forensic audit.
 *
 * Proves that graph mutations participate in the parent definition's
 * optimistic-lock protocol and that definition lifecycle events are durable
 * business evidence rather than SLF4J-only diagnostics.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WorkflowDefinitionConcurrencyAuditTest {

    @Autowired private WorkflowDefinitionService definitionService;
    @Autowired private WorkflowDefinitionRepository definitionRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow audit tenant', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-audit-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Workflow audit user', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-audit-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void stepMutationInvalidatesAStalePublisherSnapshot() {
        WorkflowDefinition definition = createDefinition("WF-LOCK-STEP");
        WorkflowDefinition stalePublisher = definitionRepository.findById(tenantId, definition.id()).orElseThrow();

        definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", null, null, null), userId);

        assertThatThrownBy(() -> definitionRepository.save(
                stalePublisher.publish(userId, "sha256:stale-step")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void transitionMutationInvalidatesAStalePublisherSnapshot() {
        WorkflowDefinition definition = createDefinition("WF-LOCK-EDGE");
        WorkflowStep start = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", null, null, null), userId);
        WorkflowStep end = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "end", "End",
                WorkflowStep.StepType.END, 2, "{}", null, null, null), userId);

        WorkflowDefinition stalePublisher = definitionRepository.findById(tenantId, definition.id()).orElseThrow();

        definitionService.addTransition(
                tenantId, definition.id(), start.id(), end.id(),
                "finish", "SUCCESS", null, 10, "{}", userId);

        assertThatThrownBy(() -> definitionRepository.save(
                stalePublisher.publish(userId, "sha256:stale-edge")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void definitionCreateIsWrittenToDurablePlatformAudit() {
        WorkflowDefinition definition = createDefinition("WF-AUDIT-CREATE");

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM platform_audit_logs
                WHERE target_tenant_id = ?
                  AND action = 'WORKFLOW.DEFINITION.CREATE'
                  AND resource_id = ?
                  AND result = 'SUCCESS'
                """, Integer.class, tenantId, definition.id().toString());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void definitionPublishIsWrittenToDurablePlatformAudit() {
        WorkflowDefinition definition = createDefinition("WF-AUDIT-PUBLISH");
        WorkflowStep start = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", null, null, null), userId);
        WorkflowStep end = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "end", "End",
                WorkflowStep.StepType.END, 2, "{}", null, null, null), userId);
        definitionService.addTransition(
                tenantId, definition.id(), start.id(), end.id(),
                "finish", "SUCCESS", null, 10, "{}", userId);

        definitionService.publish(tenantId, definition.id(), userId);

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM platform_audit_logs
                WHERE target_tenant_id = ?
                  AND action = 'WORKFLOW.DEFINITION.PUBLISH'
                  AND resource_id = ?
                  AND result = 'SUCCESS'
                """, Integer.class, tenantId, definition.id().toString());

        assertThat(count).isEqualTo(1);
    }

    private WorkflowDefinition createDefinition(String code) {
        return definitionService.create(
                WorkflowDefinition.create(
                        tenantId, code, code, "fixture", "GENERAL",
                        WorkflowDefinition.TriggerType.MANUAL, userId),
                userId);
    }
}
