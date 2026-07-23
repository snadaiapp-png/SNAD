package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral boundary to the central Workflow Engine. */
public interface WorkflowIntegrationPort {
    WorkflowDispatch dispatch(IntegrationEnvelope envelope, String workflowType, JsonNode minimizedPayload);
    void cancel(UUID tenantId, UUID workflowRunId, String correlationId, String reason);

    record WorkflowDispatch(UUID workflowRunId, Status status, Instant acceptedAt, String errorCode) {
        public WorkflowDispatch {
            status = Objects.requireNonNull(status, "status");
            acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
            if ((status == Status.ACCEPTED || status == Status.COMPLETED) && workflowRunId == null) {
                throw new IllegalArgumentException("workflowRunId required for accepted dispatch");
            }
        }
        public boolean permitsMutation() { return status == Status.COMPLETED; }
    }

    enum Status { ACCEPTED, COMPLETED, REJECTED, UNAVAILABLE, TIMED_OUT }
}
