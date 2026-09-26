package com.sanad.platform.platformiam.repository;

import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped persistence boundary for metadata attached to shared roles.
 */
public interface PlatformRoleMetadataRepository {

    Optional<PlatformRoleMetadata> findByControlTenantIdAndRoleId(UUID controlTenantId, UUID roleId);

    List<PlatformRoleMetadata> findByControlTenantId(UUID controlTenantId);

    PlatformRoleMetadata save(PlatformRoleMetadata metadata);
}
