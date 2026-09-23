package com.sanad.platform.subscription.read;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionGridRecurringAmountContractTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void annualSubscriptionSeparatesRecurringAmountFromMonthlyEquivalent() {
        var service = new SubscriptionGridQueryService(jdbc);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenant_subscriptions"),
                eq(Long.class), any(Object[].class))).thenReturn(1L);

        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.fromString("10000000-0000-0000-0000-000000000001"));
        row.put("tenant_id", UUID.fromString("20000000-0000-0000-0000-000000000001"));
        row.put("tenant_name", "Annual Tenant");
        row.put("country_code", "SA");
        row.put("status", "ACTIVE");
        row.put("billing_cycle", "ANNUAL");
        row.put("seat_quantity", 2);
        row.put("plan_id", UUID.fromString("30000000-0000-0000-0000-000000000001"));
        row.put("plan_name", "Growth");
        row.put("plan_code", "GROWTH");
        row.put("plan_version", 1);
        row.put("currency_code", "SAR");
        row.put("recurring_amount_minor", 240_000L);
        row.put("monthly_equivalent_minor", 20_000L);
        row.put("item_count", 1);
        row.put("trial", false);
        row.put("cancel_at_period_end", false);
        row.put("current_period_end", Timestamp.from(Instant.parse("2027-09-23T00:00:00Z")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        var result = service.search(null, "ACTIVE", null, null, false, 0, 20, null, null);
        var subscription = result.content().get(0);

        assertThat(subscription.billingCycle()).isEqualTo("ANNUAL");
        assertThat(subscription.recurringAmountMinor()).isEqualTo(240_000L);
        assertThat(subscription.monthlyEquivalentMinor()).isEqualTo(20_000L);
    }
}
