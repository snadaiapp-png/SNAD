package com.sanad.platform.crm.calls.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanad.platform.crm.calls.application.CallEventService;
import com.sanad.platform.crm.calls.domain.CallDirection;
import com.sanad.platform.crm.calls.domain.CallEvent;
import com.sanad.platform.crm.calls.domain.CallStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G8 call event APIs (G8-03 §27–§29) — provider-neutral, tenant-scoped,
 * read + idempotent ingestion only (PATCH/disposition surface follows when
 * the design needs it).
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CallEventController {

    static final String CAP_READ = "CRM.CALL_EVENT.READ";
    static final String CAP_WRITE = "CRM.CALL_EVENT.WRITE";

    private static final int PAGE_LIMIT = 50;

    private final CallEventService service;

    public CallEventController(CallEventService service) {
        this.service = service;
    }

    @PostMapping("/calls/events")
    @RequireCapability(CAP_WRITE)
    public ResponseEntity<CallEventView> ingest(@RequestBody IngestCallEventRequest request,
                                                Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID userId = userId(authentication);
        CallEventService.IngestResult result = service.ingest(tenantId, userId,
                new CallEventService.IngestCommand(
                        request.provider(), request.providerCallId(), request.direction(), request.source(),
                        request.phone(), request.toNumber(), request.status(), request.occurredAt(),
                        request.deviceId(), request.agentUserId()));
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .body(CallEventView.from(result.event()));
    }

    @GetMapping("/calls/{callId}")
    @RequireCapability(CAP_READ)
    public CallEventView get(@PathVariable UUID callId, Authentication authentication) {
        return CallEventView.from(service.get(tenantId(authentication), callId));
    }

    @GetMapping("/calls")
    @RequireCapability(CAP_READ)
    public CallEventListResponse list(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "50") int limit,
                                      Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        long cursorMs = 0;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split(":", 2);
            try {
                cursorMs = Long.parseLong(parts[0]);
                cursorId = parts.length > 1 ? UUID.fromString(parts[1]) : null;
            } catch (RuntimeException exception) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "cursor is invalid.");
            }
        }
        int bounded = Math.min(limit, 100);
        List<CallEvent> events = service.list(tenantId, status, cursorMs, cursorId, bounded + 1);
        boolean hasMore = events.size() > bounded;
        List<CallEventView> items = events.stream().limit(bounded).map(CallEventView::from).toList();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            CallEventView last = items.get(items.size() - 1);
            nextCursor = Base64.getUrlEncoder().encodeToString(
                    (last.createdAtMs() + ":" + last.id()).getBytes());
        }
        return new CallEventListResponse(items, nextCursor, hasMore);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static UUID tenantId(Authentication authentication) {
        return contextId(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return contextId(authentication, "user_id");
    }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }

    // ── contracts ─────────────────────────────────────────────────────────

    public record IngestCallEventRequest(
            String provider,
            String providerCallId,
            CallDirection direction,
            CallEvent.CallerSourceOfRecord source,
            String phone,
            String toNumber,
            CallStatus status,
            Instant occurredAt,
            String deviceId,
            UUID agentUserId) {
    }

    /** Response view — phone numbers are masked server-side (G8-03 §7). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CallEventView(
            UUID id,
            String provider,
            String providerCallId,
            String direction,
            String source,
            String fromNumberMasked,
            String toNumberMasked,
            String matchStatus,
            String matchedEntityType,
            UUID matchedEntityId,
            String matchSource,
            UUID agentUserId,
            String status,
            Instant ringingAt,
            Instant answeredAt,
            Instant endedAt,
            Integer durationSeconds,
            String disposition,
            long version,
            long createdAtMs) {

        static CallEventView from(CallEvent event) {
            return new CallEventView(
                    event.id(), event.provider(), event.providerCallId(),
                    event.direction().name(), event.source().name(),
                    mask(event.fromNumberNormalized()), mask(event.toNumberNormalized()),
                    event.matchStatus(), event.matchedEntityType(), event.matchedEntityId(),
                    event.matchSource(), event.agentUserId(), event.status().name(),
                    event.ringingAt(), event.answeredAt(), event.endedAt(),
                    event.durationSeconds(),
                    event.disposition() == null ? null : event.disposition().name(),
                    event.version(),
                    event.createdAt() == null ? 0 : event.createdAt().toEpochMilli());
        }

        private static String mask(String number) {
            if (number == null) return null;
            return number.length() <= 7 ? "••••" : "••••" + number.substring(number.length() - 4);
        }
    }

    public record CallEventListResponse(List<CallEventView> items, String nextCursor, boolean hasMore) {
    }
}
