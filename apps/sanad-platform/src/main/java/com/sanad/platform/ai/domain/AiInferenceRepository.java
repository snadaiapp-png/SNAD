package com.sanad.platform.ai.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for {@link AiInference}. Append-only: no update/delete. */
public interface AiInferenceRepository {
    AiInference save(AiInference inference);
    Optional<AiInference> findById(UUID tenantId, UUID id);
    List<AiInference> findByTenant(UUID tenantId, int limit);
    List<AiInference> findByAgent(UUID tenantId, UUID agentId, int limit);
    List<AiInference> findByBusinessEntity(UUID tenantId, String entityType, UUID entityId);
    long countByTenantThisMonth(UUID tenantId);
}
