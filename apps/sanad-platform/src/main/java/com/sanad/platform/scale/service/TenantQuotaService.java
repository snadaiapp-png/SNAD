package com.sanad.platform.scale.service;

import com.sanad.platform.scale.domain.TenantQuota;
import com.sanad.platform.scale.domain.TenantQuota.Dimension;
import com.sanad.platform.scale.repository.TenantQuotaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Stage 08 Sprint 1 — ST8-S1-002 Tenant Quota Service.
 *
 * Enforces per-tenant quotas. Returns {@link QuotaCheckResult} with the
 * decision and remaining budget. The caller (typically an API gateway
 * filter) is responsible for returning HTTP 429 with {@code Retry-After}
 * when {@link QuotaCheckResult#exceeded()} is true.
 *
 * Security: tenant-scoped. No method may operate on a different tenant
 * than the one supplied as an argument. Cross-tenant access is the
 * caller's responsibility (resolved from authenticated principal).
 *
 * Observability: emits {@code quota.exceeded} counter per (tenant, dimension)
 * and {@code quota.utilization} gauge.
 */
@Service
public class TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);

    private final TenantQuotaRepository repository;
    private final MeterRegistry meterRegistry;

    public TenantQuotaService(TenantQuotaRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public QuotaCheckResult checkAndConsume(String tenantId, Dimension dimension, long delta) {
        TenantQuota quota = getOrCreateDefault(tenantId, dimension);
        maybeResetIfPeriodElapsed(quota, dimension);

        if (quota.isExceeded()) {
            meterRegistry.counter("quota.exceeded",
                    Tags.of("tenant", tenantId, "dimension", dimension.name())).increment();
            log.warn("Quota exceeded tenant={} dimension={} used={} limit={}",
                    tenantId, dimension, quota.getUsedValue(), quota.getLimitValue());
            return QuotaCheckResult.exceeded(quota.getLimitValue(), quota.getUsedValue(),
                    computeRetryAfterSeconds(quota, dimension));
        }

        quota.incrementUsed(delta);
        repository.save(quota);

        emitUtilizationGauge(tenantId, dimension, quota);

        return QuotaCheckResult.ok(quota.getLimitValue(), quota.getUsedValue(), quota.remaining());
    }

    @Transactional
    public void updateLimit(String tenantId, Dimension dimension, long newLimit) {
        TenantQuota quota = getOrCreateDefault(tenantId, dimension);
        long oldLimit = quota.getLimitValue();
        quota.updateLimit(newLimit);
        repository.save(quota);
        log.info("Quota limit updated tenant={} dimension={} old={} new={}",
                tenantId, dimension, oldLimit, newLimit);
    }

    @Transactional(readOnly = true)
    public Optional<TenantQuota> get(String tenantId, Dimension dimension) {
        return repository.findByTenantIdAndDimension(tenantId, dimension);
    }

    @Transactional(readOnly = true)
    public List<TenantQuota> listForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    @Transactional
    public int resetExpired() {
        Instant now = Instant.now();
        List<TenantQuota> expired = repository.findByResetAtBefore(now);
        for (TenantQuota q : expired) {
            q.reset();
            repository.save(q);
        }
        if (!expired.isEmpty()) {
            log.info("Reset {} expired quota records", expired.size());
        }
        return expired.size();
    }

    private TenantQuota getOrCreateDefault(String tenantId, Dimension dimension) {
        return repository.findByTenantIdAndDimension(tenantId, dimension)
                .orElseGet(() -> {
                    TenantQuota fresh = new TenantQuota(tenantId, dimension,
                            defaultLimitFor(dimension), nextReset(dimension));
                    return repository.save(fresh);
                });
    }

    private void maybeResetIfPeriodElapsed(TenantQuota quota, Dimension dimension) {
        if (Instant.now().isAfter(quota.getResetAt())) {
            log.debug("Resetting quota tenant={} dimension={} (period elapsed)",
                    quota.getTenantId(), dimension);
            quota.reset();
        }
    }

    private void emitUtilizationGauge(String tenantId, Dimension dimension, TenantQuota quota) {
        double utilization = quota.getLimitValue() == 0 ? 0.0
                : (double) quota.getUsedValue() / (double) quota.getLimitValue();
        meterRegistry.gauge("quota.utilization",
                Tags.of("tenant", tenantId, "dimension", dimension.name()),
                utilization);
    }

    private long computeRetryAfterSeconds(TenantQuota quota, Dimension dimension) {
        long remaining = quota.getResetAt().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(1L, remaining);
    }

    private Instant nextReset(Dimension dimension) {
        Instant now = Instant.now();
        return switch (dimension) {
            case API_RPM -> now.plus(1, ChronoUnit.MINUTES);
            case API_RPD, AI_TOKENS_DAY, WEBHOOKS_DAY, JOBS_DAY -> now.plus(1, ChronoUnit.DAYS);
            case AI_TOKENS_MONTH -> now.plus(30, ChronoUnit.DAYS);
            case STORAGE_GB -> now.plus(365, ChronoUnit.DAYS);
        };
    }

    private long defaultLimitFor(Dimension dimension) {
        return switch (dimension) {
            case API_RPM -> 60L;
            case API_RPD -> 1_000L;
            case AI_TOKENS_DAY -> 100_000L;
            case AI_TOKENS_MONTH -> 3_000_000L;
            case STORAGE_GB -> 10L;
            case WEBHOOKS_DAY -> 10_000L;
            case JOBS_DAY -> 1_000L;
        };
    }

    /**
     * Result of a quota check.
     *
     * @param exceeded   true if the quota is exceeded
     * @param limit      the configured limit
     * @param used       the current usage (after consumption if ok)
     * @param remaining  the remaining budget
     * @param retryAfter seconds until quota resets (only meaningful if exceeded)
     */
    public record QuotaCheckResult(boolean exceeded, long limit, long used, long remaining, long retryAfter) {

        public static QuotaCheckResult ok(long limit, long used, long remaining) {
            return new QuotaCheckResult(false, limit, used, remaining, 0L);
        }

        public static QuotaCheckResult exceeded(long limit, long used, long retryAfter) {
            return new QuotaCheckResult(true, limit, used, 0L, retryAfter);
        }
    }
}
