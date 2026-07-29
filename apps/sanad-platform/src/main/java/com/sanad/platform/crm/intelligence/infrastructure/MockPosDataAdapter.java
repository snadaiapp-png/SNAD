package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.PosDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock POS adapter — returns deterministic synthetic transaction data.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.pos.provider", havingValue = "mock", matchIfMissing = true)
public class MockPosDataAdapter implements PosDataPort {

    @Override
    public PosCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId) {
        int hash = Math.abs(accountId.hashCode());
        int txCount = hash % 30;
        double avgValue = 100 + (hash % 800);
        String[] stores = {"RIYADH_001", "JEDDAH_002", "DAMMAM_003", "ONLINE"};
        double loyalty = (hash % 5000);
        return new PosCustomerSnapshot(
                accountId, txCount, avgValue,
                stores[hash % stores.length], loyalty, true
        );
    }
}
