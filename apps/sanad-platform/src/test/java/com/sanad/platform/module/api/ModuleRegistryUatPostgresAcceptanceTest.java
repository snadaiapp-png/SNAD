package com.sanad.platform.module.api;

import com.sanad.platform.commerce.PgAcceptanceWiringConfig;
import com.sanad.platform.module.lifecycle.ModuleResetResult;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — Module Registry / Entitlement identity + audit contract.
 *
 * <p>PostgreSQL Direct acceptance (host-native PostgreSQL 16.2, NO Docker, NO
 * Testcontainers, NO H2). Proves on the REAL HTTP path with REAL
 * application-issued JWTs (the production {@code JwtAuthenticationFilter}
 * stores {@code tenant_id}/{@code user_id} as {@code String}):</p>
 *
 * <ul>
 *   <li>UAT-A1: PUT  /plans/{planId}/modules/{code} must NOT 500 (ClassCastException)</li>
 *   <li>UAT-A2: POST /tenants/{id}/entitlements/recalculate must return 200 and write
 *       an audit row carrying the REAL JWT actor tenant/user (the reported UAT blocker)</li>
 *   <li>UAT-A3: POST /tenants/{id}/modules/{code}/reset must NOT 500</li>
 *   <li>UAT-B : GET /audit/v2 must return a camelCase typed contract
 *       (resourceId/resourceType/createdAt/actorTenantId/actorUserId/targetTenantId),
 *       never raw JDBC snake_case keys</li>
 *   <li>UAT-D : an ADMIN-shaped role (EXECUTIVE_MANAGE + V20260830_2 granular grants)
 *       resolves all 21 control-plane capabilities (grant chain on real schema)</li>
 *   <li>UAT-E : usage.read / audit.read regressions stay fixed (200); negative
 *       (no capability → 403) and cross-tenant (fail-closed → 403)</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "sanad.control-plane.tenant-id=" + ModuleRegistryUatPostgresAcceptanceTest.CONTROL_TENANT_ID
})
@AutoConfigureMockMvc
@ActiveProfiles("pg-acceptance")
@Import(PgAcceptanceWiringConfig.class)
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "pg-acceptance")
class ModuleRegistryUatPostgresAcceptanceTest {

    /** Dedicated sentinel control-plane tenant for this suite. */
    static final String CONTROL_TENANT_ID = "00000000-0000-0000-0000-0000000000c2";

    private static final List<String> READ_CAPABILITIES = List.of(
            "subscription.read", "catalog.read", "application.read", "plan.read",
            "pricing.read", "entitlement.read", "usage.read", "billing.read",
            "provisioning.read", "audit.read");

    private static final List<String> MANAGE_CAPABILITIES = List.of(
            "catalog.manage", "application.manage", "plan.manage", "pricing.manage",
            "entitlement.manage", "entitlement.override", "billing.adjust",
            "provisioning.retry", "subscription.create", "subscription.change_plan",
            "subscription.cancel", "subscription.suspend");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private final List<UUID> createdTenants = new ArrayList<>();
    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdRoles = new ArrayList<>();
    private final List<UUID> createdModules = new ArrayList<>();
    private final List<UUID> createdAuditRows = new ArrayList<>();
    private final List<UUID> createdProvisioningJobs = new ArrayList<>();
    private final List<UUID> createdSubscriptions = new ArrayList<>();

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private UUID seedTenant(String subdomainSuffix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, "UAT-ACC " + subdomainSuffix, "uat-acc-" + subdomainSuffix,
                Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
        return id;
    }

    private void seedControlTenant() {
        UUID id = UUID.fromString(CONTROL_TENANT_ID);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, "UAT-ACC Control Plane", "uat-acc-control-plane",
                Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
    }

