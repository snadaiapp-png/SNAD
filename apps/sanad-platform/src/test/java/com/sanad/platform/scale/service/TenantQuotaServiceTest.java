package com.sanad.platform.scale.service;

import com.sanad.platform.scale.domain.TenantQuota;
import com.sanad.platform.scale.domain.TenantQuota.Dimension;
import com.sanad.platform.scale.repository.TenantQuotaRepository;
import com.sanad.platform.scale.service.TenantQuotaService.QuotaCheckResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantQuotaServiceTest {

    private TenantQuotaRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private TenantQuotaService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantQuotaRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new TenantQuotaService(repository, meterRegistry);
    }

    @Test
    void checkAndConsumeReturnsOkWhenUnderLimit() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota quota = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);
        when(repository.findByTenantIdAndDimension("tnt-1", Dimension.API_RPM))
                .thenReturn(Optional.of(quota));
        when(repository.save(any(TenantQuota.class))).thenAnswer(inv -> inv.getArgument(0));

        QuotaCheckResult result = service.checkAndConsume("tnt-1", Dimension.API_RPM, 1L);

        assertThat(result.exceeded()).isFalse();
        assertThat(result.limit()).isEqualTo(60L);
        assertThat(result.used()).isEqualTo(1L);
        assertThat(result.remaining()).isEqualTo(59L);
    }

    @Test
    void checkAndConsumeReturnsExceededWhenAtLimit() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota quota = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);
        quota.incrementUsed(60L); // already at limit
        when(repository.findByTenantIdAndDimension("tnt-1", Dimension.API_RPM))
                .thenReturn(Optional.of(quota));

        QuotaCheckResult result = service.checkAndConsume("tnt-1", Dimension.API_RPM, 1L);

        assertThat(result.exceeded()).isTrue();
        assertThat(result.retryAfter()).isPositive();
        // Counter should be incremented
        assertThat(meterRegistry.counter("quota.exceeded",
                "tenant", "tnt-1", "dimension", "API_RPM").count()).isEqualTo(1.0);
    }

    @Test
    void createsDefaultQuotaWhenNotPresent() {
        when(repository.findByTenantIdAndDimension("tnt-new", Dimension.API_RPM))
                .thenReturn(Optional.empty());
        when(repository.save(any(TenantQuota.class))).thenAnswer(inv -> {
            TenantQuota q = inv.getArgument(0);
            q.setId(1L);
            return q;
        });

        QuotaCheckResult result = service.checkAndConsume("tnt-new", Dimension.API_RPM, 1L);

        assertThat(result.exceeded()).isFalse();
        assertThat(result.limit()).isEqualTo(60L); // default API_RPM
        assertThat(result.used()).isEqualTo(1L);
    }

    @Test
    void updateLimitChangesLimitAndPersists() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota quota = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);
        when(repository.findByTenantIdAndDimension("tnt-1", Dimension.API_RPM))
                .thenReturn(Optional.of(quota));
        when(repository.save(any(TenantQuota.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateLimit("tnt-1", Dimension.API_RPM, 120L);

        assertThat(quota.getLimitValue()).isEqualTo(120L);
        verify(repository).save(quota);
    }

    @Test
    void resetExpiredResetsAllExpiredQuotas() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        TenantQuota expired1 = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, past);
        expired1.incrementUsed(30L);
        TenantQuota expired2 = new TenantQuota("tnt-2", Dimension.API_RPM, 60L, past);
        expired2.incrementUsed(10L);

        when(repository.findByResetAtBefore(any(Instant.class)))
                .thenReturn(java.util.List.of(expired1, expired2));
        when(repository.save(any(TenantQuota.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.resetExpired();

        assertThat(count).isEqualTo(2);
        assertThat(expired1.getUsedValue()).isZero();
        assertThat(expired2.getUsedValue()).isZero();
    }
}
