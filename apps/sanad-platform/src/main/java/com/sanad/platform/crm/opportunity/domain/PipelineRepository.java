package com.sanad.platform.crm.opportunity.domain;

import java.util.List;
import java.util.UUID;

public interface PipelineRepository {
    PipelineRecord findById(UUID tenantId, UUID pipelineId);
    List<PipelineRecord> findAll(UUID tenantId);
    PipelineRecord create(UUID tenantId, UUID actorId, CreatePipelineCommand command);
    PipelineRecord update(UUID tenantId, UUID actorId, UUID pipelineId, String name, String currencyCode, long expectedVersion);
    List<StageRecord> findStages(UUID tenantId, UUID pipelineId);

    record PipelineRecord(UUID id, long version, String name, String currencyCode, boolean active,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}
    record StageRecord(UUID id, UUID pipelineId, String name, int sequence, java.math.BigDecimal probability,
            String terminalState, boolean active) {}
    record CreatePipelineCommand(String name, String currencyCode, List<String> stages) {}
}
