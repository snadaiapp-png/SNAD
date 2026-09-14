package com.sanad.platform.subscription.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATH-B G1-G — DYNAMIC 403 FORENSIC over REAL HTTP (real socket, real
 * server, real JWT issuance and validation, host-native PostgreSQL Direct;
 * NO Docker, NO Testcontainers, NO H2).
 *
 * <p>Scenario A — an authorized Control-Plane identity (the migration-seeded
 * platform administrator of the control-plane tenant) performs
 * {@code GET /api/v1/executive/subscriptions?tenantId=<tenant>} and the call
 * SUCCEEDS (HTTP 200) with tenant-filtered rows.</p>
 *
 * <p>Scenario B — an ordinary Tenant Admin (a fully capable ADMIN of another
 * tenant: the same ADMIN role invariant every tenant receives, including
 * EXECUTIVE_VIEW) sends the SAME executive request FOR ITS OWN tenant (so
 * the JWT-filter tenant binding passes) and is still denied with HTTP 403
 * by {@code ControlPlaneAccessGuard} (the control-plane tenant pin). Because
 * the request carries the caller's OWN tenantId, the denial can only come
 * from the control-plane pin — capability evaluation has already passed.
 * {@code ControlPlaneAccessGuard} is NOT weakened by this forensic; the
 * control-plane tenant id is only supplied as configuration.</p>
 *
 * <p>Scenario C — tenant-facing subscription read surface. A handler-mapping
 * scan proves that EVERY route whose path mentions a subscription is mounted
 * under {@code /api/v1/executive}; live probes of non-existent tenant-facing
 * read surfaces return 404. Recorded markers:</p>
 * <ul>
 *   <li>{@link #EXPECTED_EXECUTIVE_DENIAL} — scenario B's guarded 403</li>
 *   <li>{@link #TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING} —
 *       scenario C's negative existence proof</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // The migration-seeded control-plane tenant (V20260813_1).
        // Matches the production contract: SANAD_CONTROL_PLANE_TENANT_ID.
        "sanad.control-plane.tenant-id=" + G1GDynamic403ForensicPostgresTest.CONTROL_PLANE_TENANT_ID
})
// Full-app contexts require the `local` profile for profile-gated
// infrastructure adapters (e.g. LocalEmailAdapter as the default EmailPort):
// application.yml defaults to `local`, but CI canonical environments set
// SPRING_PROFILES_ACTIVE=default explicitly — pin the profile so the
// forensic context boots identically everywhere (same convention as
// BillingStateServiceIntegrationTest).
@ActiveProfiles("local")
class G1GDynamic403ForensicPostgresTest {

    static final String CONTROL_PLANE_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    static final String CONTROL_ADMIN_EMAIL = "snad.ai.app@gmail.com";
    static final String CONTROL_ADMIN_PASSWORD = "Senen1985";

    static final String TENANT_ADMIN_EMAIL = "g1g-forensic-admin@forensic.sanad.test";
    static final String TENANT_ADMIN_PASSWORD = "G1G-Forensic#2026";

    /** Directive-mandated forensic markers (recorded verbatim). */
    public static final String EXPECTED_EXECUTIVE_DENIAL = "EXPECTED_EXECUTIVE_DENIAL";
    public static final String TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING =
            "TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING";

    private static final String EXECUTIVE_SUBSCRIPTIONS_PATH = "/api/v1/executive/subscriptions";

    @LocalServerPort
    int port;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private UUID forensicTenantId;
    private UUID forensicUserId;
    private UUID forensicRoleId;
    private UUID forensicSubscriptionId;
    private String forensicSubdomain;

    // ------------------------------------------------------------------
    // Fixture — disposable tenant + fully capable tenant admin + subscription
    // ------------------------------------------------------------------

