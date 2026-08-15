package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loads revenue data from CRM + Finance in a REQUIRES_NEW transaction
 * so PSQLException aborts do not pollute the caller's transaction.
 *
 * <p>Used by {@link RevenueOversightService} via Spring proxy (the
 * {@code @Lazy} self-injection pattern).
 */
@Service
public class RevenueDataLoader {

    private final CrmManagementIntegrationService crmService;
    private final FinanceManagementIntegrationService financeService;

    public RevenueDataLoader(
            @Lazy CrmManagementIntegrationService crmService,
            @Lazy FinanceManagementIntegrationService financeService) {
        this.crmService = crmService;
        this.financeService = financeService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RevenueOversightService.RevenueData loadInNewTransaction(UUID tenantId) {
        BigDecimal crmWonRevenue = BigDecimal.ZERO;
        BigDecimal crmPipelineValue = BigDecimal.ZERO;
        boolean crmAvailable = false;
        Map<String, Object> rawData = new LinkedHashMap<>();

        try {
            Map<String, Object> crm = crmService.getCrmOverview(tenantId);
            crmAvailable = true;
            rawData.putAll(crm);
            crmWonRevenue = toBigDecimal(crm.get("wonRevenue"));
            crmPipelineValue = toBigDecimal(crm.get("estimatedPipelineValue"));
        } catch (Exception e) {
            rawData.put("_crmError", e.getClass().getSimpleName());
        }

        BigDecimal invoiceTotal = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        Map<String, Object> paymentStatus = Map.of();
        boolean financeAvailable = false;
        try {
            Map<String, Object> fin = financeService.getOverview(tenantId);
            financeAvailable = true;
            rawData.putAll(fin);
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
            rawData.put("_financeError", e.getClass().getSimpleName());
        }

        java.util.List<String> sources = new ArrayList<>();
        if (crmAvailable) sources.add("CRM");
        if (financeAvailable) sources.add("FINANCE");

        return new RevenueOversightService.RevenueData(
                crmWonRevenue, crmPipelineValue, invoiceTotal, collected, outstanding,
                paymentStatus, sources, rawData
        );
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
