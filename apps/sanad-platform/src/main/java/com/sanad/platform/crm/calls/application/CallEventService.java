package com.sanad.platform.crm.calls.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository.CreateActivityCommand;
import com.sanad.platform.crm.caller.application.CallerIdentificationService;
import com.sanad.platform.crm.caller.application.CallerLookupResult;
import com.sanad.platform.crm.caller.domain.CallerLookupSource;
import com.sanad.platform.crm.caller.domain.CallerMatchStatus;
import com.sanad.platform.crm.calls.domain.CallDirection;
import com.sanad.platform.crm.calls.domain.CallDisposition;
import com.sanad.platform.crm.calls.domain.CallEvent;
import com.sanad.platform.crm.calls.domain.CallEventRepository;
import com.sanad.platform.crm.calls.domain.CallStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.PhoneNumberNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Call event lifecycle orchestrator (G8-03 §19).
 *
 * <p>Serves the provider-neutral ingestion path with P0 gates —
 * idempotency (unique tenant/provider/provider_call_id), monotonic state
 * machine (no regression from out-of-order events), atomic persistence, and
 * one-time business projections (activity/timeline/audit). Matching REUSES
 * {@link CallerIdentificationService} — no second engine is written.
 */
@Service
public class CallEventService {

    private static final Logger log = LoggerFactory.getLogger(CallEventService.class);

    private static final String TAG_SOURCE = "source";

    private final CallEventRepository repository;
    private final CallerIdentificationService callerIdentification;
    private final ActivityUseCases activities;
    private final TimelineEventPort timeline;
    private final AuditPort audit;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper mapper;

    public CallEventService(
            CallEventRepository repository,
            CallerIdentificationService callerIdentification,
            ActivityUseCases activities,
            TimelineEventPort timeline,
            AuditPort audit,
            MeterRegistry meterRegistry,
            ObjectMapper mapper) {
        this.repository = repository;
        this.callerIdentification = callerIdentification;
        this.activities = activities;
        this.timeline = timeline;
        this.audit = audit;
        this.meterRegistry = meterRegistry;
        this.mapper = mapper;
    }

    /** Provider-neutral ingestion command (G8-03 §29 — no provider specifics). */
    public record IngestCommand(
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

    /** Ingestion outcome — {@code replayed} distinguishes retries (200) from creation (201). */
    public record IngestResult(CallEvent event, boolean replayed) {
    }

    /**
     * Idempotent, atomic, state-aware ingestion.
     *
     * @return the call aggregate; an existing aggregate is returned unchanged
     *         for retries/duplicates (replay semantics — provider_call_id gate)
     */
    @Transactional
    public IngestResult ingest(UUID tenantId, UUID actorId, IngestCommand command) {
        validate(command);
        Instant now = Instant.now();

        // P0 idempotency gate (unique tenant/provider/provider_call_id).
        CallEvent existing = repository
                .findByProviderCallId(tenantId, command.provider(), command.providerCallId())
                .orElse(null);
        if (existing != null) {
            if (existing.status() == command.status()) {
                // Retry/duplicate of the SAME state — replay, no new projections.
                meterRegistry.counter("call_event_duplicate_total", TAG_SOURCE, command.source().name()).increment();
                log.debug("CALL_EVENT_DUPLICATE provider={} callId={} tenant={}",
                        command.provider(), command.providerCallId(), tenantId);
                return new IngestResult(existing, true);
            }
            // Lifecycle continuation: the same endpoint drives the state
            // machine for an existing aggregate (G8-03 §27 contract).
            return new IngestResult(transition(tenantId, actorId, command.provider(),
                    command.providerCallId(), command.status(), command.occurredAt()), true);
        }

        // Normalize + bind the caller match (reuse Track A/B engine, inbound only).
        Snapshot match = resolveMatch(tenantId, actorId, command);

        CallStatus initialState = command.status();
        Instant ringingAt = command.direction() == CallDirection.INBOUND ? command.occurredAt() : null;
        Instant answeredAt = initialState == CallStatus.ANSWERED ? command.occurredAt() : null;

        CallEvent event = new CallEvent(
                UUID.randomUUID(), tenantId, 0L,
                command.provider(), command.providerCallId(),
                command.direction(), command.source(),
                match.fromNormalized(), match.toNormalized(),
                match.status(), match.entityType(), match.entityId(),
                match.contactId(), match.accountId(), match.sourceLabel(),
                command.agentUserId(), parseDevice(command.deviceId()),
                initialState,
                ringingAt, answeredAt, null, null, null,
                actorId, actorId, now, now);
        repository.create(tenantId, actorId, event, now);
        meterRegistry.counter("call_event_created_total", TAG_SOURCE, command.source().name()).increment();

        // Business-significant timeline (only for matched callers; no retry noise).
        if (match.entityId() != null) {
            timeline.record(tenantId, match.entityType(), match.entityId(),
                    CallEvent.timelineEventFor(initialState),
                    "Call " + initialState.name().toLowerCase() + " from inbound number",
                    "CRM_CALL_EVENT", event.id(), actorId, now);
        }
        audit(tenantId, actorId, event, "CALL_EVENT_CREATED", null);
        return new IngestResult(event, false);
    }

    /** Applies the monotonic state machine (G8-03 §10–§11, §26). */
    @Transactional
    public CallEvent transition(UUID tenantId, UUID actorId, String provider, String providerCallId,
                                CallStatus toStatus, Instant occurredAt) {
        Instant now = Instant.now();
        CallEvent current = repository
                .findByProviderCallId(tenantId, provider, providerCallId)
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CALL_EVENT_NOT_FOUND,
                        "No call event found for the given provider call id."));

