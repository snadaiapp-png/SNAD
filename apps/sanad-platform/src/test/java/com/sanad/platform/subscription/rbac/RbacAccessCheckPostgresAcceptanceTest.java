package com.sanad.platform.subscription.rbac;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.commerce.PgAcceptanceWiringConfig;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R0C-12 CORRECTIVE RECERTIFICATION — RBAC / REAL-JWT ACCESS-CHECK
 * PostgreSQL Direct acceptance (host-native PostgreSQL, NO Docker, NO
 * Testcontainers, NO H2 certification).
 *
 * <p>Proves the complete grant chain on the REAL migration-seeded schema:</p>
 *
 * <pre>
 * user → user_role_assignments → role → role_capabilities → access_capabilities
 * </pre>
 *
 * <p>and the REAL HTTP path:</p>
 *
 * <pre>
 * JWT → JwtAuthenticationFilter → SecurityContext → GovernanceController
 *     → CapabilityAuthorizationAspect (@RequireCapability EXECUTIVE_VIEW)
 *     → ControlPlaneAccessGuard (control-plane tenant pin)
 *     → ControlPlaneAccessService → CapabilityEvaluationService
 * </pre>
 *
 * <p>Acceptance matrix (mission §12):</p>
 * <ol>
 *   <li>RB-01 string principal IDs evaluate capabilities</li>
 *   <li>RB-02 UUID principal IDs remain compatible</li>
 *   <li>RB-03 malformed tenant string fails closed</li>
 *   <li>RB-04 malformed user string fails closed</li>
 *   <li>RB-05 missing IDs fail closed</li>
 *   <li>RB-06 unauthenticated fails closed</li>
 *   <li>RB-07 EXECUTIVE_VIEW role gets expected read capabilities (grant chain)</li>
 *   <li>RB-08 EXECUTIVE_MANAGE role gets expected manage/action capabilities</li>
 *   <li>RB-09 unrelated role gets no unauthorized SCP capability (but stays authenticated)</li>
 *   <li>RB-10 tenant A grants cannot authorize tenant B</li>
 *   <li>RB-11 revoked role removes capability</li>
 *   <li>RB-12 inactive capability is denied</li>
 *   <li>RB-13 access-check HTTP returns authenticated=true for a REAL application-issued JWT</li>
 *   <li>RB-14 required read capability map correct over HTTP (10 read capabilities)</li>
 *   <li>RB-15 cross-tenant request denied over HTTP (tenant binding + control-plane guard)</li>
 * </ol>
 *
 * <p>The identity details used by the service-level scenarios mirror the
 * REAL {@code JwtAuthenticationFilter} shape (String values) — the exact
 * production contract the corrective fix re-aligned.</p>
 */
@SpringBootTest(properties = {
        // The fixed sentinel control-plane tenant this suite provisions.
        // Matches the production contract: SANAD_CONTROL_PLANE_TENANT_ID.
        "sanad.control-plane.tenant-id=" + RbacAccessCheckPostgresAcceptanceTest.CONTROL_TENANT_ID
})
@AutoConfigureMockMvc
@ActiveProfiles("pg-acceptance")
@Import(PgAcceptanceWiringConfig.class)
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "pg-acceptance")
class RbacAccessCheckPostgresAcceptanceTest {

    /** Deterministic control-plane tenant pinned into the test property. */
    static final String CONTROL_TENANT_ID = "00000000-0000-0000-0000-0000000000c1";

    /** The ten mandatory granular read capabilities (mission §5 minimum). */
    private static final List<String> REQUIRED_READ_CAPABILITIES = List.of(
            "subscription.read", "catalog.read", "application.read", "plan.read",
            "pricing.read", "entitlement.read", "usage.read", "billing.read",
            "provisioning.read", "audit.read");

