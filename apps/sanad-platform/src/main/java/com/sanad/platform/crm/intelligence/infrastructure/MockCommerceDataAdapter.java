package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.CommerceDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Mock Commerce (e-commerce) adapter — returns deterministic synthetic purchase data.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.commerce.provider", havingValue = "mock", matchIfMissing = true)
public class MockCommerceDataAdapter implements CommerceDataPort {

    @Override
    public CommerceSnapshot loadSnapshot(UUID tenantId, UUID accountId) {
        int hash = Math.abs(accountId.hashCode());
        int orderCount = hash % 20;
        double avgOrder = 200 + (hash % 2000);
        String[] channels = {"WEB", "MOBILE", "IN_STORE"};
        double abandonRate = (hash % 40) / 100.0;
        List<String> categories = List.of("ELECTRONICS", "ACCESSORIES", "SERVICES");
        Instant lastPurchase = Instant.now().minus(hash % 60, ChronoUnit.DAYS);
        return new CommerceSnapshot(
                accountId, orderCount, avgOrder,
                channels[hash % channels.length], abandonRate,
                categories, lastPurchase, true
        );
    }
}
