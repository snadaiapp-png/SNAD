package com.sanad.platform.crm.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.idempotency.IdempotencyRecord;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared trusted-context, response, idempotency and concurrency support for CRM-008. */
@Component
final class CrmOwnershipHttpSupport {

    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;
    private final ETagService etags;

    CrmOwnershipHttpSupport(
            IdempotencyService idempotency,
            ObjectMapper mapper,
            ETagService etags) {
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.etags = etags;
    }

    Context context(Authentication authentication) {
        return new Context(
                requiredContextUuid(authentication, "tenant_id"),
                requiredContextUuid(authentication, "user_id"));
    }

    RequestTrace trace(HttpServletRequest request) {
        return new RequestTrace(
                headerUuid(request, "X-Request-ID"),
                headerUuid(request, "X-Correlation-ID"));
    }

    <T> ResponseEntity<OwnershipResponse<T>> single(
            T data,
            RequestTrace trace,
            HttpStatus status,
            String entityType,
            UUID entityId,
            long version) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        if (entityType != null && entityId != null) {
            headers.setETag(etags.etag(entityType, entityId, version));
        }
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(OwnershipResponse.of(data, trace));
    }

    <T> ResponseEntity<OwnershipListResponse<T>> list(
            List<T> data,
            String nextCursor,
            boolean hasMore,
            int limit,
            RequestTrace trace) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(OwnershipListResponse.of(data, nextCursor, hasMore, limit, trace));
    }

    void validateIfMatch(
            String header,
            String entityType,
            UUID id,
            long version) {
        etags.validateIfMatch(header, entityType, id, version);
    }

    long timestampVersion(Instant value) {
        if (value == null) return 0L;
        Instant micros = value.truncatedTo(ChronoUnit.MICROS);
        return Math.addExact(
                Math.multiplyExact(micros.getEpochSecond(), 1_000_000L),
                micros.getNano() / 1_000L);
    }

    Guard begin(
            Authentication authentication,
            String endpoint,
            String key,
            Object body,
            HttpServletRequest request) {
        if (key == null || key.isBlank()) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_KEY_REQUIRED);
        }
        Context context = context(authentication);
        String canonical = json(body == null ? Map.of() : body);
        String method = request == null || request.getMethod() == null ? "POST" : request.getMethod();
        String path = request == null || request.getRequestURI() == null ? endpoint : request.getRequestURI();
        IdempotencyService.Replay replay = idempotency.begin(
                context.tenantId(), context.userId(), endpoint, key.trim(),
                IdempotencyService.fingerprint(method, path, canonical));
        return new Guard(trace(request), replay);
    }

    <T> ResponseEntity<OwnershipResponse<T>> replay(Guard guard, Class<T> bodyType) {
        IdempotencyRecord record = guard.hit().record();
        try {
            JavaType type = mapper.getTypeFactory()
                    .constructParametricType(OwnershipResponse.class, bodyType);
            OwnershipResponse<T> body = mapper.readValue(record.responseBodyJson(), type);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Request-ID", guard.trace().requestId().toString());
            headers.set("X-Correlation-ID", guard.trace().correlationId().toString());
            if (record.responseHeadersJson() != null && !record.responseHeadersJson().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> values = mapper.readValue(
                        record.responseHeadersJson(), Map.class);
                values.forEach(headers::put);
            }
            return ResponseEntity.status(record.responseStatus())
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (Exception invalid) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR,
                    "Stored ownership idempotency response cannot be replayed.",
                    guard.trace().requestId(), invalid);
        }
    }

    <T> ResponseEntity<OwnershipResponse<T>> complete(
            Guard guard,
            T data,
            Class<T> bodyType,
            HttpStatus status,
            String entityType,
            UUID entityId,
            long version) {
        ResponseEntity<OwnershipResponse<T>> response = single(
                data, guard.trace(), status, entityType, entityId, version);
        IdempotencyService.Replay.ReplayMiss miss = guard.miss();
        idempotency.complete(
                miss.operationId(), status.value(), json(response.getBody()),
                json(response.getHeaders()), MediaType.APPLICATION_JSON_VALUE);
        return response;
    }

    void fail(Guard guard) {
        if (guard != null && guard.replay() instanceof IdempotencyService.Replay.ReplayMiss miss) {
            idempotency.fail(miss.operationId());
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR,
                    "Unable to serialize ownership HTTP data.", null, exception);
        }
    }

    private UUID requiredContextUuid(Authentication authentication, String key) {
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

    private UUID headerUuid(HttpServletRequest request, String name) {
        if (request != null) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException invalid) {
                    throw new CrmContractException(
                            CrmErrorCode.VALIDATION_ERROR,
                            name + " must be a UUID.");
                }
            }
        }
        return UUID.randomUUID();
    }

    record Context(UUID tenantId, UUID userId) { }

    record RequestTrace(UUID requestId, UUID correlationId) { }

    record OwnershipMeta(UUID requestId, UUID correlationId, Instant timestamp) {
        static OwnershipMeta of(RequestTrace trace) {
            return new OwnershipMeta(trace.requestId(), trace.correlationId(), Instant.now());
        }
    }

    record OwnershipResponse<T>(T data, OwnershipMeta meta) {
        static <T> OwnershipResponse<T> of(T data, RequestTrace trace) {
            return new OwnershipResponse<>(data, OwnershipMeta.of(trace));
        }
    }

    record OwnershipPage(String nextCursor, boolean hasMore, int limit) { }

    record OwnershipListResponse<T>(
            List<T> data,
            OwnershipPage page,
            OwnershipMeta meta) {
        static <T> OwnershipListResponse<T> of(
                List<T> data,
                String nextCursor,
                boolean hasMore,
                int limit,
                RequestTrace trace) {
            return new OwnershipListResponse<>(
                    data == null ? List.of() : List.copyOf(data),
                    new OwnershipPage(nextCursor, hasMore, limit),
                    OwnershipMeta.of(trace));
        }
    }

    record Guard(RequestTrace trace, IdempotencyService.Replay replay) {
        boolean isReplay() {
            return replay instanceof IdempotencyService.Replay.ReplayHit;
        }

        IdempotencyService.Replay.ReplayHit hit() {
            if (replay instanceof IdempotencyService.Replay.ReplayHit hit) return hit;
            throw new IllegalStateException("Idempotency operation is not a replay");
        }

        IdempotencyService.Replay.ReplayMiss miss() {
            if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) return miss;
            throw new IllegalStateException("Idempotency operation is not new");
        }
    }
}
