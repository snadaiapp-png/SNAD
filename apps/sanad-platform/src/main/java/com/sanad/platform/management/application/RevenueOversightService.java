package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executive Revenue Oversight (GAP 19).
 *
 * <p>Aggregates revenue data from CRM (won opportunity revenue) and Finance
 * (invoiced, collected, outstanding amounts) into a single typed overview
 * without duplicating business logic from either module.
 *
 * <p>READ-ONLY and tenant-scoped. Delegates to:
 * <ul>
 *   <li>{@link CrmManagementIntegrationService#getCrmOverview} for CRM revenue metrics.</li>
 *   <li>{@link FinanceManagementIntegrationService#getOverview} for Finance revenue metrics.</li>
 * </ul>
 *
 * <p>CRITICAL: The CRM/Finance integration services may internally catch
 * PSQLException (e.g. when a column doesn't exist in the test fixture).
 * PostgreSQL then aborts the entire transaction. To prevent this from
 * polluting the caller's outer transaction, this service delegates the
 * actual data loading to {@link RevenueDataLoader} which runs in a
 * {@link Propagation#REQUIRES_NEW REQUIRES_NEW} transaction via the
 * Spring AOP proxy.
 */
@Service
public class RevenueOversightService {

    private final RevenueDataLoader dataLoader;

    public RevenueOversightService(@Lazy RevenueDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    /**
     * Build the unified Executive Revenue Overview for a tenant.
     * Routes through {@link RevenueDataLoader#loadInNewTransaction(UUID)}
     * (REQUIRES_NEW) so failures do not pollute the caller's transaction.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExecutiveRevenueOverview(UUID tenantId) {
        RevenueData revenue = dataLoader.loadInNewTransaction(tenantId);
        Map<String, Object> result = new LinkedHashMap<>(revenue.rawData);
        result.put("crmWonRevenue", revenue.crmWonRevenue);
        result.put("crmPipelineValue", revenue.crmPipelineValue);
        result.put("invoiceTotalValue", revenue.invoiceTotalValue);
        result.put("collectedRevenue", revenue.collectedRevenue);
        result.put("outstandingAmount", revenue.outstandingAmount);
        result.put("paymentStatusSummary", revenue.paymentStatus);
        result.put("revenueVariance", revenue.crmWonRevenue.subtract(revenue.collectedRevenue));
        result.put("sourceModules", revenue.sourceModules);
        result.put("generatedAt", java.time.Instant.now().toString());
        return result;
    }

    /** Composite record loaded in a separate transaction. */
    public record RevenueData(
            BigDecimal crmWonRevenue,
            BigDecimal crmPipelineValue,
            BigDecimal invoiceTotalValue,
            BigDecimal collectedRevenue,
            BigDecimal outstandingAmount,
            Map<String, Object> paymentStatus,
            List<String> sourceModules,
            Map<String, Object> rawData
    ) {}
}
