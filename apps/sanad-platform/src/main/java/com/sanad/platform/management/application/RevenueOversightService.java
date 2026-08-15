package com.sanad.platform.management.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Executive Revenue Oversight (GAP 19).
 *
 * <p>Aggregates revenue data from CRM (won opportunity revenue) and Finance
 * (invoiced, collected, outstanding amounts) into a single typed overview
 * without duplicating business logic from either module.
 *
 * <p>This service is READ-ONLY and tenant-scoped. It delegates to:
 * <ul>
 *   <li>{@link CrmManagementIntegrationService#getCrmOverview} for CRM revenue metrics.</li>
 *   <li>{@link FinanceManagementIntegrationService#getOverview} for Finance revenue metrics.</li>
 * </ul>
 *
 * <p>No new tables, no duplicate logic — pure aggregation. The result is
 * surfaced in the Executive Command Center dashboard and in the
 * Executive Report (GAP 25).
 */
@Service
public class RevenueOversightService {

    private final CrmManagementIntegrationService crmService;
    private final FinanceManagementIntegrationService financeService;

    public RevenueOversightService(
            CrmManagementIntegrationService crmService,
            FinanceManagementIntegrationService financeService) {
        this.crmService = crmService;
        this.financeService = financeService;
    }

    /**
     * Build the unified Executive Revenue Overview for a tenant.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code crmWonRevenue} — CRM opportunity won revenue (estimated)</li>
     *   <li>{@code crmPipelineValue} — CRM open pipeline value (estimated)</li>
     *   <li>{@code invoiceTotalValue} — Finance total invoiced amount</li>
     *   <li>{@code collectedRevenue} — Finance collected (paid) revenue</li>
     *   <li>{@code outstandingAmount} — Finance outstanding (open invoice) amount</li>
     *   <li>{@code paymentStatusSummary} — counts of PAID/OPEN/VOID invoices</li>
     *   <li>{@code revenueVariance} — difference between CRM won + Finance collected (informational)</li>
     *   <li>{@code sourceModules} — list of modules that contributed data</li>
     *   <li>{@code generatedAt} — timestamp of this aggregation</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExecutiveRevenueOverview(UUID tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // CRM revenue
        BigDecimal crmWonRevenue = BigDecimal.ZERO;
        BigDecimal crmPipelineValue = BigDecimal.ZERO;
        boolean crmAvailable = false;
        try {
            Map<String, Object> crm = crmService.getCrmOverview(tenantId);
            crmAvailable = true;
            Object won = crm.get("wonRevenue");
            if (won instanceof BigDecimal) crmWonRevenue = (BigDecimal) won;
            else if (won instanceof Number) crmWonRevenue = BigDecimal.valueOf(((Number) won).doubleValue());
            Object pipeline = crm.get("estimatedPipelineValue");
            if (pipeline instanceof BigDecimal) crmPipelineValue = (BigDecimal) pipeline;
            else if (pipeline instanceof Number) crmPipelineValue = BigDecimal.valueOf(((Number) pipeline).doubleValue());
        } catch (Exception e) {
            result.put("_crmError", e.getClass().getSimpleName());
        }

        // Finance revenue
        BigDecimal invoiceTotal = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        Map<String, Object> paymentStatus = Map.of();
        boolean financeAvailable = false;
        try {
            Map<String, Object> fin = financeService.getOverview(tenantId);
            financeAvailable = true;
            invoiceTotal = toBigDecimal(fin.get("invoiceTotalValue"));
            collected = toBigDecimal(fin.get("collectedRevenue"));
            outstanding = toBigDecimal(fin.get("outstandingAmount"));
            Object pss = fin.get("paymentStatusCounts");
            if (pss instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) pss;
                paymentStatus = cast;
            }
        } catch (Exception e) {
            result.put("_financeError", e.getClass().getSimpleName());
        }

        result.put("crmWonRevenue", crmWonRevenue);
        result.put("crmPipelineValue", crmPipelineValue);
        result.put("invoiceTotalValue", invoiceTotal);
        result.put("collectedRevenue", collected);
        result.put("outstandingAmount", outstanding);
        result.put("paymentStatusSummary", paymentStatus);

        // Variance: difference between CRM won revenue and Finance collected revenue.
        // Positive = CRM booked more than Finance collected (collection gap).
        // Negative = Finance collected more than CRM booked (could be data lag, advance payments, etc.)
        // This is informational only — not a KPI.
        BigDecimal variance = crmWonRevenue.subtract(collected);
        result.put("revenueVariance", variance);

        // Source modules that contributed data (for audit trail)
        java.util.List<String> sources = new java.util.ArrayList<>();
        if (crmAvailable) sources.add("CRM");
        if (financeAvailable) sources.add("FINANCE");
        result.put("sourceModules", sources);

        result.put("generatedAt", java.time.Instant.now().toString());
        return result;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
