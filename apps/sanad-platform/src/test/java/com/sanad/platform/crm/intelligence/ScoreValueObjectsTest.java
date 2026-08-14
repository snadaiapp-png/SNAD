package com.sanad.platform.crm.intelligence;

import com.sanad.platform.crm.intelligence.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CRM-010 domain value objects.
 * Tests invariants, band derivation, and factory methods.
 */
class ScoreValueObjectsTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final java.sql.Timestamp NOW = java.sql.Timestamp.from(Instant.now());

    // ── HealthScore ──

    @Nested
    class HealthScoreTests {

        @Test
        void validHealthScore_acceptsFullRange() {
            assertThatNoException().isThrownBy(() ->
                    new HealthScore(0.0, HealthScore.BAND_CRITICAL, NOW, List.of()));
            assertThatNoException().isThrownBy(() ->
                    new HealthScore(100.0, HealthScore.BAND_THRIVING, NOW, List.of()));
        }

        @Test
        void rejectsValueAbove100() {
            assertThatThrownBy(() -> new HealthScore(100.1, "X", NOW, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsValueBelow0() {
            assertThatThrownBy(() -> new HealthScore(-0.1, "X", NOW, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullCalculatedAt() {
            assertThatThrownBy(() -> new HealthScore(50.0, "BAND", null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void bandFor_derivesCorrectBands() {
            assertThat(HealthScore.bandFor(0.0)).isEqualTo(HealthScore.BAND_CRITICAL);
            assertThat(HealthScore.bandFor(24.9)).isEqualTo(HealthScore.BAND_CRITICAL);
            assertThat(HealthScore.bandFor(25.0)).isEqualTo(HealthScore.BAND_AT_RISK);
            assertThat(HealthScore.bandFor(49.9)).isEqualTo(HealthScore.BAND_AT_RISK);
            assertThat(HealthScore.bandFor(50.0)).isEqualTo(HealthScore.BAND_HEALTHY);
            assertThat(HealthScore.bandFor(74.9)).isEqualTo(HealthScore.BAND_HEALTHY);
            assertThat(HealthScore.bandFor(75.0)).isEqualTo(HealthScore.BAND_THRIVING);
            assertThat(HealthScore.bandFor(100.0)).isEqualTo(HealthScore.BAND_THRIVING);
        }
    }

    // ── CustomerLifetimeValue ──

    @Nested
    class CustomerLifetimeValueTests {

        @Test
        void validCLV_acceptsZeroValues() {
            assertThatNoException().isThrownBy(() ->
                    new CustomerLifetimeValue(0, 0, CustomerLifetimeValue.TIER_LOW_VALUE, NOW, 0.0));
        }

        @Test
        void rejectsNegativePredictedValue() {
            assertThatThrownBy(() -> new CustomerLifetimeValue(-1, 0, "X", NOW, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsConfidenceAbove1() {
            assertThatThrownBy(() -> new CustomerLifetimeValue(100, 50, "X", NOW, 1.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void tierFor_derivesCorrectTiers() {
            assertThat(CustomerLifetimeValue.tierFor(99999)).isEqualTo(CustomerLifetimeValue.TIER_MID_VALUE);
            assertThat(CustomerLifetimeValue.tierFor(24999)).isEqualTo(CustomerLifetimeValue.TIER_LOW_VALUE);
            assertThat(CustomerLifetimeValue.tierFor(25000)).isEqualTo(CustomerLifetimeValue.TIER_MID_VALUE);
            assertThat(CustomerLifetimeValue.tierFor(100000)).isEqualTo(CustomerLifetimeValue.TIER_HIGH_VALUE);
        }
    }

    // ── EngagementScore ──

    @Nested
    class EngagementScoreTests {

        @Test
        void bandFor_derivesCorrectBands() {
            assertThat(EngagementScore.bandFor(0)).isEqualTo(EngagementScore.BAND_DORMANT);
            assertThat(EngagementScore.bandFor(20)).isEqualTo(EngagementScore.BAND_LOW);
            assertThat(EngagementScore.bandFor(40)).isEqualTo(EngagementScore.BAND_MODERATE);
            assertThat(EngagementScore.bandFor(70)).isEqualTo(EngagementScore.BAND_HIGH);
        }
    }

    // ── RiskScore ──

    @Nested
    class RiskScoreTests {

        @Test
        void bandFor_derivesCorrectBands() {
            assertThat(RiskScore.bandFor(0)).isEqualTo(RiskScore.BAND_LOW_RISK);
            assertThat(RiskScore.bandFor(30)).isEqualTo(RiskScore.BAND_MEDIUM_RISK);
            assertThat(RiskScore.bandFor(60)).isEqualTo(RiskScore.BAND_HIGH_RISK);
        }
    }

    // ── LoyaltyScore ──

    @Nested
    class LoyaltyScoreTests {

        @Test
        void bandFor_derivesCorrectBands() {
            assertThat(LoyaltyScore.bandFor(0)).isEqualTo(LoyaltyScore.BAND_NEW);
            assertThat(LoyaltyScore.bandFor(25)).isEqualTo(LoyaltyScore.BAND_GROWING);
            assertThat(LoyaltyScore.bandFor(50)).isEqualTo(LoyaltyScore.BAND_LOYAL);
            assertThat(LoyaltyScore.bandFor(80)).isEqualTo(LoyaltyScore.BAND_CHAMPION);
        }
    }

    // ── ScoreComponent ──

    @Nested
    class ScoreComponentTests {

        @Test
        void of_computesWeightedValue() {
            ScoreComponent comp = ScoreComponent.of("response_time", 0.30, 80.0);
            assertThat(comp.weightedValue()).isEqualTo(24.0);
        }

        @Test
        void rejectsWeightAbove1() {
            assertThatThrownBy(() -> ScoreComponent.of("x", 1.1, 50))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankName() {
            assertThatThrownBy(() -> ScoreComponent.of("", 0.5, 50))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── RiskFactor ──

    @Test
    void riskFactor_rejectsContributionAbove100() {
        assertThatThrownBy(() -> new RiskFactor("churn", "HIGH", 100.1, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── NextBestAction ──

    @Nested
    class NextBestActionTests {

        @Test
        void validNBA_acceptsAllFields() {
            assertThatNoException().isThrownBy(() -> new NextBestAction(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID,
                    "SCHEDULE_FOLLOWUP", "Follow up", 0.85, "reasoning",
                    NextBestAction.STATUS_PENDING, NOW, NOW.plusSeconds(3600),
                    true, null, null, 0));
        }

        @Test
        void rejectsConfidenceAbove1() {
            assertThatThrownBy(() -> new NextBestAction(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID,
                    "X", "desc", 1.1, "", "PENDING", NOW, NOW.plusSeconds(60),
                    true, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void defaultsStatusToPending_whenNull() {
            NextBestAction nba = new NextBestAction(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID,
                    "ACTION", "desc", 0.5, "", null, NOW, NOW.plusSeconds(60),
                    true, null, null, 0);
            assertThat(nba.status()).isEqualTo("PENDING");
        }
    }

    // ── CustomerProfile ──

    @Test
    void customerProfile_rejectsNullAccountId() {
        assertThatThrownBy(() -> new CustomerProfile(
                null, TENANT_ID, "Name", "CUSTOMER", "ACTIVE",
                "ENTERPRISE", "GOLD", "LOW", null, List.of(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerProfile_rejectsBlankDisplayName() {
        assertThatThrownBy(() -> new CustomerProfile(
                ACCOUNT_ID, TENANT_ID, "", "CUSTOMER", "ACTIVE",
                "ENTERPRISE", "GOLD", "LOW", null, List.of(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── SegmentMembership ──

    @Test
    void segmentMembership_defaultsAssignedAtToNow() {
        SegmentMembership m = new SegmentMembership(
                UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, UUID.randomUUID(),
                null, null, null, true);
        assertThat(m.assignedAt()).isNotNull();
        assertThat(m.membershipType()).isEqualTo(SegmentMembership.TYPE_MANUAL);
    }
}
