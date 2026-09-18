package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — capability naming contract (design §9, task card SECURITY_GATES).
 *
 * <p>Canonical convention {@code HRM.<DOMAIN>.<ACTION>}; the recruitment
 * family set is pinned exactly to the §9 matrix. Outbox event codes (§12,
 * e.g. HRM.RECRUITMENT.OPENING_PUBLISHED) are deliberately NOT capabilities.</p>
 */
class RecruitmentCapabilityNamingContractTest {

    private static final java.util.Set<String> DESIGN_SECTION9_SET = java.util.Set.of(
            "HRM.RECRUITMENT.OPENING.VIEW",
            "HRM.RECRUITMENT.OPENING.MANAGE",
            "HRM.RECRUITMENT.OPENING.PUBLISH",
            "HRM.RECRUITMENT.CANDIDATE.VIEW",
            "HRM.RECRUITMENT.CANDIDATE.MANAGE",
            "HRM.RECRUITMENT.APPLICATION.MANAGE",
            "HRM.RECRUITMENT.APPLICATION.ADVANCE",
            "HRM.RECRUITMENT.APPLICATION.REJECT",
            "HRM.RECRUITMENT.INTERVIEW.MANAGE",
            "HRM.RECRUITMENT.INTERVIEW.SCHEDULE",
            "HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME",
            "HRM.RECRUITMENT.OFFER.MANAGE",
            "HRM.RECRUITMENT.OFFER.EXTEND",
            "HRM.RECRUITMENT.OFFER.APPROVE",
            "HRM.RECRUITMENT.HIRE.CONVERT");

    @Test
    void recruitmentCapabilitySet_matchesDesignSection9Exactly() {
        assertThat(com.sanad.platform.hr.recruitment.RecruitmentCapabilities.all())
                .as("capability family pinned to design §9")
                .containsExactlyInAnyOrderElementsOf(DESIGN_SECTION9_SET);
    }

    @Test
    void everyCapability_matchesNamingConvention() {
        for (String code : com.sanad.platform.hr.recruitment.RecruitmentCapabilities.all()) {
            assertThat(code).matches("^HRM\\.RECRUITMENT(\\.[A-Z][A-Z_]*)+$");
        }
    }

    @Test
    void noOutboxEventCode_leaksIntoCapabilityFamily() {
        for (String code : DESIGN_SECTION9_SET) {
            assertThat(code.endsWith("_PUBLISHED") || code.endsWith("_ACCEPTED")
                    || code.endsWith("_REJECTED") || code.endsWith("_COMPLETED"))
                    .as("capability %s must not be an outbox event code", code)
                    .isFalse();
        }
    }
}
