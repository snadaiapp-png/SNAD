package com.sanad.platform.crm.collaboration.infrastructure;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task 6 — Recipient Eligibility.
 *
 * <p>Unit test that verifies the {@link PlatformRecipientEligibilityAdapter}
 * composes {@link OwnershipUserValidationPort#isActiveUser(UUID, UUID)} with
 * {@link CapabilityEvaluationService#evaluate(UUID, UUID, String, UUID)} to
 * produce an {@link EligibilityDecision} without mutating any RBAC state.
 *
 * <p>The adapter MUST short-circuit on inactive users (no RBAC evaluation)
 * and MUST preserve the upstream denial reason verbatim. When the upstream
 * denial reason is null, the adapter falls back to {@code "RBAC_DENIED"}.
 *
 * <p>Security boundary: the adapter MUST NOT reference UserRoleGrantService,
 * RoleService, RoleCapabilityService, AccessCapabilityService,
 * UserRoleGrantRepository, RoleRepository, SecurityContextHolder,
 * TenantRlsTransactionContext, JdbcTemplate, or NamedParameterJdbcTemplate.
 */
@DisplayName("Task 6 — Platform Recipient Eligibility Adapter")
@ExtendWith(MockitoExtension.class)
class PlatformRecipientEligibilityAdapterTest {

    @Mock
    private OwnershipUserValidationPort users;

    @Mock
    private CapabilityEvaluationService capabilities;

    private PlatformRecipientEligibilityAdapter adapter;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-00000000a001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-00000000a002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-4000-8000-00000000a003");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-4000-8000-00000000a004");
    private static final String REQUIRED_CAPABILITY = "CRM.TASK.READ";

    @BeforeEach
    void setup() {
        adapter = new PlatformRecipientEligibilityAdapter(users, capabilities);
    }

    // 1. activeUserWithRequiredCapabilityIsEligible
    @Test
    @DisplayName("1. active user with required capability → ELIGIBLE")
    void activeUserWithRequiredCapabilityIsEligible() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, ORGANIZATION_ID))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, ORGANIZATION_ID,
                        REQUIRED_CAPABILITY, true, "ROLE_CAPABILITY_MATCH",
                        ROLE_ID, "AGENT"));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reason()).isEqualTo("ELIGIBLE");
    }

    // 2. inactiveUserIsRejectedBeforeRbacEvaluation
    @Test
    @DisplayName("2. inactive user → USER_NOT_ACTIVE_IN_TENANT and RBAC is never consulted")
    void inactiveUserIsRejectedBeforeRbacEvaluation() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(false);

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo("USER_NOT_ACTIVE_IN_TENANT");
        verifyNoInteractions(capabilities);
    }

    // 3. activeUserWithoutCapabilityIsRejected
    @Test
    @DisplayName("3. active user without required capability → upstream NO_MATCHING_ACTIVE_ROLE preserved")
    void activeUserWithoutCapabilityIsRejected() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, ORGANIZATION_ID))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, ORGANIZATION_ID,
                        REQUIRED_CAPABILITY, false, "NO_MATCHING_ACTIVE_ROLE",
                        null, null));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_MATCHING_ACTIVE_ROLE");
    }

    // 4. capabilityNotFoundReasonIsPreserved
    @Test
    @DisplayName("4. CAPABILITY_NOT_FOUND reason is preserved verbatim")
    void capabilityNotFoundReasonIsPreserved() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, ORGANIZATION_ID))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, ORGANIZATION_ID,
                        REQUIRED_CAPABILITY, false, "CAPABILITY_NOT_FOUND",
                        null, null));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo("CAPABILITY_NOT_FOUND");
    }

    // 5. inactiveCapabilityReasonIsPreserved
    @Test
    @DisplayName("5. CAPABILITY_INACTIVE reason is preserved verbatim")
    void inactiveCapabilityReasonIsPreserved() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, ORGANIZATION_ID))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, ORGANIZATION_ID,
                        REQUIRED_CAPABILITY, false, "CAPABILITY_INACTIVE",
                        null, null));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo("CAPABILITY_INACTIVE");
    }

    // 6. requiredCapabilityNullIsRejected
    @Test
    @DisplayName("6. null requiredCapability → IllegalArgumentException before any downstream call")
    void requiredCapabilityNullIsRejected() {
        assertThatThrownBy(() -> adapter.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCapability is required");
        verifyNoInteractions(users);
        verifyNoInteractions(capabilities);
    }

    // 7. requiredCapabilityBlankIsRejected
    @Test
    @DisplayName("7. blank requiredCapability (\"\" and \"   \") → IllegalArgumentException")
    void requiredCapabilityBlankIsRejected() {
        assertThatThrownBy(() -> adapter.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCapability is required");
        assertThatThrownBy(() -> adapter.evaluate(TENANT_ID, USER_ID, ORGANIZATION_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCapability is required");
        verifyNoInteractions(users);
        verifyNoInteractions(capabilities);
    }

    // 8. nullTenantRejected
    @Test
    @DisplayName("8. null tenantId → NullPointerException before any downstream call")
    void nullTenantRejected() {
        assertThatThrownBy(() -> adapter.evaluate(null, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(users);
        verifyNoInteractions(capabilities);
    }

    // 9. nullUserRejected
    @Test
    @DisplayName("9. null userId → NullPointerException before any downstream call")
    void nullUserRejected() {
        assertThatThrownBy(() -> adapter.evaluate(TENANT_ID, null, ORGANIZATION_ID, REQUIRED_CAPABILITY))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(users);
        verifyNoInteractions(capabilities);
    }

    // 10. tenantWideOrganizationNullIsAllowed
    @Test
    @DisplayName("10. null organizationId is allowed (tenant-wide scope) and passed through to CapabilityEvaluationService")
    void tenantWideOrganizationNullIsAllowed() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, null))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, null,
                        REQUIRED_CAPABILITY, true, "ROLE_CAPABILITY_MATCH",
                        ROLE_ID, "AGENT"));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, null, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reason()).isEqualTo("ELIGIBLE");
        verify(capabilities).evaluate(eq(TENANT_ID), eq(USER_ID), eq(REQUIRED_CAPABILITY), eq(null));
    }

    // 11. deniedResponseWithoutReasonFallsBackSafely
    @Test
    @DisplayName("11. denied RBAC response with null reason falls back to RBAC_DENIED")
    void deniedResponseWithoutReasonFallsBackSafely() {
        when(users.isActiveUser(TENANT_ID, USER_ID)).thenReturn(true);
        when(capabilities.evaluate(TENANT_ID, USER_ID, REQUIRED_CAPABILITY, ORGANIZATION_ID))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, ORGANIZATION_ID,
                        REQUIRED_CAPABILITY, false, null,
                        null, null));

        EligibilityDecision decision = adapter.evaluate(
                TENANT_ID, USER_ID, ORGANIZATION_ID, REQUIRED_CAPABILITY);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo("RBAC_DENIED");
    }
}