    private UUID seedUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status,
                                   password_hash, must_change_password, platform_admin,
                                   session_version, created_at, updated_at)
                VALUES (?, ?, ?, 'UAT Acceptance User', 'ACTIVE', 'seed-no-login',
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
                VALUES (?, ?, ?, ?, 'R0C-12 UAT corrective acceptance role', 'ACTIVE', ?, ?)
                """, id, tenantId, code, code + " role", Timestamp.from(now), Timestamp.from(now));
        createdRoles.add(id);
        return id;
    }

    private void grantCapability(UUID tenantId, UUID roleId, String capabilityCode) {
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?, ?, c.id, NOW()
                FROM access_capabilities c
                WHERE c.code = UPPER(?) AND c.status = 'ACTIVE'
                """, tenantId, roleId, capabilityCode);
    }

    private void assignRole(UUID tenantId, UUID userId, UUID roleId) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NULL, 'ACTIVE', ?, ?)
                """, tenantId, userId, roleId, Timestamp.from(now), Timestamp.from(now));
    }

    /** Provision the ADMIN-shaped control-plane identity (V20260813_1 + V20260830_2 semantics). */
    private record AdminIdentity(UUID tenantId, UUID userId, UUID roleId) {}

    private AdminIdentity provisionControlPlaneAdmin(String tag) {
        UUID tenantId = UUID.fromString(CONTROL_TENANT_ID);
        seedControlTenant();
        UUID userId = seedUser(tenantId, tag + "-" + UUID.randomUUID() + "@uat-acc.test");
        UUID roleId = seedRole(tenantId, "ADMIN");
        grantCapability(tenantId, roleId, "EXECUTIVE_MANAGE");
        grantCapability(tenantId, roleId, "EXECUTIVE_VIEW");
        for (String code : READ_CAPABILITIES) grantCapability(tenantId, roleId, code);
        for (String code : MANAGE_CAPABILITIES) grantCapability(tenantId, roleId, code);
        assignRole(tenantId, userId, roleId);
        return new AdminIdentity(tenantId, userId, roleId);
    }

    private String realJwt(UUID tenantId, UUID userId, String email) {
        // REAL application-issued JWT — String tenant_id/user_id claims, the
        // exact production shape produced by JwtAuthenticationFilter.
        return jwtTokenProvider.mintAccessToken(userId, tenantId, email);
    }

    private UUID seedModule(String code) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO modules (id, code, name, status, display_order, enabled, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 0, true, ?, ?)
                """, id, code, "UAT module " + code, Timestamp.from(now), Timestamp.from(now));
        createdModules.add(id);
        return id;
    }

    private UUID anySeededPlanId() {
        return jdbc.queryForObject("SELECT id FROM saas_plans ORDER BY created_at LIMIT 1", UUID.class);
    }

    private void seedAuditRow(UUID targetTenantId, String action) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO platform_audit_logs (id, actor_tenant_id, actor_user_id, target_tenant_id,
                                                 action, resource_type, resource_id, reason, result,
                                                 correlation_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'TENANT_SUBSCRIPTION', ?, 'uat seed row', 'SUCCESS', ?, NOW())
                """, id, targetTenantId, targetTenantId, targetTenantId, action,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        createdAuditRows.add(id);
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdProvisioningJobs) {
            jdbc.update("DELETE FROM provisioning_jobs WHERE id = ?", id);
        }
        for (UUID id : createdSubscriptions) {
            jdbc.update("DELETE FROM provisioning_jobs WHERE subscription_id = ?", id);
            jdbc.update("DELETE FROM tenant_subscriptions WHERE id = ?", id);
        }
        for (UUID id : createdAuditRows) {
            jdbc.update("DELETE FROM platform_audit_logs WHERE id = ?", id);
        }
        // Endpoint-written audit rows, scoped to this run's sentinel tenant.
        jdbc.update("""
                DELETE FROM platform_audit_logs
                WHERE target_tenant_id = ? OR actor_tenant_id = ?
                """, UUID.fromString(CONTROL_TENANT_ID), UUID.fromString(CONTROL_TENANT_ID));
        for (UUID id : createdModules) {
            jdbc.update("DELETE FROM plan_module_entitlements WHERE module_id = ?", id);
            jdbc.update("DELETE FROM module_capabilities WHERE module_id = ?", id);
            jdbc.update("DELETE FROM modules WHERE id = ?", id);
        }
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        }
        for (UUID roleId : createdRoles) {
            jdbc.update("DELETE FROM role_capabilities WHERE role_id = ?", roleId);
            jdbc.update("DELETE FROM user_role_assignments WHERE role_id = ?", roleId);
            jdbc.update("DELETE FROM roles WHERE id = ?", roleId);
        }
        for (UUID tenantId : createdTenants) {
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        createdAuditRows.clear();
        createdModules.clear();
        createdUsers.clear();
        createdRoles.clear();
        createdTenants.clear();
    }

    // ------------------------------------------------------------------
    // UAT-A — BLOCKER A: real-JWT identity extraction (ClassCastException → 500)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-A2 (R1/R10): REAL JWT + ADMIN → entitlements/recalculate = 200, audit row with correct actor")
    void uatA2_recalculate_realJwt_200_andAuditActor() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a2");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/executive/tenants/" + admin.tenantId() + "/entitlements/recalculate")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a2@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk()) // RED today: 500 ClassCastException
                .andExpect(jsonPath("$.status").value("RECALCULATED"))
                .andExpect(jsonPath("$.tenantId").value(admin.tenantId().toString()))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("ClassCastException").doesNotContain("\"status\":500");

        // R10: audit row must carry the REAL JWT actor tenant/user (fail if null/other).
        Integer auditRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_audit_logs
                WHERE action = 'ENTITLEMENTS_RECALCULATED'
                  AND actor_tenant_id = ? AND actor_user_id = ? AND target_tenant_id = ?
                """, Integer.class, admin.tenantId(), admin.userId(), admin.tenantId());
        assertThat(auditRows).as("ENTITLEMENTS_RECALCULATED audit row with JWT actor identity").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("UAT-A1: REAL JWT + ADMIN → PUT plan-module entitlement = 200 (no ClassCastException)")
    void uatA1_putPlanModule_realJwt_200() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a1");
        seedModule("UATCRM");
        UUID planId = anySeededPlanId();
        UUID moduleId = jdbc.queryForObject("SELECT id FROM modules WHERE code = 'UATCRM'", UUID.class);

        mockMvc.perform(
                        put("/api/v1/executive/plans/" + planId + "/modules/UATCRM")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a1@uat-acc.test"))
                                .header("Content-Type", "application/json")
                                .content("""
                                        {"moduleId":"%s","moduleEnabled":true,"capabilityCode":"UATCRM.MAX_ROWS",
                                         "capabilityValue":null,"limitValue":100,"quotaValue":null,"quotaPeriod":"MONTHLY"}
                                        """.formatted(moduleId)))
                .andExpect(status().isOk()) // RED today: 500 ClassCastException
                .andExpect(jsonPath("$.moduleEnabled").value(true));
    }

    @Test
    @DisplayName("UAT-A3: REAL JWT + ADMIN → module reset endpoint must not 500 on identity extraction")
    void uatA3_moduleReset_realJwt_no500() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a3");
        seedModule("UATX"); // not in ModuleResetRegistry → business FAILURE, HTTP 200

        mockMvc.perform(
                        post("/api/v1/executive/tenants/" + admin.tenantId() + "/modules/UATX/reset")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a3@uat-acc.test"))
                                .header("Content-Type", "application/json")
                                .content("{}"))
                .andExpect(status().isOk()) // RED today: 500 ClassCastException before any business logic
                // unsupported module: pre-existing domain contract is an explicit business-failure
                // status (ModuleResetResult.STATUS_FAILED = "RESET_FAILED") with errorMessage, HTTP 200
                .andExpect(jsonPath("$.status").value(ModuleResetResult.STATUS_FAILED));
    }

    // ------------------------------------------------------------------
    // UAT-B — BLOCKER B: /audit/v2 JSON contract (camelCase, typed)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-B (R2): REAL JWT → /audit/v2 exposes camelCase contract keys, never snake_case")
    void uatB_auditContract_camelCaseKeys() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-b");
        seedAuditRow(admin.tenantId(), "SUBSCRIPTION_ACTIVATED");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/executive/audit/v2")
                                .param("tenantId", admin.tenantId().toString())
                                .param("sort", "created_at")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-b@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                // camelCase typed contract — RED today: raw JDBC snake_case keys
                .andExpect(jsonPath("$.content[0].resourceId").exists())
                .andExpect(jsonPath("$.content[0].resourceType").exists())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].actorTenantId").exists())
                .andExpect(jsonPath("$.content[0].actorUserId").exists())
                .andExpect(jsonPath("$.content[0].targetTenantId").exists())
                .andExpect(jsonPath("$.content[0].action").exists())
                .andExpect(jsonPath("$.content[0].result").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // The legacy raw JDBC keys must NEVER leak to the frontend contract.
        assertThat(body).doesNotContain("\"resource_id\"");
        assertThat(body).doesNotContain("\"resource_type\"");
        assertThat(body).doesNotContain("\"created_at\"");
        assertThat(body).doesNotContain("\"actor_tenant_id\"");
        assertThat(body).doesNotContain("\"actor_user_id\"");
        assertThat(body).doesNotContain("\"target_tenant_id\"");
        assertThat(body).doesNotContain("\"correlation_id\"");
    }

    // ------------------------------------------------------------------
    // UAT-F — PROVISIONING TYPED DTO CONTRACT (GET /provisioning/jobs)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-F (provisioning): REAL JWT → /provisioning/jobs returns typed camelCase rows; foreign tenantId is fail-closed denied (403); null members mapped")
    void uatF_provisioningJobs_typedCamelCaseContract() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-f");
        Instant now = Instant.now();

        // FK target: one control-tenant subscription + one FAILED provisioning job
        UUID subscriptionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                    seat_quantity, credit_balance_minor, started_at, current_period_start,
                    current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, ?, ?, ?, false, ?, ?)
                """, subscriptionId, admin.tenantId(), anySeededPlanId(),
                Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plus(30, ChronoUnit.DAYS)),
                Timestamp.from(now), Timestamp.from(now));
        createdSubscriptions.add(subscriptionId);

        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO provisioning_jobs (id, tenant_id, subscription_id, action, status,
                    attempts, started_at, completed_at, error_code, created_at, updated_at)
                VALUES (?, ?, ?, 'PROVISION_SUBSCRIPTION', 'FAILED', 2, ?, NULL,
                        'UAT_TEST_ERROR', ?, ?)
                """, jobId, admin.tenantId(), subscriptionId,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        createdProvisioningJobs.add(jobId);

        mockMvc.perform(
                        get("/api/v1/executive/provisioning/jobs")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-f@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                // typed camelCase contract — RED today: raw JDBC snake_case keys
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].tenantId").exists())
                .andExpect(jsonPath("$[0].subscriptionId").exists())
                .andExpect(jsonPath("$[0].startedAt").exists())
                .andExpect(jsonPath("$[0].errorCode").value("UAT_TEST_ERROR"))
                .andExpect(jsonPath("$[0].attempts").value(2))
                .andExpect(jsonPath("$[0].action").value("PROVISION_SUBSCRIPTION"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andReturn();

        // tenant scoping: a foreign tenantId filter is DENIED (403) by the
        // tenant-scoped guard — fail-closed deny, never a cross-tenant data listing
        mockMvc.perform(
                        get("/api/v1/executive/provisioning/jobs")
                                .param("tenantId", UUID.randomUUID().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-f@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isForbidden());

        // full-body key audit: snake_case must never leak; nullable column maps to explicit null member
        MvcResult result = mockMvc.perform(
                        get("/api/v1/executive/provisioning/jobs")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-f@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"tenant_id\"");
        assertThat(body).doesNotContain("\"subscription_id\"");
        assertThat(body).doesNotContain("\"started_at\"");
        assertThat(body).doesNotContain("\"completed_at\"");
        assertThat(body).doesNotContain("\"error_code\"");
        assertThat(body).doesNotContain("\"created_at\"");
        assertThat(body).contains("\"completedAt\":null");
    }

    // ------------------------------------------------------------------
    // UAT-D — BLOCKER D: ADMIN grant chain resolves all SCP capabilities
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-D: ADMIN-shaped role → access-check/v2 grants all read+manage capabilities (real chain)")
    void uatD_adminGrantChain_allCapabilitiesTrue() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-d");
        String token = realJwt(admin.tenantId(), admin.userId(), "uat-d@uat-acc.test");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/executive/access-check/v2")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        for (String code : READ_CAPABILITIES) {
            assertThat(body).as("ADMIN chain must grant %s", code).contains("\"" + code + "\":true");
        }
        for (String code : MANAGE_CAPABILITIES) {
            assertThat(body).as("ADMIN chain must grant %s", code).contains("\"" + code + "\":true");
        }
    }

    // ------------------------------------------------------------------
    // UAT-E — BLOCKER E: usage/audit 403 regression + negative + cross-tenant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-E1 (R7): REAL JWT + USAGE.READ → GET /usage = 200 (regression proof)")
    void uatE1_usageRead_realJwt_200() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-e1");

        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-e1@uat-acc.test")))
                // Regression proof: was 403 CAPABILITY_NOT_FOUND before V20260901_1.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("UAT-E2 (R8): REAL JWT + AUDIT.READ → GET /audit/v2 = 200 (regression proof)")
    void uatE2_auditRead_realJwt_200() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-e2");

        mockMvc.perform(
                        get("/api/v1/executive/audit/v2")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-e2@uat-acc.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("UAT-E3 (R9): authenticated user WITHOUT the capability → 403, fail closed")
    void uatE3_noCapability_403() throws Exception {
        UUID tenantId = UUID.fromString(CONTROL_TENANT_ID);
        seedControlTenant();
        UUID userId = seedUser(tenantId, "uat-e3-" + UUID.randomUUID() + "@uat-acc.test");
        UUID roleId = seedRole(tenantId, "SHOP_VIEWER");
        grantCapability(tenantId, roleId, "USER.READ"); // unrelated, non-SCP
        assignRole(tenantId, userId, roleId);

        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", tenantId.toString())
                                .header("Authorization", "Bearer " + realJwt(tenantId, userId, "uat-e3@uat-acc.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UAT-E4 (IDENTITY-5): foreign-tenant executive identity → fail-closed 403, no cross-tenant data")
    void uatE4_crossTenant_failClosed() throws Exception {
        seedControlTenant();
        UUID foreignTenant = seedTenant("fx-" + UUID.randomUUID().toString().substring(0, 8));
        UUID userId = seedUser(foreignTenant, "uat-e4-" + UUID.randomUUID() + "@uat-acc.test");
        UUID roleId = seedRole(foreignTenant, "ADMIN");
        grantCapability(foreignTenant, roleId, "EXECUTIVE_MANAGE");
        for (String code : READ_CAPABILITIES) grantCapability(foreignTenant, roleId, code);
        for (String code : MANAGE_CAPABILITIES) grantCapability(foreignTenant, roleId, code);
        assignRole(foreignTenant, userId, roleId);

        // Full admin in his OWN tenant — still denied the control plane (guard).
        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", foreignTenant.toString())
                                .header("Authorization", "Bearer " + realJwt(foreignTenant, userId, "uat-e4@uat-acc.test")))
                .andExpect(status().isForbidden());

        // Recalculate from a foreign tenant must be denied as well.
        mockMvc.perform(
                        post("/api/v1/executive/tenants/" + foreignTenant + "/entitlements/recalculate")
                                .header("Authorization", "Bearer " + realJwt(foreignTenant, userId, "uat-e4@uat-acc.test"))
                                .header("Content-Type", "application/json")
                                .content("{}"))
                .andExpect(status().isForbidden());
    }
}
