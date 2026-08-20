package com.sanad.platform.crm.caller.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.caller.domain.CallerCandidate;
import com.sanad.platform.crm.caller.domain.CallerIdentificationRepository;
import com.sanad.platform.crm.caller.domain.CallerLookupSource;
import com.sanad.platform.crm.caller.domain.CallerMatchStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.party.domain.PhoneNumberNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic caller matching engine (G8-02 §11).
 *
 * <p>Chain: raw phone → normalizer (single authority) → candidate lookup
 * (canonical source, exact only) → lead fallback (ADR-002, only when the
 * canonical pool is empty) → tiered ranking (§9 policy) → ambiguity detection
 * (NO_RANDOM_MATCH) → privacy evaluation (server-side masking) → result.
 *
 * <p>READ-ONLY by design in this track: no records are written except audit
 * and metrics. No customer is auto-created.
 */
@Service
public class CallerIdentificationService {

    private static final Logger log = LoggerFactory.getLogger(CallerIdentificationService.class);

    /** Sentinel inputs that represent a withheld/private number (G8-02 §13). */
    private static final Set<String> PRIVATE_SENTINELS = Set.of(
            "PRIVATE", "WITHHELD", "BLOCKED", "ANONYMOUS", "UNKNOWN", "PRIVATE_NUMBER");

    private static final String TAG_RESULT = "result";
    private static final String TAG_SOURCE = "source";

    private final CallerIdentificationRepository repository;
    private final CallerLookupRateLimiter rateLimiter;
    private final AuditPort audit;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper mapper;

