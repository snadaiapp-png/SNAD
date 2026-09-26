package com.sanad.platform.subscription.read;

import com.sanad.platform.subscription.commercial.TenantCommercialStateService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantDirectoryCommercialStateTest {

    @Test
    void directoryExposesBackendDerivedLoginAndCommercialAction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantCommercialStateService commercial = mock(TenantCommercialStateService.class);
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID subscriptionId = UUID.fromString("20000000-0000-0000-0000-000000000001");

        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenants"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", tenantId,
                "name", "Acme",
                "code", "acme",
                "status", "ACTIVE",
                "country_code", "SA",
                "currency_code", "SAR",
                "subscription_count", 1L,
                "subscription_status", "ACTIVE",
                "created_at", Timestamp.from(Instant.parse("2026-09-23T00:00:00Z"))
        )));
        when(commercial.resolve(tenantId, "ACTIVE")).thenReturn(
                new TenantCommercialStateService.TenantCommercialState(
                        tenantId, "ACTIVE", subscriptionId, "ACTIVE", "CURRENT",
                        TenantCommercialStateService.AccessDecision.ACCESS_ALLOWED,
                        TenantCommercialStateService.CommercialAction.UPGRADE,
                        null, true));

        var page = new TenantDirectoryQueryService(jdbc, commercial)
                .search(null, null, null, 0, 20, "name", "ASC");

        var row = page.content().get(0);
        assertThat(row.effectiveSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(row.accessDecision()).isEqualTo("ACCESS_ALLOWED");
        assertThat(row.commercialAction()).isEqualTo("UPGRADE");
        assertThat(row.loginAllowed()).isTrue();
    }
}