        if (current.status() == toStatus) {
            // Duplicate event of the same state — idempotent replay.
            meterRegistry.counter("call_event_duplicate_total", TAG_SOURCE, current.source().name()).increment();
            return current;
        }
        if (!CallStatus.isAllowedTransition(current.status(), toStatus)) {
            // Out-of-order (rank regression) or illegal transition.
            boolean regression = rank(toStatus) < rank(current.status());
            if (regression) {
                meterRegistry.counter("call_event_transition_rejected_total", TAG_SOURCE, current.source().name()).increment();
                log.debug("CALL_EVENT_OUT_OF_ORDER current={} incoming={} tenant={}",
                        current.status(), toStatus, tenantId);
                return current; // state monotonicity — never regress a confirmed state
            }
            meterRegistry.counter("call_event_transition_rejected_total", TAG_SOURCE, current.source().name()).increment();
            throw new CrmContractException(CrmErrorCode.CALL_EVENT_INVALID_TRANSITION,
                    "Call status transition from " + current.status() + " to " + toStatus + " is not allowed.");
        }

        if (toStatus.isTerminal()) {
            int durationSeconds = 0;
            if (current.answeredAt() != null) {
                durationSeconds = (int) Math.max(0, Duration.between(current.answeredAt(), occurredAt).getSeconds());
            }
            CallDisposition disposition = dispositionFor(toStatus);
            CallEvent completed = repository.complete(tenantId, current.id(), current.version(), actorId,
                    toStatus, occurredAt, durationSeconds, disposition, now);
            meterRegistry.counter("call_event_completed_total", TAG_SOURCE, completed.source().name()).increment();
            projectBusinessOutcome(tenantId, actorId, completed);
            audit(tenantId, actorId, completed, "CALL_DISPOSITION_UPDATED", current.status());
            return completed;
        }

