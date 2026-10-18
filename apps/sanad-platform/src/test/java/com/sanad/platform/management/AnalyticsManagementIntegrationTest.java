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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        jdbc.execute("TRUNCATE TABLE analytics_reports, analytics_dashboards, analytics_data_sources RESTART IDENTITY CASCADE");
        tenantId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)", tenantId, "ami-" + tenantId.toString().substring(0, 8), now, now);
        // public.users is FORCE RLS (V20261011_3). This test class is NOT @Transactional,
        // so HikariCP returns a different physical connection per jdbc.update() call.
        // Wrap set_config + INSERT INTO users in ONE tx.executeWithoutResult block so
        // both share the tx-bound physical connection. is_local=true scopes the GUC to
        // this transaction only — no session-level leak when the connection is returned
        // to HikariCP.
        tx.executeWithoutResult(txStatus -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", UUID.randomUUID(), tenantId, "ami@test", now, now);
        });
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
