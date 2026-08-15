package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executive Revenue Oversight (GAP 19) — REAL data, no placeholders.
 *
 * <p>Aggregates revenue data from CRM (won opportunity revenue + pipeline value)
 * and Finance (invoiced, collected, outstanding amounts) into a single typed
 * overview without duplicating business logic from either module.
 *
 * <p>ARCHITECTURE (v20260815.9):
 * <pre>
 *   RevenueOversightService.getExecutiveRevenueOverview(tenantId)   [no @Transactional]
 *       │
 *       ├── RevenueOversightLoader.loadCrm(tenantId)    [@Transactional(REQUIRES_NEW)]
 *       │     → calls CrmManagementIntegrationService.getCrmOverview
 *       │     → if exception: propagates (NOT caught inside the REQUIRES_NEW method)
 *       │
 *       └── RevenueOversightLoader.loadFinance(tenantId) [@Transactional(REQUIRES_NEW)]
 *             → calls FinanceManagementIntegrationService.getOverview
 *             → if exception: propagates
 * </pre>
 *
 * <p>Each loader method runs in its own REQUIRES_NEW transaction via the Spring
 * AOP proxy (self-injected with @Lazy). If CRM throws (e.g. a SQL error), the
 * CRM transaction is rolled back — but the Finance transaction and the caller's
 * transaction are unaffected. The caller catches the exception and substitutes
 * an UNAVAILABLE marker.
 *
 * <p>CRITICAL: The @Transactional(REQUIRES_NEW) methods MUST NOT catch exceptions
 * internally. If they do, Spring will still mark the transaction as rollback-only
 * (because PostgreSQL aborted it), and the commit phase will throw
 * UnexpectedRollbackException — which would then propagate to the caller's
 * transaction and poison it.
 */
@Service
public class RevenueOversightService {

    private final RevenueOversightLoader loader;

    public RevenueOversightService(@Lazy RevenueOversightLoader loader) {
        this.loader = loader;
    }

    /**
     * Build the unified Executive Revenue Overview for a tenant.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code crmWonRevenue} — CRM opportunity won revenue (sum of amount for WON)</li>
     *   <li>{@code crmPipelineValue} — CRM open pipeline value (sum of amount for non-WON/LOST/CLOSED)</li>
     *   <li>{@code invoiceTotalValue} — Finance total invoiced amount</li>
     *   <li>{@code collectedRevenue} — Finance collected (paid) revenue</li>
     *   <li>{@code outstandingAmount} — Finance outstanding amount</li>
     *   <li>{@code paymentStatusSummary} — counts of payment statuses</li>
     *   <li>{@code invoiceCount} — total invoice count</li>
     *   <li>{@code paymentCount} — total payment count</li>
     *   <li>{@code revenueVariance} — difference between CRM won + Finance collected</li>
     *   <li>{@code sourceModules} — list of modules that contributed data</li>
     *   <li>{@code generatedAt} — timestamp of this aggregation</li>
     * </ul>
     */
    public Map<String, Object> getExecutiveRevenueOverview(UUID tenantId) {
        // Load CRM data in an isolated transaction
        Map<String, Object> crmData;
        boolean crmAvailable;
        try {
            crmData = loader.loadCrm(tenantId);
            crmAvailable = true;
        } catch (Exception e) {
            crmData = Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
            crmAvailable = false;
        }

        // Load Finance data in an isolated transaction
        Map<String, Object> financeData;
        boolean financeAvailable;
        try {
            financeData = loader.loadFinance(tenantId);
            financeAvailable = true;
        } catch (Exception e) {
            financeData = Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
            financeAvailable = false;
        }

        // Assemble the unified result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("crmWonRevenue", toBigDecimal(crmData.get("wonRevenue")));
        result.put("crmPipelineValue", toBigDecimal(crmData.get("estimatedPipelineValue")));
        result.put("invoiceTotalValue", toBigDecimal(financeData.get("invoiceTotalValue")));
        result.put("financeBilledAmount", toBigDecimal(financeData.get("invoiceTotalValue")));
        result.put("financePaidAmount", toBigDecimal(financeData.get("collectedRevenue")));
        result.put("financeOutstandingAmount", toBigDecimal(financeData.get("outstandingAmount")));
        result.put("collectedRevenue", toBigDecimal(financeData.get("collectedRevenue")));
        result.put("outstandingAmount", toBigDecimal(financeData.get("outstandingAmount")));
        result.put("paymentStatusSummary", financeData.getOrDefault("paymentStatusCounts", Map.of()));
        result.put("invoiceCount", toInt(financeData.get("totalInvoices")));
        result.put("paymentCount", toInt(financeData.get("totalPayments")));

        // Variance: difference between CRM won revenue and Finance collected revenue.
        BigDecimal crmWon = toBigDecimal(crmData.get("wonRevenue"));
        BigDecimal collected = toBigDecimal(financeData.get("collectedRevenue"));
        result.put("revenueVariance", crmWon.subtract(collected));

        // Source modules
        List<String> sources = new ArrayList<>();
        if (crmAvailable) sources.add("CRM");
        if (financeAvailable) sources.add("FINANCE");
        result.put("sourceModules", sources);

        // Include the raw overview data for consumers that need the full picture
        result.put("crmOverview", crmData);
        result.put("financeOverview", financeData);

        result.put("generatedAt", java.time.Instant.now().toString());
        return result;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
}
