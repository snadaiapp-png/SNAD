package com.sanad.platform.crm.intelligence.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * A configurable scoring model defining component weights for a score type.
 */
public record ScoringModel(
        UUID modelId,
        UUID tenantId,
        String scoreType,
        String version,
        JsonNode weights,
        boolean active,
        Instant activatedAt
) {
    public static final String TYPE_HEALTH = "HEALTH";
    public static final String TYPE_CLV = "CLV";
    public static final String TYPE_ENGAGEMENT = "ENGAGEMENT";
    public static final String TYPE_RISK = "RISK";
    public static final String TYPE_LOYALTY = "LOYALTY";

    public ScoringModel {
        if (modelId == null) throw new IllegalArgumentException("modelId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (scoreType == null || scoreType.isBlank())
            throw new IllegalArgumentException("scoreType is required");
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("version is required");
        if (weights == null) throw new IllegalArgumentException("weights is required");
    }
}
