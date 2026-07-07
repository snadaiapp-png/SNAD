package com.sanad.platform.crm.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral CRM AI contract.
 *
 * <p>CRM modules must call this contract rather than a model provider directly.
 * Implementations are responsible for policy enforcement, tenant isolation,
 * redaction, audit metadata, cost controls, evaluation, and deterministic
 * fallback behavior.</p>
 */
public interface CrmAiGateway {

    CrmAiResponse execute(CrmAiRequest request);

    record CrmAiRequest(
            String tenantId,
            String useCase,
            String subjectId,
            Map<String, Object> context,
            List<String> allowedFields,
            String correlationId,
            boolean humanConfirmationRequired) {

        public CrmAiRequest {
            tenantId = requireText(tenantId, "tenantId");
            useCase = requireText(useCase, "useCase");
            subjectId = requireText(subjectId, "subjectId");
            correlationId = requireText(correlationId, "correlationId");
            context = Map.copyOf(Objects.requireNonNullElse(context, Map.of()));
            allowedFields = List.copyOf(Objects.requireNonNullElse(allowedFields, List.of()));
        }
    }

    record CrmAiResponse(
            Status status,
            Map<String, Object> output,
            List<String> evidenceReferences,
            String modelReference,
            double confidence,
            String explanation,
            boolean humanConfirmationRequired) {

        public CrmAiResponse {
            status = Objects.requireNonNull(status, "status");
            output = Map.copyOf(Objects.requireNonNullElse(output, Map.of()));
            evidenceReferences = List.copyOf(Objects.requireNonNullElse(evidenceReferences, List.of()));
            modelReference = Objects.requireNonNullElse(modelReference, "none");
            explanation = Objects.requireNonNullElse(explanation, "");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
        }
    }

    enum Status {
        GENERATED,
        FALLBACK,
        REJECTED
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
