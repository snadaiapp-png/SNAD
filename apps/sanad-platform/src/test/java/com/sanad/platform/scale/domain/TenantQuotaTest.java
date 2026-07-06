package com.sanad.platform.scale.domain;

import com.sanad.platform.scale.domain.TenantQuota.Dimension;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantQuotaTest {

    @Test
    void newlyCreatedQuotaHasZeroUsage() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);

        assertThat(q.getUsedValue()).isZero();
        assertThat(q.getLimitValue()).isEqualTo(60L);
        assertThat(q.remaining()).isEqualTo(60L);
        assertThat(q.isExceeded()).isFalse();
    }

    @Test
    void incrementUsedAddsToUsage() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);

        q.incrementUsed(10L);

        assertThat(q.getUsedValue()).isEqualTo(10L);
        assertThat(q.remaining()).isEqualTo(50L);
        assertThat(q.isExceeded()).isFalse();
    }

    @Test
    void isExceededReturnsTrueWhenUsedReachesLimit() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);

        q.incrementUsed(60L);

        assertThat(q.isExceeded()).isTrue();
        assertThat(q.remaining()).isZero();
    }

    @Test
    void resetClearsUsageAndAdvancesResetAt() {
        Instant originalReset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, originalReset);
        q.incrementUsed(30L);
        Instant beforeReset = Instant.now();

        q.reset();

        assertThat(q.getUsedValue()).isZero();
        // After reset(), resetAt should be set to Instant.now() (around 'beforeReset').
        // Assert it is at or after beforeReset (not the original future reset).
        assertThat(q.getResetAt()).isAfterOrEqualTo(beforeReset);
        assertThat(q.getResetAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void updateLimitChangesLimitValue() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);

        q.updateLimit(120L);

        assertThat(q.getLimitValue()).isEqualTo(120L);
    }

    @Test
    void rejectsNegativeLimit() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> new TenantQuota("tnt-1", Dimension.API_RPM, -1L, reset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitValue");
    }

    @Test
    void rejectsNegativeDelta() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);

        assertThatThrownBy(() -> q.incrementUsed(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delta");
    }

    @Test
    void rejectsNullTenantId() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> new TenantQuota(null, Dimension.API_RPM, 60L, reset))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void equalityByTenantIdAndDimension() {
        Instant reset = Instant.now().plus(1, ChronoUnit.DAYS);
        TenantQuota q1 = new TenantQuota("tnt-1", Dimension.API_RPM, 60L, reset);
        TenantQuota q2 = new TenantQuota("tnt-1", Dimension.API_RPM, 100L, reset);
        TenantQuota q3 = new TenantQuota("tnt-2", Dimension.API_RPM, 60L, reset);
        TenantQuota q4 = new TenantQuota("tnt-1", Dimension.AI_TOKENS_DAY, 60L, reset);

        assertThat(q1).isEqualTo(q2);
        assertThat(q1).isNotEqualTo(q3);
        assertThat(q1).isNotEqualTo(q4);
        assertThat(q1.hashCode()).isEqualTo(q2.hashCode());
    }
}
