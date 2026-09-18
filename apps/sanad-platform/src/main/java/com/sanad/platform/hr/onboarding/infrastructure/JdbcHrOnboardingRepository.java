package com.sanad.platform.hr.onboarding.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC persistence boundary for HRM-G1 T9 onboarding.
 *
 * <p>Every mutation is one tenant-scoped transaction using
 * {@code set_config('app.tenant_id', ..., true)} so FORCE RLS remains the
 * final isolation boundary. Audit and outbox evidence are written in the same
 * transaction as the state transition.</p>
 */
@Repository
public class JdbcHrOnboardingRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;

    @Autowired
    public JdbcHrOnboardingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public record TaskSnapshot(UUID id, UUID planId, String state,
                               UUID assigneeUserId, boolean workflowLinked) {
    }

    public UUID createPlan(HrCommandContext ctx, UUID employmentId,
                           String templateCode, boolean workflowLinked) {
        return inTransaction(ctx.tenantId(), connection -> {
            if (!exists(connection,
                    "SELECT 1 FROM hr_employees WHERE tenant_id=? AND id=?",
                    ctx.tenantId(), employmentId)) {
                throw new IllegalStateException("HRM_ONBOARDING_EMPLOYMENT_NOT_FOUND: " + employmentId);
            }

            TemplateRow template = loadTemplate(connection, ctx.tenantId(), templateCode);
            if (template == null) {
                throw new IllegalStateException("HRM_ONBOARDING_TEMPLATE_NOT_FOUND: " + templateCode);
            }
            List<TemplateTask> tasks = parseTemplate(template.definition());
            if (tasks.isEmpty()) {
                throw new IllegalStateException("HRM_ONBOARDING_TEMPLATE_INVALID: template contains no tasks");
            }

            UUID planId = UUID.randomUUID();
            UUID checklistId = UUID.randomUUID();
            String planNumber = "ONB-" + planId.toString().replace("-", "").substring(0, 20);

            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO hr_onboarding_plans
                        (id, tenant_id, plan_number, employment_id, checklist_template_id,
                         state, workflow_linked, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, NOW(), NOW())
                    """)) {
                ps.setObject(1, planId);
                ps.setObject(2, ctx.tenantId());
                ps.setString(3, planNumber);
                ps.setObject(4, employmentId);
                ps.setObject(5, template.id());
                ps.setBoolean(6, workflowLinked);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO hr_onboarding_checklists
                        (id, tenant_id, plan_id, template_id, template_code,
                         template_version, definition_snapshot, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                    """)) {
                ps.setObject(1, checklistId);
                ps.setObject(2, ctx.tenantId());
                ps.setObject(3, planId);
                ps.setObject(4, template.id());
                ps.setString(5, template.code());
                ps.setInt(6, template.version());
                ps.setString(7, template.definition());
                ps.executeUpdate();
            }

            for (TemplateTask task : tasks) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO hr_onboarding_tasks
                            (id, tenant_id, plan_id, checklist_id, seq, title, state,
                             assignee_user_id, due_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, NOW(), NOW())
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, ctx.tenantId());
                    ps.setObject(3, planId);
                    ps.setObject(4, checklistId);
                    ps.setInt(5, task.seq());
                    ps.setString(6, task.title());
                    ps.setObject(7, task.assigneeUserId());
                    if (task.dueOffsetDays() == null) {
                        ps.setTimestamp(8, null);
                    } else {
                        ps.setObject(8, OffsetDateTime.now(ZoneOffset.UTC).plusDays(task.dueOffsetDays()));
                    }
                    ps.executeUpdate();
                }
            }

            appendAudit(connection, ctx, "HRM.ONBOARDING.PLAN.CREATE",
                    "HrOnboardingPlan", planId, null, "ACTIVE", null);
            appendOutbox(connection, ctx, "HRM.ONBOARDING.PLAN_CREATED",
                    "HrOnboardingPlan", planId,
                    "{\"plan_id\":\"" + planId + "\",\"employment_id\":\"" + employmentId + "\"}");
            return planId;
        });
    }

    public TaskSnapshot findTask(HrCommandContext ctx, UUID taskId) {
        return inTransaction(ctx.tenantId(), connection -> loadTask(connection, ctx.tenantId(), taskId, false));
    }

    public void completeTask(HrCommandContext ctx, UUID taskId, UUID requestId) {
        inTransaction(ctx.tenantId(), connection -> {
            IdempotencyClaim claim = claimIdempotency(connection, ctx, "HRM.ONBOARDING.TASK.COMPLETE",
                    requestId, taskId.toString());
            if (claim.replay()) {
                return null;
            }

            TaskSnapshot task = loadTask(connection, ctx.tenantId(), taskId, true);
            if (task == null) {
                throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
            }
            if ("DONE".equals(task.state())) {
                completeIdempotency(connection, claim.id());
                return null;
            }
            if (!"OPEN".equals(task.state())) {
                throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: task " + taskId
                        + " is " + task.state());
            }

            updateTaskState(connection, ctx.tenantId(), taskId, "DONE", null);
            appendAudit(connection, ctx, "HRM.ONBOARDING.TASK.COMPLETE",
                    "HrOnboardingTask", taskId, "OPEN", "DONE", null);
            appendOutbox(connection, ctx, "HRM.ONBOARDING.TASK_COMPLETED",
                    "HrOnboardingTask", taskId,
                    "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + task.planId()
                            + "\",\"actor_ref\":\"" + ctx.actorUserId() + "\"}");
            derivePlanCompletion(connection, ctx, task.planId());
            completeIdempotency(connection, claim.id());
            return null;
        });
    }

    public void waiveTask(HrCommandContext ctx, UUID taskId, String reasonCode, UUID requestId) {
        inTransaction(ctx.tenantId(), connection -> {
            IdempotencyClaim claim = claimIdempotency(connection, ctx, "HRM.ONBOARDING.TASK.WAIVE",
                    requestId, taskId + "|" + reasonCode);
            if (claim.replay()) {
                return null;
            }

            TaskSnapshot task = loadTask(connection, ctx.tenantId(), taskId, true);
            if (task == null) {
                throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
            }
            if ("WAIVED".equals(task.state())) {
                completeIdempotency(connection, claim.id());
                return null;
            }
            if (!"OPEN".equals(task.state())) {
                throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: task " + taskId
                        + " is " + task.state());
            }

            updateTaskState(connection, ctx.tenantId(), taskId, "WAIVED", reasonCode);
            appendAudit(connection, ctx, "HRM.ONBOARDING.TASK.WAIVE",
                    "HrOnboardingTask", taskId, "OPEN", "WAIVED", reasonCode);
            appendOutbox(connection, ctx, "HRM.ONBOARDING.TASK_WAIVED",
                    "HrOnboardingTask", taskId,
                    "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + task.planId()
                            + "\",\"waiver_reason_code\":\"" + reasonCode + "\"}");
            derivePlanCompletion(connection, ctx, task.planId());
            completeIdempotency(connection, claim.id());
            return null;
        });
    }

    public void cancelPlan(HrCommandContext ctx, UUID planId, String reasonCode, UUID requestId) {
        inTransaction(ctx.tenantId(), connection -> {
            IdempotencyClaim claim = claimIdempotency(connection, ctx, "HRM.ONBOARDING.PLAN.CANCEL",
                    requestId, planId + "|" + reasonCode);
            if (claim.replay()) {
                return null;
            }

            PlanRow plan = loadPlan(connection, ctx.tenantId(), planId, true);
            if (plan == null) {
                throw new IllegalStateException("HRM_ONBOARDING_PLAN_NOT_FOUND: " + planId);
            }
            if (!"ACTIVE".equals(plan.state())) {
                throw new IllegalStateException("HRM_ONBOARDING_PLAN_TERMINAL: " + plan.state());
            }

            List<UUID> openTasks = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id
                    FROM hr_onboarding_tasks
                    WHERE tenant_id=? AND plan_id=? AND state='OPEN'
                    ORDER BY seq
                    FOR UPDATE
                    """)) {
                ps.setObject(1, ctx.tenantId());
                ps.setObject(2, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        openTasks.add(rs.getObject(1, UUID.class));
                    }
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE hr_onboarding_tasks
                    SET state='WAIVED', reason_code=?, resolved_at=NOW(), updated_at=NOW()
                    WHERE tenant_id=? AND plan_id=? AND state='OPEN'
                    """)) {
                ps.setString(1, reasonCode);
                ps.setObject(2, ctx.tenantId());
                ps.setObject(3, planId);
                ps.executeUpdate();
            }

            for (UUID taskId : openTasks) {
                appendAudit(connection, ctx, "HRM.ONBOARDING.TASK.WAIVE",
                        "HrOnboardingTask", taskId, "OPEN", "WAIVED", reasonCode);
                appendOutbox(connection, ctx, "HRM.ONBOARDING.TASK_WAIVED",
                        "HrOnboardingTask", taskId,
                        "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + planId
                                + "\",\"waiver_reason_code\":\"" + reasonCode + "\"}");
                if (plan.workflowLinked()) {
                    appendOutbox(connection, ctx, "HRM.ONBOARDING.WORKFLOW_CANCEL_REQUESTED",
                            "HrOnboardingTask", taskId,
                            "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + planId + "\"}");
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE hr_onboarding_plans
                    SET state='CANCELLED', cancelled_reason_code=?, version=version+1, updated_at=NOW()
                    WHERE tenant_id=? AND id=? AND state='ACTIVE'
                    """)) {
                ps.setString(1, reasonCode);
                ps.setObject(2, ctx.tenantId());
                ps.setObject(3, planId);
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("HRM_ONBOARDING_PLAN_TERMINAL: concurrent transition");
                }
            }

            appendAudit(connection, ctx, "HRM.ONBOARDING.PLAN.CANCEL",
                    "HrOnboardingPlan", planId, "ACTIVE", "CANCELLED", reasonCode);
            appendOutbox(connection, ctx, "HRM.ONBOARDING.PLAN_CANCELLED",
                    "HrOnboardingPlan", planId,
                    "{\"plan_id\":\"" + planId + "\",\"reason_code\":\"" + reasonCode + "\"}");
            completeIdempotency(connection, claim.id());
            return null;
        });
    }

    public void applyWorkflowTransition(HrCommandContext ctx, UUID taskId, long transitionSeq,
                                        String outcome, String reasonCode) {
        inTransaction(ctx.tenantId(), connection -> {
            TaskSnapshot task = loadTask(connection, ctx.tenantId(), taskId, true);
            if (task == null) {
                throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
            }
            if (!task.workflowLinked()) {
                throw new IllegalStateException("HRM_ONBOARDING_WORKFLOW_NOT_LINKED: " + taskId);
            }

            int inserted;
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO hr_onboarding_workflow_transitions
                        (tenant_id, task_id, transition_seq, outcome, reason_code,
                         actor_user_id, correlation_id, applied_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (tenant_id, task_id, transition_seq) DO NOTHING
                    """)) {
                ps.setObject(1, ctx.tenantId());
                ps.setObject(2, taskId);
                ps.setLong(3, transitionSeq);
                ps.setString(4, outcome);
                ps.setString(5, reasonCode);
                ps.setObject(6, ctx.actorUserId());
                ps.setObject(7, ctx.correlationId());
                inserted = ps.executeUpdate();
            }
            if (inserted == 0) {
                return null;
            }

            if ("DONE".equals(outcome)) {
                if ("OPEN".equals(task.state())) {
                    updateTaskState(connection, ctx.tenantId(), taskId, "DONE", null);
                    appendAudit(connection, ctx, "HRM.ONBOARDING.WORKFLOW.APPLY_DONE",
                            "HrOnboardingTask", taskId, "OPEN", "DONE", null);
                    appendOutbox(connection, ctx, "HRM.ONBOARDING.TASK_COMPLETED",
                            "HrOnboardingTask", taskId,
                            "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + task.planId()
                                    + "\",\"actor_ref\":\"" + ctx.actorUserId() + "\"}");
                    derivePlanCompletion(connection, ctx, task.planId());
                } else if (!"DONE".equals(task.state())) {
                    throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + task.state() + " -> DONE");
                }
            } else if ("WAIVED".equals(outcome) || "CANCELLED".equals(outcome)) {
                if (reasonCode == null || reasonCode.isBlank()) {
                    throw new IllegalStateException("HRM_REASON_REJECTED: workflow waiver reason is required");
                }
                if ("OPEN".equals(task.state())) {
                    updateTaskState(connection, ctx.tenantId(), taskId, "WAIVED", reasonCode);
                    appendAudit(connection, ctx, "HRM.ONBOARDING.WORKFLOW.APPLY_WAIVED",
                            "HrOnboardingTask", taskId, "OPEN", "WAIVED", reasonCode);
                    appendOutbox(connection, ctx, "HRM.ONBOARDING.TASK_WAIVED",
                            "HrOnboardingTask", taskId,
                            "{\"task_id\":\"" + taskId + "\",\"plan_id\":\"" + task.planId()
                                    + "\",\"waiver_reason_code\":\"" + reasonCode + "\"}");
                    derivePlanCompletion(connection, ctx, task.planId());
                } else if (!"WAIVED".equals(task.state())) {
                    throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + task.state() + " -> WAIVED");
                }
            } else {
                throw new IllegalStateException("HRM_ONBOARDING_WORKFLOW_OUTCOME_REJECTED: " + outcome);
            }
            return null;
        });
    }

    private void derivePlanCompletion(Connection connection, HrCommandContext ctx, UUID planId)
            throws SQLException {
        PlanRow plan = loadPlan(connection, ctx.tenantId(), planId, true);
        if (plan == null || !"ACTIVE".equals(plan.state())) {
            return;
        }

        int open;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM hr_onboarding_tasks
                WHERE tenant_id=? AND plan_id=? AND state='OPEN'
                """)) {
            ps.setObject(1, ctx.tenantId());
            ps.setObject(2, planId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                open = rs.getInt(1);
            }
        }
        if (open != 0) {
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_onboarding_plans
                SET state='COMPLETED', version=version+1, updated_at=NOW()
                WHERE tenant_id=? AND id=? AND state='ACTIVE'
                """)) {
            ps.setObject(1, ctx.tenantId());
            ps.setObject(2, planId);
            if (ps.executeUpdate() == 1) {
                appendAudit(connection, ctx, "HRM.ONBOARDING.PLAN.DERIVED_COMPLETE",
                        "HrOnboardingPlan", planId, "ACTIVE", "COMPLETED", null);
                appendOutbox(connection, ctx, "HRM.ONBOARDING.PLAN_COMPLETED",
                        "HrOnboardingPlan", planId, "{\"plan_id\":\"" + planId + "\"}");
            }
        }
    }

    private TaskSnapshot loadTask(Connection connection, UUID tenantId, UUID taskId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT t.id, t.plan_id, t.state, t.assignee_user_id, p.workflow_linked
                FROM hr_onboarding_tasks t
                JOIN hr_onboarding_plans p
                  ON p.tenant_id=t.tenant_id AND p.id=t.plan_id
                WHERE t.tenant_id=? AND t.id=?
                """ + (lock ? " FOR UPDATE OF t, p" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new TaskSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getString("state"),
                        rs.getObject("assignee_user_id", UUID.class),
                        rs.getBoolean("workflow_linked"));
            }
        }
    }

    private PlanRow loadPlan(Connection connection, UUID tenantId, UUID planId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT id, state, workflow_linked
                FROM hr_onboarding_plans
                WHERE tenant_id=? AND id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, planId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new PlanRow(rs.getObject("id", UUID.class), rs.getString("state"),
                                rs.getBoolean("workflow_linked"))
                        : null;
            }
        }
    }

    private TemplateRow loadTemplate(Connection connection, UUID tenantId, String code)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, code, version, definition::text
                FROM hr_onboarding_checklist_templates
                WHERE tenant_id=? AND code=? AND is_active=TRUE
                FOR SHARE
                """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new TemplateRow(rs.getObject("id", UUID.class), rs.getString("code"),
                                rs.getInt("version"), rs.getString("definition"))
                        : null;
            }
        }
    }

    private static List<TemplateTask> parseTemplate(String raw) {
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("HRM_ONBOARDING_TEMPLATE_INVALID: definition must be an array");
            }
            List<TemplateTask> result = new ArrayList<>();
            int fallbackSeq = 1;
            for (JsonNode node : root) {
                int seq = node.path("seq").asInt(fallbackSeq);
                String title = node.path("title").asText(null);
                if (title == null || title.isBlank()) {
                    title = node.path("titleKey").asText(null);
                }
                if (title == null || title.isBlank() || seq < 1) {
                    throw new IllegalStateException("HRM_ONBOARDING_TEMPLATE_INVALID: task title/seq");
                }
                UUID assignee = null;
                String assigneeText = node.path("assigneeUserId").asText(null);
                if (assigneeText != null && !assigneeText.isBlank()) {
                    assignee = UUID.fromString(assigneeText);
                }
                Integer dueOffset = node.has("dueOffsetDays") && !node.get("dueOffsetDays").isNull()
                        ? node.get("dueOffsetDays").asInt() : null;
                result.add(new TemplateTask(seq, title, assignee, dueOffset));
                fallbackSeq++;
            }
            return List.copyOf(result);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HRM_ONBOARDING_TEMPLATE_INVALID: " + e.getMessage(), e);
        }
    }

    private static void updateTaskState(Connection connection, UUID tenantId, UUID taskId,
                                        String target, String reason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_onboarding_tasks
                SET state=?, reason_code=?, resolved_at=NOW(), updated_at=NOW()
                WHERE tenant_id=? AND id=? AND state='OPEN'
                """)) {
            ps.setString(1, target);
            ps.setString(2, reason);
            ps.setObject(3, tenantId);
            ps.setObject(4, taskId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("HRM_TRANSITION_CONFLICT: task " + taskId);
            }
        }
    }

    private static boolean exists(Connection connection, String sql, UUID tenantId, UUID id)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void appendAudit(Connection connection, HrCommandContext ctx, String action,
                                    String resourceType, UUID resourceId, String before,
                                    String after, String reason) throws SQLException {
        UUID auditId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_audit_ledger
                    (id, tenant_id, actor_user_id, action, resource_type, resource_id,
                     data_classification, reason, before_state, after_state, result,
                     correlation_id, request_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPERATIONAL', ?, ?::jsonb, ?::jsonb,
                        'SUCCESS', ?, ?, NOW())
                """)) {
            ps.setObject(1, auditId);
            ps.setObject(2, ctx.tenantId());
            ps.setObject(3, ctx.actorUserId());
            ps.setString(4, action);
            ps.setString(5, resourceType);
            ps.setObject(6, resourceId);
            ps.setString(7, reason);
            ps.setString(8, stateJson(before));
            ps.setString(9, stateJson(after));
            ps.setObject(10, ctx.correlationId());
            ps.setObject(11, ctx.correlationId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_audit_delivery
                    (audit_id, tenant_id, status, attempt_count, max_attempts, available_at, updated_at)
                VALUES (?, ?, 'PENDING', 0, 8, NOW(), NOW())
                """)) {
            ps.setObject(1, auditId);
            ps.setObject(2, ctx.tenantId());
            ps.executeUpdate();
        }
    }

    private static String stateJson(String state) {
        return state == null ? null : "{\"state\":\"" + state + "\"}";
    }

    private static void appendOutbox(Connection connection, HrCommandContext ctx, String eventType,
                                     String aggregateType, UUID entityId, String payload)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_domain_event_outbox
                    (event_id, tenant_id, event_type, event_version, aggregate_type,
                     aggregate_id, entity_id, actor_user_id, occurred_at, correlation_id,
                     idempotency_key, data_classification, payload, status, available_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, NOW(), ?, ?, 'OPERATIONAL',
                        ?::jsonb, 'READY', NOW())
                """)) {
            UUID eventId = UUID.randomUUID();
            ps.setObject(1, eventId);
            ps.setObject(2, ctx.tenantId());
            ps.setString(3, eventType);
            ps.setString(4, aggregateType);
            ps.setObject(5, entityId);
            ps.setObject(6, entityId);
            ps.setObject(7, ctx.actorUserId());
            ps.setObject(8, ctx.correlationId());
            ps.setString(9, eventType + ":" + entityId + ":" + ctx.correlationId());
            ps.setString(10, payload);
            ps.executeUpdate();
        }
    }

    private static IdempotencyClaim claimIdempotency(Connection connection, HrCommandContext ctx,
                                                     String operation, UUID requestId,
                                                     String fingerprintSource) throws SQLException {
        String fingerprint = sha256(operation + "|" + fingerprintSource);
        UUID id = UUID.randomUUID();
        int inserted;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_idempotency_records
                    (id, tenant_id, principal_id, operation_code, idempotency_key,
                     request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, principal_id, operation_code, idempotency_key)
                DO NOTHING
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, ctx.tenantId());
            ps.setObject(3, ctx.actorUserId());
            ps.setString(4, operation);
            ps.setString(5, requestId.toString());
            ps.setString(6, fingerprint);
            inserted = ps.executeUpdate();
        }
        if (inserted == 1) {
            return new IdempotencyClaim(id, false);
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, request_fingerprint, response_status
                FROM hr_idempotency_records
                WHERE tenant_id=? AND principal_id=? AND operation_code=? AND idempotency_key=?
                FOR UPDATE
                """)) {
            ps.setObject(1, ctx.tenantId());
            ps.setObject(2, ctx.actorUserId());
            ps.setString(3, operation);
            ps.setString(4, requestId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: canonical row missing");
                }
                if (!fingerprint.equals(rs.getString("request_fingerprint"))) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: request fingerprint mismatch");
                }
                Integer status = rs.getObject("response_status") == null ? null : rs.getInt("response_status");
                if (status == null) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_IN_FLIGHT: " + requestId);
                }
                return new IdempotencyClaim(rs.getObject("id", UUID.class), true);
            }
        }
    }

    private static void completeIdempotency(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_idempotency_records
                SET response_status=200, response_body='{}'::jsonb,
                    expires_at=NOW() + INTERVAL '24 hours'
                WHERE id=? AND response_status IS NULL
                """)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_HASH_FAILED", e);
        }
    }

    private <T> T inTransaction(UUID tenantId, SqlWork<T> work) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Throwable e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (e instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("HRM_ONBOARDING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // connection is closing
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_ONBOARDING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    private record TemplateRow(UUID id, String code, int version, String definition) {
    }

    private record TemplateTask(int seq, String title, UUID assigneeUserId, Integer dueOffsetDays) {
    }

    private record PlanRow(UUID id, String state, boolean workflowLinked) {
    }

    private record IdempotencyClaim(UUID id, boolean replay) {
    }
}
