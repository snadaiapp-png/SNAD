package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import com.sanad.platform.crm.integration.orchestration.WorkflowIntegrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Claims and dispatches only {@code WORKFLOW_DISPATCH} events. The external
 * Workflow Engine call is always outside a database transaction; persistence
 * of the response and outbox finalization occur atomically afterward.
 */
@Component
public class CrmWorkflowOutboxWorker {

    public static final Set<String> ACCEPTED_EVENT_TYPES = Set.of("WORKFLOW_DISPATCH");

    private static final Logger log = LoggerFactory.getLogger(CrmWorkflowOutboxWorker.class);

    private final CrmIntegrationStore store;
    private final CrmWorkflowStore workflowStore;
    private final WorkflowIntegrationPort workflowPort;
    private final ObjectMapper mapper;
    private final TransactionTemplate txTemplate;
    private final String workerId;
    private final int claimTimeoutSeconds;

    public CrmWorkflowOutboxWorker(
            CrmIntegrationStore store,
            CrmWorkflowStore workflowStore,
            WorkflowIntegrationPort workflowPort,
            ObjectMapper mapper,
            TransactionTemplate txTemplate,
            @Value("${sanad.integration.workflow-worker-id:workflow-worker-1}") String workerId,
            @Value("${sanad.integration.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.store = store;
        this.workflowStore = workflowStore;
        this.workflowPort = workflowPort;
        this.mapper = mapper;
        this.txTemplate = txTemplate;
        this.workerId = workerId;
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    @Scheduled(fixedDelay = 2500, initialDelay = 6000)
    public void processWorkflowEvents() {
        while (true) {
            var claimed = txTemplate.execute(status ->
                    store.claimNextOutboxEvent(workerId, claimTimeoutSeconds, ACCEPTED_EVENT_TYPES));
            if (claimed == null || claimed.isEmpty()) return;
            try {
                processSingleEvent(claimed.get());
            } catch (Exception error) {
                log.error("Workflow worker failed for outbox event {}", claimed.get().id(), error);
            }
        }
    }

    public void processSingleEvent(CrmIntegrationStore.OutboxEvent event) {
        if (!ACCEPTED_EVENT_TYPES.contains(event.eventType())) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Workflow worker received unsupported event type");
        }

        CrmIntegrationStore.StoredRequest initial = store.find(
                        event.tenantId(), event.integrationRequestId())
                .orElseThrow(() -> new IntegrationException(
                        IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Workflow integration request not found"));

        if (!"WORKFLOW".equals(initial.integrationType())) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "WORKFLOW_DISPATCH event is not bound to a workflow request");
        }

        if (!Set.of("PENDING", "DISPATCHED").contains(initial.status())) {
            txTemplate.execute(status -> {
                store.completeOutboxEvent(
                        event.tenantId(), event.id(), event.version(),
                        event.claimToken(), event.claimedBy());
                return null;
            });
            return;
        }

        CrmIntegrationStore.StoredRequest dispatched = initial;
        if ("PENDING".equals(initial.status())) {
            dispatched = txTemplate.execute(status -> {
                CrmIntegrationStore.TransitionResult transition = store.transitionStatus(
                        event.tenantId(),
                        event.integrationRequestId(),
                        initial.version(),
                        Set.of("PENDING"),
                        "DISPATCHED");
                if (!transition.success()) {
                    throw new IntegrationException(
                            IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "Workflow PENDING to DISPATCHED transition conflict");
                }
                return transition.request();
            });
        }
        if (dispatched == null) {
            throw new IntegrationException(
                    IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "Workflow dispatch transaction returned no request");
        }

        IntegrationEnvelope envelope = reconstructEnvelope(dispatched);
        String workflowType = workflowType(event);
        WorkflowIntegrationPort.WorkflowDispatch result;
        try {
            result = workflowPort.dispatch(envelope, workflowType, event.payload());
        } catch (Exception error) {
            finalizeFailure(event, IntegrationErrorCode.WORKFLOW_ENGINE_UNAVAILABLE);
            return;
        }

        final CrmIntegrationStore.StoredRequest requestForFinalization = dispatched;
        final WorkflowIntegrationPort.WorkflowDispatch dispatchResult = result;
        try {
            txTemplate.execute(status -> {
                switch (dispatchResult.status()) {
                    case ACCEPTED -> workflowStore.attachAcceptedRun(
                            event.tenantId(),
                            event.integrationRequestId(),
                            requestForFinalization.version(),
                            dispatchResult.workflowRunId());
                    case COMPLETED -> workflowStore.finalizeImmediateDispatch(
                            event.tenantId(),
                            event.integrationRequestId(),
                            requestForFinalization.version(),
                            dispatchResult.workflowRunId(),
                            "COMPLETED",
                            resultPayload(dispatchResult, workflowType),
                            null);
                    case REJECTED -> workflowStore.finalizeImmediateDispatch(
                            event.tenantId(),
                            event.integrationRequestId(),
                            requestForFinalization.version(),
                            dispatchResult.workflowRunId(),
                            "REJECTED",
                            resultPayload(dispatchResult, workflowType),
                            safeErrorCode(dispatchResult.errorCode(), IntegrationErrorCode.WORKFLOW_POLICY_DENIED));
                    case TIMED_OUT -> throw new RetryableWorkflowDispatchException(
                            IntegrationErrorCode.WORKFLOW_ENGINE_TIMEOUT);
                    case UNAVAILABLE -> throw new RetryableWorkflowDispatchException(
                            IntegrationErrorCode.WORKFLOW_ENGINE_UNAVAILABLE);
                }
                store.completeOutboxEvent(
                        event.tenantId(), event.id(), event.version(),
                        event.claimToken(), event.claimedBy());
                return null;
            });
        } catch (RetryableWorkflowDispatchException retryable) {
            finalizeFailure(event, retryable.code());
        }
    }

    private void finalizeFailure(
            CrmIntegrationStore.OutboxEvent event,
            IntegrationErrorCode errorCode) {
        boolean retryable = errorCode.isRetryable()
                && event.attemptCount() < event.maxAttempts();
        txTemplate.execute(status -> {
            store.failOutboxEvent(
                    event.tenantId(), event.id(), event.version(),
                    event.claimToken(), event.claimedBy(),
                    errorCode.name(), retryable);
            if (!retryable) {
                CrmIntegrationStore.StoredRequest current = store.find(
                                event.tenantId(), event.integrationRequestId())
                        .orElseThrow();
                CrmIntegrationStore.TransitionResult transition = store.transitionStatus(
                        event.tenantId(),
                        event.integrationRequestId(),
                        current.version(),
                        Set.of("DISPATCHED"),
                        "UNAVAILABLE");
                if (!transition.success()) {
                    throw new IntegrationException(
                            IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "Workflow dead-letter request transition conflict");
                }
            }
            return null;
        });
    }

    private IntegrationEnvelope reconstructEnvelope(CrmIntegrationStore.StoredRequest request) {
        if (request.contractName() == null || request.contractName().isBlank()
                || request.contractVersion() == null || request.contractVersion().isBlank()
                || request.causationId() == null || request.causationId().isBlank()
                || request.dataClassification() == null || request.dataClassification().isBlank()
                || request.requiredCapability() == null || request.requiredCapability().isBlank()
                || request.requestedLocale() == null || request.requestedLocale().isBlank()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Stored workflow request is missing required envelope fields");
        }
        Locale locale = Locale.forLanguageTag(request.requestedLocale());
        if (locale.getLanguage().isBlank()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Stored workflow request locale is invalid");
        }
        return new IntegrationEnvelope(
                request.contractName(),
                request.contractVersion(),
                request.tenantId(),
                request.actorId(),
                request.correlationId(),
                request.causationId(),
                request.idempotencyKey(),
                request.sourceEntityType(),
                request.sourceEntityId(),
                request.sourceEntityVersion(),
                request.requestedAt(),
                request.expiresAt(),
                locale,
                request.requiredCapability(),
                request.dataClassification());
    }

    private String workflowType(CrmIntegrationStore.OutboxEvent event) {
        String workflowType = event.payload() == null
                ? null : event.payload().path("workflowType").asText(null);
        if (workflowType == null || workflowType.isBlank()) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "WORKFLOW_DISPATCH payload is missing workflowType");
        }
        try {
            CrmWorkflowUseCases.WorkflowType.valueOf(workflowType);
            return workflowType;
        } catch (IllegalArgumentException error) {
            throw new IntegrationException(
                    IntegrationErrorCode.INVALID_CONTRACT,
                    "Unsupported workflowType: " + workflowType);
        }
    }

    private ObjectNode resultPayload(
            WorkflowIntegrationPort.WorkflowDispatch result,
            String workflowType) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("workflowType", workflowType);
        payload.put("status", result.status().name());
        payload.put("acceptedAt", result.acceptedAt().toString());
        if (result.workflowRunId() != null) {
            payload.put("workflowRunId", result.workflowRunId().toString());
        }
        if (result.errorCode() != null) payload.put("errorCode", result.errorCode());
        return payload;
    }

    private static String safeErrorCode(
            String providerCode,
            IntegrationErrorCode fallback) {
        return providerCode == null || providerCode.isBlank()
                ? fallback.name() : providerCode.strip();
    }

    private static final class RetryableWorkflowDispatchException extends RuntimeException {
        private final IntegrationErrorCode code;

        private RetryableWorkflowDispatchException(IntegrationErrorCode code) {
            super(code.name());
            this.code = code;
        }

        private IntegrationErrorCode code() {
            return code;
        }
    }
}