    @BeforeEach
    void seedForensicIdentityChain() {
        // Self-healing seed: purge any residue from earlier runs of this class
        // (login mints refresh-token rows, which FK-block naive user deletes
        // and would make the fixed forensic email ambiguous across tenants).
        purgeForensicResidue();

        forensicTenantId = UUID.randomUUID();
        forensicUserId = UUID.randomUUID();
        forensicRoleId = UUID.randomUUID();
        forensicSubscriptionId = UUID.randomUUID();
        forensicSubdomain = "g1g-forensic-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();

        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, forensicTenantId, "G1-G Forensic Tenant", forensicSubdomain,
                Timestamp.from(now), Timestamp.from(now));

        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, must_change_password, platform_admin,
                                   session_version, created_at, updated_at)
                VALUES (?, ?, ?, 'G1-G Forensic Tenant Admin', 'ACTIVE', ?, false, false, 0, ?, ?)
                """, forensicUserId, forensicTenantId, TENANT_ADMIN_EMAIL,
                passwordEncoder.encode(TENANT_ADMIN_PASSWORD),
                Timestamp.from(now), Timestamp.from(now));

        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
                VALUES (?, ?, 'ADMIN', 'Administrator', 'G1-G forensic tenant admin', 'ACTIVE', ?, ?)
                """, forensicRoleId, forensicTenantId, Timestamp.from(now), Timestamp.from(now));

        // Mirror the platform-wide ADMIN invariant (V20260813_1 STEP 5 /
        // V20260820_9): every ACTIVE capability is granted to every ADMIN role.
        // The tenant admin therefore holds EXECUTIVE_VIEW — the 403 in
        // scenario B is attributable to ControlPlaneAccessGuard alone.
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, c.id, NOW()
                FROM access_capabilities c
                WHERE c.status = 'ACTIVE'
                """, forensicTenantId, forensicRoleId);

        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NULL, 'ACTIVE', ?, ?)
                """, forensicTenantId, forensicUserId, forensicRoleId,
                Timestamp.from(now), Timestamp.from(now));

        // One real subscription row for the forensic tenant so scenario A's
        // tenant-filtered read returns actual filtered data (not just []).
        UUID planId = jdbc.queryForObject(
                "SELECT id FROM saas_plans WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                UUID.class);
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                        seat_quantity, credit_balance_minor, started_at, current_period_start,
                        current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 5, 0, NOW(), NOW(),
                        NOW() + INTERVAL '30 days', FALSE, NOW(), NOW())
                """, forensicSubscriptionId, forensicTenantId, planId);
    }

    @AfterEach
    void cleanupForensicRecords() {
        purgeForensicResidue();
    }

    /** Deletes every trace of the forensic identity chain, FK-safe order. */
    private void purgeForensicResidue() {
        List<UUID> tenantIds = jdbc.queryForList(
                "SELECT id FROM tenants WHERE subdomain LIKE 'g1g-forensic-%'", UUID.class);
        List<UUID> userIds = jdbc.queryForList(
                "SELECT id FROM users WHERE email = ?", UUID.class, TENANT_ADMIN_EMAIL);
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        }
        for (UUID tenantId : tenantIds) {
            jdbc.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
        }
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (UUID tenantId : tenantIds) {
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    // ------------------------------------------------------------------
    // Real-HTTP helpers
    // ------------------------------------------------------------------

    private String login(String email, String password) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), headers),
                String.class);
        assertThat(response.getStatusCode().value())
                .as("login must succeed for " + email + " — body: " + response.getBody())
                .isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getBody());
        String token = body.path("accessToken").asText(null);
        assertThat(token).as("login must mint an accessToken").isNotBlank();
        return token;
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // ------------------------------------------------------------------
    // Scenario A — authorized Control-Plane identity reads tenant-filtered subscriptions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A: control-plane identity reads tenant-filtered executive subscriptions (200)")
    void scenarioA_controlPlaneIdentityReadsTenantFilteredSubscriptions() throws Exception {
        String controlToken = login(CONTROL_ADMIN_EMAIL, CONTROL_ADMIN_PASSWORD);

        // (1) The executive tenant-filtered read model (v2 grid). The
        // JwtAuthenticationFilter's tenant binding is certified fail-closed
        // (RB-15a): a ?tenantId different from the JWT tenant is denied at the
        // filter, so the control-plane identity reads tenant-tagged
        // subscription rows through the grid and filters on tenantId equal to
        // its own tenant for the singular read (2). Neither the guard nor the
        // filter is weakened here.
        ResponseEntity<String> grid = get("/api/v1/executive/subscriptions/v2?size=50", controlToken);
        assertThat(grid.getStatusCode().value()).isEqualTo(200);
        JsonNode gridBody = objectMapper.readTree(grid.getBody());
        JsonNode content = gridBody.path("content");
        assertThat(content.isArray()).isTrue();

        JsonNode forensicRow = null;
        for (JsonNode row : content) {
            if (forensicTenantId.toString().equals(row.path("tenantId").asText())) {
                forensicRow = row;
                break;
            }
        }
        assertThat(forensicRow)
                .as("control-plane identity must be able to read the forensic tenant's subscription row")
                .isNotNull();
        assertThat(forensicRow.path("status").asText()).isEqualTo("ACTIVE");

        // (2) A literally tenant-filtered read whose filter matches the
        // control-plane identity's own JWT tenant: binding passes, guard
        // passes, controller returns the filtered list (200).
        ResponseEntity<String> ownFiltered = get(
                EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + CONTROL_PLANE_TENANT_ID, controlToken);
        assertThat(ownFiltered.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(ownFiltered.getBody()).isArray()).isTrue();
    }

    // ------------------------------------------------------------------
    // Scenario B — ordinary Tenant Admin hits the SAME executive route and is 403-denied
    // ------------------------------------------------------------------

    @Test
    @DisplayName("B: ordinary tenant admin on /api/v1/executive/** is denied 403 — EXPECTED_EXECUTIVE_DENIAL")
    void scenarioB_tenantAdminExecutiveRequestIsDenied403() throws Exception {
        String tenantAdminToken = login(TENANT_ADMIN_EMAIL, TENANT_ADMIN_PASSWORD);

        // The request targets the caller's OWN tenant — the JwtAuthenticationFilter
        // tenant binding passes, capability evaluation passes (ADMIN invariant), so
        // the 403 can only originate from the ControlPlaneAccessGuard tenant pin.
        ResponseEntity<String> response = get(
                EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + forensicTenantId, tenantAdminToken);

        assertThat(response.getStatusCode().value())
                .as("EXPECTED_EXECUTIVE_DENIAL: " + response.getBody())
                .isEqualTo(403);

        // Contrast: an unauthenticated call is 401 (authentication), not 403 (authorization).
        ResponseEntity<String> anonymous = get(
                EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + forensicTenantId, null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // Scenario C — tenant-facing subscription read surface
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C: no tenant-facing subscription read surface — TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING")
    void scenarioC_tenantFacingSubscriptionReadSurfaceDoesNotExist() throws Exception {
        // (1) Handler-mapping scan: EVERY route whose path mentions a
        // subscription must be mounted under the control-plane surface.
        List<String> subscriptionRoutes = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            for (String pattern : info.getPatternValues()) {
                if (pattern.toLowerCase().contains("subscription")) {
                    subscriptionRoutes.add(pattern);
                    assertThat(pattern)
                            .as("every subscription route must be control-plane gated ("
                                    + handlerMethod.getBeanType().getSimpleName() + ")")
                            .startsWith("/api/v1/executive");
                }
            }
        });
        assertThat(subscriptionRoutes)
                .as("the executive subscription surface must exist for scenario A to be meaningful")
                .isNotEmpty();

        // (2) Live probes with the tenant admin's real token: tenant-facing
        // read surfaces are MISSING (404 — no route at all, not a denial).
        String tenantAdminToken = login(TENANT_ADMIN_EMAIL, TENANT_ADMIN_PASSWORD);
        for (String missingSurface : List.of(
                "/api/v1/workspace/subscriptions",
                "/api/v1/tenant/subscriptions",
                "/api/v1/workspace/tenants/" + forensicTenantId + "/subscriptions")) {
            ResponseEntity<String> probe = get(missingSurface, tenantAdminToken);
            assertThat(probe.getStatusCode().value())
                    .as("probe " + missingSurface + " must be 404 (surface missing)")
                    .isEqualTo(404);
        }

        // (3) Record the mandated markers verbatim in the execution evidence.
        System.out.println("G1G_FORENSIC_MARKER: " + EXPECTED_EXECUTIVE_DENIAL
                + " (scenario B: 403 on " + EXECUTIVE_SUBSCRIPTIONS_PATH + ")");
        System.out.println("G1G_FORENSIC_MARKER: " + TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING
                + " (scenario C: no tenant-facing subscription read route; "
                + subscriptionRoutes.size() + " subscription routes, all under /api/v1/executive)");
    }
}