        CallEvent after = repository.transition(tenantId, current.id(), current.version(), actorId,
                toStatus, occurredAt, now);
        if (after.matchedEntityId() != null) {
            timeline.record(tenantId, after.matchedEntityType(), after.matchedEntityId(),
                    CallEvent.timelineEventFor(toStatus),
                    "Call " + toStatus.name().toLowerCase(),
                    "CRM_CALL_EVENT", after.id(), actorId, now);
        }
        audit(tenantId, actorId, after, "CALL_EVENT_STATUS_CHANGED", current.status());
        return after;
    }

    public CallEvent get(UUID tenantId, UUID callId) {
        return repository.get(tenantId, callId)
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CALL_EVENT_NOT_FOUND,
                        "The requested call event was not found."));
    }

    /** Bounded, cursor-stable listing (created_at DESC). */
    public List<CallEvent> list(UUID tenantId, String status, long cursorMs, UUID cursorId, int limit) {
        return repository.list(tenantId, status, cursorMs, cursorId, Math.min(limit, CallEventRepository.MAX_LIST_LIMIT));
    }

    // ── internals ─────────────────────────────────────────────────────────

    private record Snapshot(String status, String entityType, UUID entityId, UUID contactId,
                            UUID accountId, String sourceLabel, String fromNormalized, String toNormalized) {}

    private Snapshot resolveMatch(UUID tenantId, UUID actorId, IngestCommand command) {
        String toNormalized = normalizeOptional(command.toNumber());
        if (command.direction() != CallDirection.INBOUND || command.phone() == null || command.phone().isBlank()) {
            return new Snapshot(CallEvent.MATCH_UNKNOWN, null, null, null, null, null, null, toNormalized);
        }
        String phone = command.phone().trim();
        CallerLookupResult result = callerIdentification.lookup(
                tenantId, actorId, phone, "SA", sourceOf(command.source()), false);
        String matchStatus = switch (result.matchStatus()) {
            case EXACT -> CallEvent.MATCH_EXACT;
            case AMBIGUOUS -> CallEvent.MATCH_AMBIGUOUS;
            case PRIVATE_NUMBER -> CallEvent.MATCH_PRIVATE;
            case INVALID_NUMBER -> CallEvent.MATCH_INVALID;
            case RESTRICTED -> CallEvent.MATCH_RESTRICTED;
            default -> CallEvent.MATCH_UNKNOWN;
        };
        String fromNormalized = PhoneNumberNormalizer.normalizePhone(phone, "SA");
        if (matchStatus.equals(CallEvent.MATCH_EXACT) && result.entityId() != null) {
            return new Snapshot(matchStatus, result.entityType(), result.entityId(),
                    "CONTACT".equals(result.entityType()) ? result.entityId() : null,
                    result.accountId(), result.matchSource(), fromNormalized, toNormalized);
        }
        return new Snapshot(matchStatus, null, null, null, null, null, fromNormalized, toNormalized);
    }

    private static CallerLookupSource sourceOf(CallEvent.CallerSourceOfRecord source) {
        return switch (source) {
            case ANDROID_CALL -> CallerLookupSource.ANDROID_CALL;
            case IOS_CALLER_EXTENSION -> CallerLookupSource.IOS_CALLER_EXTENSION;
            case PBX -> CallerLookupSource.PBX;
            case VOIP -> CallerLookupSource.VOIP;
            default -> CallerLookupSource.MANUAL;
        };
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return PhoneNumberNormalizer.normalizePhone(value.trim(), "SA");
    }

    private static int rank(CallStatus status) {
        return switch (status) {
            case RINGING -> 0;
            case ANSWERED -> 1;
            case COMPLETED -> 2;
            default -> 3;
        };
    }

    private static CallDisposition dispositionFor(CallStatus status) {
        return switch (status) {
            case COMPLETED -> CallDisposition.CONNECTED;
            case MISSED -> CallDisposition.NO_ANSWER;
            case REJECTED -> CallDisposition.REJECTED;
            case BUSY -> CallDisposition.BUSY;
            case FAILED -> CallDisposition.FAILED;
            default -> null;
        };
    }

    /**
     * One logical CALL activity per real call, created exactly once at the
     * terminal transition (monotonic — retries never reproduce it).
     */
    private void projectBusinessOutcome(UUID tenantId, UUID actorId, CallEvent completed) {
        String relatedType = completed.matchedEntityType();
        UUID relatedId = completed.matchedEntityId();
        String subject = completed.direction() == CallDirection.INBOUND
                ? "Inbound call" : "Outbound call";
        String body = "Status: " + completed.status()
                + (completed.durationSeconds() != null ? ", duration: " + completed.durationSeconds() + "s" : "");
        activities.create(tenantId, actorId, new CreateActivityCommand(
                "CALL", subject, body,
                relatedType == null || relationSafe(relatedType) ? relatedType : null,
                relatedId,
                completed.agentUserId() != null ? completed.agentUserId() : actorId,
                null,
                completed.ringingAt() == null ? null : java.time.OffsetDateTime.ofInstant(completed.ringingAt(), java.time.ZoneOffset.UTC),
                null));
        if (completed.matchedEntityId() != null) {
            timeline.record(tenantId, completed.matchedEntityType(), completed.matchedEntityId(),
                    CallEvent.timelineEventFor(completed.status()),
                    "Call " + completed.status().name().toLowerCase()
                            + (completed.durationSeconds() != null ? " (" + completed.durationSeconds() + "s)" : ""),
                    "CRM_CALL_EVENT", completed.id(), actorId, Instant.now());
        }
    }

    private static boolean relationSafe(String entityType) {
        return "ACCOUNT".equals(entityType) || "CONTACT".equals(entityType)
                || "LEAD".equals(entityType) || "OPPORTUNITY".equals(entityType);
    }

    private void audit(UUID tenantId, UUID actorId, CallEvent event, String action, CallStatus before) {
        ObjectNode after = mapper.createObjectNode();
        after.put("callId", event.id().toString());
        after.put("status", event.status().name());
        after.put("direction", event.direction().name());
        after.put("matchStatus", event.matchStatus());
        if (event.matchedEntityId() != null) {
            after.put("matchedEntityType", event.matchedEntityType());
            after.put("matchedEntityId", event.matchedEntityId().toString());
            after.put("matchSource", event.matchSource());
        }
        if (event.durationSeconds() != null) after.put("durationSeconds", event.durationSeconds());
        if (event.disposition() != null) after.put("disposition", event.disposition().name());
        // NO phone numbers in audit (G8-03 §24).
        ObjectNode beforeNode = before == null ? null : mapper.createObjectNode().put("status", before.name());
        audit.record(tenantId, actorId, action, "CRM_CALL_EVENT", event.id(),
                new AuditPort.AuditChange(beforeNode, after), Instant.now());
    }

    private static UUID parseDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return null;
        try {
            return UUID.fromString(deviceId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void validate(IngestCommand command) {
        if (command.provider() == null || command.provider().isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "provider is required.");
        }
        if (command.providerCallId() == null || command.providerCallId().isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "providerCallId is required.");
        }
        if (command.direction() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "direction is required.");
        }
        if (command.source() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "source is required.");
        }
        if (command.status() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "status is required.");
        }
        if (command.occurredAt() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "occurredAt is required.");
        }
    }
}
