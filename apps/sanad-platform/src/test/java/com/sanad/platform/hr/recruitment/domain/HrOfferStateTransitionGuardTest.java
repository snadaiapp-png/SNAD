package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — §6.3 HrOffer transition guard matrix.
 *
 * <p>DRAFT→PENDING_APPROVAL (policy) or DRAFT→EXTENDED (no approval path);
 * PENDING_APPROVAL→EXTENDED/DRAFT(reject); EXTENDED→ACCEPTED/DECLINED/
 * WITHDRAWN/EXPIRED. EXTENDED→DRAFT forbidden (new OfferVersion instead);
 * ACCEPTED/DECLINED/EXPIRED/WITHDRAWN terminal.</p>
 */
class HrOfferStateTransitionGuardTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private HrTransitionContext ctx(String reasonCode) {
        return new HrTransitionContext(TENANT, "user-offers", reasonCode);
    }

    @Test
    void draft_movesToPendingApproval_whenPolicyRequiresApproval() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.DRAFT, HrOfferState.PENDING_APPROVAL)).isTrue();
    }

    @Test
    void draft_movesDirectlyToExtended_whenNoApprovalRequired() {
        assertThat(HrOfferTransitions.isAllowed(HrOfferState.DRAFT, HrOfferState.EXTENDED)).isTrue();
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
    void check_acceptance_needsNoReason() {
        assertThat(HrOfferTransitions.check(ctx(null), HrOfferState.EXTENDED, HrOfferState.ACCEPTED).allowed())
                .isTrue();
    }

    @Test
    void guard_acceptsExactlyTheDesignSection6Matrix() {
        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "DRAFT->PENDING_APPROVAL",
                        "DRAFT->EXTENDED",
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
