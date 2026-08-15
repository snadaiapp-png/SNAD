package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Isolated loader for CRM + Finance revenue data (v20260815.9).
 *
 * <p>Each method runs in its own {@link Propagation#REQUIRES_NEW} transaction
 * via the Spring AOP proxy (self-injected with @Lazy). This means:
 * <ul>
 *   <li>If CRM throws (e.g. SQL error), the CRM transaction is rolled back
 *       but the Finance transaction and the caller's transaction are
 *       unaffected.</li>
 *   <li>The methods do NOT catch exceptions internally — they let exceptions
 *       propagate so Spring can properly roll back the REQUIRES_NEW
 *       transaction. The caller ({@link RevenueOversightService}) catches
 *       the exception and substitutes an UNAVAILABLE marker.</li>
 * </ul>
 *
 * <p>CRITICAL: Do NOT add try/catch inside these methods. If you catch
 * an exception inside a @Transactional(REQUIRES_NEW) method, Spring
 * will still mark the transaction as rollback-only (because PostgreSQL
 * aborted it), and the commit phase will throw UnexpectedRollbackException.
 */
@Service
public class RevenueOversightLoader {

    private final CrmManagementIntegrationService crmService;
    private final FinanceManagementIntegrationService financeService;

    public RevenueOversightLoader(
            @Lazy CrmManagementIntegrationService crmService,
            @Lazy FinanceManagementIntegrationService financeService) {
        this.crmService = crmService;
        this.financeService = financeService;
    }

    /**
     * Load CRM overview in an isolated REQUIRES_NEW transaction.
     * If the CRM query fails, the exception propagates (NOT caught here).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> loadCrm(UUID tenantId) {
        return crmService.getCrmOverview(tenantId);
    }

    /**
     * Load Finance overview in an isolated REQUIRES_NEW transaction.
     * If the Finance query fails, the exception propagates (NOT caught here).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> loadFinance(UUID tenantId) {
        return financeService.getOverview(tenantId);
    }
}
