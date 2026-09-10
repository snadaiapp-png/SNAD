package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — §6.1 HrJobOpening transition guard matrix.
 *
 * <p>Allowed: DRAFT→PENDING_APPROVAL; PENDING_APPROVAL→OPEN (approve) /
 * →DRAFT (reject, reason) / →CANCELLED; OPEN⇄PAUSED; OPEN/PAUSED→CLOSED;
 * DRAFT/PENDING_APPROVAL/OPEN/PAUSED→CANCELLED.</p>
 *
 * <p>Forbidden: any →DRAFT from OPEN/PAUSED/CLOSED; CLOSED→anything;
 * CANCELLED→anything; DRAFT→OPEN directly (approval gate). Fail-closed on
 * unknown pairs.</p>
 */
class HrJobOpeningStateTransitionGuardTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ACTOR = "user-openings";

    private HrTransitionContext ctx(String reasonCode) {
        return new HrTransitionContext(TENANT, ACTOR, reasonCode);
    }

    @Test
    void submit_movesDraftToPendingApproval_withoutReason() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.DRAFT, HrJobOpeningState.PENDING_APPROVAL))
                .as("submit is the canonical DRAFT exit")
                .isTrue();
        assertThat(HrJobOpeningTransitions.requiresReason(HrJobOpeningState.DRAFT, HrJobOpeningState.PENDING_APPROVAL))
                .isFalse();
    }

    @Test
    void approve_movesPendingApprovalToOpen() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.OPEN))
                .isTrue();
    }

    @Test
    void reject_movesPendingApprovalBackToDraft_withRegisteredReason() {
        assertThat(HrJobOpeningTransitions.requiresReason(HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.DRAFT))
                .as("reject carries a mandatory reason")
                .isTrue();
        assertThat(HrJobOpeningTransitions
                .check(ctx(RecruitmentReasonCodes.OPENING_REJECTION), HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.DRAFT)
                .allowed())
                .isTrue();
    }

    @Test
    void pauseResume_cycleBetweenOpenAndPaused() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.OPEN, HrJobOpeningState.PAUSED)).isTrue();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PAUSED, HrJobOpeningState.OPEN)).isTrue();
    }

    @Test
    void close_reachableFromOpenAndPaused() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.OPEN, HrJobOpeningState.CLOSED)).isTrue();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PAUSED, HrJobOpeningState.CLOSED)).isTrue();
    }

    @Test
    void cancel_reachableFromEveryPreTerminalState() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.DRAFT, HrJobOpeningState.CANCELLED)).isTrue();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.CANCELLED)).isTrue();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.OPEN, HrJobOpeningState.CANCELLED)).isTrue();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PAUSED, HrJobOpeningState.CANCELLED)).isTrue();
    }

    @Test
    void draftToOpen_direct_isForbidden_approvalGateCannotBeSkipped() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.DRAFT, HrJobOpeningState.OPEN))
                .as("approval gate cannot be skipped")
                .isFalse();
    }

    @Test
    void backToDraft_fromOpenPausedClosed_isForbidden() {
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.OPEN, HrJobOpeningState.DRAFT)).isFalse();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.PAUSED, HrJobOpeningState.DRAFT)).isFalse();
        assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.CLOSED, HrJobOpeningState.DRAFT)).isFalse();
    }

    @Test
    void terminalStates_neverTransitionOut() {
        for (HrJobOpeningState to : HrJobOpeningState.values()) {
            assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.CLOSED, to))
                    .as("CLOSED is terminal (to=%s)", to).isFalse();
            assertThat(HrJobOpeningTransitions.isAllowed(HrJobOpeningState.CANCELLED, to))
                    .as("CANCELLED is terminal (to=%s)", to).isFalse();
        }
    }

    @Test
    void check_rejectsForbiddenPair_evenWithValidReason() {
        HrTransitionDecision d = HrJobOpeningTransitions
                .check(ctx(RecruitmentReasonCodes.OPENING_REJECTION), HrJobOpeningState.DRAFT, HrJobOpeningState.OPEN);
        assertThat(d.allowed()).isFalse();
        assertThat(d.violationCode()).isEqualTo("HRM_TRANSITION_FORBIDDEN");
    }

    @Test
    void check_failsClosed_whenReasonRequiredButUnknown() {
        HrTransitionDecision unknown = HrJobOpeningTransitions
                .check(ctx("NOT_A_REAL_REASON"), HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.DRAFT);
        assertThat(unknown.allowed())
                .as("unknown reason codes fail closed")
                .isFalse();

        HrTransitionDecision blank = HrJobOpeningTransitions
                .check(ctx(null), HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.DRAFT);
        assertThat(blank.allowed()).isFalse();
    }

    @Test
    void check_allowsAllowedPairWithoutReason() {
        assertThat(HrJobOpeningTransitions
                .check(ctx(null), HrJobOpeningState.DRAFT, HrJobOpeningState.PENDING_APPROVAL).allowed())
                .isTrue();
    }

    @Test
    void context_withoutTenant_failsClosed() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new HrTransitionContext(null, ACTOR, null),
                "every G1 transition tuple carries tenant_id");
    }

    @Test
    void guard_acceptsExactlyTheDesignSection6Matrix() {
        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "DRAFT->PENDING_APPROVAL",
                        "PENDING_APPROVAL->OPEN",
                        "PENDING_APPROVAL->DRAFT",
                        "OPEN->PAUSED",
                        "PAUSED->OPEN",
                        "OPEN->CLOSED",
                        "PAUSED->CLOSED",
                        "DRAFT->CANCELLED",
                        "PENDING_APPROVAL->CANCELLED",
                        "OPEN->CANCELLED",
                        "PAUSED->CANCELLED"),
                HrJobOpeningState.class, HrJobOpeningState.values(),
                HrJobOpeningTransitions::isAllowed);
        com.sanad.platform.test.HrG1StateMatrixHarness.assertTerminals(
                java.util.Set.of(HrJobOpeningState.CLOSED, HrJobOpeningState.CANCELLED),
                HrJobOpeningState.values(),
                HrJobOpeningTransitions::isAllowed);
    }
}