    private static final List<String> MANAGE_AND_ACTION_CAPABILITIES = List.of(
            "catalog.manage", "plan.manage", "pricing.manage", "entitlement.manage",
            "entitlement.override", "billing.adjust", "provisioning.retry",
            "subscription.create", "subscription.change_plan", "subscription.cancel",
            "subscription.suspend", "application.manage");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ControlPlaneAccessService accessService;
    @Autowired private CapabilityEvaluationService evaluationService;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private final List<UUID> createdTenants = new ArrayList<>();
    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdRoles = new ArrayList<>();

    // ------------------------------------------------------------------
    // Fixture helpers — disposable identity chain on the REAL schema
    // ------------------------------------------------------------------

    private UUID seedTenant(String subdomainSuffix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, "RBAC-ACC " + subdomainSuffix, "rbac-acc-" + subdomainSuffix,
                Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
        return id;
    }

    private UUID seedUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, must_change_password, platform_admin,
                                   session_version, created_at, updated_at)
                VALUES (?, ?, ?, 'RBAC Acceptance User', 'ACTIVE', 'seed-no-login',
                        false, false, 0, ?, ?)
                """, id, tenantId, email, Timestamp.from(now), Timestamp.from(now));
        createdUsers.add(id);
        return id;
    }

    private UUID seedRole(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'R0C-12 corrective acceptance role', 'ACTIVE', ?, ?)
                """, id, tenantId, code, code + " role", Timestamp.from(now), Timestamp.from(now));
        createdRoles.add(id);
        return id;
    }

    /** Grants a capability to a role through the REAL access_capabilities catalog.
     *
     * <p>Capability codes are stored UPPERCASE (V20260901_1 canonicalization);
     * the production {@code AccessCapabilityService.requireCode} normalizes
     * lookups with {@code toUpperCase(Locale.ROOT)}, so the fixture matches
     * the same canonical catalog with {@code UPPER(?)}.</p>
     */
    private void grantCapability(UUID tenantId, UUID roleId, String capabilityCode) {
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, c.id, NOW()
                FROM access_capabilities c
                WHERE c.code = UPPER(?) AND c.status = 'ACTIVE'
                """, tenantId, roleId, capabilityCode);
    }

    /** Assigns a role to a user through user_role_assignments (status ACTIVE by default). */
    private void assignRole(UUID tenantId, UUID userId, UUID roleId, String status) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NULL, ?, ?, ?)
                """, tenantId, userId, roleId, status, Timestamp.from(now), Timestamp.from(now));
    }

    /** Builds the REAL JwtAuthenticationFilter details shape (String values). */
    private Authentication stringDetailsAuth(UUID tenantId, UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId.toString());
        details.put("user_id", userId.toString());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId.toString(), null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(details);
        return auth;
    }

    /** Provisions a complete EXECUTIVE_VIEW identity (grant chain, migration semantics). */
    private record ExecutiveIdentity(UUID tenantId, UUID userId, UUID roleId) {}

    private ExecutiveIdentity provisionExecutiveViewer(UUID tenantId, String email) {
        UUID userId = seedUser(tenantId, email);
        UUID roleId = seedRole(tenantId, "EXECUTIVE_VIEW");
        grantCapability(tenantId, roleId, "EXECUTIVE_VIEW");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(tenantId, roleId, code);
        }
        assignRole(tenantId, userId, roleId, "ACTIVE");
        return new ExecutiveIdentity(tenantId, userId, roleId);
    }

    @AfterEach
    void cleanup() {
        // Reverse dependency order, scoped to THIS run's records only.
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        }
        for (UUID roleId : createdRoles) {
            jdbc.update("DELETE FROM role_capabilities WHERE role_id = ?", roleId);
        }
        for (UUID roleId : createdRoles) {
            jdbc.update("DELETE FROM user_role_assignments WHERE role_id = ?", roleId);
            jdbc.update("DELETE FROM roles WHERE id = ?", roleId);
        }
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (UUID tenantId : createdTenants) {
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        // Defensive restore of any capability status touched by RB-12.
        jdbc.update("""
                UPDATE access_capabilities SET status = 'ACTIVE'
                WHERE status = 'INACTIVE' AND code IN (UPPER(?), UPPER(?))
                """, "subscription.read", "catalog.manage");
        createdTenants.clear();
        createdUsers.clear();
        createdRoles.clear();
    }

    // ------------------------------------------------------------------
    // RB-01 / RB-02 — principal representation compatibility
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RB-01: String principal IDs (REAL filter shape) evaluate capabilities through the real grant chain")
    void rb01_stringPrincipals_evaluateCapabilities() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb01-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleId = seedRole(tenantId, "EXECUTIVE_VIEW");
        grantCapability(tenantId, roleId, "EXECUTIVE_VIEW");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(tenantId, roleId, code);
        }
        assignRole(tenantId, userId, roleId, "ACTIVE");

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(stringDetailsAuth(tenantId, userId));

        assertThat(result.authenticated()).as("String details must authenticate").isTrue();
        for (String code : REQUIRED_READ_CAPABILITIES) {
            assertThat(result.capabilities())
                    .as("granted read capability %s must be true through the real chain", code)
                    .containsEntry(code, true);
        }
    }

    @Test
    @DisplayName("RB-02: UUID principal IDs remain backward-compatible through the real chain")
    void rb02_uuidPrincipals_backwardCompatible() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb02-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleId = seedRole(tenantId, "EXECUTIVE_VIEW");
        grantCapability(tenantId, roleId, "EXECUTIVE_VIEW");
        grantCapability(tenantId, roleId, "subscription.read");
        assignRole(tenantId, userId, roleId, "ACTIVE");

        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId); // UUID object — legacy shape
        details.put("user_id", userId);     // UUID object — legacy shape
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId.toString(), null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(details);

        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(auth);

        assertThat(result.authenticated()).isTrue();
        assertThat(result.capabilities()).containsEntry("subscription.read", true);
        assertThat(result.capabilities()).containsEntry("catalog.manage", false);
    }

    // ------------------------------------------------------------------
    // RB-03..RB-06 — fail-closed principal extraction (real chain)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RB-03: malformed tenant string fails closed on the real chain")
    void rb03_malformedTenant_failsClosed() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb03-" + UUID.randomUUID() + "@rbac-acc.test");

        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", "definitely-not-a-uuid");
        details.put("user_id", userId.toString());

        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(auth(details));
        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("RB-04: malformed user string fails closed on the real chain")
    void rb04_malformedUser_failsClosed() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb04-" + UUID.randomUUID() + "@rbac-acc.test");

        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId.toString());
        details.put("user_id", "user-42-no-uuid");

        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(auth(details));
        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("RB-05: missing tenant_id / user_id fails closed on the real chain")
    void rb05_missingIds_failsClosed() {
        Map<String, Object> onlyUser = new HashMap<>();
        onlyUser.put("user_id", UUID.randomUUID().toString());
        ControlPlaneAccessService.AccessCheckV2 r1 = accessService.accessCheck(auth(onlyUser));
        assertThat(r1.authenticated()).isFalse();
        assertThat(r1.capabilities()).isEmpty();

        Map<String, Object> onlyTenant = new HashMap<>();
        onlyTenant.put("tenant_id", UUID.randomUUID().toString());
        ControlPlaneAccessService.AccessCheckV2 r2 = accessService.accessCheck(auth(onlyTenant));
        assertThat(r2.authenticated()).isFalse();
        assertThat(r2.capabilities()).isEmpty();
    }

    @Test
    @DisplayName("RB-06: unauthenticated Authentication fails closed on the real chain")
    void rb06_unauthenticated_failsClosed() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "anon@example.com", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setAuthenticated(false);
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", UUID.randomUUID().toString());
        details.put("user_id", UUID.randomUUID().toString());
        auth.setDetails(details);

        ControlPlaneAccessService.AccessCheckV2 result = accessService.accessCheck(auth);
        assertThat(result.authenticated()).isFalse();
        assertThat(result.capabilities()).isEmpty();
    }

    private Authentication auth(Map<String, Object> details) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("principal", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(details);
        return auth;
    }

    // ------------------------------------------------------------------
    // RB-07 / RB-08 / RB-09 — role semantics (migration contract)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RB-07: EXECUTIVE_VIEW role yields the ten mandatory read capabilities")
    void rb07_executiveView_role_readCapabilities() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        ExecutiveIdentity identity = provisionExecutiveViewer(
                tenantId, "rb07-" + UUID.randomUUID() + "@rbac-acc.test");

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(stringDetailsAuth(identity.tenantId(), identity.userId()));

        assertThat(result.authenticated()).isTrue();
        for (String code : REQUIRED_READ_CAPABILITIES) {
            assertThat(result.capabilities())
                    .as("EXECUTIVE_VIEW must imply %s", code)
                    .containsEntry(code, true);
        }
        // A read-only role must NOT hold manage/action powers (no privilege expansion).
        for (String code : MANAGE_AND_ACTION_CAPABILITIES) {
            assertThat(result.capabilities())
                    .as("EXECUTIVE_VIEW must NOT imply %s", code)
                    .containsEntry(code, false);
        }
    }

    @Test
    @DisplayName("RB-08: EXECUTIVE_MANAGE role yields manage/action capabilities and reads")
    void rb08_executiveManage_role_manageCapabilities() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb08-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleId = seedRole(tenantId, "EXECUTIVE_MANAGE");
        grantCapability(tenantId, roleId, "EXECUTIVE_MANAGE");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(tenantId, roleId, code);
        }
        for (String code : MANAGE_AND_ACTION_CAPABILITIES) {
            grantCapability(tenantId, roleId, code);
        }
        assignRole(tenantId, userId, roleId, "ACTIVE");

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(stringDetailsAuth(tenantId, userId));

        assertThat(result.authenticated()).isTrue();
        for (String code : MANAGE_AND_ACTION_CAPABILITIES) {
            assertThat(result.capabilities())
                    .as("EXECUTIVE_MANAGE must imply %s", code)
                    .containsEntry(code, true);
        }
        for (String code : REQUIRED_READ_CAPABILITIES) {
            assertThat(result.capabilities()).containsEntry(code, true);
        }
    }

    @Test
    @DisplayName("RB-09: unrelated role receives no unauthorized SCP capability but stays authenticated")
    void rb09_unrelatedRole_noScpCapabilities_stillAuthenticated() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(tenantId, "rb09-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleId = seedRole(tenantId, "SHOP_VIEWER");
        // Unrelated, non-SCP capability only (stored UPPERCASE — USER.READ).
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, c.id, NOW()
                FROM access_capabilities c WHERE c.code = 'USER.READ'
                """, tenantId, roleId);
        assignRole(tenantId, userId, roleId, "ACTIVE");

        ControlPlaneAccessService.AccessCheckV2 result =
                accessService.accessCheck(stringDetailsAuth(tenantId, userId));

        // Authorization failure is NOT authentication failure (mission §6).
        assertThat(result.authenticated()).isTrue();
        for (String code : REQUIRED_READ_CAPABILITIES) {
            assertThat(result.capabilities()).containsEntry(code, false);
        }
        for (String code : MANAGE_AND_ACTION_CAPABILITIES) {
            assertThat(result.capabilities()).containsEntry(code, false);
        }
    }

    // ------------------------------------------------------------------
    // RB-10 / RB-11 / RB-12 — grant lifecycle and tenant isolation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RB-10: tenant A grants cannot authorize tenant B identities (schema + service proof)")
    void rb10_crossTenantGrants_dontAuthorize() {
        UUID tenantA = seedTenant("a-" + UUID.randomUUID().toString().substring(0, 8));
        UUID tenantB = seedTenant("b-" + UUID.randomUUID().toString().substring(0, 8));
        // User belongs to tenant B and holds the executive role ONLY in tenant B.
        UUID userIdB = seedUser(tenantB, "rb10-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleB = seedRole(tenantB, "EXECUTIVE_VIEW");
        grantCapability(tenantB, roleB, "EXECUTIVE_VIEW");
        grantCapability(tenantB, roleB, "subscription.read");
        assignRole(tenantB, userIdB, roleB, "ACTIVE");

        // (a) Schema-level isolation: user_role_assignments carries a composite
        //     FK (tenant_id, user_id) → users(tenant_id, id) — a tenant A grant
        //     row for a tenant B user is IMPOSSIBLE to insert.
        UUID roleA = seedRole(tenantA, "EXECUTIVE_VIEW");
        grantCapability(tenantA, roleA, "subscription.read");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> assignRole(tenantA, userIdB, roleA, "ACTIVE"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("fk_user_role_user");

        // (b) Service-level isolation: evaluating tenant A scope for the
        //     tenant B user is REJECTED — UserRoleGrantService.requireUser
        //     throws because the user does not exist in tenant A (fail-closed).
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> evaluationService.evaluate(tenantA, userIdB, "subscription.read", null))
                .isInstanceOf(com.sanad.platform.access.AccessResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        // §6 note: "authenticated=true with denied capability" is pinned by
        // RB-09 (unrelated role) over the same real transactional chain, and
        // by RB-13/14 over real HTTP. A foreign-tenant identity can never
        // reach this consumer through HTTP at all — the JwtAuthenticationFilter
        // rejects unknown tenant/user pairs with 401 before any controller runs.
    }

    @Test
    @DisplayName("RB-11: revoking the role assignment removes the capability")
    void rb11_revokedRole_removesCapability() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        ExecutiveIdentity identity = provisionExecutiveViewer(
                tenantId, "rb11-" + UUID.randomUUID() + "@rbac-acc.test");

        ControlPlaneAccessService.AccessCheckV2 before =
                accessService.accessCheck(stringDetailsAuth(identity.tenantId(), identity.userId()));
        assertThat(before.capabilities()).containsEntry("subscription.read", true);

        jdbc.update(
                "UPDATE user_role_assignments SET status = 'REVOKED', updated_at = NOW() WHERE user_id = ?",
                identity.userId());

        ControlPlaneAccessService.AccessCheckV2 after =
                accessService.accessCheck(stringDetailsAuth(identity.tenantId(), identity.userId()));
        assertThat(after.authenticated()).isTrue();
        assertThat(after.capabilities()).containsEntry("subscription.read", false);
    }

    @Test
    @DisplayName("RB-12: inactive capability is denied for everyone")
    void rb12_inactiveCapability_denied() {
        UUID tenantId = seedTenant(UUID.randomUUID().toString().substring(0, 8));
        ExecutiveIdentity identity = provisionExecutiveViewer(
                tenantId, "rb12-" + UUID.randomUUID() + "@rbac-acc.test");

        try {
            jdbc.update("""
                    UPDATE access_capabilities SET status = 'INACTIVE', updated_at = NOW()
                    WHERE code = UPPER(?)
                    """, "subscription.read");
            AccessDecisionResponse decision = evaluationService.evaluate(
                    identity.tenantId(), identity.userId(), "subscription.read", null);
            assertThat(decision.allowed())
                    .as("an INACTIVE capability must be denied even with a valid role grant")
                    .isFalse();
        } finally {
            jdbc.update("""
                    UPDATE access_capabilities SET status = 'ACTIVE', updated_at = NOW()
                    WHERE code = UPPER(?)
                    """, "subscription.read");
        }

        AccessDecisionResponse restored = evaluationService.evaluate(
                identity.tenantId(), identity.userId(), "subscription.read", null);
        assertThat(restored.allowed()).isTrue();
    }

    // ------------------------------------------------------------------
    // RB-13 / RB-14 / RB-15 — REAL HTTP path with a REAL application JWT
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RB-13/14: REAL JWT over HTTP → authenticated=true and the required read capability map")
    void rb13_rb14_realJwt_httpAccessCheck() throws Exception {
        UUID tenantId = UUID.fromString(CONTROL_TENANT_ID);
        seedTenantWithId(tenantId, "control-plane-acc");
        UUID userId = seedUser(tenantId, "rb13-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleId = seedRole(tenantId, "EXECUTIVE_VIEW");
        grantCapability(tenantId, roleId, "EXECUTIVE_VIEW");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(tenantId, roleId, code);
        }
        assignRole(tenantId, userId, roleId, "ACTIVE");

        // REAL application-issued JWT — String tenant_id/user_id claims,
        // exactly what the browser presents through the BFF.
        String token = jwtTokenProvider.mintAccessToken(userId, tenantId, "rb13@rbac-acc.test");

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/v1/executive/access-check/v2")
                                .header("Authorization", "Bearer " + token)
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.capabilities").isMap())
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString();
        assertThat(body).contains("\"authenticated\":true");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            assertThat(body)
                    .as("HTTP access-check must grant %s=true for an executive viewer", code)
                    .contains("\"" + code + "\":true");
        }
        // Read-only role: manage powers stay explicitly false (no privilege expansion).
        assertThat(body).contains("\"catalog.manage\":false");
        assertThat(body).contains("\"billing.adjust\":false");
    }

    @Test
    @DisplayName("RB-13b: missing JWT over HTTP fails closed with 401/403")
    void rb13b_missingJwt_httpFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/executive/access-check/v2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RB-15: cross-tenant request denied — tenant binding and control-plane guard")
    void rb15_crossTenant_requestsDenied() throws Exception {
        UUID controlTenantId = UUID.fromString(CONTROL_TENANT_ID);
        seedTenantWithId(controlTenantId, "control-plane-acc");
        UUID controlUser = seedUser(controlTenantId, "rb15a-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID controlRole = seedRole(controlTenantId, "EXECUTIVE_VIEW");
        grantCapability(controlTenantId, controlRole, "EXECUTIVE_VIEW");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(controlTenantId, controlRole, code);
        }
        assignRole(controlTenantId, controlUser, controlRole, "ACTIVE");
        String controlToken = jwtTokenProvider.mintAccessToken(
                controlUser, controlTenantId, "rb15a@rbac-acc.test");

        // (a) Tenant binding: a request-supplied tenantId conflicting with the
        //     JWT tenant is rejected by JwtAuthenticationFilter (403).
        UUID foreignTenant = seedTenant("x-" + UUID.randomUUID().toString().substring(0, 8));
        mockMvc.perform(get("/api/v1/executive/access-check/v2")
                                .param("tenantId", foreignTenant.toString())
                                .header("Authorization", "Bearer " + controlToken))
                .andExpect(status().isForbidden());

        // (b) Control-plane guard: a legitimate EXECUTIVE_VIEW identity from a
        //     NON-control tenant (with EXECUTIVE_VIEW + reads in its own tenant)
        //     is denied access-check/v2 by ControlPlaneAccessGuard (403).
        UUID userIdB = seedUser(foreignTenant, "rb15b-" + UUID.randomUUID() + "@rbac-acc.test");
        UUID roleB = seedRole(foreignTenant, "EXECUTIVE_VIEW");
        grantCapability(foreignTenant, roleB, "EXECUTIVE_VIEW");
        for (String code : REQUIRED_READ_CAPABILITIES) {
            grantCapability(foreignTenant, roleB, code);
        }
        assignRole(foreignTenant, userIdB, roleB, "ACTIVE");
        String foreignToken = jwtTokenProvider.mintAccessToken(
                userIdB, foreignTenant, "rb15b@rbac-acc.test");

        mockMvc.perform(get("/api/v1/executive/access-check/v2")
                                .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isForbidden());
    }

    private void seedTenantWithId(UUID id, String subdomain) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, "RBAC-ACC Control Plane", subdomain,
                Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
    }
}
