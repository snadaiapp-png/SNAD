package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import com.sanad.platform.crm.integration.orchestration.WorkflowIntegrationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Application use cases for governed CRM workflow dispatch and lifecycle control. */
@Service
public class CrmWorkflowUseCases {

    public enum WorkflowType {
        ASSIGNMENT,
        OPPORTUNITY_APPROVAL,
        REMINDER,
        ESCALATION
    }

    private static final Set<String> CALLBACK_TERMINAL_STATES = Set.of(
            "COMPLETED", "REJECTED", "CANCELLED", "TIMED_OUT", "UNAVAILABLE");

    private final CrmIntegrationStore store;
    private final CrmWorkflowStore workflowStore;
    private final CrmEntitySnapshotPort entitySnapshotPort;
    private final WorkflowIntegrationPort workflowPort;
    private final ObjectMapper mapper;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final CorrelationContextPort correlationContext;

    public CrmWorkflowUseCases(
            CrmIntegrationStore store,
            CrmWorkflowStore workflowStore,
            CrmEntitySnapshotPort entitySnapshotPort,
            WorkflowIntegrationPort workflowPort,
            ObjectMapper mapper,
            AuditPort audit,
            TimelineEventPort timeline,
            CorrelationContextPort correlationContext) {
        this.store = store;
        this.workflowStore = workflowStore;
        this.entitySnapshotPort = entitySnapshotPort;
        this.workflowPort = workflowPort;
        this.mapper = mapper;
        this.audit = audit;
        this.timeline = timeline;
        this.correlationContext = correlationContext;
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest dispatchAssignmentWorkflow(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, String sourceEntityType, UUID sourceEntityId,
            long sourceEntityVersion, JsonNode payload, Locale locale) {
        return dispatchWorkflow(tenantId, actorId, correlationId, causationId, idempotencyKey,
                WorkflowType.ASSIGNMENT, sourceEntityType, sourceEntityId,
                sourceEntityVersion, payload, locale);
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest dispatchOpportunityApprovalWorkflow(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, String sourceEntityType, UUID sourceEntityId,
            long sourceEntityVersion, JsonNode payload, Locale locale) {
        return dispatchWorkflow(tenantId, actorId, correlationId, causationId, idempotencyKey,
                WorkflowType.OPPORTUNITY_APPROVAL, sourceEntityType, sourceEntityId,
                sourceEntityVersion, payload, locale);
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest scheduleReminder(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, String sourceEntityType, UUID sourceEntityId,
            long sourceEntityVersion, JsonNode payload, Locale locale) {
        return dispatchWorkflow(tenantId, actorId, correlationId, causationId, idempotencyKey,
                WorkflowType.REMINDER, sourceEntityType, sourceEntityId,
                sourceEntityVersion, payload, locale);
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest scheduleEscalation(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, String sourceEntityType, UUID sourceEntityId,
            long sourceEntityVersion, JsonNode payload, Locale locale) {
        return dispatchWorkflow(tenantId, actorId, correlationId, causationId, idempotencyKey,
                WorkflowType.ESCALATION, sourceEntityType, sourceEntityId,
                sourceEntityVersion, payload, locale);
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest dispatchWorkflow(
            UUID tenantId,
            UUID actorId,
            String correlationId,
            String causationId,
            String idempotencyKey,
            WorkflowType workflowType,
            String sourceEntityType,
            UUID sourceEntityId,
            long sourceEntityVersion,
            JsonNode payload,
            Locale locale) {
        validateEntity(tenantId, sourceEntityType, sourceEntityId, sourceEntityVersion);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.workflow." + workflowType.name().toLowerCase(Locale.ROOT),
                "1.0",
                tenantId,
                actorId,
                correlationId,
                causationId,
                idempotencyKey,
                sourceEntityType,
                sourceEntityId,
                sourceEntityVersion,
                now,
                now.plus(5, ChronoUnit.MINUTES),
                locale == null ? Locale.ENGLISH : locale,
                "CRM.WORKFLOW.EXECUTE",
                "INTERNAL");

        ObjectNode minimizedPayload = mapper.createObjectNode();
        minimizedPayload.put("workflowType", workflowType.name());
        if (payload != null && payload.isObject()) {
            payload.fields().forEachRemaining(entry -> minimizedPayload.set(entry.getKey(), entry.getValue()));
        }

        CrmIntegrationStore.CreateResult created = store.create(envelope, "WORKFLOW", minimizedPayload);
        if (created.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return created.request();
        }
        store.createOutboxEvent(
                tenantId,
                created.request().id(),
                "WORKFLOW",
                "WORKFLOW_DISPATCH",
                idempotencyKey,
                minimizedPayload);

        // Audit trail for workflow dispatch
        ObjectNode auditAfter = mapper.createObjectNode();
        auditAfter.put("workflowType", workflowType.name());
        auditAfter.put("sourceEntityType", sourceEntityType);
        auditAfter.put("sourceEntityId", sourceEntityId.toString());
        auditAfter.put("status", "PENDING");
        audit.record(tenantId, actorId, "WORKFLOW_DISPATCHED", "INTEGRATION_REQUEST",
                created.request().id(), new AuditChange(null, auditAfter), now);

        // Timeline event for workflow started
        timeline.record(tenantId, sourceEntityType, sourceEntityId,
                "crm.workflow.dispatched",
                "Workflow " + workflowType.name() + " dispatched",
                "CRM_INTEGRATION", created.request().id(), actorId, now);

        return created.request();
    }

    public CrmIntegrationStore.StoredRequest getWorkflowStatus(UUID tenantId, UUID requestId) {
        CrmIntegrationStore.StoredRequest request = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(
                        IntegrationErrorCode.ENTITY_NOT_FOUND, "Workflow request not found"));
        if (!"WORKFLOW".equals(request.integrationType())) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT, "Request is not a workflow integration");
        }
        return request;
    }

    /** External cancellation is performed outside a database transaction. */
    public CrmIntegrationStore.StoredRequest cancelWorkflow(
            UUID tenantId,
            UUID requestId,
            long expectedVersion,
            String correlationId,
            String idempotencyKey,
            String reason) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Workflow cancellation idempotency key is required");
        }
        CrmIntegrationStore.StoredRequest request = getWorkflowStatus(tenantId, requestId);
        if (request.version() != expectedVersion) {
            throw new IntegrationException(
                    IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                    "Workflow request version mismatch");
        }
        if (!Set.of("ACCEPTED", "RUNNING").contains(request.status())) {
            if ("CANCELLED".equals(request.status())) return request;
            throw new IntegrationException(
                    IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Workflow cannot be cancelled from status " + request.status());
        }
        if (request.externalReference() == null) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Workflow run reference is missing");
        }

