package com.sanad.platform.crm.integration.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CRM-facing port for the central AI Gateway.
 * CRM must never call a model provider directly or treat advisory output as an executable command.
 */
public interface AiGatewayPort {

    AiResult request(IntegrationEnvelope envelope, AiRequest request);

    record AiRequest(
            UseCase useCase,
            Map<String, Object> minimizedProjection,
            List<String> permittedSourceReferences,
            boolean humanConfirmationRequired
    ) {
        public AiRequest {
            useCase = Objects.requireNonNull(useCase, "useCase");
            minimizedProjection = Map.copyOf(Objects.requireNonNullElse(minimizedProjection, Map.of()));
            permittedSourceReferences = List.copyOf(
                    Objects.requireNonNullElse(permittedSourceReferences, List.of()));
        }
    }

    record AiResult(
            Status status,
            String advisoryText,
            String actionCode,
            String explanation,
            Double confidence,
            Instant generatedAt,
            Instant expiresAt,
            List<String> sourceReferences,
            String policyDecision,
            boolean humanConfirmationRequired,
            String safeErrorCode
    ) {
        public AiResult {
            status = Objects.requireNonNull(status, "status");
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            sourceReferences = List.copyOf(Objects.requireNonNullElse(sourceReferences, List.of()));
            if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
            if (expiresAt != null && !expiresAt.isAfter(generatedAt)) {
                throw new IllegalArgumentException("expiresAt must be after generatedAt");
            }
            if (status != Status.AVAILABLE) {
                advisoryText = null;
                actionCode = null;
                explanation = null;
                confidence = null;
                sourceReferences = List.of();
            }
        }

        public boolean isAdvisoryOnly() {
            return true;
        }

        public boolean requiresHumanConfirmation() {
            return humanConfirmationRequired || actionCode != null;
        }
    }

    enum UseCase {
        CUSTOMER_SUMMARY,
        NEXT_BEST_ACTION,
        SCORING_EXPLANATION
    }

    enum Status {
        AVAILABLE,
        UNAVAILABLE,
        POLICY_DENIED,
        UNSAFE_OUTPUT,
        PARTIAL,
        TIMEOUT
    }
}
