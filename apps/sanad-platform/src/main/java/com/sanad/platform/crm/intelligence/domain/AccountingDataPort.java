package com.sanad.platform.crm.intelligence.domain;

import java.util.UUID;

/**
 * Provider-neutral port for Accounting data.
 */
public interface AccountingDataPort {
    AccountingSnapshot loadSnapshot(UUID tenantId, UUID accountId);

    record AccountingSnapshot(
            UUID accountId, double totalReceivable, double totalPayable,
            int daysSalesOutstanding, String creditRating,
            double revenueYtd, double grossMargin,
            boolean available) {

        public static AccountingSnapshot unavailable(UUID accountId) {
            return new AccountingSnapshot(accountId, 0, 0,
                    0, "UNKNOWN", 0, 0, false);
        }
    }
}
