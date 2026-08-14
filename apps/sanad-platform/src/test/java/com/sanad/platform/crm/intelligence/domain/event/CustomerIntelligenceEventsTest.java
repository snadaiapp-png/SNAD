package com.sanad.platform.crm.intelligence.domain.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for customer intelligence domain events.
 */
class CustomerIntelligenceEventsTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Nested
    class CustomerScoreCalculatedEventTests {

        @Test
        void eventType_isCorrect() {
            var event = new CustomerScoreCalculatedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTH", 75.0, "HEALTHY",
                    70.0, 5.0, "MANUAL", NOW, "corr-1");
            assertThat(event.eventType()).isEqualTo("crm.intelligence.score.calculated");
        }

        @Test
        void hasChanged_trueWhenDeltaExceedsThreshold() {
            var event = new CustomerScoreCalculatedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTH", 75.0, "HEALTHY",
                    70.0, 5.0, "MANUAL", NOW, "corr-1");
            assertThat(event.hasChanged()).isTrue();
        }

        @Test
        void hasChanged_falseWhenDeltaIsMinimal() {
            var event = new CustomerScoreCalculatedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTH", 70.0, "HEALTHY",
                    70.0, 0.0001, "MANUAL", NOW, "corr-1");
            assertThat(event.hasChanged()).isFalse();
        }

        @Test
        void hasChanged_falseWhenPreviousIsNull() {
            var event = new CustomerScoreCalculatedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTH", 50.0, "HEALTHY",
                    null, 0, "MANUAL", NOW, "corr-1");
            assertThat(event.hasChanged()).isFalse();
        }
    }

    @Nested
    class CustomerHealthChangedEventTests {

        @Test
        void eventType_isCorrect() {
            var event = new CustomerHealthChangedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTHY", "AT_RISK", 45.0, NOW, "corr-1");
            assertThat(event.eventType()).isEqualTo("crm.intelligence.health.changed");
        }

        @Test
        void implementsEventInterface() {
            var event = new CustomerHealthChangedEvent(
                    TENANT_ID, ACCOUNT_ID, "HEALTHY", "AT_RISK", 45.0, NOW, "corr-1");
            assertThat(event).isInstanceOf(CustomerIntelligenceEvent.class);
            assertThat(event.tenantId()).isEqualTo(TENANT_ID);
            assertThat(event.accountId()).isEqualTo(ACCOUNT_ID);
        }
    }

    @Nested
    class CustomerSegmentChangedEventTests {

        @Test
        void changeTypeConstants_exist() {
            assertThat(CustomerSegmentChangedEvent.CHANGE_ADDED).isEqualTo("ADDED");
            assertThat(CustomerSegmentChangedEvent.CHANGE_REMOVED).isEqualTo("REMOVED");
        }

        @Test
        void eventType_isCorrect() {
            var event = new CustomerSegmentChangedEvent(
                    TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), "VIP",
                    CustomerSegmentChangedEvent.CHANGE_ADDED, NOW, "corr-1");
            assertThat(event.eventType()).isEqualTo("crm.intelligence.segment.changed");
        }
    }

    @Test
    void nextBestActionGeneratedEvent_hasCorrectType() {
        var event = new NextBestActionGeneratedEvent(
                TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), "SCHEDULE_FOLLOWUP",
                0.85, NOW, "corr-1");
        assertThat(event.eventType()).isEqualTo("crm.intelligence.next_best_action.generated");
    }

    @Test
    void customerLifetimeValueUpdatedEvent_hasCorrectType() {
        var event = new CustomerLifetimeValueUpdatedEvent(
                TENANT_ID, ACCOUNT_ID, 250000, "HIGH_VALUE", 0.85, NOW, "corr-1");
        assertThat(event.eventType()).isEqualTo("crm.intelligence.lifetime_value.updated");
    }

    @Test
    void opportunityScoreUpdatedEvent_hasCorrectType() {
        var event = new OpportunityScoreUpdatedEvent(
                TENANT_ID, ACCOUNT_ID, 87.0, "UPSELL", 45000, NOW, "corr-1");
        assertThat(event.eventType()).isEqualTo("crm.intelligence.opportunity.updated");
    }
}
