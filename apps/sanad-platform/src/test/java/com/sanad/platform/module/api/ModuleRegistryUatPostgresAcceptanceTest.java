package com.sanad.platform.module.api;

import com.sanad.platform.commerce.PgAcceptanceWiringConfig;
import com.sanad.platform.crm.test.RlsTestSupport;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>PostgreSQL Direct acceptance (host-native PostgreSQL, NO Docker, NO
 * Testcontainers, NO H2). Proves on the REAL HTTP path with REAL
 * application-issued JWTs (the production {@code JwtAuthenticationFilter}
 * stores {@code tenant_id}/{@code user_id} as {@code String}):</p>
 *
 * <ul>
 *   <li>UAT-A1: REAL JWT resolves canonical identity + capabilities over
 *       {@code /access-check/v2} (control-plane admin grant chain)</li>
 *   <li>UAT-A1b: PUT {@code /plans/{planId}/modules/{code}} must NOT 500 (ClassCastException)</li>
 *   <li>UAT-A2: POST {@code /tenants/{id}/entitlements/recalculate} must return 200 and write
 *       an audit row carrying the REAL JWT actor tenant/user (the reported UAT blocker)</li>
 *   <li>UAT-A3: POST {@code /tenants/{id}/modules/{code}/reset} must NOT 500</li>
 *   <li>UAT-B : GET {@code /audit/v2} must return a camelCase typed contract
 *       (resourceId/resourceType/createdAt/actorTenantId/actorUserId/targetTenantId),
 *       never raw JDBC snake_case keys</li>
 *   <li>UAT-D : usage authorized path returns 200</li>
 *   <li>UAT-E : usage.read / audit.read regressions stay fixed (200); negative
 *       (missing capability → 403) and foreign-tenant (fail-closed → 403)</li>
 *   <li>UAT-F : {@code /provisioning/jobs} returns typed camelCase null-safe rows,
 *       tenant-scoped; foreign tenantId is fail-closed denied (403)</li>
 * </ul>
 *
 * <p>Harness provenance (R0C12 final draft-gate): this class is the certified
 * Z-handoff UAT acceptance test (Restore7 manifest entry) ported to the CURRENT
 * repository test conventions — {@code pg-acceptance} profile +
 * {@link PgAcceptanceWiringConfig} wiring + {@link JwtTokenProvider#mintAccessToken}
 * instead of the obsolete {@code PostgresAcceptanceTest} base and
 * {@code jwtForClaims} helper. Every behavioral assertion of the certified
 * handoff is preserved; no assertion was weakened, skipped, or removed.</p>
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

    /** Foreign tenant used for fail-closed cross-tenant proofs (never authorized). */
    private static final UUID OTHER_TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

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
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private final List<UUID> createdTenants = new ArrayList<>();
    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdRoles = new ArrayList<>();
    private final List<UUID> createdModules = new ArrayList<>();
    private final List<UUID> createdAuditRows = new ArrayList<>();
    private final List<UUID> createdUsageRows = new ArrayList<>();
    private final List<UUID> createdPlans = new ArrayList<>();
    private final List<UUID> createdSubscriptionIds = new ArrayList<>();
    private final List<UUID> createdProvisioningJobs = new ArrayList<>();

    // ------------------------------------------------------------------
    // Fixtures (current-repository conventions, run-scoped cleanup)
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

    /** Idempotently (re)creates a fixed-UUID tenant used by fail-closed proofs. */
    private void seedFixedTenant(UUID id, String name, String subdomain) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, name, subdomain, Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
    }

    private void seedControlTenant() {
        seedFixedTenant(UUID.fromString(CONTROL_TENANT_ID),
                "UAT-ACC Control Plane", "uat-acc-control-plane");
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

    /** Grants a capability through the REAL migration-seeded access_capabilities catalog. */
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
        // Defensive: remove any stale row from an interrupted previous run.
        jdbc.update("DELETE FROM plan_module_entitlements WHERE module_id IN (SELECT id FROM modules WHERE code = ?)", code);
        jdbc.update("DELETE FROM module_capabilities WHERE module_id IN (SELECT id FROM modules WHERE code = ?)", code);
        jdbc.update("DELETE FROM modules WHERE code = ?", code);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO modules (id, code, name, status, display_order, enabled, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 0, true, ?, ?)
                """, id, code, "UAT module " + code, Timestamp.from(now), Timestamp.from(now));
        createdModules.add(id);
        return id;
    }

    /** Seeds a dedicated plan + version; returns the plan id. */
    private UUID seedPlanWithVersion() {
        jdbc.update("DELETE FROM tenant_subscriptions WHERE plan_id IN (SELECT id FROM saas_plans WHERE code = ?)", "uat-acc-plan");
        jdbc.update("DELETE FROM plan_module_entitlements WHERE plan_id IN (SELECT id FROM saas_plans WHERE code = ?)", "uat-acc-plan");
        jdbc.update("DELETE FROM plan_versions WHERE plan_id IN (SELECT id FROM saas_plans WHERE code = ?)", "uat-acc-plan");
        jdbc.update("DELETE FROM saas_plans WHERE code = ?", "uat-acc-plan");
        UUID planId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code,
                    monthly_price_minor, annual_price_minor, trial_days, max_users,
                    max_organizations, storage_mb, created_at, updated_at)
                VALUES (?, 'uat-acc-plan', 'UAT Acceptance Plan', 'ACTIVE', 'SAR',
                    0, 0, 0, 1, 1, 1, ?, ?)
                """, planId, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO plan_versions (id, plan_id, version_number, status, currency_code,
                    monthly_price_minor, annual_price_minor, trial_days, max_users, max_organizations,
                    storage_mb, created_at, updated_at)
                VALUES (?, ?, 1, 'ACTIVE', 'SAR', 0, 0, 0, 1, 1, 1, ?, ?)
                """, planVersionId, planId, Timestamp.from(now), Timestamp.from(now));
        createdPlans.add(planId);
        return planId;
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

    private void seedUsageRow(UUID tenantId) {
        UUID id = UUID.randomUUID();
        // usage_aggregates is a FORCE-RLS table — the INSERT must run inside a
        // transaction-local app.tenant_id GUC scope (RlsTestSupport pattern).
        transactions.executeWithoutResult(status -> {
            namedJdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource("t", tenantId.toString()),
                    String.class);
            namedJdbc.update("""
                    INSERT INTO usage_aggregates (id, tenant_id, metric_code, period_type, period_start,
                                                  total, updated_at)
                    VALUES (:id, :t, 'users', 'MONTHLY', date_trunc('month', NOW()), 5, NOW())
                    """, new MapSqlParameterSource(Map.of("id", id, "t", tenantId)));
        });
        createdUsageRows.add(id);
    }

    /** Seeds a subscription + a RETRYING provisioning job with NULL started/completed/error members. */
    private UUID seedProvisioningJob(UUID tenantId, UUID planId) {
        UUID subscriptionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                    billing_cycle, seat_quantity, credit_balance_minor, started_at,
                    current_period_start, current_period_end, cancel_at_period_end, created_at, updated_at)
                SELECT ?, ?, ?, pv.id, 'ACTIVE', 'MONTHLY', 1, 0, NOW(), NOW(), NOW() + INTERVAL '30 days',
                       false, NOW(), NOW()
                FROM plan_versions pv WHERE pv.plan_id = ?
                """, subscriptionId, tenantId, planId, planId);
        jdbc.update("""
                INSERT INTO provisioning_jobs (id, tenant_id, subscription_id, action, status, attempts,
                    started_at, completed_at, error_code, created_at, updated_at)
                VALUES (?, ?, ?, 'PROVISION_SUBSCRIPTION', 'RETRYING', 1, NULL, NULL, NULL, NOW(), NOW())
                """, jobId, tenantId, subscriptionId);
        createdSubscriptionIds.add(subscriptionId);
        createdProvisioningJobs.add(jobId);
        return jobId;
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdProvisioningJobs) {
            jdbc.update("DELETE FROM provisioning_jobs WHERE id = ?", id);
        }
        for (UUID id : createdSubscriptionIds) {
            jdbc.update("DELETE FROM provisioning_jobs WHERE subscription_id = ?", id);
            jdbc.update("DELETE FROM tenant_subscriptions WHERE id = ?", id);
        }
        // createdUsageRows are cleaned by the tenant-scoped FORCE-RLS pass below.
        for (UUID id : createdAuditRows) {
            jdbc.update("DELETE FROM platform_audit_logs WHERE id = ?", id);
        }
        for (UUID planId : createdPlans) {
            jdbc.update("DELETE FROM tenant_subscriptions WHERE plan_id = ?", planId);
            jdbc.update("DELETE FROM plan_module_entitlements WHERE plan_id = ?", planId);
            jdbc.update("DELETE FROM plan_versions WHERE plan_id = ?", planId);
            jdbc.update("DELETE FROM saas_plans WHERE id = ?", planId);
        }
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
            jdbc.update("DELETE FROM provisioning_jobs WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", tenantId);
            // usage_aggregates is FORCE-RLS — tenant-scoped GUC transaction required.
            RlsTestSupport.deleteTenantRows(namedJdbc, transactions, tenantId,
                    List.of("usage_aggregates"));
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        createdProvisioningJobs.clear();
        createdSubscriptionIds.clear();
        createdUsageRows.clear();
        createdAuditRows.clear();
        createdPlans.clear();
        createdModules.clear();
        createdUsers.clear();
        createdRoles.clear();
        createdTenants.clear();
    }

    // ------------------------------------------------------------------
    // UAT-A — BLOCKER A: real-JWT identity extraction (ClassCastException → 500)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-A1: REAL JWT resolves canonical identity + capabilities (control-plane grant chain)")
    void uatA1_accessCheck_realJwt_resolvesCanonicalIdentityAndCapabilities() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a1-id");

        mockMvc.perform(
                        get("/api/v1/executive/access-check/v2")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a1-id@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.capabilities").isMap());

        MvcResult result = mockMvc.perform(
                        get("/api/v1/executive/access-check/v2")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a1-id@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"authenticated\":true");
        for (String code : READ_CAPABILITIES) {
            assertThat(body)
                    .as("control-plane admin must resolve read capability %s", code)
                    .contains("\"" + code + "\":true");
        }
        for (String code : MANAGE_CAPABILITIES) {
            assertThat(body)
                    .as("control-plane admin must resolve manage capability %s", code)
                    .contains("\"" + code + "\":true");
        }
        assertThat(body).contains("\"EXECUTIVE_VIEW\":true");
        assertThat(body).contains("\"EXECUTIVE_MANAGE\":true");
    }

    @Test
    @DisplayName("UAT-A1b: REAL JWT + ADMIN → PUT plan-module entitlement = 200 (no ClassCastException)")
    void uatA1b_putPlanModule_realJwt_200() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a1b");
        UUID planId = seedPlanWithVersion();
        UUID moduleId = seedModule("UATCRM");

        mockMvc.perform(
                        put("/api/v1/executive/plans/" + planId + "/modules/UATCRM")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a1b@uat-acc.test"))
                                .header("Content-Type", "application/json")
                                .content("""
                                        {"moduleId":"%s","moduleEnabled":true,"capabilityCode":"UATCRM.MAX_ROWS",
                                         "capabilityValue":null,"limitValue":100,"quotaValue":null,"quotaPeriod":"MONTHLY"}
                                        """.formatted(moduleId)))
                .andExpect(status().isOk()) // REGRESSION GUARD: 500 ClassCastException before R0C-12 corrective
                .andExpect(jsonPath("$.moduleEnabled").value(true));
    }

    @Test
    @DisplayName("UAT-A2 (R1/R10): REAL JWT + ADMIN → entitlements/recalculate = 200, audit row with correct actor")
    void uatA2_recalculate_realJwt_200_andAuditActor() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a2");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/executive/tenants/" + admin.tenantId() + "/entitlements/recalculate")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a2@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk()) // REGRESSION GUARD: 500 ClassCastException before R0C-12 corrective
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
    @DisplayName("UAT-A3: REAL JWT + ADMIN → module reset endpoint must not 500 on identity extraction")
    void uatA3_moduleReset_realJwt_no500() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-a3");
        seedModule("UATX"); // not in ModuleResetRegistry → business FAILURE, HTTP 200

        mockMvc.perform(
                        post("/api/v1/executive/tenants/" + admin.tenantId() + "/modules/UATX/reset")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-a3@uat-acc.test"))
                                .header("Content-Type", "application/json")
                                .content("{}"))
                .andExpect(status().isOk()) // REGRESSION GUARD: 500 ClassCastException before any business logic
                // unsupported module: pre-existing domain contract is an explicit business-failure
                // status (ModuleResetResult.STATUS_FAILED = "RESET_FAILED") with errorMessage, HTTP 200
                .andExpect(jsonPath("$.status").value(ModuleResetResult.STATUS_FAILED));
    }

    // ------------------------------------------------------------------
    // UAT-B — BLOCKER B: /audit/v2 JSON contract (camelCase, typed)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-B (R2): REAL JWT → /audit/v2 exposes camelCase contract keys, never snake_case")
    void uatB_auditContract_isTypedCamelCaseAndRenderable() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-b");
        seedAuditRow(admin.tenantId(), "SUBSCRIPTION_ACTIVATED");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/executive/audit/v2")
                                .param("tenantId", admin.tenantId().toString())
                                .param("sort", "created_at")
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-b@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                // camelCase typed contract — REGRESSION GUARD: raw JDBC snake_case keys
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
    // UAT-D — usage authorized path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-D: REAL JWT + usage.read → GET /usage returns 200")
    void uatD_usageAuthorized_realJwt_returns200() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-d");
        seedUsageRow(admin.tenantId());

        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-d@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // UAT-E — fail-closed regressions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-E1: missing USAGE.READ fails closed with 403")
    void uatE1_missingUsageCapability_failsClosed403() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-e1");

        jdbc.update("""
                DELETE FROM role_capabilities
                WHERE role_id = ? AND capability_id = (SELECT id FROM access_capabilities WHERE code = 'USAGE.READ')
                """, admin.roleId());

        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-e1@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UAT-E2: missing AUDIT.READ fails closed with 403")
    void uatE2_missingAuditCapability_failsClosed403() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-e2");

        jdbc.update("""
                DELETE FROM role_capabilities
                WHERE role_id = ? AND capability_id = (SELECT id FROM access_capabilities WHERE code = 'AUDIT.READ')
                """, admin.roleId());

        mockMvc.perform(
                        get("/api/v1/executive/audit/v2")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-e2@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UAT-E3: foreign-tenant access fails closed (tenant binding violation → 403)")
    void uatE3_foreignTenantAccess_failsClosed() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-e3");
        seedFixedTenant(OTHER_TENANT_ID, "UAT-ACC Foreign", "uat-acc-foreign");

        mockMvc.perform(
                        get("/api/v1/executive/usage")
                                .param("tenantId", OTHER_TENANT_ID.toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-e3@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // UAT-F — PROVISIONING TYPED DTO CONTRACT (GET /provisioning/jobs)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UAT-F: REAL JWT → /provisioning/jobs returns typed camelCase null-safe rows, tenant-scoped; foreign tenantId denied 403")
    void uatF_provisioningContract_isCamelCaseNullSafeAndTenantScoped() throws Exception {
        AdminIdentity admin = provisionControlPlaneAdmin("uat-f");
        UUID planId = seedPlanWithVersion();
        UUID jobId = seedProvisioningJob(admin.tenantId(), planId);

        mockMvc.perform(
                        get("/api/v1/executive/provisioning/jobs")
                                .param("tenantId", admin.tenantId().toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-f@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(admin.tenantId().toString()))
                .andExpect(jsonPath("$[0].subscriptionId").exists())
                .andExpect(jsonPath("$[0].startedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].completedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].errorCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].tenant_id").doesNotExist())
                .andExpect(jsonPath("$[0].started_at").doesNotExist());

        // Foreign tenantId parameter conflicts with the JWT tenant → fail-closed 403.
        seedFixedTenant(OTHER_TENANT_ID, "UAT-ACC Foreign", "uat-acc-foreign");
        mockMvc.perform(
                        get("/api/v1/executive/provisioning/jobs")
                                .param("tenantId", OTHER_TENANT_ID.toString())
                                .header("Authorization", "Bearer " + realJwt(admin.tenantId(), admin.userId(), "uat-f@uat-acc.test"))
                                .header("Accept", "application/json"))
                .andExpect(status().isForbidden());
    }
}
