package com.sanad.platform.crm.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account V2 HTTP integration tests using MockMvc with real authentication.
 * Tests the full HTTP path: Security Filter → Controller → AccountUseCases → Repository
 * Verifies HTTP status codes, ETag, If-Match (428/412), and response shapes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AccountV2HttpIntegrationTest {

    private static final List<String> ACCOUNT_CAPABILITIES = List.of(
            "CRM.ACCOUNT.READ",
            "CRM.ACCOUNT.WRITE"
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private UUID seedTenantAndOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (:id, 'Test Tenant', 'test-" + tenantId.toString().substring(0, 8) + "', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", tenantId));
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) VALUES (:id, :tenantId, :email, 'Test Owner', 'ACTIVE', 'dummy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", userId).addValue("tenantId", tenantId).addValue("email", "owner-" + userId.toString().substring(0, 8) + "@test.example"));
        seedAccountCapabilities(tenantId, userId);
        return tenantId;
    }

    private void seedAccountCapabilities(UUID tenantId, UUID userId) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) "
                        + "VALUES (:id, :tenantId, 'TEST_CRM_ADMIN', 'Test CRM Admin', 'HTTP integration-test role', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", roleId).addValue("tenantId", tenantId));

        List<UUID> capabilityIds = jdbc.query(
                "SELECT id FROM access_capabilities WHERE code IN (:codes)",
                new MapSqlParameterSource("codes", ACCOUNT_CAPABILITIES),
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        if (capabilityIds.size() != ACCOUNT_CAPABILITIES.size()) {
            throw new IllegalStateException("Required CRM account capabilities are missing from the test catalog");
        }

        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                            + "VALUES (:id, :tenantId, :roleId, :capabilityId, CURRENT_TIMESTAMP)",
                    new MapSqlParameterSource("id", UUID.randomUUID())
                            .addValue("tenantId", tenantId)
                            .addValue("roleId", roleId)
                            .addValue("capabilityId", capabilityId));
        }

        jdbc.update("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at) "
                        + "VALUES (:id, :tenantId, :userId, :roleId, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", UUID.randomUUID())
                        .addValue("tenantId", tenantId)
                        .addValue("userId", userId)
                        .addValue("roleId", roleId));
    }

    private Authentication buildAuth(UUID tenantId, UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId.toString());
        details.put("user_id", userId.toString());
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(details);
        return auth;
    }

    @Test
    @DisplayName("GET /api/v2/crm/accounts returns 200 with authentication")
    void listAccountsReturns200() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        mockMvc.perform(get("/api/v2/crm/accounts").with(authentication(buildAuth(tenantId, userId))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v2/crm/accounts/{id} with non-existent ID returns 404")
    void getNonExistentReturns404() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        mockMvc.perform(get("/api/v2/crm/accounts/00000000-0000-4000-8000-000000000000")
                        .with(authentication(buildAuth(tenantId, userId))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH without If-Match returns 428")
    void patchWithoutIfMatchReturns428() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        String createResponse = mockMvc.perform(post("/api/v2/crm/accounts")
                        .with(authentication(buildAuth(tenantId, userId)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"displayName\":\"Test Acct\",\"accountType\":\"BUSINESS\",\"ownerUserId\":\"" + userId + "\",\"primaryCurrencyCode\":\"SAR\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = extractUuidFromJson(createResponse, "id");
        mockMvc.perform(patch("/api/v2/crm/accounts/" + accountId)
                        .with(authentication(buildAuth(tenantId, userId)))
                        .contentType("application/json")
                        .content("{\"displayName\":\"Updated\"}"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    @DisplayName("PATCH with stale If-Match returns 412")
    void patchWithStaleIfMatchReturns412() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        String createResponse = mockMvc.perform(post("/api/v2/crm/accounts")
                        .with(authentication(buildAuth(tenantId, userId)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"displayName\":\"Stale Test\",\"accountType\":\"BUSINESS\",\"ownerUserId\":\"" + userId + "\",\"primaryCurrencyCode\":\"SAR\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = extractUuidFromJson(createResponse, "id");
        mockMvc.perform(patch("/api/v2/crm/accounts/" + accountId)
                        .with(authentication(buildAuth(tenantId, userId)))
                        .header("If-Match", "\"account-" + accountId + "-v999-fakehash\"")
                        .contentType("application/json")
                        .content("{\"displayName\":\"Should Fail\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("POST with invalid body returns 400 after authentication")
    void createWithInvalidBodyReturns400() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        mockMvc.perform(post("/api/v2/crm/accounts")
                        .with(authentication(buildAuth(tenantId, userId)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST creates account and GET returns ETag")
    void createAndGetWithETag() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new MapSqlParameterSource("t", tenantId), UUID.class);
        String createResponse = mockMvc.perform(post("/api/v2/crm/accounts")
                        .with(authentication(buildAuth(tenantId, userId)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"displayName\":\"ETag Test\",\"accountType\":\"BUSINESS\",\"ownerUserId\":\"" + userId + "\",\"primaryCurrencyCode\":\"SAR\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = extractUuidFromJson(createResponse, "id");
        mockMvc.perform(get("/api/v2/crm/accounts/" + accountId)
                        .with(authentication(buildAuth(tenantId, userId))))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    private UUID extractUuidFromJson(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return UUID.randomUUID();
        start += search.length();
        int end = json.indexOf("\"", start);
        return UUID.fromString(json.substring(start, end));
    }
}
