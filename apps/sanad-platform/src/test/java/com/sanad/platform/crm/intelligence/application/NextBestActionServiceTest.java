package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.NextBestActionPort;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.infrastructure.CustomerIntelligenceCache;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NextBestActionServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final NextBestActionPort nbaPort = mock(NextBestActionPort.class);
    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final CustomerIntelligenceEventPublisher eventPublisher = mock(CustomerIntelligenceEventPublisher.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);
    private final NextBestActionService service = new NextBestActionService(
            nbaPort, queryAdapter, eventPublisher, timeline, cache, validator);

    private NextBestAction createTestNba(String status) {
        return new NextBestAction(ACTION_ID, TENANT_ID, ACCOUNT_ID,
                "SCHEDULE_FOLLOWUP", "Follow up call", 0.85, "AI recommended",
                status, Instant.now(), Instant.now().plusSeconds(86400 * 7),
                true, null, null, 0);
    }

    @BeforeEach
    void setUp() {
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);
    }

    @Nested
    @DisplayName("generateRecommendation")
    class GenerateRecommendationTests {

        @Test
        @DisplayName("should create NBA and publish event")
        void shouldCreateNbaAndPublishEvent() {
            // Arrange
            NextBestAction nba = createTestNba(NextBestAction.STATUS_PENDING);
            when(nbaPort.create(eq(TENANT_ID), eq(ACCOUNT_ID), eq("SCHEDULE_FOLLOWUP"),
                    anyString(), eq(0.85), anyString(), any(Instant.class), eq(true)))
                    .thenReturn(nba);

            // Act
            NextBestAction result = service.generateRecommendation(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, "SCHEDULE_FOLLOWUP",
                    "Follow up call", 0.85, "AI recommended", true);

            // Assert
            assertThat(result).isEqualTo(nba);
            verify(eventPublisher).publish(any());
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.nba.generated"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(ACTION_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should reject non-existent customer")
        void shouldRejectNonExistentCustomer() {
            // Arrange
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.generateRecommendation(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, "ACTION", "desc", 0.8, "reason", true))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }
    }

    @Nested
    @DisplayName("acceptRecommendation")
    class AcceptRecommendationTests {

        @Test
        @DisplayName("should accept and invalidate cache")
        void shouldAcceptAndInvalidateCache() {
            // Arrange
            NextBestAction nba = createTestNba(NextBestAction.STATUS_ACCEPTED);
            when(nbaPort.resolve(TENANT_ID, ACTION_ID, NextBestAction.STATUS_ACCEPTED, ACTOR_ID, 0))
                    .thenReturn(Optional.of(nba));

            // Act
            Optional<NextBestAction> result = service.acceptRecommendation(
                    TENANT_ID, ACTION_ID, ACTOR_ID, 0);

            // Assert
            assertThat(result).isPresent();
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.nba.accepted"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(ACTION_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should return empty when NBA not found")
        void shouldReturnEmptyWhenNotFound() {
            when(nbaPort.resolve(TENANT_ID, ACTION_ID, NextBestAction.STATUS_ACCEPTED, ACTOR_ID, 0))
                    .thenReturn(Optional.empty());

            Optional<NextBestAction> result = service.acceptRecommendation(
                    TENANT_ID, ACTION_ID, ACTOR_ID, 0);

            assertThat(result).isEmpty();
            verifyNoInteractions(cache);
        }
    }

    @Nested
    @DisplayName("rejectRecommendation")
    class RejectRecommendationTests {

        @Test
        @DisplayName("should reject and invalidate cache")
        void shouldRejectAndInvalidateCache() {
            // Arrange
            NextBestAction nba = createTestNba(NextBestAction.STATUS_REJECTED);
            when(nbaPort.resolve(TENANT_ID, ACTION_ID, NextBestAction.STATUS_REJECTED, ACTOR_ID, 0))
                    .thenReturn(Optional.of(nba));

            // Act
            Optional<NextBestAction> result = service.rejectRecommendation(
                    TENANT_ID, ACTION_ID, ACTOR_ID, 0);

            // Assert
            assertThat(result).isPresent();
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("expireStaleRecommendations")
    class ExpireStaleTests {

        @Test
        @DisplayName("should delegate to port and return count")
        void shouldDelegateToPort() {
            when(nbaPort.expireStale(TENANT_ID)).thenReturn(3);

            int expired = service.expireStaleRecommendations(TENANT_ID);

            assertThat(expired).isEqualTo(3);
            verify(nbaPort).expireStale(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("getPendingActions")
    class GetPendingActionsTests {

        @Test
        @DisplayName("should return pending actions from query adapter")
        void shouldReturnPendingActions() {
            // Arrange
            List<NextBestAction> pending = List.of(
                    createTestNba(NextBestAction.STATUS_PENDING));
            when(queryAdapter.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(pending);

            // Act
            List<NextBestAction> result = service.getPendingActions(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).hasSize(1);
        }
    }
}
