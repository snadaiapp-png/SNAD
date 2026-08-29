package com.sanad.platform.subscription.usage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    private UsageMeteringService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new UsageMeteringService(jdbc);
    }

    @Test
    @DisplayName("ingest: records the event and upserts the monthly aggregate")
    void ingestWritesEventAndAggregate() {
        UsageMeteringService.IngestResult result = service.ingest(
                TENANT_ID, "ai_tokens", 1500L, "workflow-runner", "job-42",
                Instant.parse("2026-08-29T10:00:00Z"));

        assertThat(result.duplicate()).isFalse();
        verify(jdbc).update(contains("INSERT INTO usage_events"), any(), eq(TENANT_ID),
                eq("ai_tokens"), eq(1500L), eq("workflow-runner"), eq("job-42"), any(), any());
        verify(jdbc).update(contains("INSERT INTO usage_aggregates"), any(), eq(TENANT_ID),
                eq("ai_tokens"), any(), any(), any());
    }

    @Test
    @DisplayName("ingest: duplicate idempotency key is a no-op (tenant-scoped)")
    void ingestIsIdempotent() {
        when(jdbc.update(contains("INSERT INTO usage_events"), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        UsageMeteringService.IngestResult result = service.ingest(
                TENANT_ID, "ai_tokens", 1500L, "workflow-runner", "job-42",
                Instant.parse("2026-08-29T10:00:00Z"));

        assertThat(result.duplicate()).isTrue();
        verify(jdbc, never()).update(contains("INSERT INTO usage_aggregates"),
                any(), any(), any(), any(), any(), any());
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
                contains("FROM usage_aggregates"), eq(TENANT_ID), eq("ai_tokens")))
                .thenReturn(List.of(Map.of("total", 3_800_000L)));
        when(jdbc.<Long>queryForObject(
                contains("COALESCE(pe.limit_value"), eq(Long.class),
                eq(TENANT_ID), eq("USAGE.AI_TOKENS"), eq(TENANT_ID), eq("USAGE.AI_TOKENS")))
                .thenReturn(5_000_000L);
        when(jdbc.<String>queryForObject(
                contains("FROM usage_metrics"), eq(String.class), eq("ai_tokens")))
                .thenReturn("HARD_LIMIT");

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
                contains("FROM usage_aggregates"), eq(TENANT_ID), eq("users")))
                .thenReturn(List.of(Map.of("total", 42L)));
        when(jdbc.<Long>queryForObject(
                contains("COALESCE(pe.limit_value"), eq(Long.class),
                eq(TENANT_ID), eq("USAGE.USERS"), eq(TENANT_ID), eq("USAGE.USERS")))
                .thenReturn(null);
        when(jdbc.<String>queryForObject(
                contains("FROM usage_metrics"), eq(String.class), eq("users")))
                .thenReturn("UNLIMITED");

        Optional<UsageMeteringService.UsageSnapshot> snapshot =
                service.usageSnapshot(TENANT_ID, "users");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().limitKind()).isEqualTo("UNLIMITED");
        assertThat(snapshot.get().warning()).isFalse();
    }

    @Test
    @DisplayName("usage read model: no aggregates yields empty, not fabricated zero")
    void missingMetricIsEmpty() {
        when(jdbc.queryForList(contains("FROM usage_aggregates"), eq(TENANT_ID), eq("storage_gb")))
                .thenReturn(List.of());

        assertThat(service.usageSnapshot(TENANT_ID, "storage_gb")).isEmpty();
    }
}
