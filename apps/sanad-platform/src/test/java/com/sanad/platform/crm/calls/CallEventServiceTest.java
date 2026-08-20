package com.sanad.platform.crm.calls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.caller.application.CallerIdentificationService;
import com.sanad.platform.crm.caller.application.CallerLookupResult;
import com.sanad.platform.crm.caller.domain.CallerMatchStatus;
import com.sanad.platform.crm.calls.application.CallEventService;
import com.sanad.platform.crm.calls.domain.CallDirection;
import com.sanad.platform.crm.calls.domain.CallDisposition;
import com.sanad.platform.crm.calls.domain.CallEvent;
import com.sanad.platform.crm.calls.domain.CallEventRepository;
import com.sanad.platform.crm.calls.domain.CallStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Call event lifecycle unit tests (G8-03 §62).
 */
class CallEventServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private CallEventRepository repository;
    private CallerIdentificationService caller;
    private ActivityUseCases activities;
    private TimelineEventPort timeline;
    private AuditPort audit;
    private CallEventService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        repository = mock(CallEventRepository.class);
        caller = mock(CallerIdentificationService.class);
        activities = mock(ActivityUseCases.class);
        timeline = mock(TimelineEventPort.class);
        audit = mock(AuditPort.class);
        service = new CallEventService(repository, caller, activities, timeline, audit,
                new SimpleMeterRegistry(), new ObjectMapper());
        now = Instant.parse("2026-08-20T10:00:00Z");
        when(repository.findByProviderCallId(any(), any(), any())).thenReturn(Optional.empty());
        when(caller.lookup(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(CallerLookupResult.empty(CallerMatchStatus.UNKNOWN));
    }

    private CallEventService.IngestCommand inbound(String callId, CallStatus status) {
        return new CallEventService.IngestCommand("NATIVE", callId, CallDirection.INBOUND,
                CallEvent.CallerSourceOfRecord.ANDROID_CALL, "0541234567", null, status, now, null, null);
    }

    @Test
    void createsRingingCallWithUnknownMatchWhenNoCallerData() {
        CallEvent created = service.ingest(TENANT, USER, inbound("c-1", CallStatus.RINGING)).event();

        assertThat(created.status()).isEqualTo(CallStatus.RINGING);
        assertThat(created.matchStatus()).isEqualTo(CallEvent.MATCH_UNKNOWN);
        assertThat(created.fromNumberNormalized()).isEqualTo("+966541234567");
        verify(repository).create(eq(TENANT), eq(USER), any(), any());
    }

    @Test
    void bindsExactMatchSnapshotWhenCallerIsKnown() {
        UUID contactId = UUID.randomUUID();
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(new CallerLookupResult(CallerMatchStatus.EXACT, "CONTACT", contactId,
                        "محمد أحمد", null, null, "Mobile", true, true, "ACTIVE",
                        "INTERNAL", "CANONICAL_COMMUNICATION_METHOD", null));

        CallEvent created = service.ingest(TENANT, USER, inbound("c-2", CallStatus.RINGING)).event();

        assertThat(created.matchStatus()).isEqualTo(CallEvent.MATCH_EXACT);
        assertThat(created.matchedEntityType()).isEqualTo("CONTACT");
        assertThat(created.matchedEntityId()).isEqualTo(contactId);
        verify(timeline).record(eq(TENANT), eq("CONTACT"), eq(contactId),
                eq("crm.call.started"), any(), eq("CRM_CALL_EVENT"), any(), eq(USER), any());
    }

    @Test
    void ambiguousCallerIsSnapshotWithoutIdentityLeak() {
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(CallerLookupResult.ambiguous(2));

        CallEvent created = service.ingest(TENANT, USER, inbound("c-3", CallStatus.RINGING)).event();

        assertThat(created.matchStatus()).isEqualTo(CallEvent.MATCH_AMBIGUOUS);
        assertThat(created.matchedEntityId()).isNull();
    }

    @Test
    void restrictedCallerIsSnapshotWithoutIdentityLeak() {
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(CallerLookupResult.empty(CallerMatchStatus.RESTRICTED));

        CallEvent created = service.ingest(TENANT, USER, inbound("c-4", CallStatus.RINGING)).event();

        assertThat(created.matchStatus()).isEqualTo(CallEvent.MATCH_RESTRICTED);
        assertThat(created.matchedEntityId()).isNull();
    }

    @Test
    void duplicateProviderEventIsIdempotentReplayWithoutNewProjections() {
        CallEvent existing = service.ingest(TENANT, USER, inbound("c-5", CallStatus.RINGING)).event();
        when(repository.findByProviderCallId(any(), any(), eq("c-5"))).thenReturn(Optional.of(existing));

        CallEventService.IngestResult replay = service.ingest(TENANT, USER, inbound("c-5", CallStatus.RINGING));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.event().id()).isEqualTo(existing.id());
        // Create happened exactly once — the replay did not persist anything.
        verify(repository, org.mockito.Mockito.times(1)).create(eq(TENANT), eq(USER), any(), any());
    }

    @Test
    void ringingToAnsweredThenCompletedComputesDurationAndProjectsOnce() {
        UUID contactId = UUID.randomUUID();
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(new CallerLookupResult(CallerMatchStatus.EXACT, "CONTACT", contactId,
                        "محمد أحمد", null, null, "Mobile", true, true, "ACTIVE",
                        "INTERNAL", "CANONICAL_COMMUNICATION_METHOD", null));
        CallEvent created = service.ingest(TENANT, USER, inbound("c-6", CallStatus.RINGING)).event();
        CallEvent answered = new CallEvent(
                created.id(), TENANT, 1L, "NATIVE", "c-6", CallDirection.INBOUND,
                CallEvent.CallerSourceOfRecord.ANDROID_CALL, "+966541234567", null,
                CallEvent.MATCH_EXACT, "CONTACT", contactId, contactId, null,
                "CANONICAL_COMMUNICATION_METHOD", USER, null, CallStatus.ANSWERED,
                now, now.plusSeconds(5), null, null, null, USER, USER, now, now.plusSeconds(5));
        when(repository.get(any(), any())).thenReturn(Optional.of(created));
        when(repository.findByProviderCallId(any(), any(), eq("c-6"))).thenReturn(Optional.of(created));
        when(repository.transition(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(answered);

        CallEvent afterAnswer = service.transition(TENANT, USER, "NATIVE", "c-6", CallStatus.ANSWERED, now.plusSeconds(5));
        assertThat(afterAnswer.status()).isEqualTo(CallStatus.ANSWERED);
        when(repository.findByProviderCallId(any(), any(), eq("c-6"))).thenReturn(Optional.of(answered));

        CallEvent completed = new CallEvent(
                created.id(), TENANT, 2L, "NATIVE", "c-6", CallDirection.INBOUND,
                CallEvent.CallerSourceOfRecord.ANDROID_CALL, "+966541234567", null,
                CallEvent.MATCH_EXACT, "CONTACT", contactId, contactId, null,
                "CANONICAL_COMMUNICATION_METHOD", USER, null, CallStatus.COMPLETED,
                now, now.plusSeconds(5), now.plusSeconds(30), 25, CallDisposition.CONNECTED,
                USER, USER, now, now.plusSeconds(30));
        when(repository.complete(any(), any(), anyLong(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(completed);

        CallEvent done = service.transition(TENANT, USER, "NATIVE", "c-6", CallStatus.COMPLETED, now.plusSeconds(30));

        assertThat(done.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(done.durationSeconds()).isEqualTo(25);
        assertThat(done.disposition()).isEqualTo(CallDisposition.CONNECTED);
        verify(activities).create(eq(TENANT), eq(USER), any());
        verify(timeline).record(eq(TENANT), eq("CONTACT"), eq(contactId),
                eq("crm.call.completed"), any(), eq("CRM_CALL_EVENT"), any(), eq(USER), any());
    }

    @Test
    void terminalTransitionCreatesExactlyOneActivityEvenAfterRetry() {
        UUID contactId = UUID.randomUUID();
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(new CallerLookupResult(CallerMatchStatus.EXACT, "CONTACT", contactId,
                        "محمد أحمد", null, null, "Mobile", true, true, "ACTIVE",
                        "INTERNAL", "CANONICAL_COMMUNICATION_METHOD", null));
        CallEvent preTerminal = service.ingest(TENANT, USER, inbound("c-7", CallStatus.RINGING)).event();
        CallEvent terminal = new CallEvent(
                UUID.randomUUID(), TENANT, 1L, "NATIVE", "c-7", CallDirection.INBOUND,
                CallEvent.CallerSourceOfRecord.ANDROID_CALL, "+966541234567", null,
                CallEvent.MATCH_EXACT, "CONTACT", contactId, contactId, null,
                "CANONICAL_COMMUNICATION_METHOD", USER, null, CallStatus.MISSED,
                now, null, now.plusSeconds(20), null, null, USER, USER, now, now.plusSeconds(20));
        when(repository.findByProviderCallId(any(), any(), eq("c-7"))).thenReturn(Optional.of(preTerminal));
        when(repository.complete(any(), any(), anyLong(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(terminal);

        service.transition(TENANT, USER, "NATIVE", "c-7", CallStatus.MISSED, now.plusSeconds(20));
        // A retried MISSED event is a duplicate → returned as-is, activity NOT re-created.
        when(repository.findByProviderCallId(any(), any(), eq("c-7"))).thenReturn(Optional.of(terminal));

        service.transition(TENANT, USER, "NATIVE", "c-7", CallStatus.MISSED, now.plusSeconds(20));

        verify(activities).create(eq(TENANT), eq(USER), any());
    }

    @Test
    void illegalTransitionIsRejected() {
        CallEvent current = new CallEvent(UUID.randomUUID(), TENANT, 1L, "NATIVE", "c-8",
                CallDirection.INBOUND, CallEvent.CallerSourceOfRecord.ANDROID_CALL,
                "+966541234567", null, CallEvent.MATCH_UNKNOWN, null, null, null, null,
                null, USER, null, CallStatus.RINGING, now, null, null, null,
                null, USER, USER, now, now);
        when(repository.findByProviderCallId(any(), any(), eq("c-8"))).thenReturn(Optional.of(current));

        // RINGING -> COMPLETED skips ANSWERED: illegal (non-regression) transition.
        assertThatThrownBy(() -> service.transition(TENANT, USER, "NATIVE", "c-8",
                CallStatus.COMPLETED, now.plusSeconds(1)))
                .isInstanceOf(CrmContractException.class)
                .extracting(e -> ((CrmContractException) e).code())
                .isEqualTo(CrmErrorCode.CALL_EVENT_INVALID_TRANSITION);
    }

    @Test
    void outOfOrderEventNeverRegressesConfirmedState() {
        service.ingest(TENANT, USER, inbound("c-9", CallStatus.ANSWERED)).event();
        CallEvent answered = new CallEvent(UUID.randomUUID(), TENANT, 1L, "NATIVE", "c-9",
                CallDirection.INBOUND, CallEvent.CallerSourceOfRecord.ANDROID_CALL,
                "+966541234567", null, CallEvent.MATCH_UNKNOWN, null, null, null, null,
                null, USER, null, CallStatus.ANSWERED, now, now, null, null, null, USER, USER, now, now);
        when(repository.findByProviderCallId(any(), any(), eq("c-9"))).thenReturn(Optional.of(answered));

        // A late RINGING webhook must NOT demote the confirmed ANSWERED state.
        CallEvent after = service.transition(TENANT, USER, "NATIVE", "c-9", CallStatus.RINGING, now.plusSeconds(1));

        assertThat(after.status()).isEqualTo(CallStatus.ANSWERED);
        verify(repository, never()).transition(any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void missedCallProjectsBusinessOutcomeWithDisposition() {
        UUID contactId = UUID.randomUUID();
        when(caller.lookup(any(), any(), eq("0541234567"), eq("SA"), any(), eq(false)))
                .thenReturn(new CallerLookupResult(CallerMatchStatus.EXACT, "CONTACT", contactId,
                        "محمد أحمد", null, null, "Mobile", true, true, "ACTIVE",
                        "INTERNAL", "CANONICAL_COMMUNICATION_METHOD", null));
        CallEvent created = service.ingest(TENANT, USER, inbound("c-10", CallStatus.RINGING)).event();
        CallEvent missed = new CallEvent(UUID.randomUUID(), TENANT, 1L, "NATIVE", "c-10",
                CallDirection.INBOUND, CallEvent.CallerSourceOfRecord.ANDROID_CALL,
                "+966541234567", null, CallEvent.MATCH_EXACT, "CONTACT", contactId, contactId, null,
                "CANONICAL_COMMUNICATION_METHOD", USER, null, CallStatus.MISSED,
                now, null, now.plusSeconds(20), null, CallDisposition.NO_ANSWER,
                USER, USER, now, now.plusSeconds(20));
        when(repository.findByProviderCallId(any(), any(), eq("c-10"))).thenReturn(Optional.of(created));
        when(repository.complete(any(), any(), anyLong(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(missed);

        CallEvent result = service.transition(TENANT, USER, "NATIVE", "c-10", CallStatus.MISSED, now.plusSeconds(20));

        assertThat(result.disposition()).isEqualTo(CallDisposition.NO_ANSWER);
        verify(activities).create(eq(TENANT), eq(USER), any());
    }
}
