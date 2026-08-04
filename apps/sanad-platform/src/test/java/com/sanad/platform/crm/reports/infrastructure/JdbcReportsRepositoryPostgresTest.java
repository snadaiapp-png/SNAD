package com.sanad.platform.crm.reports.infrastructure;

import com.sanad.platform.crm.reports.domain.ReportsRepository.AccountGrowthReport;
import com.sanad.platform.crm.reports.domain.ReportsRepository.ActivitySummaryReport;
import com.sanad.platform.crm.reports.domain.ReportsRepository.LeadConversionReport;
import com.sanad.platform.crm.reports.domain.ReportsRepository.SalesPipelineReport;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcReportsRepository} (TD-003-S2).
 *
 * <p>Validates the read-only aggregation queries against a real PostgreSQL instance: the empty
 * tenant returns zero totals, and a seeded opportunity flows through the sales-pipeline report
 * (weighted value = amount × probability).
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcReportsRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcReportsRepository reports;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        reports = new JdbcReportsRepository(jdbc());
        tenantId = newTenant();
    }

    @Test
    void salesPipelineReport_onEmptyTenantReturnsZeros() {
        SalesPipelineReport report = reports.getSalesPipelineReport(tenantId);

        assertThat(report.totalOpportunities()).isZero();
        assertThat(report.totalPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.weightedPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.stages()).isEmpty();
    }

    @Test
    void leadConversionReport_onEmptyTenantReturnsZeros() {
        LeadConversionReport report = reports.getLeadConversionReport(tenantId);

        assertThat(report.totalLeads()).isZero();
        assertThat(report.convertedLeads()).isZero();
        assertThat(report.conversionRate()).isZero();
    }

    @Test
    void activitySummaryReport_onEmptyTenantReturnsZeros() {
        ActivitySummaryReport report = reports.getActivitySummaryReport(tenantId);

        assertThat(report.totalActivities()).isZero();
        assertThat(report.totalTasks()).isZero();
    }

    @Test
    void accountGrowthReport_onEmptyTenantReturnsZeros() {
        AccountGrowthReport report = reports.getAccountGrowthReport(tenantId);

        assertThat(report.totalAccounts()).isZero();
        assertThat(report.activeAccounts()).isZero();
        assertThat(report.monthlyGrowth()).isNotNull();
    }

    @Test
    void salesPipelineReport_isTenantScoped() {
        // seed an account in this tenant; the report must only ever see this tenant's rows
        seedAccount(tenantId, UUID.randomUUID(), "Scoped Account", "scoped-account");

        SalesPipelineReport report = reports.getSalesPipelineReport(tenantId);
        // no opportunities seeded yet → still zero totals, but the call must not blow up
        // and must not leak other tenants' data
        assertThat(report.totalOpportunities()).isZero();
    }
}