        workflowPort.cancel(
                tenantId,
                request.externalReference(),
                correlationId,
                idempotencyKey.strip(),
                reason == null || reason.isBlank() ? "Cancelled by CRM user" : reason.strip());

        CrmIntegrationStore.TransitionResult transition = store.transitionStatus(
                tenantId,
                requestId,
                expectedVersion,
                Set.of("ACCEPTED", "RUNNING"),
                "CANCELLED");
        if (!transition.success()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                    "Workflow changed while cancellation was being processed");
        }

        // Audit trail for workflow cancellation
        Instant cancelTime = Instant.now();
        ObjectNode cancelAudit = mapper.createObjectNode();
        cancelAudit.put("status", "CANCELLED");
        cancelAudit.put("reason", reason != null ? reason : "Cancelled by CRM user");
        audit.record(tenantId, request.actorId(), "WORKFLOW_CANCELLED", "INTEGRATION_REQUEST",
                requestId, new AuditChange(null, cancelAudit), cancelTime);

        // Timeline event for workflow cancelled
        timeline.record(tenantId, request.sourceEntityType(), request.sourceEntityId(),
                "crm.workflow.cancelled",
                "Workflow cancelled",
                "CRM_INTEGRATION", requestId, request.actorId(), cancelTime);

        return transition.request();
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest handleWorkflowCallback(
            UUID tenantId,
            UUID workflowRunId,
            String correlationId,
            String callbackStatus,
            Instant occurredAt,
            JsonNode callbackResult,
            String errorCode) {
        CrmIntegrationStore.StoredRequest request = workflowStore
                .findByExternalReference(tenantId, workflowRunId)
                .orElseThrow(() -> new IntegrationException(
                        IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Workflow request not found for callback run"));
        if (!request.correlationId().equals(correlationId)) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Workflow callback correlation mismatch");
        }

        String targetStatus = normalizeCallbackStatus(callbackStatus);
        if (targetStatus.equals(request.status())) {
            return request;
        }
        if (CrmIntegrationStore.TERMINAL_STATES.contains(request.status())) {
            throw new IntegrationException(
                    IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Terminal workflow request cannot accept a different callback status");
        }

        if ("ACCEPTED".equals(targetStatus)) {
            return request;
        }
        if ("RUNNING".equals(targetStatus)) {
            CrmIntegrationStore.TransitionResult transition = store.transitionStatus(
                    tenantId, request.id(), request.version(), Set.of("ACCEPTED"), "RUNNING");
            if (!transition.success()) {
                throw new IntegrationException(
                        IntegrationErrorCode.STATE_TRANSITION_FAILED,
                        "Workflow RUNNING callback transition conflict");
            }
            // Timeline event for workflow running
            timeline.record(tenantId, request.sourceEntityType(), request.sourceEntityId(),
                    "crm.workflow.running",
                    "Workflow is running",
                    "CRM_INTEGRATION", request.id(), request.actorId(), Instant.now());
            return transition.request();
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("workflowRunId", workflowRunId.toString());
        result.put("status", targetStatus);
        result.put("occurredAt", (occurredAt == null ? Instant.now() : occurredAt).toString());
        if (callbackResult != null) result.set("result", callbackResult);
        if (errorCode != null && !errorCode.isBlank()) result.put("errorCode", errorCode.strip());

        CrmIntegrationStore.TransitionResult transition = store.transitionWithResult(
                tenantId,
                request.id(),
                request.version(),
                Set.of("ACCEPTED", "RUNNING"),
                targetStatus,
                workflowRunId,
                result,
                errorCode);
        if (!transition.success()) {
            CrmIntegrationStore.StoredRequest current = store.find(tenantId, request.id()).orElseThrow();
            if (targetStatus.equals(current.status())) return current;
            throw new IntegrationException(
                    IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "Workflow terminal callback transition conflict");
        }

        // Audit trail for terminal workflow callback
        Instant callbackTime = occurredAt != null ? occurredAt : Instant.now();
        ObjectNode callbackAudit = mapper.createObjectNode();
        callbackAudit.put("status", targetStatus);
        callbackAudit.put("workflowRunId", workflowRunId.toString());
        if (errorCode != null && !errorCode.isBlank()) callbackAudit.put("errorCode", errorCode);
        audit.record(tenantId, request.actorId(), "WORKFLOW_" + targetStatus, "INTEGRATION_REQUEST",
                request.id(), new AuditChange(null, callbackAudit), callbackTime);

        // Timeline event for workflow terminal state
        String timelineEventType = "COMPLETED".equals(targetStatus)
                ? "crm.workflow.completed"
                : "REJECTED".equals(targetStatus) ? "crm.workflow.rejected"
                : "crm.workflow.terminated";
        String timelineSummary = "Workflow " + targetStatus.toLowerCase(Locale.ROOT);
        timeline.record(tenantId, request.sourceEntityType(), request.sourceEntityId(),
                timelineEventType, timelineSummary,
                "CRM_INTEGRATION", request.id(), request.actorId(), callbackTime);

        return transition.request();
    }

    private void validateEntity(
            UUID tenantId, String entityType, UUID entityId, long expectedVersion) {
        CrmEntitySnapshotPort.CrmEntitySnapshot snapshot =
                entitySnapshotPort.load(tenantId, entityType, entityId);
        if (snapshot == null) {
            throw new IntegrationException(
                    IntegrationErrorCode.ENTITY_NOT_FOUND, "Workflow source entity not found");
        }
        if (!snapshot.tenantId().equals(tenantId)
                || !snapshot.entityType().equals(entityType)
                || !snapshot.entityId().equals(entityId)) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_TENANT, "Workflow source entity identity mismatch");
        }
        if (!snapshot.active()) {
            throw new IntegrationException(
                    IntegrationErrorCode.ENTITY_STATE_CONFLICT, "Workflow source entity is inactive");
        }
        if (snapshot.currentVersion() != expectedVersion) {
            throw new IntegrationException(
                    IntegrationErrorCode.STALE_RECOMMENDATION,
                    "Workflow source entity version is stale");
        }
    }

    private static String normalizeCallbackStatus(String callbackStatus) {
        if (callbackStatus == null || callbackStatus.isBlank()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT, "Workflow callback status is required");
        }
        String normalized = callbackStatus.strip().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(normalized)
                && !"RUNNING".equals(normalized)
                && !CALLBACK_TERMINAL_STATES.contains(normalized)) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Unsupported workflow callback status: " + normalized);
        }
        return normalized;
    }
}
