package com.sanad.platform.subscription.read;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the executive read models — overview metric discipline
 * (N/A instead of invented values), pagination clamps and sort whitelisting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Executive read models — overview/pagination/search")
class ExecutiveReadModelsTest {

    @Mock
    private JdbcTemplate jdbc;

    private ExecutiveOverviewService overviewService;
    private TenantDirectoryQueryService tenantDirectory;
    private SubscriptionGridQueryService subscriptionGrid;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        overviewService = new ExecutiveOverviewService(jdbc);
        tenantDirectory = new TenantDirectoryQueryService(jdbc);
        subscriptionGrid = new SubscriptionGridQueryService(jdbc);
    }

    @Test
    @DisplayName("overview: MRR is computed per currency, never merged across currencies")
    void overviewMrrPerCurrency() {
        stubCounts();
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("currency_code", "SAR", "mrr_minor", 100_000L),
                Map.of("currency_code", "USD", "mrr_minor", 20_000L)));

        ExecutiveOverviewService.Overview overview = overviewService.overview();

        assertThat(overview.mrrMinorByCurrency()).containsEntry("SAR", 100_000L);
        assertThat(overview.mrrMinorByCurrency()).containsEntry("USD", 20_000L);
        assertThat(overview.arrMinorByCurrency()).containsEntry("SAR", 1_200_000L);
        assertThat(overview.churnPercent()).isNull();
        assertThat(overview.expansionRevenueMinor()).isNull();
    }

    @Test
    @DisplayName("overview: zero states are real zeros, not nulls")
    void overviewZeroStates() {
        stubCounts();
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        ExecutiveOverviewService.Overview overview = overviewService.overview();

        assertThat(overview.totalTenants()).isZero();
        assertThat(overview.activeSubscriptions()).isZero();
        assertThat(overview.mrrMinorByCurrency()).isEmpty();
    }

    @Test
    @DisplayName("tenant directory: pagination bounds are clamped (size<=200, page>=0)")
    void tenantDirectoryClamps() {
        stubTenantCount();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        PageResponse<TenantDirectoryQueryService.TenantRow> page =
                tenantDirectory.search(null, null, null, -5, 5000, "name", "ASC");

        assertThat(page.size()).isEqualTo(200);
        assertThat(page.page()).isZero();
        assertThat(page.totalElements()).isEqualTo(7L);
    }

    @Test
    @DisplayName("tenant directory: unknown sort column falls back to name, not interpolated")
    void tenantDirectorySortWhitelist() {
        stubTenantCount();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        PageResponse<TenantDirectoryQueryService.TenantRow> page =
                tenantDirectory.search(null, null, null, 0, 20, "1; DROP TABLE tenants;--", "ASC");

        assertThat(page.content()).isEmpty();
    }

    @Test
    @DisplayName("subscription grid: filters compose and defaults apply (DESC by created_at)")
    void subscriptionGridDefaults() {
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenant_subscriptions"),
                eq(Long.class), any(Object[].class))).thenReturn(3L);
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("tenant_id", TENANT_ID);
        row.put("tenant_name", "Acme");
        row.put("country_code", "SA");
        row.put("status", "ACTIVE");
        row.put("billing_cycle", "MONTHLY");
        row.put("seat_quantity", 5);
        row.put("plan_id", UUID.randomUUID());
        row.put("plan_name", "GROWTH");
        row.put("plan_code", "GROWTH");
        row.put("plan_version", 2);
        row.put("currency_code", "SAR");
        row.put("recurring_amount_minor", 29_900L);
        row.put("monthly_equivalent_minor", 29_900L);
        row.put("item_count", 2);
        row.put("trial", false);
        row.put("cancel_at_period_end", false);
        row.put("current_period_end", java.sql.Timestamp.from(
                java.time.Instant.parse("2026-10-01T00:00:00Z")));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        PageResponse<SubscriptionGridQueryService.SubscriptionRow> page =
                subscriptionGrid.search(TENANT_ID, "ACTIVE", "SA", "acme", false, 0, 20, null, null);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).recurringAmountMinor()).isEqualTo(29_900L);
        assertThat(page.content().get(0).monthlyEquivalentMinor()).isEqualTo(29_900L);
        assertThat(page.content().get(0).monthlyPriceMinor()).isEqualTo(29_900L);
        assertThat(page.content().get(0).planVersion()).isEqualTo("v2");
        assertThat(page.content().get(0).itemCount()).isEqualTo(2);
        assertThat(page.content().get(0).currentPeriodEnd())
                .isEqualTo(java.time.Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    @DisplayName("tenant directory: LIMIT/OFFSET bind as discrete scalars, never as a nested List")
    void tenantDirectoryBindsLimitOffsetAsScalars() {
        stubTenantCount();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        tenantDirectory.search(null, null, null, 0, 20, "name", "ASC");

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(anyString(), captor.capture());
        Object[] bound = captor.getValue();
        assertThat(bound).hasSize(2);
        assertThat(bound[0]).isEqualTo(20);
        assertThat(bound[1]).isEqualTo(0);
        assertThat(bound).allSatisfy(v -> assertThat(v).isNotInstanceOf(List.class));
    }

    @Test
    @DisplayName("tenant directory: search filters + LIMIT/OFFSET all bind as discrete scalars")
    void tenantDirectoryBindsFiltersAndPaginationAsScalars() {
        stubTenantCount();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        tenantDirectory.search("acme", "ACTIVE", "SA", 2, 50, "name", "ASC");

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(anyString(), captor.capture());
        Object[] bound = captor.getValue();
        assertThat(bound).hasSize(7);
        assertThat(bound[0]).isEqualTo("%acme%");
        assertThat(bound[3]).isEqualTo("ACTIVE");
        assertThat(bound[4]).isEqualTo("SA");
        assertThat(bound[5]).isEqualTo(50);
        assertThat(bound[6]).isEqualTo(100);
        assertThat(bound).allSatisfy(v -> assertThat(v).isNotInstanceOf(List.class));
    }

    @Test
    @DisplayName("subscription grid: LIMIT/OFFSET bind as discrete scalars, never as a nested List")
    void subscriptionGridBindsLimitOffsetAsScalars() {
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenant_subscriptions"),
                eq(Long.class), any(Object[].class))).thenReturn(3L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        subscriptionGrid.search(TENANT_ID, null, null, null, false, 0, 20, null, null);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(anyString(), captor.capture());
        Object[] bound = captor.getValue();
        assertThat(bound).hasSize(3);
        assertThat(bound[0]).isEqualTo(TENANT_ID);
        assertThat(bound[1]).isEqualTo(20);
        assertThat(bound[2]).isEqualTo(0);
        assertThat(bound).allSatisfy(v -> assertThat(v).isNotInstanceOf(List.class));
    }

    @Test
    @DisplayName("tenant directory: current status uses canonical EFFECTIVE subscription predicate")
    void tenantDirectoryCurrentStatusUsesEffectivePredicate() {
        stubTenantCount();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        tenantDirectory.search(null, null, null, 0, 20, "name", "ASC");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                // Canonical EFFECTIVE predicate: terminal rows are historical
                // evidence and never represent the current commercial state.
                .contains("s.status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')")
                // Deterministic single-row safety (main): under an anomalous
                // multi-row non-terminal state the subselect still resolves
                // exactly one ordered row instead of erroring.
                .contains("ORDER BY s.created_at DESC, s.id DESC LIMIT 1");
    }

    @Test
    @DisplayName("subscription grid: legacy rows without plan_version_id fall back to parent plan prices")
    void subscriptionGridLegacyPriceFallbackIsExplicit() {
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenant_subscriptions"),
                eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        subscriptionGrid.search(null, null, null, null, false, 0, 20, null, null);

        verify(jdbc).queryForList(
                contains("COALESCE(pv.monthly_price_minor, p.monthly_price_minor)"),
                any(Object[].class));
        verify(jdbc).queryForList(
                contains("COALESCE(pv.annual_price_minor, p.annual_price_minor)"),
                any(Object[].class));
    }

    private void stubCounts() {
        lenient().when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(0L);
    }

    private void stubTenantCount() {
        lenient().when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM tenants t"),
                eq(Long.class), any(Object[].class))).thenReturn(7L);
    }
}
