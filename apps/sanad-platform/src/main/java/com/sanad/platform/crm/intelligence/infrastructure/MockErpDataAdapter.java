package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import com.sanad.platform.crm.intelligence.domain.ErpDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Mock ERP adapter — returns deterministic synthetic data based on accountId hash.
 * Active when provider=mock (default). Disabled in production by IntelligenceProductionGuard.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.erp.provider", havingValue = "mock", matchIfMissing = true)
public class MockErpDataAdapter implements ErpDataPort {

    @Override
    public ErpCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId) {
        int hash = Math.abs(accountId.hashCode());
        double revenue = 50000 + (hash % 300000);
        int orders = 10 + (hash % 50);
        double balance = (hash % 30000);
        String paymentStatus = (hash % 5 == 0) ? "OVERDUE" : "CURRENT";
        String creditStatus = (hash % 4 == 0) ? "REVIEW" : "GOOD_STANDING";
        Instant lastOrder = Instant.now().minus(hash % 30, ChronoUnit.DAYS);
        return new ErpCustomerSnapshot(accountId, revenue, orders, balance,
                paymentStatus, creditStatus, lastOrder, true);
    }
}
