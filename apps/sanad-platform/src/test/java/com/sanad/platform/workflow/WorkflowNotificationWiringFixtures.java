package com.sanad.platform.workflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * W.3 TEST-HYGIENE — shared fixture seeding for the Task 15 notification
 * wiring test family, extracted verbatim from
 * {@code WorkflowTask15NotificationWiringIntegrationTest} so that the hygiene
 * regression test exercises the EXACT same seeding code path (not a copy).
 *
 * <p>The seeder registers every tenant it creates and
 * {@link #sweepCreated()} deterministically removes every owned fixture via
 * {@link WorkflowTenantFixtureSweeper}. This makes "clean up every owned
 * fixture" the default behavior of the shared seeding path instead of an
 * optional afterthought.</p>
 */
final class WorkflowNotificationWiringFixtures {

    record Recipient(UUID userId, UUID employeeId) {}

    record Fixture(UUID tenantId, UUID actorUserId, UUID definitionId,
                   String targetStepType, List<Recipient> recipients) {}

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final List<UUID> createdTenants = new ArrayList<>();

    WorkflowNotificationWiringFixtures(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    Fixture fixture(String tag, String targetStepType, boolean workPool, int recipientCount) {
        UUID tenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        List<Recipient> recipients = new ArrayList<>();
        for (int i = 0; i < recipientCount; i++) {
            recipients.add(new Recipient(UUID.randomUUID(), UUID.randomUUID()));
        }

        tenantTx(tenantId, () -> {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Task15 " + tag,
                    "t15-" + tag + "-" + tenantId.toString().substring(0, 8), now, now);
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                            + "VALUES (?, ?, ?, 'Task15 Actor', 'ACTIVE', 'dummy', ?, ?)",
                    actorUserId, tenantId,
                    "t15-actor-" + actorUserId.toString().substring(0, 8) + "@test", now, now);

            int index = 0;
            for (Recipient recipient : recipients) {
                jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                                + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                        recipient.userId(), tenantId,
                        "t15-recipient-" + recipient.userId().toString().substring(0, 8) + "@test",
                        "Task15 Recipient " + index, now, now);
                jdbc.update("""
                        INSERT INTO hr_employees (
                            id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                            employment_type, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'Task15', 'Recipient', ?,
                                  'FULL_TIME', 'ACTIVE', ?, ?)
                        """, recipient.employeeId(), tenantId, recipient.userId(),
                        "T15-" + tag + "-" + index, "Task15 Recipient " + index, now, now);
                if (workPool) {
                    grantCapability(tenantId, recipient.userId(), "WORKFLOW.TASK_EXECUTE");
                }
                index++;
            }

            jdbc.update("""
                    INSERT INTO workflow_definitions (
                        id, tenant_id, definition_family_id, code, name, module, version, status,
                        trigger_type, created_by, version_lock, engine_generation, publication_state,
                        schema_version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'GENERAL', 1, 'ACTIVE',
                              'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                    """, definitionId, tenantId, definitionId,
                    "WF15-" + tag.toUpperCase(), "Task15 " + tag, actorUserId, now, now);

            UUID startStep = createStep(tenantId, definitionId, "start", "START", 1,
                    "{}", null);
            String targetConfig = workPool
                    ? "{}"
                    : "{\"assigneeEmployeeId\":\"" + recipients.get(0).employeeId() + "\"}";
            UUID targetStep = createStep(tenantId, definitionId, "review", targetStepType, 2,
                    targetConfig, workPool ? "WORKFLOW.TASK_EXECUTE" : null);
            UUID endStep = createStep(tenantId, definitionId, "end", "END", 3,
                    "{}", null);
            createTransition(tenantId, definitionId, startStep, targetStep, "begin");
            createTransition(tenantId, definitionId, targetStep, endStep, "done");
            return null;
        });

        createdTenants.add(tenantId);
        return new Fixture(tenantId, actorUserId, definitionId, targetStepType, List.copyOf(recipients));
    }

    /**
     * Deterministically removes every fixture seeded by this instance
     * (idempotent; safe to call from {@code @AfterEach}).
     */
    void sweepCreated() {
        for (UUID tenantId : createdTenants) {
            WorkflowTenantFixtureSweeper.sweepTenant(jdbc, transactionManager, tenantId);
        }
        createdTenants.clear();
    }

    List<UUID> registeredTenants() {
        return List.copyOf(createdTenants);
    }

    private UUID createStep(UUID tenantId, UUID definitionId, String key, String type,
                            int sequence, String configuration, String requiredCapability) {
        UUID stepId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, required_capability, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 0, ?, ?)
                """, stepId, tenantId, definitionId, key, key, type, sequence,
                configuration, requiredCapability, now, now);
        return stepId;
    }

    private void createTransition(UUID tenantId, UUID definitionId, UUID fromStep,
                                  UUID toStep, String key) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, definitionId, fromStep, toStep, key, now, now);
    }

    /**
     * Idempotent RBAC fixture: one ADMIN role per tenant can accumulate the
     * capability and be assigned to multiple candidate users.
     */
    private void grantCapability(UUID tenantId, UUID userId, String capabilityCode) {
        List<UUID> roles = jdbc.queryForList(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'ADMIN'",
                UUID.class, tenantId);
        UUID roleId;
        if (roles.isEmpty()) {
            roleId = UUID.randomUUID();
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                            + "VALUES (?, ?, 'ADMIN', 'Administrator', 'ACTIVE', ?, ?)",
                    roleId, tenantId, now, now);
        } else {
            roleId = roles.get(0);
        }

        Integer roleCapabilityCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_capabilities rc
                JOIN access_capabilities ac ON ac.id = rc.capability_id
                WHERE rc.tenant_id = ? AND rc.role_id = ? AND ac.code = ?
                """, Integer.class, tenantId, roleId, capabilityCode);
        if (roleCapabilityCount != null && roleCapabilityCount == 0) {
            jdbc.update("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT ?, ?, ?, id, NOW() FROM access_capabilities WHERE code = ?
                    """, UUID.randomUUID(), tenantId, roleId, capabilityCode);
        }

        Integer assignmentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role_assignments
                WHERE tenant_id = ? AND user_id = ? AND role_id = ? AND status = 'ACTIVE'
                """, Integer.class, tenantId, userId, roleId);
        if (assignmentCount != null && assignmentCount == 0) {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("""
                    INSERT INTO user_role_assignments (
                        id, tenant_id, user_id, role_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, UUID.randomUUID(), tenantId, userId, roleId, now, now);
        }
    }

    <T> T tenantTx(UUID tenantId, Supplier<T> supplier) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            return supplier.get();
        });
    }
}
