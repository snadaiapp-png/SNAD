package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.application.CrmWorkflowUseCases;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import com.sanad.platform.crm.integration.security.WorkflowCallbackSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Authenticated service-only callback endpoint for the central Workflow Engine. */
@RestController
public class CrmWorkflowCallbackController {

    private final ObjectMapper mapper;
    private final WorkflowCallbackSecurity callbackSecurity;
    private final CrmWorkflowUseCases useCases;

    public CrmWorkflowCallbackController(
            ObjectMapper mapper,
            WorkflowCallbackSecurity callbackSecurity,
            CrmWorkflowUseCases useCases) {
        this.mapper = mapper;
        this.callbackSecurity = callbackSecurity;
        this.useCases = useCases;
    }

    @PostMapping(
            value = "/internal/crm/integrations/workflows/callback",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> callback(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-SNAD-Signature") String signature,
            @RequestHeader("X-SNAD-Timestamp") String timestamp,
            @RequestHeader("X-SNAD-Nonce") String nonce,
            @RequestBody String rawBody) {
        try {
            JsonNode body = mapper.readTree(rawBody);
            UUID tenantId = requiredUuid(body, "tenantId");
            UUID workflowRunId = requiredUuid(body, "workflowRunId");
            String status = requiredText(body, "status");
            String correlationId = requiredText(body, "correlationId");
            String contractVersion = requiredText(body, "contractVersion");
            Instant occurredAt = optionalInstant(body, "occurredAt");
            JsonNode result = body.get("result");
            String errorCode = optionalText(body, "errorCode");

            callbackSecurity.verify(
                    authorization,
                    signature,
                    timestamp,
                    nonce,
                    rawBody,
                    tenantId,
                    correlationId,
                    contractVersion);

            useCases.handleWorkflowCallback(
                    tenantId,
                    workflowRunId,
                    correlationId,
                    status,
                    occurredAt,
                    result,
                    errorCode);
            return ResponseEntity.noContent().build();
        } catch (WorkflowCallbackSecurity.CallbackSecurityException error) {
            HttpStatus status = "CALLBACK_REPLAY_DETECTED".equals(error.code())
                    ? HttpStatus.CONFLICT : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("errorCode", error.code()));
        } catch (IntegrationException error) {
            HttpStatus status = HttpStatus.resolve(error.httpStatus());
            return ResponseEntity.status(
                            status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("errorCode", error.errorCode().name()));
        } catch (Exception malformed) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("errorCode", "INVALID_CALLBACK_PAYLOAD"));
        }
    }

    private static UUID requiredUuid(JsonNode body, String field) {
        return UUID.fromString(requiredText(body, field));
    }

    private static String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static String optionalText(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText(null) : null;
    }

    private static Instant optionalInstant(JsonNode body, String field) {
        String value = optionalText(body, field);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
