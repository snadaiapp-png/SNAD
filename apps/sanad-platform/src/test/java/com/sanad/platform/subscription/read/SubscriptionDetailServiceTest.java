package com.sanad.platform.subscription.read;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * R0C-12 G5-R2 — the authoritative design (§8/§9) requires the subscription
 * detail read model to carry an entitlements section (plan-derived ∪
 * item-derived). The R0C-11 base assembled overview/items/invoices/changes/
 * provisioningJobs/audit only; this pins the additive entitlements contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionDetailService — detail entitlements section (R0C-12 G5-R2)")
class SubscriptionDetailServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private SubscriptionDetailService service;

    private static final UUID SUBSCRIPTION_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new SubscriptionDetailService(jdbc);
    }

    private void stubDetailQueries() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("id", SUBSCRIPTION_ID);
        overview.put("tenantId", TENANT_ID);
        overview.put("status", "ACTIVE");
        lenient().when(jdbc.queryForMap(contains("FROM tenant_subscriptions"), eq(SUBSCRIPTION_ID)))
                .thenReturn(overview);
        lenient().when(jdbc.queryForList(contains("FROM subscription_items"), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
        lenient().when(jdbc.queryForList(contains("FROM billing_invoices"), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
        lenient().when(jdbc.queryForList(contains("subscription_commands"), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
        lenient().when(jdbc.queryForList(contains("FROM provisioning_jobs"), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
        lenient().when(jdbc.queryForList(contains("FROM platform_audit_logs"), eq(SUBSCRIPTION_ID), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
        lenient().when(jdbc.query(contains("subscription_commands"),
                        org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String, Object>>>any(),
                        eq(SUBSCRIPTION_ID), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("detail carries a bounded, parameterized entitlements section")
    void detailCarriesEntitlementsSection() {
        stubDetailQueries();
        Map<String, Object> planRow = new HashMap<>();
        planRow.put("source", "PLAN");
        planRow.put("moduleCode", "hr");
        planRow.put("moduleName", "Human Resources");
        planRow.put("capabilityCode", "USAGE.EMPLOYEES");
        planRow.put("moduleEnabled", true);
        planRow.put("booleanValue", null);
        planRow.put("limitValue", 250L);
        Map<String, Object> productRow = new HashMap<>();
        productRow.put("source", "PRODUCT");
        productRow.put("moduleCode", "hr");
        productRow.put("moduleName", "Human Resources");
        productRow.put("capabilityCode", "hr.payroll.enabled");
        productRow.put("moduleEnabled", true);
        productRow.put("booleanValue", true);
        productRow.put("limitValue", null);
        when(jdbc.queryForList(contains("product_entitlements"), eq(SUBSCRIPTION_ID), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of(planRow, productRow));

        SubscriptionDetailService.SubscriptionDetail detail = service.detail(SUBSCRIPTION_ID);

        assertThat(detail.entitlements()).hasSize(2);
        assertThat(detail.entitlements().get(0).get("source")).isEqualTo("PLAN");
        assertThat(detail.entitlements().get(1).get("source")).isEqualTo("PRODUCT");
        assertThat(detail.entitlements().get(1).get("booleanValue")).isEqualTo(true);
    }

    @Test
    @DisplayName("entitlements section stays present (empty, never null) when no rows exist")
    void entitlementsSectionNeverNull() {
        stubDetailQueries();
        when(jdbc.queryForList(contains("product_entitlements"), eq(SUBSCRIPTION_ID), eq(SUBSCRIPTION_ID)))
                .thenReturn(List.of());

        SubscriptionDetailService.SubscriptionDetail detail = service.detail(SUBSCRIPTION_ID);

        assertThat(detail.entitlements()).isNotNull();
        assertThat(detail.entitlements()).isEmpty();
    }

    @Test
    @DisplayName("unknown subscription still fails closed")
    void unknownSubscriptionFailsClosed() {
        when(jdbc.queryForMap(anyString(), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.detail(SUBSCRIPTION_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
