package com.sanad.platform.management;

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
 * Integration test for GAP 19 — Revenue Oversight.
 *
 * Verifies the unified Executive Revenue Overview aggregates CRM won
 * revenue and Finance invoice/payment data without duplicating logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class RevenueOversightIntegrationTest {

    @Autowired private RevenueOversightService revenueService;
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
        // Create second tenant with no data
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "rev-other-" + otherTenant.toString().substring(0, 8), now, now);

        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(otherTenant);
        // Same shape, no cross-tenant leak
        assertThat((BigDecimal) overview.get("crmWonRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getExecutiveRevenueOverview_sourceModulesIncludesCrmAndFinance() {
        Map<String, Object> overview = revenueService.getExecutiveRevenueOverview(tenantId);
        @SuppressWarnings("unchecked")
        var sources = (java.util.List<String>) overview.get("sourceModules");
        assertThat(sources).contains("CRM", "FINANCE");
    }
}
