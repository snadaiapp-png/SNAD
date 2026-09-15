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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATH-B G1-G — dynamic 403 forensic over a real HTTP server and host-native
 * PostgreSQL Direct. Both identities are disposable test fixtures; no seeded
 * owner credential is embedded in this source.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sanad.control-plane.tenant-id=" + G1GDynamic403ForensicPostgresTest.CONTROL_PLANE_TENANT_ID
})
@ActiveProfiles("local")
class G1GDynamic403ForensicPostgresTest {

    static final String CONTROL_PLANE_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private String controlAdminCredential;
    private String tenantAdminCredential;

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

    private UUID controlUserId;
    private UUID controlRoleId;
    private String controlAdminEmail;
    private UUID forensicTenantId;
    private UUID forensicUserId;
    private UUID forensicRoleId;
    private UUID forensicSubscriptionId;
    private String tenantAdminEmail;

    @BeforeEach
    void seedForensicIdentityChains() {
        purgeForensicResidue();
        Instant now = Instant.now();
        controlAdminCredential = UUID.randomUUID() + "-C!9g";
        tenantAdminCredential = UUID.randomUUID() + "-T!9g";

        UUID controlTenantId = UUID.fromString(CONTROL_PLANE_TENANT_ID);
        controlRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = 'ADMIN' AND status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                UUID.class, controlTenantId);
        assertThat(controlRoleId).as("control-plane ADMIN role must exist").isNotNull();

        controlUserId = UUID.randomUUID();
        controlAdminEmail = "g1g-control-" + controlUserId + "@forensic.sanad.test";
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, must_change_password, platform_admin,
                                   session_version, created_at, updated_at)
                VALUES (?, ?, ?, 'G1-G Disposable Control Admin', 'ACTIVE', ?, false, true, 0, ?, ?)
                """, controlUserId, controlTenantId, controlAdminEmail,
                passwordEncoder.encode(controlAdminCredential), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NULL, 'ACTIVE', ?, ?)
                """, controlTenantId, controlUserId, controlRoleId, Timestamp.from(now), Timestamp.from(now));

        forensicTenantId = UUID.randomUUID();
        forensicUserId = UUID.randomUUID();
        forensicRoleId = UUID.randomUUID();
        forensicSubscriptionId = UUID.randomUUID();
        tenantAdminEmail = "g1g-tenant-" + forensicUserId + "@forensic.sanad.test";
        String forensicSubdomain = "g1g-forensic-" + UUID.randomUUID().toString().substring(0, 8);

        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, 'G1-G Forensic Tenant', ?, 'ACTIVE', ?, ?)
                """, forensicTenantId, forensicSubdomain, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, must_change_password, platform_admin,
                                   session_version, created_at, updated_at)
                VALUES (?, ?, ?, 'G1-G Forensic Tenant Admin', 'ACTIVE', ?, false, false, 0, ?, ?)
                """, forensicUserId, forensicTenantId, tenantAdminEmail,
                passwordEncoder.encode(tenantAdminCredential), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
                VALUES (?, ?, 'ADMIN', 'Administrator', 'G1-G forensic tenant admin', 'ACTIVE', ?, ?)
                """, forensicRoleId, forensicTenantId, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, c.id, NOW()
                FROM access_capabilities c
                WHERE c.status = 'ACTIVE'
                """, forensicTenantId, forensicRoleId);
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NULL, 'ACTIVE', ?, ?)
                """, forensicTenantId, forensicUserId, forensicRoleId, Timestamp.from(now), Timestamp.from(now));

        UUID planId = jdbc.queryForObject(
                "SELECT id FROM saas_plans WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1", UUID.class);
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

    private void purgeForensicResidue() {
        List<UUID> disposableUsers = jdbc.queryForList(
                "SELECT id FROM users WHERE email LIKE 'g1g-%@forensic.sanad.test'", UUID.class);
        for (UUID userId : disposableUsers) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        }

        List<UUID> tenantIds = jdbc.queryForList(
                "SELECT id FROM tenants WHERE subdomain LIKE 'g1g-forensic-%'", UUID.class);
        for (UUID tenantId : tenantIds) {
            jdbc.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        for (UUID userId : disposableUsers) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private String login(String email, String password) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), headers),
                String.class);
        assertThat(response.getStatusCode().value())
                .as("login must succeed for disposable forensic identity")
                .isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getBody());
        String token = body.path("accessToken").asText(null);
        assertThat(token).isNotBlank();
        return token;
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("A: disposable control-plane identity can read executive subscriptions")
    void scenarioA_controlPlaneIdentityReadsExecutiveSubscriptions() throws Exception {
        String controlToken = login(controlAdminEmail, controlAdminCredential);

        ResponseEntity<String> grid = get("/api/v1/executive/subscriptions/v2?size=50", controlToken);
        assertThat(grid.getStatusCode().value()).isEqualTo(200);
        JsonNode content = objectMapper.readTree(grid.getBody()).path("content");
        assertThat(content.isArray()).isTrue();
        boolean forensicRowFound = false;
        for (JsonNode row : content) {
            if (forensicTenantId.toString().equals(row.path("tenantId").asText())) {
                forensicRowFound = true;
                assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
            }
        }
        assertThat(forensicRowFound).isTrue();

        ResponseEntity<String> ownFiltered = get(
                EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + CONTROL_PLANE_TENANT_ID, controlToken);
        assertThat(ownFiltered.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("B: ordinary tenant admin is denied executive subscription surface")
    void scenarioB_tenantAdminExecutiveRequestIsDenied403() throws Exception {
        String tenantAdminToken = login(tenantAdminEmail, tenantAdminCredential);
        ResponseEntity<String> response = get(
                EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + forensicTenantId, tenantAdminToken);
        assertThat(response.getStatusCode().value())
                .as(EXPECTED_EXECUTIVE_DENIAL)
                .isEqualTo(403);
        assertThat(get(EXECUTIVE_SUBSCRIPTIONS_PATH + "?tenantId=" + forensicTenantId, null)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("C: tenant-facing subscription read surface is absent")
    void scenarioC_tenantFacingSubscriptionReadSurfaceDoesNotExist() throws Exception {
        List<String> subscriptionRoutes = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            for (String pattern : info.getPatternValues()) {
                if (pattern.toLowerCase().contains("subscription")) {
                    subscriptionRoutes.add(pattern);
                    assertThat(pattern)
                            .as("subscription route owned by " + handlerMethod.getBeanType().getSimpleName())
                            .startsWith("/api/v1/executive");
                }
            }
        });
        assertThat(subscriptionRoutes).isNotEmpty();

        String tenantAdminToken = login(tenantAdminEmail, tenantAdminCredential);
        for (String missingSurface : List.of(
                "/api/v1/workspace/subscriptions",
                "/api/v1/tenant/subscriptions",
                "/api/v1/workspace/tenants/" + forensicTenantId + "/subscriptions")) {
            assertThat(get(missingSurface, tenantAdminToken).getStatusCode().value())
                    .as(TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING + ": " + missingSurface)
                    .isEqualTo(404);
        }

        System.out.println("G1G_FORENSIC_MARKER: " + EXPECTED_EXECUTIVE_DENIAL);
        System.out.println("G1G_FORENSIC_MARKER: " + TENANT_WORKSPACE_SUBSCRIPTION_READ_SURFACE_MISSING);
    }
}
