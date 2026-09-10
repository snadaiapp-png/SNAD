package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — §6.2 HrApplication transition guard matrix.
 *
 * <p>Forward-only one-stage advancement; rejection/withdrawal allowed from
 * any pre-HIRED stage with mandatory reason; HIRED reachable ONLY from OFFER
 * (and only via §7 conversion — enforced at service layer with the ledger).</p>
 */
class HrApplicationStateTransitionGuardTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private HrTransitionContext ctx(String reasonCode) {
        return new HrTransitionContext(TENANT, "user-recruiter", reasonCode);
    }

    @Test
    void stages_advanceExactlyOneStep() {
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.APPLIED, HrApplicationState.SCREENING)).isTrue();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.SCREENING, HrApplicationState.INTERVIEW)).isTrue();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.INTERVIEW, HrApplicationState.OFFER)).isTrue();
    }

    @Test
    void stageSkips_areForbidden() {
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.APPLIED, HrApplicationState.OFFER))
                .as("APPLIED→OFFER skips stages")
                .isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.APPLIED, HrApplicationState.INTERVIEW))
                .as("APPLIED→INTERVIEW skips stages")
                .isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.SCREENING, HrApplicationState.OFFER))
                .as("SCREENING→OFFER skips stages")
                .isFalse();
    }

    @Test
    void hired_onlyFromOffer() {
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.OFFER, HrApplicationState.HIRED)).isTrue();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.APPLIED, HrApplicationState.HIRED)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.SCREENING, HrApplicationState.HIRED)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.INTERVIEW, HrApplicationState.HIRED)).isFalse();
    }

    @Test
    void backwardTransitions_areForbidden() {
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.SCREENING, HrApplicationState.APPLIED)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.INTERVIEW, HrApplicationState.SCREENING)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.INTERVIEW, HrApplicationState.APPLIED)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.OFFER, HrApplicationState.INTERVIEW)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.OFFER, HrApplicationState.SCREENING)).isFalse();
        assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.OFFER, HrApplicationState.APPLIED)).isFalse();
    }

    @Test
    void rejection_allowedFromEveryPreHiredStage_reasonMandatory() {
        for (HrApplicationState from : new HrApplicationState[] {
                HrApplicationState.APPLIED, HrApplicationState.SCREENING,
                HrApplicationState.INTERVIEW, HrApplicationState.OFFER }) {
            assertThat(HrApplicationTransitions.isAllowed(from, HrApplicationState.REJECTED))
                    .as("rejection from %s", from).isTrue();
            assertThat(HrApplicationTransitions.requiresReason(from, HrApplicationState.REJECTED))
                    .as("rejection reason mandatory from %s", from).isTrue();
        }
    }

    @Test
    void withdrawal_allowedFromEveryPreHiredStage_reasonMandatory() {
        for (HrApplicationState from : new HrApplicationState[] {
                HrApplicationState.APPLIED, HrApplicationState.SCREENING,
                HrApplicationState.INTERVIEW, HrApplicationState.OFFER }) {
            assertThat(HrApplicationTransitions.isAllowed(from, HrApplicationState.WITHDRAWN))
                    .as("withdrawal from %s", from).isTrue();
            assertThat(HrApplicationTransitions.requiresReason(from, HrApplicationState.WITHDRAWN))
                    .as("withdrawal reason mandatory from %s", from).isTrue();
        }
    }

    @Test
    void terminalStates_neverTransitionOut() {
        for (HrApplicationState to : HrApplicationState.values()) {
            assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.HIRED, to))
                    .as("HIRED is terminal (to=%s)", to).isFalse();
            assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.REJECTED, to))
                    .as("REJECTED is terminal (to=%s)", to).isFalse();
            assertThat(HrApplicationTransitions.isAllowed(HrApplicationState.WITHDRAWN, to))
                    .as("WITHDRAWN is terminal (to=%s)", to).isFalse();
        }
    }

    @Test
    void check_enforcesReasonRegistry_failClosed() {
        HrTransitionDecision registered = HrApplicationTransitions
                .check(ctx(RecruitmentReasonCodes.APPLICATION_REJECTION), HrApplicationState.APPLIED, HrApplicationState.REJECTED);
        assertThat(registered.allowed()).isTrue();

        HrTransitionDecision unknown = HrApplicationTransitions
                .check(ctx("MADE_UP_REASON"), HrApplicationState.APPLIED, HrApplicationState.REJECTED);
        assertThat(unknown.allowed()).isFalse();
        assertThat(unknown.violationCode()).isEqualTo("HRM_REASON_REJECTED");

        HrTransitionDecision missing = HrApplicationTransitions
                .check(ctx(null), HrApplicationState.APPLIED, HrApplicationState.WITHDRAWN);
        assertThat(missing.allowed()).isFalse();
    }

    @Test
    void check_stageAdvance_needsNoReason() {
        assertThat(HrApplicationTransitions
                .check(ctx(null), HrApplicationState.APPLIED, HrApplicationState.SCREENING).allowed())
                .isTrue();
    }

    @Test
    void guard_acceptsExactlyTheDesignSection6Matrix() {
        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "APPLIED->SCREENING",
                        "SCREENING->INTERVIEW",
                        "INTERVIEW->OFFER",
                        "OFFER->HIRED",
                        "APPLIED->REJECTED",
                        "SCREENING->REJECTED",
                        "INTERVIEW->REJECTED",
                        "OFFER->REJECTED",
                        "APPLIED->WITHDRAWN",
                        "SCREENING->WITHDRAWN",
                        "INTERVIEW->WITHDRAWN",
                        "OFFER->WITHDRAWN"),
                HrApplicationState.class, HrApplicationState.values(),
                HrApplicationTransitions::isAllowed);
        com.sanad.platform.test.HrG1StateMatrixHarness.assertTerminals(
                java.util.Set.of(HrApplicationState.HIRED, HrApplicationState.REJECTED, HrApplicationState.WITHDRAWN),
                HrApplicationState.values(),
                HrApplicationTransitions::isAllowed);
    }
}
