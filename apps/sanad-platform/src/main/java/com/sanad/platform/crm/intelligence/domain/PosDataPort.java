package com.sanad.platform.crm.intelligence.domain;

import java.util.UUID;

/**
 * Provider-neutral port for POS data.
 */
public interface PosDataPort {
    PosCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId);

    record PosCustomerSnapshot(
            UUID accountId, int transactionCount30d, double avgTransactionValue,
            String preferredStore, double loyaltyPointsBalance,
            boolean available) {

        public static PosCustomerSnapshot unavailable(UUID accountId) {
            return new PosCustomerSnapshot(accountId, 0, 0,
                    "UNKNOWN", 0, false);
        }
    }
}
