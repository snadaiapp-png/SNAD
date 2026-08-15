package com.sanad.platform.management;

import com.sanad.platform.management.application.AnalyticsManagementIntegrationService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class AnalyticsManagementIntegrationTest {

    @Autowired private AnalyticsManagementIntegrationService analyticsIntegrationService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE analytics_reports, analytics_dashboards, analytics_data_sources RESTART IDENTITY CASCADE");
        tenantId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)", tenantId, "ami-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", UUID.randomUUID(), tenantId, "ami@test", now, now);
    }

    @Test
    void getAnalyticsOverview_returnsDashboardMetrics() {
        var overview = analyticsIntegrationService.getAnalyticsOverview(tenantId);
        assertThat(overview).containsKey("totalDashboards");
        assertThat(overview).containsKey("activeDashboards");
        assertThat(overview.get("totalDashboards")).isEqualTo(0);
    }

    @Test
    void getAnalyticsOverview_returnsReportMetrics() {
        var overview = analyticsIntegrationService.getAnalyticsOverview(tenantId);
        assertThat(overview).containsKey("totalReports");
        assertThat(overview).containsKey("activeReports");
        assertThat(overview).containsKey("scheduledReports");
        assertThat(overview.get("totalReports")).isEqualTo(0);
    }

    @Test
    void getAnalyticsOverview_returnsDataSourceMetrics() {
        var overview = analyticsIntegrationService.getAnalyticsOverview(tenantId);
        assertThat(overview).containsKey("totalDataSources");
        assertThat(overview).containsKey("activeDataSources");
        assertThat(overview).containsKey("pendingDataSources");
        assertThat(overview).containsKey("errorDataSources");
    }

    @Test
    void getAnalyticsOverview_isTenantScoped() {
        var otherTenant = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)", otherTenant, "other-" + otherTenant.toString().substring(0, 8), now, now);
        var overview = analyticsIntegrationService.getAnalyticsOverview(otherTenant);
        assertThat(overview.get("totalDashboards")).isEqualTo(0);
        assertThat(overview.get("totalReports")).isEqualTo(0);
        assertThat(overview.get("totalDataSources")).isEqualTo(0);
    }

    @Test
    void getAnalyticsOverview_returnsTypeBreakdowns() {
        var overview = analyticsIntegrationService.getAnalyticsOverview(tenantId);
        assertThat(overview).containsKey("dashboardTypeBreakdown");
        assertThat(overview).containsKey("reportTypeBreakdown");
        assertThat(overview).containsKey("dataSourceTypeBreakdown");
    }
}
