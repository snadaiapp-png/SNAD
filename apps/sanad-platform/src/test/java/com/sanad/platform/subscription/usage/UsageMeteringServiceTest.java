package com.sanad.platform.subscription.usage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for usage metering: idempotent ingestion and the
 * usage-vs-entitlement read model with limit kinds and warning thresholds.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsageMeteringService — idempotent ingestion + read model")
class UsageMeteringServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private TenantRlsTransactionContext tenantRlsContext;

    private UsageMeteringService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new UsageMeteringService(jdbc, tenantRlsContext);
    }

    @Test
    @DisplayName("ingest: records the event and upserts the monthly aggregate")
    void ingestWritesEventAndAggregate() {
        UsageMeteringService.IngestResult result = service.ingest(
                TENANT_ID, "ai_tokens", 1500L, "workflow-runner", "job-42",
                Instant.parse("2026-08-29T10:00:00Z"));

        assertThat(result.duplicate()).isFalse();
        verify(jdbc).update(contains("INSERT INTO usage_events"), any(), eq(TENANT_ID),
                eq("ai_tokens"), eq(1500L), eq("workflow-runner"), eq("job-42"), any());
        verify(jdbc).update(contains("INSERT INTO usage_aggregates"), any(), eq(TENANT_ID),
                eq("ai_tokens"), any(), any());
    }

    @Test
    @DisplayName("ingest: duplicate idempotency key is a no-op (tenant-scoped)")
    void ingestIsIdempotent() {
        when(jdbc.update(contains("INSERT INTO usage_events"), any(), any(), any(), any(),
                any(), any(), any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        UsageMeteringService.IngestResult result = service.ingest(
                TENANT_ID, "ai_tokens", 1500L, "workflow-runner", "job-42",
                Instant.parse("2026-08-29T10:00:00Z"));

        assertThat(result.duplicate()).isTrue();
        verify(jdbc, never()).update(contains("INSERT INTO usage_aggregates"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ingest: scopes the transaction to the tenant (FORCE RLS contract)")
    void ingestAppliesTenantRlsScope() {
        service.ingest(TENANT_ID, "ai_tokens", 10L, "src", "key-1", Instant.now());
        verify(tenantRlsContext).applyForCurrentTransaction(TENANT_ID);
    }

    @Test
    @DisplayName("ingest: rejects negative quantity")
    void ingestRejectsNegative() {
        assertThatThrownBy(() -> service.ingest(
                TENANT_ID, "ai_tokens", -5L, "src", "key", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("usage read model: HARD_LIMIT at 76% triggers the warning threshold")
    void usageReadModelWarns() {
        when(jdbc.queryForList(
                contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of(
                        "metric_code", "ai_tokens",
                        "total", 3_800_000L,
                        "period_start", java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")))));
        when(jdbc.queryForList(contains("FROM usage_metrics")))
                .thenReturn(List.of(Map.of("code", "ai_tokens", "limit_kind", "HARD_LIMIT")));
        when(jdbc.queryForList(contains("GROUP BY pe.capability_code"), any(Object[].class)))
                .thenReturn(List.of(Map.of("capability_code", "USAGE.AI_TOKENS", "max_limit", 5_000_000L)));

        Optional<UsageMeteringService.UsageSnapshot> snapshot =
                service.usageSnapshot(TENANT_ID, "ai_tokens");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().current()).isEqualTo(3_800_000L);
        assertThat(snapshot.get().limit()).isEqualTo(5_000_000L);
        assertThat(snapshot.get().percent()).isEqualTo(76);
        assertThat(snapshot.get().limitKind()).isEqualTo("HARD_LIMIT");
        assertThat(snapshot.get().warning()).isTrue();
    }

    @Test
    @DisplayName("usage read model: UNLIMITED never warns")
    void unlimitedNeverWarns() {
        when(jdbc.queryForList(
                contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of(
                        "metric_code", "users",
                        "total", 42L,
                        "period_start", java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")))));
        when(jdbc.queryForList(contains("FROM usage_metrics")))
                .thenReturn(List.of(Map.of("code", "users", "limit_kind", "UNLIMITED")));
        when(jdbc.queryForList(contains("GROUP BY pe.capability_code"), any(Object[].class)))
                .thenReturn(List.of());

        Optional<UsageMeteringService.UsageSnapshot> snapshot =
                service.usageSnapshot(TENANT_ID, "users");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().limitKind()).isEqualTo("UNLIMITED");
        assertThat(snapshot.get().warning()).isFalse();
    }

    @Test
    @DisplayName("usage read model: SQL is pinned to the current UTC month, never latest historical month")
    void usageReadModelUsesCurrentUtcMonth() {
        when(jdbc.queryForList(
                contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of());

        assertThat(service.usageSnapshots(TENANT_ID)).isEmpty();

        verify(jdbc).queryForList(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("date_trunc('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')")
                                && !sql.contains("MAX(i.period_start)")),
                eq(TENANT_ID));
    }

    @Test
    @DisplayName("usage read model: no aggregates yields empty, not fabricated zero")
    void missingMetricIsEmpty() {
        when(jdbc.queryForList(contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of());

        assertThat(service.usageSnapshot(TENANT_ID, "storage_gb")).isEmpty();
    }

    // ------------------------------------------------------------------
    // R0C-12 G4-R1 / G6-R6: all five limit kinds proven; 0.9 critical
    // threshold wired; monthly period exposed for the usage read model.
    // ------------------------------------------------------------------

    private void stubAggregate(long total) {
        when(jdbc.queryForList(contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of(Map.of(
                        "metric_code", "ai_tokens",
                        "total", total,
                        "period_start", java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")))));
    }

    private void stubLimit(Long limit) {
        when(jdbc.queryForList(contains("GROUP BY pe.capability_code"), any(Object[].class)))
                .thenReturn(limit == null
                        ? List.of()
                        : List.of(Map.of("capability_code", "USAGE.AI_TOKENS", "max_limit", limit)));
    }

    private void stubKind(String kind) {
        when(jdbc.queryForList(contains("FROM usage_metrics")))
                .thenReturn(List.of(Map.of("code", "ai_tokens", "limit_kind", kind)));
    }

    @Test
    @DisplayName("usage read model: snapshot carries the MONTHLY period of the aggregate")
    void snapshotCarriesMonthlyPeriod() {
        stubAggregate(1_000L);
        stubLimit(5_000_000L);
        stubKind("HARD_LIMIT");

        Optional<UsageMeteringService.UsageSnapshot> snapshot =
                service.usageSnapshot(TENANT_ID, "ai_tokens");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().periodStart())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("usage read model: 90%+ HARD_LIMIT crosses the critical threshold")
    void criticalAtNinetyPercent() {
        stubAggregate(4_600_000L);
        stubLimit(5_000_000L);
        stubKind("HARD_LIMIT");

        UsageMeteringService.UsageSnapshot snapshot =
                service.usageSnapshot(TENANT_ID, "ai_tokens").orElseThrow();

        assertThat(snapshot.percent()).isEqualTo(92);
        assertThat(snapshot.warning()).isTrue();
        assertThat(snapshot.critical()).isTrue();
    }

    @Test
    @DisplayName("usage read model: warning band (75–89%) is not critical")
    void warningBandIsNotCritical() {
        stubAggregate(3_800_000L);
        stubLimit(5_000_000L);
        stubKind("HARD_LIMIT");

        UsageMeteringService.UsageSnapshot snapshot =
                service.usageSnapshot(TENANT_ID, "ai_tokens").orElseThrow();

        assertThat(snapshot.percent()).isEqualTo(76);
        assertThat(snapshot.warning()).isTrue();
        assertThat(snapshot.critical()).isFalse();
    }

    @Test
    @DisplayName("usage read model: SOFT_LIMIT warns on the same thresholds")
    void softLimitWarns() {
        stubAggregate(4_000_000L);
        stubLimit(5_000_000L);
        stubKind("SOFT_LIMIT");

        UsageMeteringService.UsageSnapshot snapshot =
                service.usageSnapshot(TENANT_ID, "ai_tokens").orElseThrow();

        assertThat(snapshot.limitKind()).isEqualTo("SOFT_LIMIT");
        assertThat(snapshot.warning()).isTrue();
        assertThat(snapshot.critical()).isFalse();
    }

    @Test
    @DisplayName("usage read model: OVERAGE and PAY_AS_YOU_GO are billed kinds — never threshold-warn")
    void billedKindsNeverThresholdWarn() {
        for (String kind : List.of("OVERAGE", "PAY_AS_YOU_GO")) {
            stubAggregate(4_900_000L);
            stubLimit(5_000_000L);
            stubKind(kind);

            UsageMeteringService.UsageSnapshot snapshot =
                    service.usageSnapshot(TENANT_ID, "ai_tokens").orElseThrow();

            assertThat(snapshot.limitKind()).isEqualTo(kind);
            assertThat(snapshot.warning()).as("warning for " + kind).isFalse();
            assertThat(snapshot.critical()).as("critical for " + kind).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // R0C-12 G5-R3: batched tenant usage read model — fixed query count,
    // never 1 + 3N (N = metric count).
    // ------------------------------------------------------------------

    private List<Map<String, Object>> aggregateRows(int metricCount) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < metricCount; i++) {
            rows.add(Map.of(
                    "metric_code", "metric_" + i,
                    "total", (long) (i + 1) * 100,
                    "period_start", java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z"))));
        }
        return rows;
    }

    @Test
    @DisplayName("usageSnapshots: fixed query budget regardless of metric count (no N+1)")
    void usageSnapshotsBatchesQueries() {
        int metricCount = 8;
        when(jdbc.queryForList(contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(aggregateRows(metricCount));
        when(jdbc.queryForList(contains("FROM usage_metrics")))
                .thenReturn(List.of(
                        Map.of("code", "metric_0", "limit_kind", "HARD_LIMIT"),
                        Map.of("code", "metric_1", "limit_kind", "SOFT_LIMIT"),
                        Map.of("code", "metric_2", "limit_kind", "UNLIMITED"),
                        Map.of("code", "metric_3", "limit_kind", "OVERAGE"),
                        Map.of("code", "metric_4", "limit_kind", "PAY_AS_YOU_GO"),
                        Map.of("code", "metric_5", "limit_kind", "HARD_LIMIT"),
                        Map.of("code", "metric_6", "limit_kind", "HARD_LIMIT"),
                        Map.of("code", "metric_7", "limit_kind", "HARD_LIMIT")));
        when(jdbc.queryForList(contains("GROUP BY pe.capability_code"), any(Object[].class)))
                .thenReturn(List.of(Map.of("capability_code", "USAGE.METRIC_0", "max_limit", 1_000L)));

        List<UsageMeteringService.UsageSnapshot> snapshots = service.usageSnapshots(TENANT_ID);

        assertThat(snapshots).hasSize(metricCount);
        assertThat(snapshots.get(0).metricCode()).isEqualTo("metric_0");
        assertThat(snapshots.get(0).limit()).isEqualTo(1_000L);
        assertThat(snapshots.get(0).limitKind()).isEqualTo("HARD_LIMIT");
        assertThat(snapshots.get(0).periodStart()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(snapshots.get(2).limit()).isNull();
        assertThat(snapshots.get(2).limitKind()).isEqualTo("UNLIMITED");

        long jdbcCalls = org.mockito.Mockito.mockingDetails(jdbc).getInvocations().size();
        assertThat(jdbcCalls).as("jdbc interactions must stay bounded (metrics + aggregates + limits)")
                .isLessThanOrEqualTo(4);
        // FORCE-RLS contract: the batched read scopes the transaction to the tenant
        verify(tenantRlsContext).applyForCurrentTransaction(TENANT_ID);
    }

    @Test
    @DisplayName("usageSnapshots: tenants without entitlement limits resolve to null limits, never 500")
    void usageSnapshotsNoLimitsResolveSoftly() {
        when(jdbc.queryForList(contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(aggregateRows(2));
        when(jdbc.queryForList(contains("FROM usage_metrics")))
                .thenReturn(List.of(
                        Map.of("code", "metric_0", "limit_kind", "HARD_LIMIT"),
                        Map.of("code", "metric_1", "limit_kind", "SOFT_LIMIT")));
        when(jdbc.queryForList(contains("GROUP BY pe.capability_code"), any(Object[].class)))
                .thenReturn(List.of());

        List<UsageMeteringService.UsageSnapshot> snapshots = service.usageSnapshots(TENANT_ID);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).limit()).isNull();
        assertThat(snapshots.get(0).percent()).isNull();
        assertThat(snapshots.get(0).warning()).isFalse();
        assertThat(snapshots.get(0).critical()).isFalse();
    }

    @Test
    @DisplayName("usageSnapshots: empty aggregate history yields empty list (parity with legacy read model)")
    void usageSnapshotsEmptyHistory() {
        when(jdbc.queryForList(contains("FROM usage_aggregates u"), eq(TENANT_ID)))
                .thenReturn(List.of());

        assertThat(service.usageSnapshots(TENANT_ID)).isEmpty();
    }
}
