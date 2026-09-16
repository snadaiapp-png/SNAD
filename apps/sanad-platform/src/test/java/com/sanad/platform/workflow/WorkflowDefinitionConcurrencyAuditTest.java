package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED regression coverage for the 2026-09-16 Workflow forensic audit.
 *
 * Proves that graph mutation/publication serialize on the parent definition
 * row without changing the public versionLock contract, and that definition
 * lifecycle events are durable business evidence rather than SLF4J-only
 * diagnostics.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WorkflowDefinitionConcurrencyAuditTest {

    @Autowired private WorkflowDefinitionService definitionService;
    @Autowired private WorkflowDefinitionRepository definitionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

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
    void graphMutationsPreserveTheExistingVersionLockContract() {
        WorkflowDefinition definition = createDefinition("WF-LOCK-COMPAT");
        long expectedVersionLock = definition.versionLock();

        WorkflowStep start = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", null, null, null), userId);
        WorkflowStep end = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "end", "End",
                WorkflowStep.StepType.END, 2, "{}", null, null, null), userId);
        definitionService.addTransition(
                tenantId, definition.id(), start.id(), end.id(),
                "finish", "SUCCESS", null, 10, "{}", userId);

        WorkflowDefinition reloaded =
                definitionRepository.findById(tenantId, definition.id()).orElseThrow();
        assertThat(reloaded.versionLock()).isEqualTo(expectedVersionLock);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void graphMutationWaitsForTheParentDefinitionRowLock() throws Exception {
        WorkflowDefinition definition = createDefinition("WF-LOCK-SERIAL");
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var lockFuture = executor.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    definitionRepository.findByIdForUpdate(tenantId, definition.id()).orElseThrow();
                    lockAcquired.countDown();
                    try {
                        if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release definition row lock");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            });

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var mutationFuture = executor.submit(() -> definitionService.addStep(WorkflowStep.create(
                    tenantId, definition.id(), "start", "Start",
                    WorkflowStep.StepType.START, 1, "{}", null, null, null), userId));

            Thread.sleep(200);
            assertThat(mutationFuture.isDone())
                    .as("graph mutation must wait while the parent definition row is locked")
                    .isFalse();

            releaseLock.countDown();
            lockFuture.get(5, TimeUnit.SECONDS);
            assertThat(mutationFuture.get(5, TimeUnit.SECONDS).stepKey()).isEqualTo("start");
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
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
