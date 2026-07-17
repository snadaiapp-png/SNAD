package com.sanad.platform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
@Transactional
class IntegratedBusinessProcessesE2ETest {

    private static final UUID TENANT_A = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_B = UUID.fromString("82000000-0000-0000-0000-000000000002");
    private static final UUID NO_CAP_A = UUID.fromString("82000000-0000-0000-0000-000000000003");
    private static final UUID ROLE_A = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_B = UUID.fromString("83000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedIdentityAndCapabilities() {
        Instant now = Instant.now();
        seedTenant(TENANT_A, "e2e-process-a", "E2E Process Tenant A", now);
        seedTenant(TENANT_B, "e2e-process-b", "E2E Process Tenant B", now);
        seedUser(ADMIN_A, TENANT_A, "process-admin-a@example.test", now);
        seedUser(ADMIN_B, TENANT_B, "process-admin-b@example.test", now);
        seedUser(NO_CAP_A, TENANT_A, "process-no-cap@example.test", now);
        seedRole(ROLE_A, TENANT_A, now);
        seedRole(ROLE_B, TENANT_B, now);
        seedRoleAssignment(TENANT_A, ROLE_A, ADMIN_A, now);
        seedRoleAssignment(TENANT_B, ROLE_B, ADMIN_B, now);
        grantBusinessProcessCapabilities(TENANT_A, ROLE_A, now);
        grantBusinessProcessCapabilities(TENANT_B, ROLE_B, now);
    }

