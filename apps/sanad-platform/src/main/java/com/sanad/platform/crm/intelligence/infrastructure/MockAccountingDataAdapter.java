package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.AccountingDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock Accounting adapter — returns deterministic synthetic financial data.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.accounting.provider", havingValue = "mock", matchIfMissing = true)
public class MockAccountingDataAdapter implements AccountingDataPort {

    @Override
    public AccountingSnapshot loadSnapshot(UUID tenantId, UUID accountId) {
        int hash = Math.abs(accountId.hashCode());
        double receivable = hash % 50000;
        double payable = hash % 20000;
        int dso = 20 + (hash % 40);
        String[] ratings = {"A", "B", "C", "A+"};
        double revenueYtd = 50000 + (hash % 200000);
        double margin = 0.20 + (hash % 30) / 100.0;
        return new AccountingSnapshot(
                accountId, receivable, payable, dso,
                ratings[hash % ratings.length], revenueYtd, margin, true
        );
    }
}
