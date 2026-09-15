package com.sanad.platform.hr.recruitment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G1 T8 — the governed candidate → hire conversion command payload
 * (design §7; directive T8.2).
 *
 * <p>The offer version's immutable terms (contract terms + compensation JSON)
 * remain the authoritative material source; this command carries the
 * employment/identity resolution inputs the offer aggregate does not own:</p>
 * <ul>
 *   <li>{@code identityClaims} — verified identity claims driving §7.1.4
 *       person resolution. Strong government identifiers (NATIONAL_ID, IQAMA,
 *       PASSPORT) may link an existing Person; an EMAIL claim alone never
 *       does (§7.3 case 2 — ambiguity fails closed).</li>
 *   <li>{@code legalEntityId} / {@code workerClassificationCode} /
 *       {@code laborJurisdictionCode} / {@code employmentStartDate} — the
 *       canonical employment creation inputs (G0 write path).</li>
 *   <li>{@code positionId} — explicit position intent; when {@code null} the
 *       opening's position (if any) is used. A resolved position is created
 *       OCCUPYING with the in-transaction occupancy re-check (§T8.5).</li>
 *   <li>{@code contractNumber} — optional; generated when absent.</li>
 * </ul>
 */
public record HireConversionCommand(
        String idempotencyKey,
        List<IdentityClaim> identityClaims,
        UUID legalEntityId,
        String workerClassificationCode,
        String laborJurisdictionCode,
        LocalDate employmentStartDate,
        BigDecimal allocationPercent,
        UUID positionId,
        String contractNumber) {

    /** Identity claim types strong enough to LINK an existing person (§7.3 case 1). */
    public static final List<String> STRONG_IDENTIFIER_TYPES =
            List.of("NATIONAL_ID", "IQAMA", "PASSPORT");
    public static final String EMAIL_IDENTIFIER_TYPE = "EMAIL";

    public HireConversionCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_KEY_REQUIRED: idempotency key is mandatory "
                    + "for the hire conversion command");
        }
        if (legalEntityId == null) {
            throw new IllegalStateException("HRM_VALIDATION_FAILED: legalEntityId is mandatory");
        }
        if (workerClassificationCode == null || workerClassificationCode.isBlank()) {
            throw new IllegalStateException("HRM_VALIDATION_FAILED: workerClassificationCode is mandatory");
        }
        if (employmentStartDate == null) {
            throw new IllegalStateException("HRM_VALIDATION_FAILED: employmentStartDate is mandatory");
        }
        identityClaims = identityClaims == null ? List.of() : List.copyOf(identityClaims);
    }

    /**
     * One verified identity claim. Plaintext values live only inside the
     * governed conversion transaction — they are normalized, blind-indexed
     * and encrypted by the G0 person authority, and never logged.
     */
    public record IdentityClaim(String identifierType, String issuingCountryCode, String value) {
        public IdentityClaim {
            if (identifierType == null || identifierType.isBlank()) {
                throw new IllegalStateException("HRM_VALIDATION_FAILED: identifierType is mandatory");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("HRM_VALIDATION_FAILED: identity claim value is mandatory");
            }
        }
    }
}
