package com.sanad.platform.subscription.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * R0C-10 — effective/history subscription resolution (MODEL_B query contract).
 *
 * <p>The frozen R0C-9/R0C-10 multiplicity model distinguishes:</p>
 * <ul>
 *   <li><b>ALL_HISTORY</b> — every subscription row of the tenant (terminal
 *       rows included); cardinality 0..N.</li>
 *   <li><b>EFFECTIVE</b> — rows with {@code status NOT IN ('CANCELLED',
 *       'EXPIRED','TERMINATED')}; cardinality 0..1, bounded by the partial
 *       unique index {@code uk_tenant_subscriptions_effective}
 *       (V20260906_1).</li>
 * </ul>
 *
 * <p><b>Canonical chronology authority:</b> {@code (created_at DESC, id DESC)} —
 * the same deterministic ordering the existing read services already use for
 * "latest subscription" (e.g. TenantDirectoryQueryService.subscription_status).
 * There is no sequence column; created_at is NOT NULL and id is the PK.</p>
 *
 * <p>The only {@code LIMIT 1} in this component is the explicit, deterministic
 * latest-historical contract used by the continuation guard — never an
 * arbitrary row selection. Current-state consumers MUST resolve through
 * {@link #findEffectiveSubscription(UUID)} (or
 * {@link #hasEffectiveSubscription(UUID)}); exact lifecycle, invoice and
 * provisioning work remains subscription_id-scoped; reporting consumers keep
 * ALL_HISTORY.</p>
 */
@Service
public class SubscriptionResolutionService {

    /** Terminal lifecycle statuses — the effective predicate excludes exactly these. */
    private static final String EFFECTIVE_PREDICATE =
            "status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')";

    private final JdbcTemplate jdbc;

    public SubscriptionResolutionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A resolved effective (non-terminal) subscription. */
    public record EffectiveSubscription(
            UUID id, UUID tenantId, UUID planId, String status,
            String billingState, Instant createdAt) {
    }

    /** A historical subscription row (terminal or not). */
    public record HistoricalSubscription(UUID id, String status, Instant createdAt) {
    }

    /**
     * Resolve the tenant's unique EFFECTIVE subscription —
     * {@code status NOT IN ('CANCELLED','EXPIRED','TERMINATED')}.
     *
     * <p>No arbitrary {@code LIMIT 1}: the partial unique index bounds the
     * result at one row; the deterministic ordering is defensive and keeps
     * the query well-defined even against a schema where the invariant does
     * not yet exist.</p>
     */
    public Optional<EffectiveSubscription> findEffectiveSubscription(UUID tenantId) {
        List<EffectiveSubscription> rows = jdbc.query(
                "SELECT id, tenant_id, plan_id, status, billing_state, created_at "
                        + "FROM tenant_subscriptions WHERE tenant_id = ? AND " + EFFECTIVE_PREDICATE
                        + " ORDER BY created_at DESC, id DESC",
                (rs, i) -> new EffectiveSubscription(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("billing_state"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * ALL_HISTORY — every subscription row of the tenant, oldest last
     * (deterministic {@code created_at DESC, id DESC} ordering).
     */
    public List<HistoricalSubscription> findAllHistory(UUID tenantId) {
        return jdbc.query(
                "SELECT id, status, created_at FROM tenant_subscriptions "
                        + "WHERE tenant_id = ? ORDER BY created_at DESC, id DESC",
                (rs, i) -> new HistoricalSubscription(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId);
    }

    /**
     * The deterministic latest historical row — the continuation-guard
     * contract. Explicit ordering + explicit LIMIT 1 (never arbitrary):
     * the latest row's terminal status decides which continuation rule
     * applies (EXPIRED successor / CANCELLED resume-only / TERMINATED deferral).
     */
    public Optional<HistoricalSubscription> findLatestHistorical(UUID tenantId) {
        List<HistoricalSubscription> rows = jdbc.query(
                "SELECT id, status, created_at FROM tenant_subscriptions "
                        + "WHERE tenant_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, i) -> new HistoricalSubscription(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Whether the tenant currently has an effective (non-terminal) subscription. */
    public boolean hasEffectiveSubscription(UUID tenantId) {
        return findEffectiveSubscription(tenantId).isPresent();
    }

    /** True when the status is terminal under the frozen MODEL_B contract. */
    public static boolean isTerminal(String status) {
        return "CANCELLED".equals(status) || "EXPIRED".equals(status) || "TERMINATED".equals(status);
    }

    /** Timestamp binding helper kept package-visible for deterministic tests. */
    static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
