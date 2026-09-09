package com.sanad.platform.module.api;

import com.sanad.platform.test.PostgresAcceptanceTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R0C-12 Human-UAT regression acceptance on PostgreSQL Direct.
 *
 * <p>This class captures the production blockers observed during the first
 * operator trial with a real application JWT. It deliberately exercises the
 * HTTP boundary so authentication details travel through the same String UUID
 * representation produced by {@code JwtAuthenticationFilter}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pg-acceptance")
class ModuleRegistryUatPostgresAcceptanceTest extends PostgresAcceptanceTest {

    private static final UUID OPERATOR_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPERATOR_USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        seedTenant(OPERATOR_TENANT, "Operator", "operator", "ACTIVE");
        seedTenant(TARGET_TENANT, "Target", "target", "ACTIVE");
        seedTenant(OTHER_TENANT, "Other", "other", "ACTIVE");
        seedOperatorRole();
        seedUsageAndAudit();
    }

    @AfterEach
    void cleanup() {
        List<String> tables = List.of(
                "platform_audit_logs",
                "usage_aggregates",
                "usage_events",
                "role_capabilities",
                "user_role_assignments",
                "access_capabilities",
                "roles",
                "users",
                "tenants"
        );
        for (String table : tables) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (Exception ignored) {
                // The full migration chain can legitimately omit an optional
                // table in narrower profiles; cleanup must not mask the test.
            }
        }
    }

    @Test
    void uatA1_accessCheck_realJwt_resolvesCanonicalIdentityAndCapabilities() throws Exception {
        mvc.perform(get("/api/v1/executive/access-check/v2")
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.capabilities['subscription.read']").value(true))
                .andExpect(jsonPath("$.capabilities['usage.read']").value(true))
                .andExpect(jsonPath("$.capabilities['audit.read']").value(true));
    }

    @Test
    void uatA2_entitlementRecalculate_realJwt_doesNotClassCastAndWritesAudit() throws Exception {
        seedMinimalModuleRegistry(TARGET_TENANT);

        mvc.perform(post("/api/v1/executive/tenants/{tenantId}/entitlements/recalculate", TARGET_TENANT)
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TARGET_TENANT.toString()));

        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM platform_audit_logs WHERE actor_tenant_id=? AND actor_user_id=? "
                        + "AND target_tenant_id=?",
                Integer.class, OPERATOR_TENANT, OPERATOR_USER, TARGET_TENANT);
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    void uatA3_moduleReset_realJwt_no500() throws Exception {
        seedMinimalModuleRegistry(TARGET_TENANT);

        mvc.perform(post("/api/v1/executive/tenants/{tenantId}/modules/{moduleCode}/reset",
                        TARGET_TENANT, "CRM")
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET_FAILED"));
    }

    @Test
    void uatB_auditContract_isTypedCamelCaseAndRenderable() throws Exception {
        mvc.perform(get("/api/v1/executive/audit/v2")
                        .param("tenantId", TARGET_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resourceId").isString())
                .andExpect(jsonPath("$.content[0].resourceType").isString())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[0].actorTenantId").isString())
                .andExpect(jsonPath("$.content[0].actorUserId").isString())
                .andExpect(jsonPath("$.content[0].targetTenantId").isString())
                .andExpect(jsonPath("$.content[0].resource_id").doesNotExist())
                .andExpect(jsonPath("$.content[0].created_at").doesNotExist());
    }

    @Test
    void uatD_usageAuthorized_realJwt_returns200() throws Exception {
        mvc.perform(get("/api/v1/executive/usage")
                        .param("tenantId", TARGET_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk());
    }

    @Test
    void uatE1_missingUsageCapability_failsClosed403() throws Exception {
        jdbc.update("DELETE FROM role_capabilities WHERE capability_id = "
                + "(SELECT id FROM access_capabilities WHERE code='USAGE.READ')");

        mvc.perform(get("/api/v1/executive/usage")
                        .param("tenantId", TARGET_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uatE2_missingAuditCapability_failsClosed403() throws Exception {
        jdbc.update("DELETE FROM role_capabilities WHERE capability_id = "
                + "(SELECT id FROM access_capabilities WHERE code='AUDIT.READ')");

        mvc.perform(get("/api/v1/executive/audit/v2")
                        .param("tenantId", TARGET_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uatE3_foreignTenantAccess_failsClosed() throws Exception {
        mvc.perform(get("/api/v1/executive/usage")
                        .param("tenantId", OTHER_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uatF_provisioningContract_isCamelCaseNullSafeAndTenantScoped() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        jdbc.update("INSERT INTO saas_plans(id,code,name,status,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE','SAR',NOW(),NOW())",
                planId, "uat-f", "UAT F");
        jdbc.update("INSERT INTO plan_versions(id,plan_id,version_number,status,currency_code," 
                        + "monthly_price_minor,annual_price_minor,trial_days,max_users,max_organizations,storage_mb," 
                        + "created_at,updated_at) VALUES (?,?,1,'ACTIVE','SAR',0,0,0,1,1,1,NOW(),NOW())",
                planVersionId, planId);
        jdbc.update("INSERT INTO tenant_subscriptions(id,tenant_id,plan_id,plan_version_id,status,billing_cycle," 
                        + "seat_quantity,credit_balance_minor,started_at,current_period_start,current_period_end," 
                        + "cancel_at_period_end,created_at,updated_at) " 
                        + "VALUES (?,?,?,?,'ACTIVE','MONTHLY',1,0,NOW(),NOW(),NOW()+INTERVAL '30 days',false,NOW(),NOW())",
                subscriptionId, TARGET_TENANT, planId, planVersionId);
        jdbc.update("INSERT INTO provisioning_jobs(id,tenant_id,subscription_id,action,status,attempts," 
                        + "started_at,completed_at,error_code,created_at,updated_at) " 
                        + "VALUES (?,?,?,'PROVISION','RETRYING',1,NULL,NULL,NULL,NOW(),NOW())",
                jobId, TARGET_TENANT, subscriptionId);

        mvc.perform(get("/api/v1/executive/provisioning/jobs")
                        .param("tenantId", TARGET_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(TARGET_TENANT.toString()))
                .andExpect(jsonPath("$[0].subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$[0].startedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].completedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].errorCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].tenant_id").doesNotExist())
                .andExpect(jsonPath("$[0].started_at").doesNotExist());

        mvc.perform(get("/api/v1/executive/provisioning/jobs")
                        .param("tenantId", OTHER_TENANT.toString())
                        .header("Authorization", bearer(OPERATOR_TENANT, OPERATOR_USER)))
                .andExpect(status().isForbidden());
    }

    private void seedTenant(UUID id, String name, String subdomain, String status) {
        jdbc.update("INSERT INTO tenants(id,name,subdomain,status,country_code,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?, 'SA','SAR',NOW(),NOW())",
                id, name, subdomain, status);
    }

    private void seedOperatorRole() {
        jdbc.update("INSERT INTO users(id,email,display_name,status,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                OPERATOR_USER, "operator@uat.example", "Operator");
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles(id,tenant_id,code,name,status,created_at,updated_at) "
                        + "VALUES (?,?, 'ADMIN','Admin','ACTIVE',NOW(),NOW())",
                roleId, OPERATOR_TENANT);
        jdbc.update("INSERT INTO user_role_assignments(id,tenant_id,user_id,role_id,created_at) "
                        + "VALUES (?,?,?,?,NOW())",
                UUID.randomUUID(), OPERATOR_TENANT, OPERATOR_USER, roleId);

        for (String code : List.of(
                "EXECUTIVE_VIEW", "EXECUTIVE_MANAGE", "SUBSCRIPTION.READ",
                "ENTITLEMENT.READ", "ENTITLEMENT.MANAGE", "ENTITLEMENT.OVERRIDE",
                "USAGE.READ", "AUDIT.READ", "PROVISIONING.READ", "PROVISIONING.RETRY")) {
            UUID capabilityId = UUID.randomUUID();
            jdbc.update("INSERT INTO access_capabilities(id,code,name,status,created_at,updated_at) "
                            + "VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                    capabilityId, code, code);
            jdbc.update("INSERT INTO role_capabilities(id,tenant_id,role_id,capability_id,created_at) "
                            + "VALUES (?,?,?,?,NOW())",
                    UUID.randomUUID(), OPERATOR_TENANT, roleId, capabilityId);
        }
    }

    private void seedUsageAndAudit() {
        jdbc.update("INSERT INTO usage_aggregates(id,tenant_id,metric_code,period_start,period_end,current_value," 
                        + "created_at,updated_at) VALUES (?,?, 'users',CURRENT_DATE,CURRENT_DATE+1,5,NOW(),NOW())",
                UUID.randomUUID(), TARGET_TENANT);
        jdbc.update("INSERT INTO platform_audit_logs(id,actor_tenant_id,actor_user_id,target_tenant_id,action," 
                        + "resource_type,resource_id,reason,result,correlation_id,created_at) " 
                        + "VALUES (?,?,?,?, 'TEST','tenant',?,'uat','SUCCESS',?,NOW())",
                UUID.randomUUID(), OPERATOR_TENANT, OPERATOR_USER, TARGET_TENANT,
                TARGET_TENANT.toString(), UUID.randomUUID().toString());
    }

    private void seedMinimalModuleRegistry(UUID tenantId) {
        UUID moduleId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        jdbc.update("INSERT INTO modules(id,code,name,status,display_order,enabled,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE',1,true,NOW(),NOW())",
                moduleId, "CRM", "CRM");
        jdbc.update("INSERT INTO saas_plans(id,code,name,status,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE','SAR',NOW(),NOW())",
                planId, "uat-plan", "UAT Plan");
        jdbc.update("INSERT INTO plan_versions(id,plan_id,version_number,status,currency_code," 
                        + "monthly_price_minor,annual_price_minor,trial_days,max_users,max_organizations,storage_mb," 
                        + "created_at,updated_at) VALUES (?,?,1,'ACTIVE','SAR',0,0,0,1,1,1,NOW(),NOW())",
                planVersionId, planId);
        jdbc.update("INSERT INTO tenant_subscriptions(id,tenant_id,plan_id,plan_version_id,status,billing_cycle," 
                        + "seat_quantity,credit_balance_minor,started_at,current_period_start,current_period_end," 
                        + "cancel_at_period_end,created_at,updated_at) " 
                        + "VALUES (?,?,?,?,'ACTIVE','MONTHLY',1,0,NOW(),NOW(),NOW()+INTERVAL '30 days',false,NOW(),NOW())",
                subscriptionId, tenantId, planId, planVersionId);
        jdbc.update("INSERT INTO plan_module_entitlements(id,plan_id,module_id,module_enabled,capability_code," 
                        + "capability_value,effective_at,created_at,updated_at) " 
                        + "VALUES (?,?,?,?, 'crm.read','true',NOW(),NOW(),NOW())",
                UUID.randomUUID(), planId, moduleId, true);
    }

    private String bearer(UUID tenantId, UUID userId) {
        return "Bearer " + testJwt(tenantId, userId);
    }

    private String testJwt(UUID tenantId, UUID userId) {
        Map<String, Object> claims = Map.of(
                "sub", userId.toString(),
                "tenant_id", tenantId.toString(),
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );
        return jwtForClaims(claims);
    }
}
