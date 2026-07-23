package com.sanad.platform.crm.integration.orchestration;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * CRM-facing port for the central Workflow Engine.
 * Implementations orchestrate externally; they must never mutate CRM persistence directly.
 */
public interface WorkflowIntegrationPort {

    WorkflowReference start(IntegrationEnvelope envelope, WorkflowCommand command);

    WorkflowReference cancel(IntegrationEnvelope envelope, UUID workflowReferenceId, String reason);

    WorkflowReference status(IntegrationEnvelope envelope, UUID workflowReferenceId);

    record WorkflowCommand(String workflowType, Map<String, Object> minimizedPayload) {
        public WorkflowCommand {
            if (workflowType == null || workflowType.isBlank()) {
                throw new IllegalArgumentException("workflowType is required");
            }
            minimizedPayload = Map.copyOf(Objects.requireNonNullElse(minimizedPayload, Map.of()));
        }
    }

    record WorkflowReference(
            UUID referenceId,
            Status status,
            long sourceEntityVersion,
            Instant updatedAt,
            String safeErrorCode
    ) {
        public WorkflowReference {
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
            status = Objects.requireNonNull(status, "status");
            if (sourceEntityVersion < 0) {
                throw new IllegalArgumentException("sourceEntityVersion must be non-negative");
            }
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public boolean permitsMutation() {
            return status == Status.APPROVED || status == Status.COMPLETED;
        }
    }

    enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        UNAVAILABLE,
        UNKNOWN
    }
}
