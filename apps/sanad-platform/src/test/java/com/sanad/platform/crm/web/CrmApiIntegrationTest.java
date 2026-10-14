package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
class CrmApiIntegrationTest {
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID ROLE_A = UUID.randomUUID();
    private static final UUID ROLE_B = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    // No test-managed transaction may span requests made as different tenants.
    // Each committed fixture is scoped to one tenant and one database transaction.
    @BeforeEach
    void seedIdentityAndCapabilities() {
        seedIdentity(TENANT_A, USER_A, ROLE_A, "crm-a", "CRM Tenant A", "crm-a@example.test");
        seedIdentity(TENANT_B, USER_B, ROLE_B, "crm-b", "CRM Tenant B", "crm-b@example.test");
    }

    private void inTenantTransaction(UUID tenantId, Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            action.run();
        });
    }

    private void seedIdentity(UUID tenantId, UUID userId, UUID roleId,
                              String subdomainPrefix, String name, String email) {
        inTenantTransaction(tenantId, () -> {
            Instant now = Instant.now();
            seedTenant(tenantId, subdomainPrefix + "-" + tenantId, name, now);
            seedUser(userId, tenantId, email.replace("@", "+" + userId + "@"), now);
            seedRole(roleId, tenantId, now);
            seedRoleAssignment(tenantId, roleId, userId, now);
            grantCrmCapabilities(tenantId, roleId, now);
        });
    }

    // Scoped FK-safe cleanup; never TRUNCATE, CASCADE, or delete another tenant's data.
    @AfterEach
    void cleanupIdentityAndCrmData() {
        for (UUID tenantId : List.of(TENANT_A, TENANT_B)) {
            inTenantTransaction(tenantId, () -> {
                for (String table : CRM_CHILD_TABLES) {
                    jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantId);
                }
                jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM roles WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    private static final List<String> CRM_CHILD_TABLES = List.of(
            "crm_contact_relationship_history", "crm_contact_account_relationships",
            "crm_contact_relationship_roles", "crm_contact_lookup_index",
            "crm_contact_ownership_history", "crm_phone_numbers",
            "crm_communication_methods", "crm_party_address_history", "crm_party_addresses",
            "crm_opportunity_stage_history", "crm_leads", "crm_opportunities",
            "crm_pipeline_stages", "crm_pipelines", "crm_tasks", "crm_notes",
            "crm_tag_assignments", "crm_tags", "crm_activities",
            "crm_timeline_events", "crm_audit_logs", "crm_event_outbox",
            "crm_custom_field_values", "crm_contacts", "crm_accounts");

    @Test
    void executesCompleteCrmLifecycleAgainstApplicationTables() throws Exception {
        JsonNode account = perform(post("/api/v1/crm/accounts")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Acme Arabia","accountType":"BUSINESS",
                         "primaryCurrencyCode":"SAR","preferredLocale":"ar-SA",
                         "timeZone":"Asia/Riyadh","source":"INTEGRATION_TEST"}
                        """), 201);
        String accountId = account.path("id").asText();

        JsonNode contact = perform(post("/api/v1/crm/contacts")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId":"%s","givenName":"Maha","familyName":"Saleh",
                         "preferredLocale":"ar-SA","timeZone":"Asia/Riyadh",
                         "consentSummary":"UNKNOWN"}
                        """.formatted(accountId)), 201);
        String contactId = contact.path("id").asText();

        JsonNode pipeline = perform(post("/api/v1/crm/pipelines")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Enterprise Sales","currencyCode":"SAR",
                         "stages":["New","Qualified","Proposal","Won","Lost"]}
                        """), 201);
        String pipelineId = pipeline.path("id").asText();
        JsonNode stages = perform(get("/api/v1/crm/pipelines/{pipelineId}/stages", pipelineId)
                .with(authentication(auth(TENANT_A, USER_A))), 200);
        String firstStage = stages.get(0).path("id").asText();
        String wonStage = stages.get(3).path("id").asText();

        JsonNode opportunity = perform(post("/api/v1/crm/opportunities")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId":"%s","contactId":"%s","pipelineId":"%s",
                         "stageId":"%s","name":"ERP Rollout","amount":250000,
                         "currencyCode":"SAR"}
                        """.formatted(accountId, contactId, pipelineId, firstStage)), 201);
        String opportunityId = opportunity.path("id").asText();
        assertThat(opportunity.path("probability").decimalValue()).isEqualByComparingTo("0");

        JsonNode wonOpportunity = perform(patch("/api/v1/crm/opportunities/{id}/stage", opportunityId)
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stageId":"%s","reason":"Contract signed"}
                        """.formatted(wonStage)), 200);
        assertThat(wonOpportunity.path("status").asText()).isEqualTo("WON");

        JsonNode activity = perform(post("/api/v1/crm/activities")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"activityType":"TASK","subject":"Prepare kickoff",
                         "relatedType":"ACCOUNT","relatedId":"%s","priority":80}
                        """.formatted(accountId)), 201);
        perform(patch("/api/v1/crm/activities/{id}/complete", activity.path("id").asText())
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"result":"Done"}
                        """), 200);

        JsonNode lead = perform(post("/api/v1/crm/leads")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Noura Alharbi","companyName":"Noura Labs","source":"WEB"}
                        """), 201);
        perform(patch("/api/v1/crm/leads/{id}/status", lead.path("id").asText())
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"QUALIFIED"}
                        """), 200);
        JsonNode conversion = perform(post("/api/v1/crm/leads/{id}/convert", lead.path("id").asText())
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"createOpportunity":false,"currencyCode":"SAR"}
                        """), 200);
        assertThat(conversion.path("opportunity").isNull()).isTrue();
        assertThat(conversion.path("idempotent").asBoolean()).isFalse();

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/customer-360", accountId)
                        .with(authentication(auth(TENANT_A, USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.opportunities.length()").value(1))
                .andExpect(jsonPath("$.activities.length()").value(1));

        mockMvc.perform(get("/api/v1/crm/dashboard")
                        .with(authentication(auth(TENANT_A, USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(2))
                .andExpect(jsonPath("$.contacts").value(2))
                .andExpect(jsonPath("$.openOpportunities").value(0));

        inTenantTransaction(TENANT_A, () -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=?", Long.class, TENANT_A)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=?", Long.class, TENANT_A)).isGreaterThanOrEqualTo(8);
        });
    }

    @Test
    void tenantCannotReadAnotherTenantCrmRecord() throws Exception {
        JsonNode account = perform(post("/api/v1/crm/accounts")
                .with(authentication(auth(TENANT_A, USER_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Private A","accountType":"BUSINESS","primaryCurrencyCode":"SAR"}
                        """), 201);

        mockMvc.perform(get("/api/v1/crm/accounts/{id}", account.path("id").asText())
                        .with(authentication(auth(TENANT_B, USER_B))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/crm/accounts")
                        .with(authentication(auth(TENANT_B, USER_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private JsonNode perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mockMvc.perform(request).andReturn();
        int actual = result.getResponse().getStatus();
        if (actual != expected) {
            throw new AssertionError("CRM API expected HTTP " + expected + " but received " + actual
                    + "; response=" + result.getResponse().getContentAsString()
                    + "; resolvedException=" + result.getResolvedException());
        }
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private Authentication auth(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return authentication;
    }

    private void seedTenant(UUID id, String subdomain, String name, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?)", id, subdomain, name, ts, ts);
    }

    private void seedUser(UUID id, UUID tenantId, String email, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)", id, tenantId, email, "CRM Test User", ts, ts);
    }

    private void seedRole(UUID id, UUID tenantId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?,?,'ADMIN','Administrator','CRM integration role','ACTIVE',?,?)", id, tenantId, ts, ts);
    }

    private void seedRoleAssignment(UUID tenantId, UUID roleId, UUID userId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) VALUES (?,?,?,?,NULL,'ACTIVE',?,?)", UUID.randomUUID(), tenantId, userId, roleId, ts, ts);
    }

    private void grantCrmCapabilities(UUID tenantId, UUID roleId, Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        List<UUID> capabilityIds = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'CRM.%'", UUID.class);
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?,?,?,?,?)", UUID.randomUUID(), tenantId, roleId, capabilityId, ts);
        }
    }
}
