package com.sanad.platform.crm.web;

import com.sanad.platform.crm.legacy.infrastructure.LegacyCrmInfrastructureService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
class CrmImportAndCustomFieldIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyCrmInfrastructureService extended;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void seed() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        seedIdentity(tenantA, userA, "a");
        seedIdentity(tenantB, userB, "b");
    }

    @AfterEach
    void cleanup() {
        if (tenantA == null || tenantB == null) return;
        Object[] tenants = {tenantA, tenantB};
        for (String table : List.of(
                "crm_import_errors", "crm_import_files", "crm_custom_field_values",
                "crm_timeline_events", "crm_activities", "crm_opportunity_stage_history",
                "crm_leads", "crm_opportunities", "crm_contacts", "crm_pipeline_stages",
                "crm_pipelines", "crm_accounts", "crm_import_jobs",
                "crm_custom_field_definitions")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id IN (?,?)", tenants);
        }
        jdbc.update("DELETE FROM role_capabilities WHERE tenant_id IN (?,?)", tenants);
        jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id IN (?,?)", tenants);
        jdbc.update("DELETE FROM roles WHERE tenant_id IN (?,?)", tenants);
        jdbc.update("DELETE FROM users WHERE tenant_id IN (?,?)", tenants);
        jdbc.update("DELETE FROM tenants WHERE id IN (?,?)", tenants);
    }

    @Test
    void importsCsvAndEnforcesTypedEncryptedTenantScopedCustomFields() throws Exception {
        createField("industryCode", false, true, true);
        createField("taxNumber", true, false, false);

        JsonNode account = json(post("/api/v1/crm/accounts")
                .with(authentication(auth(tenantA, userA)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Manual Account","accountType":"BUSINESS",
                         "primaryCurrencyCode":"SAR"}
                        """), 201);
        String accountId = account.path("id").asText();

        mockMvc.perform(put("/api/v1/crm/custom-fields/values/ACCOUNT/{id}", accountId)
                        .with(authentication(auth(tenantA, userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"values":{"industryCode":"health care","taxNumber":"310000000000003"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.industryCode").value("health care"))
                .andExpect(jsonPath("$.taxNumber").value("310000000000003"));

        mockMvc.perform(get("/api/v1/crm/custom-fields/values/ACCOUNT/{id}", accountId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxNumber").value("[REDACTED]"));
        mockMvc.perform(get("/api/v1/crm/custom-fields/values/ACCOUNT/{id}/sensitive", accountId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxNumber").value("310000000000003"));
        mockMvc.perform(get("/api/v1/crm/custom-fields/search")
                        .with(authentication(auth(tenantA, userA)))
                        .queryParam("entityType", "ACCOUNT")
                        .queryParam("fieldKey", "industryCode")
                        .queryParam("query", "HEALTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity_id").value(accountId));

        String csv = "Name,Type,Industry\r\nImported One,BUSINESS,technology\r\nImported Invalid,INVALID,finance\r\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "accounts.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> job = extended.uploadImport(
                auth(tenantA, userA), "ACCOUNT",
                "{\"Name\":\"displayName\",\"Type\":\"accountType\",\"Industry\":\"custom.industryCode\"}",
                file);
        UUID jobId = (UUID) job.get("id");
        assertThat(job.get("status")).isEqualTo("READY");
        assertThat(extended.processNextImportNow()).isTrue();

        mockMvc.perform(get("/api/v1/crm/imports/{id}", jobId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.processed_rows").value(2))
                .andExpect(jsonPath("$.succeeded_rows").value(1))
                .andExpect(jsonPath("$.failed_rows").value(1))
                .andExpect(jsonPath("$.error_count").value(1));
        mockMvc.perform(get("/api/v1/crm/imports/{id}/errors", jobId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].row_number").value(3))
                .andExpect(jsonPath("$[0].error_code").value("VALIDATION_ERROR"));

        String errorReport = mockMvc.perform(get("/api/v1/crm/imports/{id}/errors.csv", jobId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(errorReport).contains("VALIDATION_ERROR");

        JsonNode imported = objectMapper.readTree(mockMvc.perform(get("/api/v1/crm/accounts")
                        .with(authentication(auth(tenantA, userA)))
                        .queryParam("search", "Imported One"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String importedId = imported.get(0).path("id").asText();
        mockMvc.perform(get("/api/v1/crm/custom-fields/values/ACCOUNT/{id}", importedId)
                        .with(authentication(auth(tenantA, userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.industryCode").value("technology"));

        mockMvc.perform(get("/api/v1/crm/imports/{id}", jobId)
                        .with(authentication(auth(tenantB, userB))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/crm/custom-fields/values/ACCOUNT/{id}", accountId)
                        .with(authentication(auth(tenantB, userB))))
                .andExpect(status().isNotFound());
    }

    private void createField(String key, boolean sensitive, boolean searchable, boolean required)
            throws Exception {
        mockMvc.perform(post("/api/v1/crm/custom-fields")
                        .with(authentication(auth(tenantA, userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"ACCOUNT","fieldKey":"%s","labelAr":"%s",
                                 "labelEn":"%s","dataType":"TEXT","sensitive":%s,
                                 "searchable":%s,"required":%s}
                                """.formatted(key, key, key, sensitive, searchable, required)))
                .andExpect(status().isCreated());
    }

    private JsonNode json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    private Authentication auth(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return authentication;
    }

    private void seedIdentity(UUID tenantId, UUID userId, String suffix) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?)",
                tenantId, "crm-import-" + suffix + tenantId.toString().substring(0, 8), "CRM Import", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
                userId, tenantId, suffix + userId + "@example.test", "CRM Import User", now, now);
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?,?,'ADMIN','Administrator','CRM import role','ACTIVE',?,?)",
                roleId, tenantId, now, now);
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) VALUES (?,?,?,?,NULL,'ACTIVE',?,?)",
                UUID.randomUUID(), tenantId, userId, roleId, now, now);
        for (UUID capabilityId : jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'CRM.%'", UUID.class)) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), tenantId, roleId, capabilityId, now);
        }
    }
}
