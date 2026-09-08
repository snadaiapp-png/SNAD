package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — reason-code registry validation (fail-closed, design §6 reason
 * columns + task card "unknown reason codes fail").
 */
class RecruitmentReasonCodeValidationTest {

    @Test
    void all_returnsTheExactRegisteredUniverse() {
        assertThat(RecruitmentReasonCodes.all()).containsExactlyInAnyOrder(
                RecruitmentReasonCodes.OPENING_REJECTION,
                RecruitmentReasonCodes.APPLICATION_REJECTION,
                RecruitmentReasonCodes.APPLICATION_WITHDRAWAL,
                RecruitmentReasonCodes.OFFER_WITHDRAWAL);
    }

    @Test
    void registeredCodes_validate() {
        assertThat(RecruitmentReasonCodes.isRegistered(RecruitmentReasonCodes.OPENING_REJECTION)).isTrue();
        assertThat(RecruitmentReasonCodes.isRegistered(RecruitmentReasonCodes.APPLICATION_REJECTION)).isTrue();
        assertThat(RecruitmentReasonCodes.isRegistered(RecruitmentReasonCodes.APPLICATION_WITHDRAWAL)).isTrue();
        assertThat(RecruitmentReasonCodes.isRegistered(RecruitmentReasonCodes.OFFER_WITHDRAWAL)).isTrue();
    }

    @Test
    void unknownOrNullCodes_failClosed() {
        assertThat(RecruitmentReasonCodes.isRegistered("TOTALLY_UNKNOWN")).isFalse();
        assertThat(RecruitmentReasonCodes.isRegistered("opening_rejection")).as("case-sensitive").isFalse();
        assertThat(RecruitmentReasonCodes.isRegistered("")).isFalse();
        assertThat(RecruitmentReasonCodes.isRegistered("  ")).isFalse();
        assertThat(RecruitmentReasonCodes.isRegistered(null)).isFalse();
    }
}
