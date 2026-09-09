package com.sanad.platform.subscription.rbac;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R0C-12 corrective recertification — REAL JWT details contract (RED tests).
 *
 * <p>The production {@code JwtAuthenticationFilter} stores authentication
 * details as <strong>String</strong> values:</p>
 *
 * <pre>
 * details.put("user_id",  claims.getSubject());   // String
 * details.put("tenant_id", jwtTenantIdStr);       // String
 * </pre>
 *
 * <p>The former {@code ControlPlaneAccessService.accessCheck} required
 * {@code details.get("tenant_id") instanceof UUID} and
 * {@code details.get("user_id") instanceof UUID}, so every genuine
 * JWT-authenticated request was reported as {@code authenticated=false,
 * capabilities={}} — the observed release blocker (overview rendered via the
 * {@code @RequireCapability} aspect, which accepts Strings, while the
 * navigation access-check endpoint reported "no access").</p>
 *
 * <p>Contract pinned here:</p>
 * <ul>
 *   <li><strong>A</strong> — valid String details (the REAL filter shape)
 *       must resolve authenticated=true and evaluate granular capabilities.</li>
 *   <li><strong>B</strong> — malformed tenant_id String fails closed.</li>
 *   <li><strong>C</strong> — malformed user_id String fails closed.</li>
 *   <li><strong>D</strong> — missing tenant_id fails closed.</li>
 *   <li><strong>E</strong> — missing user_id fails closed.</li>
 *   <li><strong>F</strong> — unauthenticated Authentication fails closed.</li>
 *   <li><strong>G</strong> — UUID-valued details (legacy/tests) remain
 *       backward-compatible.</li>
 *   <li>Authorization failure is NOT authentication failure: a user holding
 *       no granular capability is still authenticated=true with that
 *       capability=false (never mapped to authenticated=false).</li>
 * </ul>
 */
class ControlPlaneAccessServiceJwtDetailsTest {

    private static final UUID TENANT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private CapabilityEvaluationService evaluationService;
    private ControlPlaneAccessService accessService;

    @BeforeEach
    void setUp() {
        evaluationService = mock(CapabilityEvaluationService.class);
        accessService = new ControlPlaneAccessService(evaluationService, null);
    }

    /** Builds an authentication exactly like the production JwtAuthenticationFilter does. */
    private Authentication authenticatedWith(Map<String, Object> details) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(details);
        return auth;
    }

    private void allowEverything() {
        when(evaluationService.evaluate(any(UUID.class), any(UUID.class), anyString(), any()))
                .thenAnswer(invocation -> new AccessDecisionResponse(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(3), invocation.getArgument(2),
                        true, "ROLE_CAPABILITY_MATCH", UUID.randomUUID(), "SCP_ADMIN"));
    }

    private void denyCode(String code) {
        when(evaluationService.evaluate(any(UUID.class), any(UUID.class), eq(code), any()))
                .thenAnswer(invocation -> new AccessDecisionResponse(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(3), invocation.getArgument(2),
                        false, "NO_MATCHING_ACTIVE_ROLE", null, null));
    }

    // ------------------------------------------------------------------
    // A — the REAL JWT details shape (Strings)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A: valid authenticated token with String tenant_id/user_id must resolve authenticated=true with capabilities")
    void stringDetails_resolveAuthenticated_true() {
        allowEverything();
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString()); // String — REAL filter shape
        details.put("user_id", USER_ID.toString());     // String — REAL filter shape

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated())
                .as("A genuine JWT-authenticated request (String details) must NOT be reported as unauthenticated")
                .isTrue();
        assertThat(result.capabilities()).isNotEmpty();
        for (String code : ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES) {
            assertThat(result.capabilities()).containsEntry(code, true);
        }
    }

    // ------------------------------------------------------------------
    // B/C — malformed principal strings fail closed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("B: malformed tenant_id String fails closed (authenticated=false, no capabilities)")
    void malformedTenantString_failsClosed() {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", "not-a-uuid");
        details.put("user_id", USER_ID.toString());

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("C: malformed user_id String fails closed (authenticated=false, no capabilities)")
    void malformedUserString_failsClosed() {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        details.put("user_id", "42");

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    // ------------------------------------------------------------------
    // D/E — missing principal entries fail closed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D: missing tenant_id fails closed")
    void missingTenant_failsClosed() {
        Map<String, Object> details = new HashMap<>();
        details.put("user_id", USER_ID.toString());

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("E: missing user_id fails closed")
    void missingUser_failsClosed() {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    // ------------------------------------------------------------------
    // F — unauthenticated Authentication fails closed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F: unauthenticated Authentication fails closed")
    void unauthenticated_failsClosed() {
        allowEverything();
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        details.put("user_id", USER_ID.toString());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setAuthenticated(false);
        auth.setDetails(details);

        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(auth);

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("F: null Authentication fails closed")
    void nullAuthentication_failsClosed() {
        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(null);

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    // ------------------------------------------------------------------
    // G — UUID-valued details remain backward-compatible
    // ------------------------------------------------------------------

    @Test
    @DisplayName("G: UUID-valued details (legacy/tests) remain backward-compatible")
    void uuidDetails_backwardCompatible() {
        allowEverything();
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID); // UUID object
        details.put("user_id", USER_ID);     // UUID object

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isTrue();
        for (String code : ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES) {
            assertThat(result.capabilities()).containsEntry(code, true);
        }
    }

    // ------------------------------------------------------------------
    // Authorization ≠ authentication (mission §6 invariant)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§6: missing granular capability is authorization=false, NOT authenticated=false")
    void deniedCapability_staysAuthenticated() {
        allowEverything();
        denyCode("catalog.manage");

        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        details.put("user_id", USER_ID.toString());

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isTrue();
        assertThat(result.capabilities()).containsEntry("catalog.manage", false);
        assertThat(result.capabilities()).containsEntry("subscription.read", true);
    }

    @Test
    @DisplayName("§6: evaluator failure degrades that capability to false, never to unauthenticated")
    void evaluatorFailure_capabilityFalse_stillAuthenticated() {
        when(evaluationService.evaluate(any(UUID.class), any(UUID.class), anyString(), any()))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, USER_ID, null, "subscription.read",
                        true, "ROLE_CAPABILITY_MATCH", UUID.randomUUID(), "SCP_ADMIN"));
        when(evaluationService.evaluate(any(UUID.class), any(UUID.class), eq("audit.read"), any()))
                .thenThrow(new RuntimeException("capability service degraded"));

        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        details.put("user_id", USER_ID.toString());

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isTrue();
        assertThat(result.capabilities()).containsEntry("subscription.read", true);
        assertThat(result.capabilities()).containsEntry("audit.read", false);
    }

    @Test
    @DisplayName("§4: identity is taken ONLY from authentication details — never defaulted to full capabilities")
    void noIdentity_neverFullCapabilities() {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        // user_id deliberately absent

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(authenticatedWith(details));

        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }
}
