package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.Segment;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.domain.SegmentPort;
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

class CustomerSegmentationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID SEGMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final SegmentPort segmentPort = mock(SegmentPort.class);
    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final CustomerIntelligenceEventPublisher eventPublisher = mock(CustomerIntelligenceEventPublisher.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);
    private final CustomerSegmentationService service = new CustomerSegmentationService(
            segmentPort, queryAdapter, eventPublisher, timeline, cache, validator);

    @BeforeEach
    void setUp() {
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);
    }

    @Nested
    @DisplayName("createSegment")
    class CreateSegmentTests {

        @Test
        @DisplayName("should create segment and record timeline event")
        void shouldCreateSegment() {
            // Arrange
            Segment segment = new Segment(SEGMENT_ID, TENANT_ID, "VIP", "VIP Customers",
                    "CUSTOM", "VIP tier customers", mapper.createObjectNode(), true, Instant.now(), Instant.now());
            when(segmentPort.createSegment(TENANT_ID, "VIP", "VIP Customers",
                    "CUSTOM", "VIP tier customers", "{}")).thenReturn(segment);

            // Act
            Segment result = service.createSegment(TENANT_ID, ACTOR_ID, "VIP", "VIP Customers",
                    "CUSTOM", "VIP tier customers", "{}");

            // Assert
            assertThat(result).isEqualTo(segment);
            verify(timeline).record(eq(TENANT_ID), eq("SEGMENT"), eq(SEGMENT_ID),
                    eq("crm.intelligence.segment.created"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(SEGMENT_ID), eq(ACTOR_ID), any());
        }
    }

    @Nested
    @DisplayName("addCustomerToSegment")
    class AddCustomerToSegmentTests {

        @Test
        @DisplayName("should add customer to segment with default MANUAL type")
        void shouldAddCustomerWithDefaultType() {
            // Arrange
            SegmentMembership membership = new SegmentMembership(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, SEGMENT_ID,
                    "MANUAL", Instant.now(), ACTOR_ID, true);
            when(segmentPort.assignSegment(TENANT_ID, ACCOUNT_ID, SEGMENT_ID, "MANUAL", ACTOR_ID))
                    .thenReturn(membership);

            // Act
            SegmentMembership result = service.addCustomerToSegment(
                    TENANT_ID, ACCOUNT_ID, SEGMENT_ID, ACTOR_ID, null);

            // Assert
            assertThat(result).isEqualTo(membership);
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher).publish(any());
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.segment.added"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(SEGMENT_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should use custom membership type when provided")
        void shouldUseCustomType() {
            // Arrange
            SegmentMembership membership = new SegmentMembership(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, SEGMENT_ID,
                    "AI_ASSIGNED", Instant.now(), ACTOR_ID, true);
            when(segmentPort.assignSegment(TENANT_ID, ACCOUNT_ID, SEGMENT_ID, "AI_ASSIGNED", ACTOR_ID))
                    .thenReturn(membership);

            // Act
            SegmentMembership result = service.addCustomerToSegment(
                    TENANT_ID, ACCOUNT_ID, SEGMENT_ID, ACTOR_ID, "AI_ASSIGNED");

            // Assert
            verify(segmentPort).assignSegment(TENANT_ID, ACCOUNT_ID, SEGMENT_ID, "AI_ASSIGNED", ACTOR_ID);
        }

        @Test
        @DisplayName("should reject non-existent customer")
        void shouldRejectNonExistentCustomer() {
            // Arrange
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.addCustomerToSegment(
                    TENANT_ID, ACCOUNT_ID, SEGMENT_ID, ACTOR_ID, "MANUAL"))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }
    }

    @Nested
    @DisplayName("removeCustomerFromSegment")
    class RemoveCustomerFromSegmentTests {

        @Test
        @DisplayName("should remove customer and publish event")
        void shouldRemoveCustomer() {
            // Act
            service.removeCustomerFromSegment(TENANT_ID, ACCOUNT_ID, SEGMENT_ID, ACTOR_ID);

            // Assert
            verify(segmentPort).deactivateMembership(TENANT_ID, ACCOUNT_ID, SEGMENT_ID);
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher).publish(any());
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.segment.removed"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(SEGMENT_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should reject inactive customer")
        void shouldRejectInactiveCustomer() {
            // Arrange
            AccountRecord inactiveAccount = new AccountRecord(
                    ACCOUNT_ID, 0, "Inactive", "Inactive", "CUSTOMER", "INACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(inactiveAccount);

            // Act & Assert
            assertThatThrownBy(() -> service.removeCustomerFromSegment(
                    TENANT_ID, ACCOUNT_ID, SEGMENT_ID, ACTOR_ID))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }
    }

    @Nested
    @DisplayName("getActiveSegments")
    class GetActiveSegmentsTests {

        @Test
        @DisplayName("should return active segments for account")
        void shouldReturnActiveSegments() {
            // Arrange
            List<SegmentMembership> memberships = List.of(
                    new SegmentMembership(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, SEGMENT_ID,
                            "MANUAL", Instant.now(), ACTOR_ID, true));
            when(queryAdapter.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(memberships);

            // Act
            List<SegmentMembership> result = service.getActiveSegments(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAllSegments")
    class GetAllSegmentsTests {

        @Test
        @DisplayName("should return all segments for tenant")
        void shouldReturnAllSegments() {
            // Arrange
            List<Segment> segments = List.of(
                    new Segment(SEGMENT_ID, TENANT_ID, "VIP", "VIP Customers",
                            "CUSTOM", "desc", mapper.createObjectNode(), true, Instant.now(), Instant.now()));
            when(queryAdapter.findAllSegments(TENANT_ID)).thenReturn(segments);

            // Act
            List<Segment> result = service.getAllSegments(TENANT_ID);

            // Assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByCode")
    class FindByCodeTests {

        @Test
        @DisplayName("should find segment by code")
        void shouldFindByCode() {
            // Arrange
            Segment segment = new Segment(SEGMENT_ID, TENANT_ID, "VIP", "VIP Customers",
                    "CUSTOM", "desc", mapper.createObjectNode(), true, Instant.now(), Instant.now());
            when(segmentPort.findByCode(TENANT_ID, "VIP")).thenReturn(Optional.of(segment));

            // Act
            Optional<Segment> result = service.findByCode(TENANT_ID, "VIP");

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().segmentCode()).isEqualTo("VIP");
        }

        @Test
        @DisplayName("should return empty when code not found")
        void shouldReturnEmptyWhenNotFound() {
            when(segmentPort.findByCode(TENANT_ID, "NONEXISTENT")).thenReturn(Optional.empty());

            Optional<Segment> result = service.findByCode(TENANT_ID, "NONEXISTENT");

            assertThat(result).isEmpty();
        }
    }
}
