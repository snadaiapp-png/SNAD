package com.sanad.platform.management.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for tenant-scoped Governance Configuration (V20260815_24+).
 *
 * <p>Replaces hard-coded SLA/alert/escalation thresholds with
 * tenant-overridable defaults. When no row exists for a key, callers
 * fall back to the Java-side default (safe behavior).
 */
public final class GovernanceConfigDtos {

    private GovernanceConfigDtos() {}

    public enum ConfigType { STRING, INTEGER, DECIMAL, BOOLEAN, DURATION_ISO, JSON }

    public record CreateConfigurationRequest(
            String configKey,
            String configValue,
            ConfigType configType,
            String description
    ) {}

    public record UpdateConfigurationRequest(
            String configValue,
            Boolean enabled
    ) {}

    public record ConfigurationResponse(
            UUID id,
            UUID tenantId,
            String configKey,
            String configValue,
            ConfigType configType,
            String description,
            boolean enabled,
            UUID updatedBy,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ConfigurationSummary(
            int totalCount,
            int enabledCount,
            int disabledCount,
            java.util.List<String> keys
    ) {}

    /** Effective value resolution result — exposes whether the default was used. */
    public record ResolvedValue<T>(
            String key,
            T value,
            boolean fromTenantOverride,
            String source
    ) {}
}
