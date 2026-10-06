package com.sanad.platform.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 persistence-level audit proof: real user administration mutations
 * through the real API layer must land canonical, tenant-correct,
 * credential-free rows in {@code platform_audit_logs} — the canonical
 * append-only audit store (no parallel audit mechanism).
 *
 * <p>Persisted before_state / after_state JSON is inspected directly: secrets
 * (temporary access secrets, hashes, single-use values, tokens) must be
 * absent, idempotent no-ops must not append rows, and failures must not
 * produce false SUCCESS entries.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserPhase7AuditPersistenceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUp() {
        tenantId = saveTenant("AuditAcme");
        otherTenantId = saveTenant("AuditOther");
    }

    @Test
    void createUserAppendsTenantCorrectCredentialFreeAuditRow() throws Exception {
        UUID userId = createUser(tenantId, "audit-create@example.com", "AuditCreate");

        List<Map<String, Object>> rows = auditRows("USER_CREATED");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("target_tenant_id")).isEqualTo(tenantId);
        assertThat(row.get("resource_type")).isEqualTo("USER");
        assertThat(String.valueOf(row.get("resource_id"))).isEqualTo(userId.toString());
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        String afterState = String.valueOf(row.get("after_state"));
        JsonNode after = objectMapper.readTree(afterState);
        assertThat(after.get("status").asText()).isEqualTo("INVITED");
        assertThat(after.has("email")).isTrue();
        // Credential material must never appear in the persisted audit JSON.
        assertThat(afterState).doesNotContainIgnoringCase("password");
        assertThat(afterState).doesNotContainIgnoringCase("credential");
        assertThat(afterState).doesNotContainIgnoringCase("token");
        assertThat(afterState).doesNotContainIgnoringCase("secret");
    }

    @Test
    void profileAndUsernameChangesAppendDistinctCanonicalRows() throws Exception {
        UUID userId = createUser(tenantId, "audit-update@example.com", "BeforeName");

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("audit-update@example.com", "AfterName")))
                .andExpect(status().isOk());

        List<Map<String, Object>> profileRows = auditRows("USER_PROFILE_UPDATED");
        assertThat(profileRows).hasSize(1);
        JsonNode after = objectMapper.readTree(String.valueOf(profileRows.get(0).get("after_state")));
        assertThat(after.get("changedFields").toString()).contains("displayName");
        assertThat(profileRows.get(0).get("result")).isEqualTo("SUCCESS");

        // Username change on the same request appends the distinct identity fact.
        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("audit-update@example.com", "renamed-id", "AfterName")))
                .andExpect(status().isOk());
        List<Map<String, Object>> usernameRows = auditRows("USER_USERNAME_CHANGED");
        assertThat(usernameRows).hasSize(1);
        JsonNode usernameAfter = objectMapper.readTree(String.valueOf(usernameRows.get(0).get("after_state")));
        assertThat(usernameAfter.get("username").asText()).isEqualTo("renamed-id");
    }

    @Test
    void idempotentProfileUpdateAppendsNoAuditRow() throws Exception {
        UUID userId = createUser(tenantId, "idem@example.com", "IdemName");

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("idem@example.com", "IdemName")))
                .andExpect(status().isOk());

        assertThat(auditRows("USER_PROFILE_UPDATED")).isEmpty();
    }

    @Test
    void lifecycleTransitionsAppendExactCanonicalRows() throws Exception {
        UUID userId = createUser(tenantId, "lifecycle@example.com", "Lifecycle");

        mockMvc.perform(patch("/api/v1/users/{userId}/suspend", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk());
        List<Map<String, Object>> suspended = auditRows("USER_SUSPENDED");
        assertThat(suspended).hasSize(1);
        assertThat(suspended.get(0).get("result")).isEqualTo("SUCCESS");

        mockMvc.perform(patch("/api/v1/users/{userId}/activate", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk());
        assertThat(auditRows("USER_ACTIVATED")).hasSize(1);

        mockMvc.perform(patch("/api/v1/users/{userId}/suspend", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk());
        assertThat(auditRows("USER_SUSPENDED")).hasSize(2);
    }

    @Test
    void failedUpdateDoesNotProduceFalseSuccessAudit() throws Exception {
        UUID userId = createUser(tenantId, "failure@example.com", "Failure");
        UUID collidingUser = createUser(tenantId, "taken@example.com", "Taken");
        assertThat(collidingUser).isNotNull();
        int before = auditRows("USER_PROFILE_UPDATED").size();

        // Duplicate email across the same tenant is rejected (409) — no
        // USER_PROFILE_UPDATED SUCCESS row may be appended.
        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePayload("taken@example.com")))
                .andExpect(status().is4xxClientError());

        assertThat(auditRows("USER_PROFILE_UPDATED")).hasSize(before);
    }

    @Test
    void crossTenantUpdateIsRejectedWithoutTargetTenantAudit() throws Exception {
        UUID userId = createUser(tenantId, "isolation@example.com", "Isolation");
        int before = auditRows("USER_PROFILE_UPDATED").size();

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", otherTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("isolation@example.com", "CrossTenant")))
                .andExpect(status().isNotFound());

        assertThat(auditRows("USER_PROFILE_UPDATED")).hasSize(before);
    }

    // ------------------------------------------------------------------

    private List<Map<String, Object>> auditRows(String action) {
        return jdbcTemplate.queryForList(
                "SELECT actor_tenant_id, actor_user_id, target_tenant_id, action, resource_type, "
                + "resource_id, reason, before_state, after_state, result, created_at "
                + "FROM platform_audit_logs WHERE action = ? ORDER BY created_at, id", action);
    }

    private UUID saveTenant(String name) {
        return tenantRepository.save(new Tenant(
                name, name.toLowerCase() + "-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId();
    }

    private UUID createUser(UUID scope, String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", scope.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(email, name)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(created.get("id").asText());
    }

    private String payload(String email, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "displayName", name));
    }

    private String updatePayload(String email, String username, String name) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "username", username, "displayName", name));
    }

    private String duplicatePayload(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "displayName", "Dup"));
    }
}