    @Test
    void provesAllFourProcessesWithFinancialInventoryWorkflowAuditAnalyticsAndRollback() throws Exception {
        JsonNode sales = execute("SALES-ORDER-TO-CASH", "sales-001", 1000, 150, 2, "SALES-E2E-SKU", null, 201);
        assertComplete(sales, 12);
        assertMoney(sales, "paymentNet", "1150.000000");
        assertMoney(sales, "startingInventory", "100.000000");
        assertMoney(sales, "endingInventory", "98.000000");
        assertThat(sales.path("inventoryMovementCount").asInt()).isEqualTo(2);

        JsonNode procurement = execute("PROCUREMENT-PROCURE-TO-PAY", "procure-001", 500, 0, 5, "PROCUREMENT-E2E-SKU", null, 201);
        assertComplete(procurement, 9);
        assertMoney(procurement, "paymentNet", "-500.000000");
        assertMoney(procurement, "startingInventory", "0.000000");
        assertMoney(procurement, "endingInventory", "5.000000");
        assertThat(procurement.path("workflowApprovalCount").asInt()).isEqualTo(1);

        JsonNode hr = execute("HR-HIRE-TO-PAY", "hire-001", 1000, 0, 1, null, null, 201);
        assertComplete(hr, 8);
        assertMoney(hr, "paymentNet", "-1000.000000");
        assertThat(hr.path("startingInventory").isNull()).isTrue();
        assertThat(hr.path("endingInventory").isNull()).isTrue();
        assertThat(hr.path("workflowApprovalCount").asInt()).isEqualTo(1);

        JsonNode commerce = execute("COMMERCE-ORDER-TO-REFUND", "commerce-001", 100, 15, 1, "COMMERCE-E2E-SKU", null, 201);
        assertComplete(commerce, 9);
        assertMoney(commerce, "paymentNet", "0.000000");
        assertMoney(commerce, "startingInventory", "50.000000");
        assertMoney(commerce, "endingInventory", "50.000000");
        assertThat(commerce.path("paymentEventCount").asInt()).isEqualTo(2);

        assertThat(count("SELECT COUNT(*) FROM bp_process_runs WHERE tenant_id=? AND status='COMPLETED'", TENANT_A))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM bp_process_steps WHERE tenant_id=?", TENANT_A))
                .isEqualTo(38);
        assertThat(count("SELECT COUNT(*) FROM bp_analytics_snapshots WHERE tenant_id=?", TENANT_A))
                .isEqualTo(20);

        JsonNode replay = execute("SALES-ORDER-TO-CASH", "sales-001", 1000, 150, 2, "SALES-E2E-SKU", null, 200);
        assertThat(replay.path("runId").asText()).isEqualTo(sales.path("runId").asText());
        assertThat(replay.path("idempotent").asBoolean()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM bp_process_runs WHERE tenant_id=?", TENANT_A)).isEqualTo(4);

        UUID salesRunId = UUID.fromString(sales.path("runId").asText());
        mockMvc.perform(get("/api/v1/business-process-e2e/runs/{runId}", salesRunId)
                        .with(authentication(auth(TENANT_B, ADMIN_B))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/business-process-e2e/runs/{runId}", salesRunId)
                        .with(authentication(auth(TENANT_A, NO_CAP_A))))
                .andExpect(status().isForbidden());

        long runsBeforeFailure = count("SELECT COUNT(*) FROM bp_process_runs WHERE tenant_id=?", TENANT_A);
        long ledgerBeforeFailure = count("SELECT COUNT(*) FROM bp_ledger_entries WHERE tenant_id=?", TENANT_A);
        mockMvc.perform(post("/api/v1/business-process-e2e/{processCode}/execute", "PROCUREMENT-PROCURE-TO-PAY")
                        .with(authentication(auth(TENANT_A, ADMIN_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("procure-rollback", 700, 0, 7,
                                "ROLLBACK-E2E-SKU", "Supplier Invoice")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(count("SELECT COUNT(*) FROM bp_process_runs WHERE tenant_id=?", TENANT_A))
                .isEqualTo(runsBeforeFailure);
        assertThat(count("SELECT COUNT(*) FROM bp_ledger_entries WHERE tenant_id=?", TENANT_A))
                .isEqualTo(ledgerBeforeFailure);
        assertThat(count("SELECT COUNT(*) FROM bp_inventory_balances WHERE tenant_id=? AND sku='ROLLBACK-E2E-SKU'", TENANT_A))
                .isZero();
    }

    private JsonNode execute(String processCode, String reference, int gross, int tax,
                             int quantity, String sku, String failAtStep, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/v1/business-process-e2e/{processCode}/execute", processCode)
                        .with(authentication(auth(TENANT_A, ADMIN_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(reference, gross, tax, quantity, sku, failAtStep)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String payload(String reference, int gross, int tax, int quantity, String sku, String failAtStep) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("externalReference", reference);
        payload.put("grossAmount", gross);
        payload.put("taxAmount", tax);
        payload.put("quantity", quantity);
        payload.put("currencyCode", "SAR");
        if (sku != null) payload.put("sku", sku);
        if (failAtStep != null) payload.put("failAtStep", failAtStep);
        return objectMapper.writeValueAsString(payload);
    }

    private void assertComplete(JsonNode result, int expectedSteps) {
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("financialReconciled").asBoolean()).isTrue();
        assertThat(result.path("inventoryReconciled").asBoolean()).isTrue();
        assertThat(result.path("analyticsConsistent").asBoolean()).isTrue();
        assertThat(result.path("idempotent").asBoolean()).isFalse();
        assertThat(result.path("blockedSteps").size()).isZero();
        assertThat(result.path("verifiedSteps").size()).isEqualTo(expectedSteps);
        assertThat(new BigDecimal(result.path("debitTotal").asText()))
                .isEqualByComparingTo(new BigDecimal(result.path("creditTotal").asText()));
        assertThat(result.path("auditCount").asInt()).isGreaterThanOrEqualTo(expectedSteps + 1);
        assertThat(result.path("ledgerEntryCount").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(result.path("paymentEventCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    private void assertMoney(JsonNode result, String field, String expected) {
        assertThat(new BigDecimal(result.path(field).asText())).isEqualByComparingTo(new BigDecimal(expected));
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
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?,?,'BP_ADMIN','Business Process Administrator','REM-P1-007 final closure role','ACTIVE',?,?)",
                id, tenantId, now, now);
    }

    private void seedRoleAssignment(UUID tenantId, UUID roleId, UUID userId, Instant now) {
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) VALUES (?,?,?,?,NULL,'ACTIVE',?,?)",
                UUID.randomUUID(), tenantId, userId, roleId, now, now);
    }

    private void grantBusinessProcessCapabilities(UUID tenantId, UUID roleId, Instant now) {
        List<UUID> capabilityIds = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'BUSINESS_PROCESS.%'", UUID.class);
        assertThat(capabilityIds).hasSize(2);
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), tenantId, roleId, capabilityId, now);
        }
    }
}
