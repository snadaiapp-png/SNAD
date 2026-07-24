package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Advisory-only boundary to the central AI Gateway. */
public interface AiGatewayPort {
    AiResult request(IntegrationEnvelope envelope, Capability capability, JsonNode minimizedPayload);

    enum Capability { CUSTOMER_SUMMARY, NEXT_BEST_ACTION, SCORING }
    enum Status { AVAILABLE, PARTIAL, UNAVAILABLE, TIMED_OUT, POLICY_DENIED, UNSAFE_OUTPUT }

    record AiResult(
            Status status,
            String generatedText,
            String actionCode,
            String explanation,
            Double confidence,
            Instant generatedAt,
            Instant expiresAt,
            boolean humanConfirmationRequired,
            List<String> sourceReferences,
            String policyVersion,
            String modelVersion
    ) {
        public AiResult {
            status = Objects.requireNonNull(status, "status");
            sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
            if (status != Status.AVAILABLE && status != Status.PARTIAL) {
                generatedText = null;
                actionCode = null;
                explanation = null;
                confidence = null;
                sourceReferences = List.of();
            }
            if (actionCode != null && !actionCode.isBlank() && !humanConfirmationRequired) {
                throw new IllegalArgumentException("Actionable AI output requires human confirmation");
            }
        }
        public boolean actionable() {
            return (status == Status.AVAILABLE || status == Status.PARTIAL)
                    && actionCode != null && !actionCode.isBlank();
        }
    }
}
