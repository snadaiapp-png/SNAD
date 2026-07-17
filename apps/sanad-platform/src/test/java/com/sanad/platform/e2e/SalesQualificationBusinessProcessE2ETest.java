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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
 * Executable REM-P1-007 foundation slice.
 *
 * Proves a real business process through public HTTP contracts and application
 * persistence rather than mocks:
 *
 * Lead -> Qualification -> Account/Contact -> Opportunity -> Won
 *
 * The test also proves tenant isolation, RBAC denial, idempotent conversion,
 * audit/timeline evidence, analytical consistency and rollback on a rejected
 * cross-account relationship. It deliberately does not claim Order-to-Cash
 * closure because quotation, order, inventory, delivery, invoice, ledger and
 * collection are outside the currently executable slice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
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

    @BeforeEach
    void seedIdentityAndCapabilities() {
        Instant now = Instant.now();
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

    @Test
    void provesLeadToWonOpportunityWithGovernedCrossCuttingEvidence() throws Exception {
        JsonNode pipeline = perform(post("/api/v1/crm/pipelines")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"REM-P1-007 Enterprise Sales","currencyCode":"SAR",
                         "stages":["New","Qualified","Proposal","Won","Lost"]}
                        """), 201);
        String pipelineId = pipeline.path("id").asText();

        JsonNode stages = perform(get("/api/v1/crm/pipelines/{pipelineId}/stages", pipelineId)
                .with(authentication(auth(TENANT_A, ADMIN_A))), 200);
        String firstStageId = stages.get(0).path("id").asText();
        String wonStageId = stages.get(3).path("id").asText();

        JsonNode lead = perform(post("/api/v1/crm/leads")
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Maha Al-Salem","companyName":"Maha Digital",
                         "email":"maha@example.test","phone":"+966500000001",
                         "source":"REM-P1-007-E2E","score":90}
                        """), 201);
        String leadId = lead.path("id").asText();

        JsonNode qualified = perform(patch("/api/v1/crm/leads/{id}/status", leadId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"QUALIFIED"}"""), 200);
        assertThat(qualified.path("status").asText()).isEqualTo("QUALIFIED");

        JsonNode conversion = perform(post("/api/v1/crm/leads/{id}/convert", leadId)
                .with(authentication(auth(TENANT_A, ADMIN_A)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountName":"Maha Digital","createOpportunity":true,
                         "pipelineId":"%s","stageId":"%s",
                         "opportunityName":"Enterprise Platform Rollout",
                         "amount":250000,"currencyCode":"SAR"}
                        """.formatted(pipelineId, firstStageId)), 200);

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
                .content("""
                        {"accountName":"Maha Digital","createOpportunity":true,
                         "pipelineId":"%s","stageId":"%s",
                         "opportunityName":"Enterprise Platform Rollout",
                         "amount":250000,"currencyCode":"SAR"}
                        """.formatted(pipelineId, firstStageId)), 200);
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
                .content("""{"result":"Commercial handoff completed"}"""), 200);

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
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }

    private void seedTenant(UUID id, String subdomain, String name, Instant now) {
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?)",
                id, subdomain, name, now, now);
    }

    private void seedUser(UUID id, UUID tenantId, String email, Instant now) {
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
                id, tenantId, email, "Business Process E2E User", now, now);
    }

    private void seedRole(UUID id, UUID tenantId, Instant now) {
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?,?,'ADMIN','Administrator','Business process E2E role','ACTIVE',?,?)",
                id, tenantId, now, now);
    }

    private void seedRoleAssignment(UUID tenantId, UUID roleId, UUID userId, Instant now) {
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) VALUES (?,?,?,?,NULL,'ACTIVE',?,?)",
                UUID.randomUUID(), tenantId, userId, roleId, now, now);
    }

    private void grantCrmCapabilities(UUID tenantId, UUID roleId, Instant now) {
        List<UUID> capabilityIds = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'CRM.%'", UUID.class);
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), tenantId, roleId, capabilityId, now);
        }
    }
}
