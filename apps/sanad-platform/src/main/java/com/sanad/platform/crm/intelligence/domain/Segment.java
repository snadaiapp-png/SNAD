package com.sanad.platform.crm.intelligence.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer segment definition.
 */
public record Segment(
        UUID id,
        UUID tenantId,
        String segmentCode,
        String segmentName,
        String segmentType,
        String description,
        JsonNode criteria,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_RULE_BASED = "RULE_BASED";
    public static final String TYPE_AI_GENERATED = "AI_GENERATED";

    public Segment {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (segmentCode == null || segmentCode.isBlank())
            throw new IllegalArgumentException("segmentCode is required");
        if (segmentName == null || segmentName.isBlank())
            throw new IllegalArgumentException("segmentName is required");
        if (segmentType == null)
            throw new IllegalArgumentException("segmentType is required");
    }
}
