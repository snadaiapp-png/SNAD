package com.sanad.platform.crm.caller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.caller.application.CallerIdentificationService;
import com.sanad.platform.crm.caller.application.CallerLookupRateLimiter;
import com.sanad.platform.crm.caller.application.CallerLookupResult;
import com.sanad.platform.crm.caller.domain.CallerCandidate;
import com.sanad.platform.crm.caller.domain.CallerIdentificationRepository;
import com.sanad.platform.crm.caller.domain.CallerLookupSource;
import com.sanad.platform.crm.caller.domain.CallerMatchStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.integration.domain.AuditPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Matching-engine unit tests (G8-02 §39).
 */
class CallerIdentificationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String PHONE = "+966541234567";

    private CallerIdentificationRepository repository;
    private AuditPort audit;
    private CallerIdentificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(CallerIdentificationRepository.class);
        audit = mock(AuditPort.class);
        service = new CallerIdentificationService(
                repository,
                new CallerLookupRateLimiter(),
                audit,
                new SimpleMeterRegistry(),
                new ObjectMapper());
    }

    @Test
    void exactVerifiedContactWinsWhenSingleCandidate() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-1", true, false, "INTERNAL")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.entityType()).isEqualTo("CONTACT");
        assertThat(result.entityId()).isEqualTo(UUID.nameUUIDFromBytes("c-1".getBytes()));
        assertThat(result.displayName()).isEqualTo("محمد أحمد");
        assertThat(result.verified()).isTrue();
        assertThat(result.matchSource()).isEqualTo(CallerCandidate.SOURCE_CANONICAL);
    }

    @Test
    void exactUnverifiedContactIsStillExact() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-2", false, false, "INTERNAL")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.verified()).isFalse();
    }

    @Test
    void preferredBeatsNonPreferredWithinTheSamePerson() {
        CallerCandidate preferred = person("c-3", false, true, "INTERNAL");
        CallerCandidate other = person("c-3", false, false, "INTERNAL");
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of(other, preferred));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.preferred()).isTrue();
    }

    @Test
    void verifiedPersonBeatsAccount() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of(
                account("a-1"),
                person("c-4", true, false, "INTERNAL")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.entityType()).isEqualTo("CONTACT");
    }

    @Test
    void accountMatchesWhenNoPersonCandidateExists() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(account("a-2")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.entityType()).isEqualTo("ACCOUNT");
        assertThat(result.accountName()).isEqualTo("شركة سند");
    }

    @Test
    void leadFallbackIsExplicitLowerPriorityAndTagged() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of());
        when(repository.findActiveLeadCandidates(TENANT, PHONE)).thenReturn(List.of(lead("l-1")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(result.entityType()).isEqualTo("LEAD");
        assertThat(result.matchSource()).isEqualTo(CallerCandidate.SOURCE_LEGACY_LEAD_PHONE);
    }

    @Test
    void leadFallbackIsNotConsultedWhenCanonicalHasCandidates() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-5", false, false, "INTERNAL")));

        lookup(PHONE);

        verify(repository, never()).findActiveLeadCandidates(TENANT, PHONE);
    }

    @Test
    void sameRankingDuplicatesAreAmbiguousWithoutCandidateDetails() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of(
                person("c-6", true, false, "INTERNAL"),
                person("c-7", true, false, "INTERNAL")));

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.AMBIGUOUS);
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.entityId()).isNull();
        assertThat(result.displayName()).isNull();
    }

    @Test
    void overflowBeyondBoundedCandidatesIsAmbiguous() {
        List<CallerCandidate> many = new ArrayList<>();
        for (int i = 0; i < CallerIdentificationRepository.CANDIDATE_LOOKUP_LIMIT + 1; i++) {
            many.add(person("c-" + i, true, false, "INTERNAL"));
        }
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(many);

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.AMBIGUOUS);
        assertThat(result.candidateCount()).isEqualTo(CallerIdentificationRepository.CANDIDATE_LOOKUP_LIMIT);
    }

    @Test
    void noMatchIsUnknown() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of());
        when(repository.findActiveLeadCandidates(TENANT, PHONE)).thenReturn(List.of());

        CallerLookupResult result = lookup(PHONE);

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.UNKNOWN);
    }

    @Test
    void privateNumberShortCircuitsBeforeRepository() {
        CallerLookupResult result = lookup("PRIVATE");

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.PRIVATE_NUMBER);
        verifyNoInteractions(repository);
    }

    @Test
    void withheldIsTreatedAsPrivateNumber() {
        assertThat(lookup("WITHHELD").matchStatus()).isEqualTo(CallerMatchStatus.PRIVATE_NUMBER);
        assertThat(lookup("blocked").matchStatus()).isEqualTo(CallerMatchStatus.PRIVATE_NUMBER);
        assertThat(lookup("ANONYMOUS").matchStatus()).isEqualTo(CallerMatchStatus.PRIVATE_NUMBER);
    }

    @Test
    void invalidNumberIsReportedWithoutLookup() {
        CallerLookupResult result = lookup("not-a-number");

        assertThat(result.matchStatus()).isEqualTo(CallerMatchStatus.INVALID_NUMBER);
        verifyNoInteractions(repository);
    }

    @Test
    void restrictedRecordIsRestrictedWithoutCapabilityAndDisclosedWithIt() {
        CallerCandidate restricted = person("c-8", true, false, "RESTRICTED");
        when(repository.findActiveCallerCandidates(TENANT, PHONE)).thenReturn(List.of(restricted));

        CallerLookupResult denied = service.lookup(TENANT, USER, PHONE, "SA",
                CallerLookupSource.MANUAL, false);
        assertThat(denied.matchStatus()).isEqualTo(CallerMatchStatus.RESTRICTED);
        assertThat(denied.displayName()).isNull();

        CallerLookupResult allowed = service.lookup(TENANT, USER, PHONE, "SA",
                CallerLookupSource.MANUAL, true);
        assertThat(allowed.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(allowed.displayName()).isEqualTo("محمد أحمد");
        assertThat(allowed.privacyLevel()).isEqualTo("RESTRICTED");
    }

    @Test
    void confidentialIsMaskedServerSideWithoutCapability() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-9", true, false, "CONFIDENTIAL", "شركة سرية")));

        CallerLookupResult masked = service.lookup(TENANT, USER, PHONE, "SA",
                CallerLookupSource.MANUAL, false);

        assertThat(masked.matchStatus()).isEqualTo(CallerMatchStatus.EXACT);
        assertThat(masked.displayName()).isNotEqualTo("محمد أحمد");
        assertThat(masked.accountName()).isNotEqualTo("شركة سرية");
        assertThat(masked.privacyLevel()).isEqualTo("CONFIDENTIAL");

        CallerLookupResult full = service.lookup(TENANT, USER, PHONE, "SA",
                CallerLookupSource.MANUAL, true);
        assertThat(full.displayName()).isEqualTo("محمد أحمد");
        assertThat(full.accountName()).isEqualTo("شركة سرية");
    }

    @Test
    void auditNeverContainsTheFullPhone() {
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-a", true, false, "INTERNAL")));

        lookup(PHONE);

        ArgumentCaptor<AuditPort.AuditChange> captor = ArgumentCaptor.forClass(AuditPort.AuditChange.class);
        verify(audit).record(org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq("crm.caller_identification.lookup"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().afterState().toString()).doesNotContain("541234567");
    }

    @Test
    void legacyLeadPhoneFormsAreDerivedExactly() {
        assertThat(CallerIdentificationRepository.legacyLeadPhoneForms("+966541234567"))
                .containsExactly("+966541234567", "966541234567", "541234567", "0541234567");
        assertThat(CallerIdentificationRepository.legacyLeadPhoneForms(null)).isEmpty();
    }

    @Test
    void rateLimitExceededReturns429() {
        CallerLookupRateLimiter limiter = new CallerLookupRateLimiter(1, 1, java.time.Duration.ofMinutes(1));
        CallerIdentificationService limited = new CallerIdentificationService(
                repository, limiter, audit, new SimpleMeterRegistry(), new ObjectMapper());
        when(repository.findActiveCallerCandidates(TENANT, PHONE))
                .thenReturn(List.of(person("c-x", true, false, "INTERNAL")));

        limited.lookup(TENANT, USER, PHONE, "SA", CallerLookupSource.MANUAL, false);

        assertThatThrownBy(() ->
                limited.lookup(TENANT, USER, PHONE, "SA", CallerLookupSource.MANUAL, false))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("rate limit");
    }

    private CallerLookupResult lookup(String phone) {
        return service.lookup(TENANT, USER, phone, "SA", CallerLookupSource.MANUAL, false);
    }

    private static CallerCandidate person(String suffix, boolean verified, boolean preferred, String privacy) {
        return person(suffix, verified, preferred, privacy, null);
    }

    private static CallerCandidate person(String suffix, boolean verified, boolean preferred,
                                          String privacy, String accountName) {
        UUID contactId = UUID.nameUUIDFromBytes(suffix.getBytes());
        return new CallerCandidate(
                UUID.randomUUID(), "PERSON", contactId, PHONE, "Mobile", preferred, verified,
                verified ? "VERIFIED" : "UNVERIFIED", privacy, "ACTIVE",
                contactId, UUID.randomUUID(), null,
                "محمد أحمد", accountName, UUID.randomUUID(), Instant.parse("2026-08-01T10:00:00Z"),
                CallerCandidate.SOURCE_CANONICAL);
    }

    private static CallerCandidate account(String suffix) {
        UUID accountId = UUID.nameUUIDFromBytes(suffix.getBytes());
        return new CallerCandidate(
                UUID.randomUUID(), "ACCOUNT", accountId, PHONE, "Office", false, false,
                "UNVERIFIED", "INTERNAL", "ACTIVE",
                null, accountId, null,
                "شركة سند", "شركة سند", UUID.randomUUID(), Instant.parse("2026-08-01T10:00:00Z"),
                CallerCandidate.SOURCE_CANONICAL);
    }

    private static CallerCandidate lead(String suffix) {
        UUID leadId = UUID.nameUUIDFromBytes(suffix.getBytes());
        return new CallerCandidate(
                null, "LEAD", leadId, "0541234567", null, false, false,
                "UNVERIFIED", "INTERNAL", "ACTIVE",
                null, null, leadId,
                "عميل محتمل", "شركة ناشئة", UUID.randomUUID(), Instant.parse("2026-08-01T10:00:00Z"),
                CallerCandidate.SOURCE_LEGACY_LEAD_PHONE);
    }
}
