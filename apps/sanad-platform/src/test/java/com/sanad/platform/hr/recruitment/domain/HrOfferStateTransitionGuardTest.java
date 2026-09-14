package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1/T7 — §6.3 HrOffer transition guard matrix, RESTORED authoritative
 * semantics (T7.2).
 *
 * <p>THE OFFER APPROVAL BYPASS IS REMOVED: DRAFT→EXTENDED does not exist.
 * EXTENDED is reachable ONLY from PENDING_APPROVAL and only behind the
 * authoritative Workflow Y2 APPROVED outcome (verified by the application
 * authority against the persisted correlation). DRAFT→PENDING_APPROVAL
 * (submit), PENDING_APPROVAL→DRAFT (reject, registered reason);
 * EXTENDED→ACCEPTED/DECLINED/WITHDRAWN/EXPIRED; EXTENDED→DRAFT forbidden
 * (new OfferVersion + re-approval instead); ACCEPTED/DECLINED/EXPIRED/
 * WITHDRAWN terminal. Fail-closed on unknown pairs.</p>
 */
class HrOfferStateTransitionGuardTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private HrTransitionContext ctx(String reasonCode) {
        return new HrTransitionContext(TENANT, "user-offers", reasonCode);
    }

    @Test
    void draft_movesToPendingApproval_whenSubmittingForApproval() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.DRAFT, HrOfferState.PENDING_APPROVAL)).isTrue();
    }

    @Test
    void draft_directToExtended_isForbidden_approvalBypassRemoved() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.DRAFT, HrOfferState.EXTENDED))
                .as("T7.2: no controller/service may promote an offer to EXTENDED "
                        + "without the authoritative Workflow Y2 approval result")
                .isFalse();
    }

    @Test
    void approval_resolvesPendingApproval() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.PENDING_APPROVAL, HrOfferState.EXTENDED)).isTrue();
        assertThat(HrOfferTransitions.requiresReason(HrOfferState.PENDING_APPROVAL, HrOfferState.DRAFT)).isTrue();
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.PENDING_APPROVAL, HrOfferState.DRAFT)).isTrue();
    }

    @Test
    void extended_resolvesToEveryTerminalOutcome() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXTENDED, HrOfferState.ACCEPTED)).isTrue();
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXTENDED, HrOfferState.DECLINED)).isTrue();
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXTENDED, HrOfferState.WITHDRAWN)).isTrue();
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXTENDED, HrOfferState.EXPIRED)).isTrue();
    }

    @Test
    void withdrawFromExtended_requiresReason() {
        assertThat(HrOfferTransitions.requiresReason(HrOfferState.EXTENDED, HrOfferState.WITHDRAWN)).isTrue();
        assertThat(HrOfferTransitions
                .check(ctx(RecruitmentReasonCodes.OFFER_WITHDRAWAL), HrOfferState.EXTENDED, HrOfferState.WITHDRAWN)
                .allowed())
                .isTrue();
    }

    @Test
    void extendedToDraft_isForbidden_newVersionInstead() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXTENDED, HrOfferState.DRAFT))
                .as("edits after EXTENDED create a new OfferVersion")
                .isFalse();
    }

    @Test
    void draftAndPendingApproval_cannotJumpToTerminalOutcomes() {
        for (HrOfferState to : new HrOfferState[] {
                HrOfferState.ACCEPTED, HrOfferState.DECLINED, HrOfferState.EXPIRED, HrOfferState.WITHDRAWN }) {
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.DRAFT, to))
                    .as("DRAFT→%s forbidden", to).isFalse();
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.PENDING_APPROVAL, to))
                    .as("PENDING_APPROVAL→%s forbidden", to).isFalse();
        }
    }

    @Test
    void terminalStates_neverTransitionOut() {
        for (HrOfferState to : HrOfferState.values()) {
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.ACCEPTED, to))
                    .as("ACCEPTED is terminal (to=%s)", to).isFalse();
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.DECLINED, to))
                    .as("DECLINED is terminal (to=%s)", to).isFalse();
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.EXPIRED, to))
                    .as("EXPIRED is terminal (to=%s)", to).isFalse();
            assertThat(HrOfferTransitions.isAllowed(HrOfferState.WITHDRAWN, to))
                    .as("WITHDRAWN is terminal (to=%s)", to).isFalse();
        }
    }

    @Test
    void check_failClosed_onUnknownReason() {
        HrTransitionDecision d = HrOfferTransitions
                .check(ctx("WHATEVER"), HrOfferState.EXTENDED, HrOfferState.WITHDRAWN);
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void rejectionBackToDraft_requiresRegisteredReason() {
        HrTransitionDecision without = HrOfferTransitions
                .check(ctx(null), HrOfferState.PENDING_APPROVAL, HrOfferState.DRAFT);
        assertThat(without.allowed())
                .as("T7.8: governed rejection reason/evidence is mandatory").isFalse();
        HrTransitionDecision with = HrOfferTransitions
                .check(ctx("OFFER_REJECTION"), HrOfferState.PENDING_APPROVAL, HrOfferState.DRAFT);
        assertThat(with.allowed()).isTrue();
    }

    @Test
    void check_acceptance_needsNoReason() {
        assertThat(HrOfferTransitions.check(ctx(null), HrOfferState.EXTENDED, HrOfferState.ACCEPTED).allowed())
                .isTrue();
    }

    @Test
    void guard_acceptsExactlyTheRestoredSection6Matrix() {
        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "DRAFT->PENDING_APPROVAL",
                        "PENDING_APPROVAL->EXTENDED",
                        "PENDING_APPROVAL->DRAFT",
                        "EXTENDED->ACCEPTED",
                        "EXTENDED->DECLINED",
                        "EXTENDED->WITHDRAWN",
                        "EXTENDED->EXPIRED"),
                HrOfferState.class, HrOfferState.values(),
                HrOfferTransitions::isAllowed);
        com.sanad.platform.test.HrG1StateMatrixHarness.assertTerminals(
                java.util.Set.of(HrOfferState.ACCEPTED, HrOfferState.DECLINED,
                        HrOfferState.EXPIRED, HrOfferState.WITHDRAWN),
                HrOfferState.values(),
                HrOfferTransitions::isAllowed);
    }
}
