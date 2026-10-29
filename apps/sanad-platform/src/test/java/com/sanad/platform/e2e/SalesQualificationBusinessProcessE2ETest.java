package com.sanad.platform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REM-P1-007 executable foundation:
 * Lead -> Qualification -> Account/Contact -> Opportunity -> Won.
 *
 * This is intentionally a partial Order-to-Cash proof. Quotation, sales order,
 * inventory, delivery, invoice, ledger, collection and reconciliation remain
 * blocked in the governed process catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SalesQualificationBusinessProcessE2ETest {

    private static final UUID TENANT_A = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_B = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID NO_CAP_A = UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final UUID ROLE_A = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_B = UUID.fromString("73000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private org.springframework.transaction.support.TransactionTemplate seedTx;

    private String runSuffix;

    @BeforeEach
    void seedIdentityAndCapabilities() {
        if (seedTx == null) {
            seedTx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        }
        runSuffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        purgeTenantBusinessRows(TENANT_A);
        purgeTenantBusinessRows(TENANT_B);
        seedTenant(TENANT_A, "e2e-sales-a", "E2E Sales Tenant A", now);
        seedTenant(TENANT_B, "e2e-sales-b", "E2E Sales Tenant B", now);
        seedUser(ADMIN_A, TENANT_A, "sales-admin-a@example.test", now);
        seedUser(ADMIN_B, TENANT_B, "sales-admin-b@example.test", now);
        seedUser(NO_CAP_A, TENANT_A, "sales-no-cap@example.test", now);
        seedRole(ROLE_A, TENANT_A, now);
        seedRole(ROLE_B, TENANT_B, now);
        seedRoleAssignment(TENANT_A, ROLE_A, ADMIN_A, now);
        seedRoleAssignment(TENANT_B, ROLE_B, ADMIN_B, now);
        grantCrmCapabilities(TENANT_A, ROLE_A, now);
        grantCrmCapabilities(TENANT_B, ROLE_B, now);
    }


    /**
     * These fixed test tenants are owned by this suite. Rows now commit (no
     * test-managed rollback), so purge this tenant's business rows before
     * seeding to keep the run deterministic on a reused database.
     */
    private void purgeTenantBusinessRows(UUID tenantId) {
        seedTx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class,
                    tenantId.toString());
            // Child-first FK order; the tenant rows are recreated by seedIdentityAndCapabilities.
            for (String sql : new String[] {
                    "DELETE FROM crm_contact_relationship_history WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_account_status_history WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_account_merge_history WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_account_addresses WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_account_identifiers WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_account_relationships WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_communication_method_history WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_communication_methods WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_call_events WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_timeline_events WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_activities WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_leads WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_contact_account_relationships WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_opportunities WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_contacts WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_accounts WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_pipeline_stages WHERE tenant_id = ?::uuid",
                    "DELETE FROM crm_pipelines WHERE tenant_id = ?::uuid",
                    "DELETE FROM role_capabilities WHERE tenant_id = ?::uuid",
                    "DELETE FROM user_role_assignments WHERE tenant_id = ?::uuid",
                    "DELETE FROM roles WHERE tenant_id = ?::uuid",
                    "DELETE FROM users WHERE tenant_id = ?::uuid",
                    "DELETE FROM tenants WHERE id = ?::uuid"}) {
                jdbc.update(sql, tenantId.toString());
            }
        });
    }

    @Test
    void provesLeadToWonOpportunityWithGovernedCrossCuttingEvidence() throws Exception {
        JsonNode pipeline = perform(post("/api/v1/crm/pipelines")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"REM-P1-007 Enterprise Sales %s",
                         "currencyCode":"SAR",
                         "stages":["New","Qualified","Proposal","Won","Lost"]}
                        """.formatted(runSuffix)), 201);
        String pipelineId = pipeline.path("id").asText();

        JsonNode stages = perform(get("/api/v1/crm/pipelines/{pipelineId}/stages", pipelineId)
                .with(authentication(auth(TENANT_A, ADMIN_A))), 200);
        String firstStageId = stages.get(0).path("id").asText();
        String wonStageId = stages.get(3).path("id").asText();

        JsonNode lead = perform(post("/api/v1/crm/leads")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Maha Al-Salem %s","companyName":"Maha Digital %s",
                         "email":"maha%s@example.test","phone":"+966500000001",
                         "source":"REM-P1-007-E2E","score":90}
                        """.formatted(runSuffix, runSuffix, runSuffix)), 201);
        String leadId = lead.path("id").asText();

        JsonNode qualified = perform(patch("/api/v1/crm/leads/{id}/status", leadId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"QUALIFIED\"}"), 200);
        assertThat(qualified.path("status").asText()).isEqualTo("QUALIFIED");

        String conversionPayload = """
                {"accountName":"Maha Digital %s","createOpportunity":true,
                 "pipelineId":"%s","stageId":"%s",
                 "opportunityName":"Enterprise Platform Rollout %s",
                 "amount":250000,"currencyCode":"SAR"}
                """.formatted(runSuffix, pipelineId, firstStageId, runSuffix);

        JsonNode conversion = perform(post("/api/v1/crm/leads/{id}/convert", leadId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(conversionPayload), 200);

        assertThat(conversion.path("idempotent").asBoolean()).isFalse();
        assertThat(conversion.path("lead").path("status").asText()).isEqualTo("CONVERTED");
        String accountId = conversion.path("account").path("id").asText();
        String contactId = conversion.path("contact").path("id").asText();
        String opportunityId = conversion.path("opportunity").path("id").asText();
        assertThat(accountId).isNotBlank();
        assertThat(contactId).isNotBlank();
        assertThat(opportunityId).isNotBlank();

        JsonNode replay = perform(post("/api/v1/crm/leads/{id}/convert", leadId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(conversionPayload), 200);
        assertThat(replay.path("idempotent").asBoolean()).isTrue();
        assertThat(replay.path("accountId").asText()).isEqualTo(accountId);
        assertThat(replay.path("contactId").asText()).isEqualTo(contactId);
        assertThat(replay.path("opportunityId").asText()).isEqualTo(opportunityId);

        JsonNode won = perform(patch("/api/v1/crm/opportunities/{id}/stage", opportunityId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stageId":"%s","reason":"Signed enterprise agreement"}
                        """.formatted(wonStageId)), 200);
        assertThat(won.path("status").asText()).isEqualTo("WON");

        JsonNode activity = perform(post("/api/v1/crm/activities")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"activityType":"TASK","subject":"Prepare commercial handoff",
                         "relatedType":"ACCOUNT","relatedId":"%s","priority":90}
                        """.formatted(accountId)), 201);
        perform(patch("/api/v1/crm/activities/{id}/complete", activity.path("id").asText())
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"Commercial handoff completed\"}"), 200);

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/customer-360", accountId)
                        .with(authentication(auth(TENANT_A, ADMIN_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.opportunities.length()").value(1))
                .andExpect(jsonPath("$.activities.length()").value(1));

        JsonNode timeline = perform(get("/api/v1/crm/timeline/ACCOUNT/{id}", accountId)
                .with(authentication(auth(TENANT_A, ADMIN_A))), 200);
        assertThat(timeline.size()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/v1/crm/dashboard")
                        .with(authentication(auth(TENANT_A, ADMIN_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(1))
                .andExpect(jsonPath("$.contacts").value(1))
                .andExpect(jsonPath("$.openOpportunities").value(0));

        assertThat(count("SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=? AND resource_id=?",
                TENANT_A, accountId)).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=? AND resource_id=?",
                TENANT_A, opportunityId)).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=? AND subject_id=?",
                TENANT_A, UUID.fromString(accountId))).isGreaterThan(0);

        mockMvc.perform(get("/api/v1/crm/accounts/{id}", accountId)
                        .with(authentication(auth(TENANT_B, ADMIN_B))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/crm/accounts/{id}", accountId)
                        .with(authentication(auth(TENANT_A, NO_CAP_A))))
                .andExpect(status().isForbidden());

        JsonNode secondAccount = perform(post("/api/v1/crm/accounts")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Unrelated Account","accountType":"BUSINESS",
                         "primaryCurrencyCode":"SAR"}
                        """), 201);
        long opportunitiesBeforeRejectedMutation = count(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id=?", TENANT_A);

        mockMvc.perform(post("/api/v1/crm/opportunities")
                        .with(authentication(auth(TENANT_A, ADMIN_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","contactId":"%s","pipelineId":"%s",
                                 "stageId":"%s","name":"Invalid cross-account opportunity",
                                 "amount":1000,"currencyCode":"SAR"}
                                """.formatted(secondAccount.path("id").asText(), contactId,
                                pipelineId, firstStageId)))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id=?", TENANT_A))
                .isEqualTo(opportunitiesBeforeRejectedMutation);
    }

    private JsonNode perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expectedStatus
    ) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private Authentication auth(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private long count(String sql, Object... arguments) {
        // RLS-scoped tables (crm_timeline_events) fail-closed on autocommit
        // connections: without a transaction the app.tenant_id GUC is whatever
        // the pooled session last held (often ''), and ''::uuid raises inside
        // the policy. Run verification counts in a tenant-scoped transaction.
        Long value = seedTx.execute(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class,
                    TENANT_A.toString());
            return jdbc.queryForObject(sql, Long.class, arguments);
        });
        return value == null ? 0L : value;
    }

    private void seedTenant(UUID id, String subdomain, String name, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?) ON CONFLICT DO NOTHING",
                id, subdomain, name, ts, ts);
    }

    private void seedUser(UUID id, UUID tenantId, String email, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        // public.users is FORCE RLS (V20261011_3). Without a class-level
        // @Transactional boundary, jdbc calls run in autocommit mode where a
        // transaction-local set_config would be discarded immediately. Each
        // fixture user therefore seeds inside its own explicit transaction
        // whose GUC is set to the user's tenant (fail-closed policy satisfied,
        // no SecurityContextHolder bound, no RLS-handler guard state created).
        seedTx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE','dummy',?,?) ON CONFLICT DO NOTHING",
                    id, tenantId, email, "Business Process E2E User", ts, ts);
        });
    }

    private void seedRole(UUID id, UUID tenantId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        // roles is FORCE RLS (V20261029_1): bind the tenant GUC in the SAME
        // transaction as the identity-plane insert.
        seedTx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?,?,'ADMIN','Administrator','Business process E2E role','ACTIVE',?,?) ON CONFLICT DO NOTHING",
                    id, tenantId, ts, ts);
        });
    }

    private void seedRoleAssignment(UUID tenantId, UUID roleId, UUID userId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        // user_role_assignments is FORCE RLS (V20261029_1): same-transaction GUC.
        seedTx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) VALUES (?,?,?,?,NULL,'ACTIVE',?,?) ON CONFLICT DO NOTHING",
                    UUID.randomUUID(), tenantId, userId, roleId, ts, ts);
        });
    }

    private void grantCrmCapabilities(UUID tenantId, UUID roleId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        // role_capabilities is FORCE RLS (V20261029_1): same-transaction GUC.
        seedTx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            List<UUID> capabilityIds = jdbc.queryForList(
                    "SELECT id FROM access_capabilities WHERE code LIKE 'CRM.%'", UUID.class);
            for (UUID capabilityId : capabilityIds) {
                jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                        UUID.randomUUID(), tenantId, roleId, capabilityId, ts);
            }
        });
    }
}
