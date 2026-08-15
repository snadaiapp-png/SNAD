package com.sanad.platform.management;

import com.sanad.platform.management.application.CrmManagementIntegrationService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CRM Management Integration Service.
 *
 * Verifies that Senior Management can query CRM data for executive overview
 * without duplicating CRM business logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class CrmManagementIntegrationTest {

    @Autowired private CrmManagementIntegrationService crmIntegrationService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "crmi-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                UUID.randomUUID(), tenantId, "crmi@test", now, now);
    }

    @Test
    void getCrmOverview_returnsAccountMetrics() {
        var overview = crmIntegrationService.getCrmOverview(tenantId);
        assertThat(overview).containsKey("totalAccounts");
        assertThat(overview).containsKey("activeAccounts");
        assertThat(overview.get("totalAccounts")).isEqualTo(0);
    }

    @Test
    void getCrmOverview_returnsOpportunityMetrics() {
        var overview = crmIntegrationService.getCrmOverview(tenantId);
        assertThat(overview).containsKey("totalOpportunities");
        assertThat(overview).containsKey("openOpportunities");
        assertThat(overview).containsKey("wonOpportunities");
        assertThat(overview).containsKey("winRate");
        assertThat(overview.get("totalOpportunities")).isEqualTo(0);
    }

    @Test
    void getCrmOverview_returnsRevenueMetrics() {
        var overview = crmIntegrationService.getCrmOverview(tenantId);
        assertThat(overview).containsKey("estimatedPipelineValue");
        assertThat(overview).containsKey("wonRevenue");
        assertThat((BigDecimal) overview.get("estimatedPipelineValue"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCrmOverview_returnsActivityMetrics() {
        var overview = crmIntegrationService.getCrmOverview(tenantId);
        assertThat(overview).containsKey("totalActivities");
        assertThat(overview).containsKey("activitiesThisMonth");
    }

    @Test
    void getCrmOverview_isTenantScoped() {
        // Overview for a different tenant should return zero data
        var otherTenant = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "other-" + otherTenant.toString().substring(0, 8), now, now);

        var overview = crmIntegrationService.getCrmOverview(otherTenant);
        assertThat(overview.get("totalAccounts")).isEqualTo(0);
        assertThat(overview.get("totalOpportunities")).isEqualTo(0);
    }
}
