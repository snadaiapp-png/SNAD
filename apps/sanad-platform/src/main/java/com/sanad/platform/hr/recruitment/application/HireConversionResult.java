package com.sanad.platform.hr.recruitment.application;

import java.util.UUID;

/**
 * HRM-G1 T8 — the exact conversion result (design §7.1 step 12/§13 route 21).
 *
 * <p>Replays (§7.3 cases 3/4) return the ORIGINAL refs with
 * {@code replayed=true}. The result carries identifiers only — no candidate
 * PII and no compensation amounts.</p>
 */
public record HireConversionResult(
        UUID offerId,
        UUID applicationId,
        UUID personId,
        boolean personReused,
        String employeeNumber,
        UUID employmentId,
        UUID assignmentId,
        UUID contractId,
        UUID compensationPackageId,
        UUID onboardingPlanId,
        boolean replayed) {
}
