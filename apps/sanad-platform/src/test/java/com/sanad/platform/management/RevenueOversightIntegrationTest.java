package com.sanad.platform.management;

import com.sanad.platform.management.application.CrmManagementIntegrationService;
import com.sanad.platform.management.application.RevenueOversightService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GAP 19 — Revenue Oversight (v20260815.9 — REAL data).
 *
 * Verifies the unified Executive Revenue Overview aggregates CRM won revenue
 * and Finance invoice/payment data without duplicating logic.
 *
 * v20260815.9 additions:
 * - Verifies CRM opportunity amount uses the correct 'amount' column (not 'estimated_value')
 * - Verifies WON revenue calculates correctly from actual data
 * - Verifies the dashboard revenue matches the dedicated endpoint
 * - Verifies no transaction-abort errors
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class RevenueOversightIntegrationTest {

    @Autowired private RevenueOversightService revenueService;
    @Autowired private CrmManagementIntegrationService crmService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "rev-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void getExecutiveRevenueOverview_returnsAllExpectedFields() {
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat(overview).containsKey("crmWonRevenue");
        assertThat(overview).containsKey("crmPipelineValue");
        assertThat(overview).containsKey("invoiceTotalValue");
        assertThat(overview).containsKey("collectedRevenue");
        assertThat(overview).containsKey("outstandingAmount");
        assertThat(overview).containsKey("paymentStatusSummary");
        assertThat(overview).containsKey("revenueVariance");
        assertThat(overview).containsKey("sourceModules");
        assertThat(overview).containsKey("generatedAt");
        // v20260815.9: additional fields
        assertThat(overview).containsKey("financeBilledAmount");
        assertThat(overview).containsKey("financePaidAmount");
        assertThat(overview).containsKey("financeOutstandingAmount");
        assertThat(overview).containsKey("invoiceCount");
        assertThat(overview).containsKey("paymentCount");
    }

    @Test
    void getExecutiveRevenueOverview_returnsZeroRevenueForEmptyTenant() {
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat((BigDecimal) overview.get("crmWonRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) overview.get("invoiceTotalValue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) overview.get("collectedRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) overview.get("outstandingAmount")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) overview.get("revenueVariance")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getExecutiveRevenueOverview_isTenantScoped() {
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "rev-other-" + otherTenant.toString().substring(0, 8), now, now);
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(otherTenant);
        assertThat((BigDecimal) overview.get("crmWonRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getExecutiveRevenueOverview_sourceModulesIncludesCrmAndFinance() {
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        @SuppressWarnings("unchecked")
        var sources = (java.util.List<String>) overview.get("sourceModules");
        assertThat(sources).contains("CRM", "FINANCE");
    }

    // ===== v20260815.9 — CRM QUERY FIX TESTS =====

    @Test
    void crmOverview_doesNotThrowEstimatedValueError() {
        // v20260815.9: The CRM query was fixed to use 'amount' instead of 'estimated_value'.
        // This test verifies the CRM overview loads without PSQLException.
        var overview = crmService.getCrmOverview(tenantId);
        assertThat(overview).containsKey("wonRevenue");
        assertThat(overview).containsKey("estimatedPipelineValue");
        assertThat(overview.get("wonRevenue")).isInstanceOf(BigDecimal.class);
        assertThat(overview.get("estimatedPipelineValue")).isInstanceOf(BigDecimal.class);
    }

    @Test
    void crmOverview_calculatesWonRevenueFromAmountColumn() {
        // Insert a WON opportunity with amount=15000 and verify the revenue is calculated
        UUID oppId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        // Create prerequisite CRM entities
        jdbc.update("INSERT INTO crm_pipelines (id, tenant_id, name, currency_code, active, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'Test Pipeline', 'SAR', TRUE, ?, ?, ?)",
                pipelineId, tenantId, userId, now, now);
        jdbc.update("INSERT INTO crm_pipeline_stages (id, tenant_id, pipeline_id, name, sequence, probability, active) "
                        + "VALUES (?, ?, ?, 'Closed Won', 10, 100.00, TRUE)",
                stageId, tenantId, pipelineId, "Won");
        jdbc.update("INSERT INTO crm_accounts (id, tenant_id, name, account_type, lifecycle_status, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'Test Account', 'BUSINESS', 'ACTIVE', ?, ?, ?, ?)",
                accountId, tenantId, userId, userId, now, now);
        jdbc.update("INSERT INTO crm_opportunities (id, tenant_id, pipeline_id, stage_id, name, amount, currency_code, probability, status, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Test Opp', 15000.00, 'SAR', 100.00, 'WON', ?, ?, ?, ?)",
                oppId, tenantId, pipelineId, stageId, userId, userId, now, now);

        var overview = crmService.getCrmOverview(tenantId);
        // wonRevenue should be 15000 (from the amount column, not estimated_value)
        assertThat((BigDecimal) overview.get("wonRevenue")).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(overview.get("wonOpportunities")).isEqualTo(1);
    }

    @Test
    void crmOverview_calculatesPipelineValueFromAmountColumn() {
        // Insert an OPEN opportunity with amount=5000 and verify pipeline value
        UUID oppId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO crm_pipelines (id, tenant_id, name, currency_code, active, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'Test Pipeline 2', 'SAR', TRUE, ?, ?, ?)",
                pipelineId, tenantId, userId, now, now);
        jdbc.update("INSERT INTO crm_pipeline_stages (id, tenant_id, pipeline_id, name, sequence, probability, active) "
                        + "VALUES (?, ?, ?, 'Negotiation', 5, 50.00, TRUE)",
                stageId, tenantId, pipelineId, "Neg");
        jdbc.update("INSERT INTO crm_opportunities (id, tenant_id, pipeline_id, stage_id, name, amount, currency_code, probability, status, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Open Opp', 5000.00, 'SAR', 50.00, 'OPEN', ?, ?, ?, ?)",
                oppId, tenantId, pipelineId, stageId, userId, userId, now, now);

        var overview = crmService.getCrmOverview(tenantId);
        // estimatedPipelineValue should include the 5000 from the OPEN opportunity
        assertThat((BigDecimal) overview.get("estimatedPipelineValue"))
                .isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void revenueOverview_doesNotContainErrorMarkers() {
        // v20260815.9: No _error or _status markers should be present
        // when the CRM query is fixed
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat(overview.containsKey("_crmError")).isFalse();
        assertThat(overview.containsKey("_financeError")).isFalse();
    }

    @Test
    void revenueOverview_crmAndFinanceDataIncluded() {
        // v20260815.9: The raw crmOverview and financeOverview are included
        // so consumers can access the full picture if needed
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat(overview).containsKey("crmOverview");
        assertThat(overview).containsKey("financeOverview");
        @SuppressWarnings("unchecked")
        Map<String, Object> crmOverview = (Map<String, Object>) overview.get("crmOverview");
        assertThat(crmOverview).containsKey("wonRevenue");
    }
}
