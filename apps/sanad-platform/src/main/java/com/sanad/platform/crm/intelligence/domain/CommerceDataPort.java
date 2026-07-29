package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for Commerce (e-commerce) data.
 */
public interface CommerceDataPort {
    CommerceSnapshot loadSnapshot(UUID tenantId, UUID accountId);

    record CommerceSnapshot(
            UUID accountId, int orderCount90d, double avgOrderValue,
            String preferredChannel, double cartAbandonmentRate,
            List<String> productCategories, Instant lastPurchaseAt,
            boolean available) {

        public static CommerceSnapshot unavailable(UUID accountId) {
            return new CommerceSnapshot(accountId, 0, 0,
                    "UNKNOWN", 0, List.of(), null, false);
        }
    }
}
