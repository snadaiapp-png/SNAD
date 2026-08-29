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
                                Integer percent, String limitKind, boolean warning) {
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
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT total FROM usage_aggregates "
                        + "WHERE tenant_id = ? AND metric_code = ? AND period_type = 'MONTHLY' "
                        + "ORDER BY period_start DESC LIMIT 1",
                tenantId, metricCode);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        long current = ((Number) rows.get(0).get("total")).longValue();

        Long limit = jdbc.queryForObject(
                """
                        SELECT COALESCE(pe.limit_value, NULL) FROM (
                            SELECT pme.limit_value
                            FROM tenant_subscriptions ts
                            JOIN plan_module_entitlements pme ON pme.plan_id = ts.plan_id
                            WHERE ts.tenant_id = ? AND ts.status IN ('ACTIVE', 'TRIALING', 'TRIAL')
                              AND pme.capability_code = ?
                            UNION ALL
                            SELECT pel.limit_value
                            FROM tenant_subscriptions ts
                            JOIN subscription_items si ON si.subscription_id = ts.id AND si.status = 'ACTIVE'
                            JOIN product_entitlements pel ON pel.product_id = si.product_id
                            WHERE ts.tenant_id = ? AND pel.capability_code = ?
                        ) pe
                        WHERE pe.limit_value IS NOT NULL
                        ORDER BY pe.limit_value DESC
                        LIMIT 1
                        """,
                Long.class, tenantId, capabilityCode(metricCode), tenantId, capabilityCode(metricCode));

        String limitKind = jdbc.queryForObject(
                "SELECT limit_kind FROM usage_metrics WHERE code = ?",
                String.class, metricCode);

        Integer percent = null;
        boolean warning = false;
        if (limit != null && limit > 0) {
            percent = (int) Math.round((double) current * 100.0 / limit);
            warning = ("HARD_LIMIT".equals(limitKind) || "SOFT_LIMIT".equals(limitKind))
                    && (percent >= WARNING_THRESHOLD_75);
        }
        return Optional.of(new UsageSnapshot(metricCode, current, limit, percent,
                limitKind == null ? "HARD_LIMIT" : limitKind, warning));
    }

    private static String capabilityCode(String metricCode) {
        return "USAGE." + metricCode.toUpperCase();
    }
}