    public CallerIdentificationService(
            CallerIdentificationRepository repository,
            CallerLookupRateLimiter rateLimiter,
            AuditPort audit,
            MeterRegistry meterRegistry,
            ObjectMapper mapper) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.meterRegistry = meterRegistry;
        this.mapper = mapper;
    }

    /**
     * Resolves an inbound phone to a minimal caller card.
     *
     * @param tenantId        authenticated tenant (never taken from the client)
     * @param userId          authenticated principal
     * @param phone           raw inbound number, or a private-number sentinel
     * @param countryHint     optional ISO alpha-2 hint (only "SA" is supported)
     * @param source          caller source contract
     * @param allowRestricted true when the caller holds CRM.CALLER_ID.READ_RESTRICTED
     * @return the minimized result; INVALID_NUMBER is returned (controller maps
     *         it to the 422 contract, G8 baseline §12.1)
     * @throws CrmContractException with {@link CrmErrorCode#RATE_LIMITED} when the
     *         anti-enumeration quota is exceeded
     */
    public CallerLookupResult lookup(
            UUID tenantId, UUID userId, String phone, String countryHint,
            CallerLookupSource source, boolean allowRestricted) {
        String sourceName = source == null ? CallerLookupSource.MANUAL.name() : source.name();
        Instant start = Instant.now();
        CallerLookupResult result;
        String logPhone;

        // 1. Private-number sentinel — no normalization, no DB lookup.
        if (phone != null && PRIVATE_SENTINELS.contains(phone.trim().toUpperCase(Locale.ROOT))) {
            logPhone = "••••";
            result = CallerLookupResult.empty(CallerMatchStatus.PRIVATE_NUMBER);
            return finish(result, tenantId, userId, sourceName, start, logPhone);
        }

        // 2. Normalize with the single shared authority (G8-ADR-001 / §8).
        String normalized = phone == null || phone.isBlank()
                ? null : PhoneNumberNormalizer.normalizePhone(phone, countryHint);
        if (normalized == null) {
            logPhone = "••••";
            result = CallerLookupResult.empty(CallerMatchStatus.INVALID_NUMBER);
            return finish(result, tenantId, userId, sourceName, start, logPhone);
        }
        logPhone = maskNumber(normalized);

        // 3. Anti-enumeration gate.
        if (!rateLimiter.tryAcquire(tenantId, userId)) {
            throw new CrmContractException(CrmErrorCode.RATE_LIMITED,
                    "Caller lookup rate limit exceeded. Please slow down.");
        }

        // 4. Candidate lookup — canonical source, exact match only.
        List<CallerCandidate> candidates = repository.findActiveCallerCandidates(tenantId, normalized);

        // 5. G8-ADR-002 lead fallback — only when the canonical pool is empty
        //    (leads rank lowest and a canonical candidate can never lose to one).
        if (candidates.isEmpty()) {
            candidates = repository.findActiveLeadCandidates(tenantId, normalized);
        }

        result = resolve(candidates, allowRestricted);
        return finish(result, tenantId, userId, sourceName, start, logPhone);
    }

    /** Tiered ranking (G8 baseline §9): verified person > preferred person > person > account > lead. */
    private static int tier(CallerCandidate candidate) {
        return switch (candidate.ownerType()) {
            case "PERSON" -> candidate.verified() ? 0 : (candidate.preferred() ? 1 : 2);
            case "ACCOUNT" -> 3;
            case "LEAD" -> 4;
            default -> 5;
        };
    }

    private CallerLookupResult resolve(List<CallerCandidate> candidates, boolean allowRestricted) {
        if (candidates.isEmpty()) {
            return CallerLookupResult.empty(CallerMatchStatus.UNKNOWN);
        }
        // Defensive bound (G8-02 §34): more than the bounded candidate list => ambiguous.
        if (candidates.size() > CallerIdentificationRepository.CANDIDATE_LOOKUP_LIMIT) {
            return CallerLookupResult.ambiguous(CallerIdentificationRepository.CANDIDATE_LOOKUP_LIMIT);
        }
        int bestTier = candidates.stream().mapToInt(CallerIdentificationService::tier).min().orElse(5);
        List<CallerCandidate> winning = candidates.stream()
                .filter(candidate -> tier(candidate) == bestTier)
                .toList();
        Set<String> identities = new HashSet<>();
        for (CallerCandidate candidate : winning) {
            identities.add(candidate.ownerType() + ":" + candidate.ownerId());
        }
        if (identities.size() > 1) {
            return CallerLookupResult.ambiguous(identities.size());
        }
        // Single identity at the winning tier — deterministic best row comes
        // first (verified DESC, preferred DESC, updated_at ASC, id ASC).
        CallerCandidate winner = winning.get(0);

        String privacy = winner.privacyClassification();
        boolean restricted = "RESTRICTED".equals(privacy);
        if (restricted && !allowRestricted) {
            return CallerLookupResult.empty(CallerMatchStatus.RESTRICTED);
        }
        boolean maskConfidential = "CONFIDENTIAL".equals(privacy) && !allowRestricted;

        String entityType = switch (winner.ownerType()) {
            case "PERSON" -> "CONTACT";
            case "ACCOUNT" -> "ACCOUNT";
            case "LEAD" -> "LEAD";
            default -> winner.ownerType();
        };
        UUID entityId = switch (winner.ownerType()) {
            case "PERSON" -> winner.contactId();
            case "ACCOUNT" -> winner.accountId();
            case "LEAD" -> winner.leadId();
            default -> winner.ownerId();
        };
        return new CallerLookupResult(
                CallerMatchStatus.EXACT,
                entityType,
                entityId,
                maskConfidential ? mask(winner.displayName()) : winner.displayName(),
                winner.accountId(),
                maskConfidential ? mask(winner.accountName()) : winner.accountName(),
                winner.phoneLabel(),
                winner.verified(),
                winner.preferred(),
                winner.entityLifecycleStatus(),
                privacy,
                winner.matchSource(),
                null);
    }

    private CallerLookupResult finish(CallerLookupResult result, UUID tenantId, UUID userId,
                                      String sourceName, Instant start, String logPhone) {
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        meterRegistry.counter("caller_lookup_total", TAG_RESULT, result.matchStatus().name(), TAG_SOURCE, sourceName).increment();
        switch (result.matchStatus()) {
            case EXACT -> meterRegistry.counter("caller_lookup_exact_total", TAG_SOURCE, sourceName).increment();
            case UNKNOWN -> meterRegistry.counter("caller_lookup_unknown_total", TAG_SOURCE, sourceName).increment();
            case AMBIGUOUS -> meterRegistry.counter("caller_lookup_ambiguous_total", TAG_SOURCE, sourceName).increment();
            case RESTRICTED -> meterRegistry.counter("caller_lookup_restricted_total", TAG_SOURCE, sourceName).increment();
            default -> { /* PRIVATE_NUMBER / INVALID_NUMBER have a shared counter only */ }
        }
        meterRegistry.timer("caller_lookup_latency", TAG_RESULT, result.matchStatus().name(), TAG_SOURCE, sourceName)
                .record(Duration.ofMillis(elapsedMs));

        ObjectNode after = mapper.createObjectNode();
        after.put("result", result.matchStatus().name());
        after.put("source", sourceName);
        if (result.matchStatus() == CallerMatchStatus.EXACT) {
            after.put("entityType", result.entityType());
            after.put("entityId", result.entityId() == null ? "" : result.entityId().toString());
            after.put("matchSource", result.matchSource() == null ? "" : result.matchSource());
        } else if (result.matchStatus() == CallerMatchStatus.AMBIGUOUS) {
            after.put("candidateCount", result.candidateCount() == null ? 0 : result.candidateCount());
        }
        audit.record(tenantId, userId, "crm.caller_identification.lookup",
                result.matchStatus() == CallerMatchStatus.EXACT ? result.entityType() : "NONE",
                result.matchStatus() == CallerMatchStatus.EXACT ? result.entityId() : null,
                new AuditPort.AuditChange(null, after), Instant.now());

        log.debug("CALLER_LOOKUP result={} phone={} source={} tenant={} user={}",
                result.matchStatus(), logPhone, sourceName, tenantId, userId);
        return result;
    }

    /** One-way diagnostic mask: last four digits only (G8-02 §30 — no full phone in logs). */
    private static String maskNumber(String value) {
        if (value == null) return "null";
        return value.length() <= 7 ? "••••" : "••••" + value.substring(value.length() - 4);
    }

    /** Server-side name mask for CONFIDENTIAL disclosure without the restricted capability. */
    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 1 ? "•" : value.substring(0, 1) + "•••";
    }
}
