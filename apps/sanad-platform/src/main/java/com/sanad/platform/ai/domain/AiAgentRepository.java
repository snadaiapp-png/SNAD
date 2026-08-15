package com.sanad.platform.ai.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for {@link AiAgent}. */
public interface AiAgentRepository {
    AiAgent save(AiAgent agent);
    Optional<AiAgent> findById(UUID tenantId, UUID id);
    Optional<AiAgent> findByCode(UUID tenantId, String code);
    List<AiAgent> findByTenant(UUID tenantId, int limit);
    List<AiAgent> findByTenantAndStatus(UUID tenantId, AiAgent.Status status, int limit);
    void deleteById(UUID tenantId, UUID id);
}
