package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider-neutral port for ERP data.
 * Returns UNAVAILABLE snapshot when not configured.
 */
public interface ErpDataPort {
    ErpCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId);

    record ErpCustomerSnapshot(
            UUID accountId, double totalRevenue, int orderCount,
            double outstandingBalance, String paymentStatus,
            String creditStatus, Instant lastOrderAt,
            boolean available) {

        public static ErpCustomerSnapshot unavailable(UUID accountId) {
            return new ErpCustomerSnapshot(accountId, 0, 0, 0,
                    "UNKNOWN", "UNKNOWN", null, false);
        }
    }
}
