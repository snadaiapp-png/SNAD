package com.sanad.platform.crm.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.idempotency.IdempotencyRecord;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact HTTP response storage and replay for governed CRM idempotency. */
@Component
public final class CrmIdempotencyHttpSupport {
    private static final TypeReference<Map<String, List<String>>> HEADERS_TYPE = new TypeReference<>() { };

    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final ETagService etags;

    public CrmIdempotencyHttpSupport(
            IdempotencyService idempotency,
            ObjectMapper objectMapper,
            ETagService etags) {
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.etags = etags;
    }

    public Guard begin(
            Authentication authentication,
            String endpoint,
            String idempotencyKey,
            Object requestBody,
            HttpServletRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_KEY_REQUIRED);
        }
        UUID tenantId = requiredContextUuid(authentication, "tenant_id");
        UUID principalId = requiredContextUuid(authentication, "user_id");
        String canonicalBody = writeJson(requestBody == null ? Map.of() : requestBody);
        String method = request == null || request.getMethod() == null ? "POST" : request.getMethod();
        String path = request == null || request.getRequestURI() == null ? endpoint : request.getRequestURI();
        String fingerprint = IdempotencyService.fingerprint(method, path, canonicalBody);
        IdempotencyService.Replay replay = idempotency.begin(
                tenantId, principalId, endpoint, idempotencyKey.trim(), fingerprint);
        return new Guard(requestId(request), replay);
    }

    public <T> ResponseEntity<SingleResponse<T>> replay(Guard guard, Class<T> bodyType) {
        IdempotencyRecord record = guard.hit().record();
        if (record.responseBodyJson() == null || record.responseBodyJson().isBlank()) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR,
                    "Stored idempotency response is incomplete.");
        }
        try {
            JavaType envelopeType = objectMapper.getTypeFactory()
                    .constructParametricType(SingleResponse.class, bodyType);
            SingleResponse<T> envelope = objectMapper.readValue(record.responseBodyJson(), envelopeType);
            return response(record, envelope);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw replayFailure(guard, exception);
        }
    }

    public <T> ResponseEntity<T> replayRaw(Guard guard, Class<T> bodyType) {
        IdempotencyRecord record = guard.hit().record();
        if (record.responseBodyJson() == null || record.responseBodyJson().isBlank()) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR,
                    "Stored idempotency response is incomplete.");
        }
        try {
            T body = objectMapper.readValue(record.responseBodyJson(), bodyType);
            return response(record, body);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw replayFailure(guard, exception);
        }
    }

    public <T> ResponseEntity<SingleResponse<T>> complete(
            Guard guard,
            T body,
            String entityType,
            long version,
            HttpStatus status) {
        SingleResponse<T> envelope = SingleResponse.of(body, guard.requestId());
        HttpHeaders headers = new HttpHeaders();
        UUID id = extractId(body);
        if (id != null && entityType != null && !entityType.isBlank()) {
            headers.setETag(etags.etag(entityType, id, version));
        }
        persist(guard, status, envelope, headers);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(envelope);
    }

    public <T> ResponseEntity<T> completeRaw(
            Guard guard,
            T body,
            HttpStatus status,
            HttpHeaders responseHeaders) {
        HttpHeaders headers = responseHeaders == null ? new HttpHeaders() : responseHeaders;
        persist(guard, status, body, headers);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    public void fail(Guard guard) {
        if (guard.replay() instanceof IdempotencyService.Replay.ReplayMiss miss) {
            idempotency.fail(miss.operationId());
        }
    }

    private void persist(Guard guard, HttpStatus status, Object body, HttpHeaders headers) {
        IdempotencyService.Replay.ReplayMiss miss = guard.miss();
        idempotency.complete(
                miss.operationId(),
                status.value(),
                writeJson(body),
                writeJson(headers),
                MediaType.APPLICATION_JSON_VALUE);
    }

    private <T> ResponseEntity<T> response(IdempotencyRecord record, T body) throws JsonProcessingException {
        HttpHeaders headers = readHeaders(record.responseHeadersJson());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(record.responseStatus()).headers(headers);
        if (record.contentType() != null && !record.contentType().isBlank()) {
            builder.contentType(MediaType.parseMediaType(record.contentType()));
        }
        return builder.body(body);
    }

    private CrmContractException replayFailure(Guard guard, Exception exception) {
        return new CrmContractException(
                CrmErrorCode.INTERNAL_ERROR,
                "Stored idempotency response cannot be replayed.",
                guard.requestId(),
                exception);
    }

    private HttpHeaders readHeaders(String json) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        if (json == null || json.isBlank()) return headers;
        Map<String, List<String>> values = objectMapper.readValue(json, HEADERS_TYPE);
        values.forEach(headers::put);
        return headers;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR,
                    "Unable to serialize CRM idempotency data.",
                    null,
                    exception);
        }
    }

    private static UUID requiredContextUuid(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Generate a valid request id below.
                }
            }
        }
        return UUID.randomUUID();
    }

    private static UUID extractId(Object dto) {
        if (dto == null || !dto.getClass().isRecord()) return null;
        try {
            for (var component : dto.getClass().getRecordComponents()) {
                if ("id".equals(component.getName()) && component.getType() == UUID.class) {
                    return (UUID) component.getAccessor().invoke(dto);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    public record Guard(UUID requestId, IdempotencyService.Replay replay) {
        public boolean isReplay() {
            return replay instanceof IdempotencyService.Replay.ReplayHit;
        }

        public IdempotencyService.Replay.ReplayHit hit() {
            if (replay instanceof IdempotencyService.Replay.ReplayHit hit) return hit;
            throw new IllegalStateException("Idempotency operation is not a replay");
        }

        public IdempotencyService.Replay.ReplayMiss miss() {
            if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) return miss;
            throw new IllegalStateException("Idempotency operation is not new");
        }
    }
}
