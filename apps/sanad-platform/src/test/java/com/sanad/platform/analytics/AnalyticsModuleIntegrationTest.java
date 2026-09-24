package com.sanad.platform.analytics;

import com.sanad.platform.analytics.application.*;
import com.sanad.platform.analytics.domain.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(SecurityPermitAllTestConfig.class)
class AnalyticsModuleIntegrationTest {

    @Autowired private AnalyticsDashboardService dashboardService;
    @Autowired private AnalyticsReportService reportService;
    @Autowired private AnalyticsDataSourceService dataSourceService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId, userId;

    @BeforeEach void setUp() {
        jdbc.execute("TRUNCATE TABLE analytics_reports, analytics_dashboards, analytics_data_sources RESTART IDENTITY CASCADE");
        tenantId = UUID.randomUUID(); userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)", tenantId, "an-"+tenantId.toString().substring(0,8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)", userId, tenantId, "an-"+userId.toString().substring(0,8)+"@test", now, now);
        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)", roleId, tenantId, now, now);
        var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'ANALYTICS.%'");
        for (var cap : caps) jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
    }

    @Test void dashboardLifecycle() {
        var d = AnalyticsDashboard.create(tenantId, "DASH-1", "Sales Dashboard", "Test", AnalyticsDashboard.DashboardType.EXECUTIVE, null, userId);
        var created = dashboardService.create(d);
        assertThat(created.status()).isEqualTo(AnalyticsDashboard.Status.DRAFT);
        var active = dashboardService.activate(tenantId, created.id());
        assertThat(active.status()).isEqualTo(AnalyticsDashboard.Status.ACTIVE);
        var inactive = dashboardService.deactivate(tenantId, created.id());
        assertThat(inactive.status()).isEqualTo(AnalyticsDashboard.Status.INACTIVE);
        var archived = dashboardService.archive(tenantId, created.id());
        assertThat(archived.status()).isEqualTo(AnalyticsDashboard.Status.ARCHIVED);
    }

    @Test void dashboardDuplicateCodeRejected() {
        dashboardService.create(AnalyticsDashboard.create(tenantId, "DUP-DASH", "First", null, null, null, userId));
        assertThatThrownBy(() -> dashboardService.create(AnalyticsDashboard.create(tenantId, "DUP-DASH", "Second", null, null, null, userId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void reportLifecycle() {
        var r = AnalyticsReport.create(tenantId, "RPT-1", "Sales Report", "Test", AnalyticsReport.ReportType.CHART, null, "SELECT * FROM sales", null, AnalyticsReport.OutputFormat.PDF, userId);
        var created = reportService.create(r);
        assertThat(created.status()).isEqualTo(AnalyticsReport.Status.DRAFT);
        var active = reportService.activate(tenantId, created.id());
        assertThat(active.status()).isEqualTo(AnalyticsReport.Status.ACTIVE);
        var executed = reportService.execute(tenantId, created.id());
        assertThat(executed.lastExecutionStatus()).isEqualTo("SUCCESS");
    }

    @Test void reportSchedule() {
        var r = AnalyticsReport.create(tenantId, "RPT-SCHED", "Monthly Report", null, null, null, null, null, null, userId);
        var created = reportService.create(r);
        var scheduled = reportService.schedule(tenantId, created.id(), "0 0 1 * *");
        assertThat(scheduled.status()).isEqualTo(AnalyticsReport.Status.SCHEDULED);
        assertThat(scheduled.scheduleCron()).isEqualTo("0 0 1 * *");
    }

    @Test void dataSourceLifecycle() {
        var ds = AnalyticsDataSource.create(tenantId, "DS-1", "CRM Data", "Test", AnalyticsDataSource.SourceType.CRM, "crm", null, userId);
        var created = dataSourceService.create(ds);
        assertThat(created.status()).isEqualTo(AnalyticsDataSource.Status.PENDING);
        var active = dataSourceService.activate(tenantId, created.id());
        assertThat(active.status()).isEqualTo(AnalyticsDataSource.Status.ACTIVE);
        var inactive = dataSourceService.deactivate(tenantId, created.id());
        assertThat(inactive.status()).isEqualTo(AnalyticsDataSource.Status.INACTIVE);
    }

    @Test void crossTenantDashboardReadReturnsEmpty() {
        var d = dashboardService.create(AnalyticsDashboard.create(tenantId, "XT-1", "Test", null, null, null, userId));
        assertThat(dashboardService.findById(UUID.randomUUID(), d.id())).isEmpty();
    }

    @Test void crossTenantReportReadReturnsEmpty() {
        var r = reportService.create(AnalyticsReport.create(tenantId, "XT-RPT-1", "Test", null, null, null, null, null, null, userId));
        assertThat(reportService.findById(UUID.randomUUID(), r.id())).isEmpty();
    }

    @Test void crossTenantDataSourceReadReturnsEmpty() {
        var ds = dataSourceService.create(AnalyticsDataSource.create(tenantId, "XT-DS-1", "Test", null, AnalyticsDataSource.SourceType.DATABASE, null, null, userId));
        assertThat(dataSourceService.findById(UUID.randomUUID(), ds.id())).isEmpty();
    }
}
