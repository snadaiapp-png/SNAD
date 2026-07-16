package com.sanad.platform.crm.reports.application;

import com.sanad.platform.crm.reports.domain.ReportsRepository;
import com.sanad.platform.crm.reports.domain.ReportsRepository.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Use-case façade for CRM reports.
 * <p>
 * Read-only service that delegates to {@link ReportsRepository}.
 * No @Transactional needed — all queries are reads.
 * <p>
 * Branch: feature/crm-reports
 */
@Service
public class ReportsUseCases {

    private final ReportsRepository repo;

    public ReportsUseCases(ReportsRepository repo) {
        this.repo = repo;
    }

    public SalesPipelineReport getSalesPipeline(UUID tenantId) {
        return repo.getSalesPipelineReport(tenantId);
    }

    public LeadConversionReport getLeadConversion(UUID tenantId) {
        return repo.getLeadConversionReport(tenantId);
    }

    public ActivitySummaryReport getActivitySummary(UUID tenantId) {
        return repo.getActivitySummaryReport(tenantId);
    }

    public AccountGrowthReport getAccountGrowth(UUID tenantId) {
        return repo.getAccountGrowthReport(tenantId);
    }
}
