package com.sanad.platform.subscription.usage;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Usage metering foundation — idempotent event ingestion, monthly aggregates,
 * and the usage read model that joins aggregates with entitlement limits.
 *
 * <p>Limit values follow the capability-code convention
 * {@code USAGE.<METRIC_CODE_UPPER>} (e.g. {@code USAGE.AI_TOKENS}) resolved
 * from plan and product entitlements; limit kind (UNLIMITED, SOFT_LIMIT,
 * HARD_LIMIT, OVERAGE, PAY_AS_YOU_GO) is metric-catalog policy. Warning
 * thresholds fire at 75% and 90%.
 */
@Service
public class UsageMeteringService {

    public static final int WARNING_THRESHOLD_75 = 75;
    public static final int WARNING_THRESHOLD_90 = 90;

    private final JdbcTemplate jdbc;
    private final TenantRlsTransactionContext tenantRlsContext;

    public UsageMeteringService(JdbcTemplate jdbc, TenantRlsTransactionContext tenantRlsContext) {
        this.jdbc = jdbc;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record IngestResult(UUID eventId, boolean duplicate) {
    }

    public record UsageSnapshot(String metricCode, long current, Long limit,
                                Integer percent, String limitKind, Instant periodStart,
                                boolean warning, boolean critical) {
    }

    @Transactional
    public IngestResult ingest(UUID tenantId, String metricCode, long quantity,
                               String source, String idempotencyKey, Instant occurredAt) {
        if (quantity < 0) {
            throw new IllegalArgumentException("usage quantity must be non-negative");
        }
        // usage tables are FORCE-RLS fail-closed — trusted paths must scope the
        // transaction to the tenant before touching them
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        UUID eventId = UUID.randomUUID();
        try {
            jdbc.update("""
                            INSERT INTO usage_events (
                                id, tenant_id, metric_code, quantity, source,
                                idempotency_key, occurred_at, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                            """,
                    eventId, tenantId, metricCode, quantity, source, idempotencyKey,
                    Timestamp.from(occurredAt), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            // idempotent replay: the same (tenant, metric, key) event already landed
            return new IngestResult(eventId, true);
        }
        upsertMonthlyAggregate(tenantId, metricCode, quantity, occurredAt);
        return new IngestResult(eventId, false);
    }

    private void upsertMonthlyAggregate(UUID tenantId, String metricCode, long quantity,
                                        Instant occurredAt) {
        Instant periodStart = ZonedDateTime.ofInstant(occurredAt, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1)
                .toInstant();
        jdbc.update("""
                        INSERT INTO usage_aggregates (
                            id, tenant_id, metric_code, period_type, period_start, total, updated_at
                        ) VALUES (?, ?, ?, 'MONTHLY', ?, ?, NOW())
                        ON CONFLICT (tenant_id, metric_code, period_type, period_start)
                        DO UPDATE SET total = usage_aggregates.total + EXCLUDED.total, updated_at = NOW()
                        """,
                UUID.randomUUID(), tenantId, metricCode,
                Timestamp.from(periodStart), quantity, Timestamp.from(Instant.now()));
    }

    @Transactional(readOnly = true)
    public Optional<UsageSnapshot> usageSnapshot(UUID tenantId, String metricCode) {
        return usageSnapshots(tenantId).stream()
                .filter(s -> s.metricCode().equals(metricCode))
                .findFirst();
    }

    /**
     * Batched tenant usage read model — exactly three statements regardless of
     * metric count (latest MONTHLY aggregates, metric catalog, batched
     * entitlement limits). Never 1 + 3N per metric.
     */
    @Transactional(readOnly = true)
    public List<UsageSnapshot> usageSnapshots(UUID tenantId) {
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        List<Map<String, Object>> aggRows = jdbc.queryForList("""
                        SELECT u.metric_code, u.total, u.period_start
                        FROM usage_aggregates u
                        WHERE u.tenant_id = ? AND u.period_type = 'MONTHLY'
                          AND u.period_start = (
                              SELECT MAX(i.period_start) FROM usage_aggregates i
                              WHERE i.tenant_id = u.tenant_id AND i.metric_code = u.metric_code
                                AND i.period_type = 'MONTHLY')
                        ORDER BY u.metric_code
                        """, tenantId);
        if (aggRows.isEmpty()) {
            return List.of();
        }
        List<String> metricCodes = aggRows.stream()
                .map(row -> (String) row.get("metric_code"))
                .toList();
        Map<String, Long> limits = loadEntitlementLimits(tenantId, metricCodes);
        Map<String, String> kinds = loadMetricKinds();
        List<UsageSnapshot> snapshots = new java.util.ArrayList<>(aggRows.size());
        for (Map<String, Object> row : aggRows) {
            String metricCode = (String) row.get("metric_code");
            long current = ((Number) row.get("total")).longValue();
            Instant periodStart = ((java.sql.Timestamp) row.get("period_start")).toInstant();
            snapshots.add(buildSnapshot(metricCode, current, periodStart,
                    limits.get(capabilityCode(metricCode)), kinds.get(metricCode)));
        }
        return List.copyOf(snapshots);
    }

    private UsageSnapshot buildSnapshot(String metricCode, long current, Instant periodStart,
                                        Long limit, String limitKind) {
        Integer percent = null;
        boolean warning = false;
        boolean critical = false;
        boolean thresholdKind = "HARD_LIMIT".equals(limitKind) || "SOFT_LIMIT".equals(limitKind);
        if (limit != null && limit > 0) {
            percent = (int) Math.round((double) current * 100.0 / limit);
            warning = thresholdKind && (percent >= WARNING_THRESHOLD_75);
            critical = thresholdKind && (percent >= WARNING_THRESHOLD_90);
        }
        return new UsageSnapshot(metricCode, current, limit, percent,
                limitKind == null ? "HARD_LIMIT" : limitKind, periodStart, warning, critical);
    }

    /**
     * Batched effective-limit resolution: max non-null limit per capability
     * code across plan-derived (plan_module_entitlements) and item-derived
     * (product_entitlements via ACTIVE items) sources — the same union the
     * per-metric path resolves.
     */
    private Map<String, Long> loadEntitlementLimits(UUID tenantId, List<String> metricCodes) {
        List<String> capabilityCodes = metricCodes.stream()
                .map(UsageMeteringService::capabilityCode)
                .toList();
        String placeholders = String.join(", ", java.util.Collections.nCopies(capabilityCodes.size(), "?"));
        String sql = """
                SELECT pe.capability_code, MAX(pe.limit_value) AS "max_limit"
                FROM (
                    SELECT pme.capability_code, pme.limit_value
                    FROM tenant_subscriptions ts
                    JOIN plan_module_entitlements pme ON pme.plan_id = ts.plan_id
                    WHERE ts.tenant_id = ? AND ts.status IN ('ACTIVE', 'TRIALING', 'TRIAL')
                      AND pme.capability_code IN (%s)
                    UNION ALL
                    SELECT pel.capability_code, pel.limit_value
                    FROM tenant_subscriptions ts
                    JOIN subscription_items si ON si.subscription_id = ts.id AND si.status = 'ACTIVE'
                    JOIN product_entitlements pel ON pel.product_id = si.product_id
                    WHERE ts.tenant_id = ? AND pel.capability_code IN (%s)
                ) pe
                WHERE pe.limit_value IS NOT NULL
                GROUP BY pe.capability_code
                """.formatted(placeholders, placeholders);
        List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        args.addAll(capabilityCodes);
        args.add(tenantId);
        args.addAll(capabilityCodes);
        Map<String, Long> limits = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            limits.put((String) row.get("capability_code"),
                    ((Number) row.get("max_limit")).longValue());
        }
        return limits;
    }

    private Map<String, String> loadMetricKinds() {
        Map<String, String> kinds = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT code, limit_kind FROM usage_metrics")) {
            kinds.put((String) row.get("code"), (String) row.get("limit_kind"));
        }
        return kinds;
    }

    private static String capabilityCode(String metricCode) {
        return "USAGE." + metricCode.toUpperCase();
    }
}
