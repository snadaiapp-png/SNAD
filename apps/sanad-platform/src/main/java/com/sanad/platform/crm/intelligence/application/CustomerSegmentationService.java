package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.Segment;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.domain.SegmentPort;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.domain.event.CustomerSegmentChangedEvent;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for customer segmentation operations.
 */
@Service
public class CustomerSegmentationService {

    private final SegmentPort segmentPort;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CachePort cache;
    private final CustomerIntelligenceValidator validator;

    public CustomerSegmentationService(SegmentPort segmentPort,
                                       CustomerIntelligenceQueryPortAdapter queryAdapter,
                                       CustomerIntelligenceEventPublisher eventPublisher,
                                       TimelineEventPort timeline,
                                       CachePort cache,
                                       CustomerIntelligenceValidator validator) {
        this.segmentPort = segmentPort;
        this.queryAdapter = queryAdapter;
        this.eventPublisher = eventPublisher;
        this.timeline = timeline;
        this.cache = cache;
        this.validator = validator;
    }

    @Transactional
    public Segment createSegment(UUID tenantId, UUID actorId, String segmentCode,
                                  String segmentName, String segmentType,
                                  String description, String criteriaJson) {
        Segment segment = segmentPort.createSegment(tenantId, segmentCode, segmentName,
                segmentType, description, criteriaJson);
        timeline.record(tenantId, "SEGMENT", segment.id(),
                "crm.intelligence.segment.created",
                "Segment created: " + segmentName,
                "CRM_INTELLIGENCE", segment.id(), actorId, Instant.now());
        return segment;
    }

    @Transactional
    public SegmentMembership addCustomerToSegment(UUID tenantId, UUID accountId,
                                                   UUID segmentId, UUID actorId,
                                                   String membershipType) {
        validator.validateCustomer(tenantId, accountId);

        SegmentMembership membership = segmentPort.assignSegment(
                tenantId, accountId, segmentId,
                membershipType != null ? membershipType : "MANUAL", actorId);

        cache.invalidateAll(tenantId, accountId);

        eventPublisher.publish(new CustomerSegmentChangedEvent(
                tenantId, accountId, segmentId, null,
                CustomerSegmentChangedEvent.CHANGE_ADDED,
                Instant.now(), "segment-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.segment.added",
                "Added to segment",
                "CRM_INTELLIGENCE", segmentId, actorId, Instant.now());

        return membership;
    }

    @Transactional
    public void removeCustomerFromSegment(UUID tenantId, UUID accountId,
                                           UUID segmentId, UUID actorId) {
        validator.validateCustomer(tenantId, accountId);

        segmentPort.deactivateMembership(tenantId, accountId, segmentId);

        cache.invalidateAll(tenantId, accountId);

        eventPublisher.publish(new CustomerSegmentChangedEvent(
                tenantId, accountId, segmentId, null,
                CustomerSegmentChangedEvent.CHANGE_REMOVED,
                Instant.now(), "segment-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.segment.removed",
                "Removed from segment",
                "CRM_INTELLIGENCE", segmentId, actorId, Instant.now());
    }

    public List<SegmentMembership> getActiveSegments(UUID tenantId, UUID accountId) {
        return queryAdapter.findActiveSegments(tenantId, accountId);
    }

    public List<Segment> getAllSegments(UUID tenantId) {
        return queryAdapter.findAllSegments(tenantId);
    }

    public Optional<Segment> findByCode(UUID tenantId, String segmentCode) {
        return segmentPort.findByCode(tenantId, segmentCode);
    }
}
