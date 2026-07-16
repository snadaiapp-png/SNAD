package com.sanad.platform.crm.opportunity.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.CreateOpportunityCommand;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.UpdateOpportunityCommand;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OpportunityUseCases {
    private final OpportunityRepository oppRepo;
    private final PipelineRepository pipelineRepo;
    private final TimelineEventPort timeline;

    public OpportunityUseCases(OpportunityRepository oppRepo, PipelineRepository pipelineRepo,
                               TimelineEventPort timeline) {
        this.oppRepo = oppRepo;
        this.pipelineRepo = pipelineRepo;
        this.timeline = timeline;
    }

    @Transactional
    public OpportunityRecord create(UUID tenantId, UUID actorId, CreateOpportunityCommand cmd) {
        OpportunityRecord created = oppRepo.create(tenantId, actorId, cmd);
        timeline.record(tenantId, "OPPORTUNITY", created.id(), "crm.opportunity.created",
                "Opportunity created", "CRM_OPPORTUNITY", created.id(), actorId, Instant.now());
        return created;
    }

    public OpportunityRecord getById(UUID tenantId, UUID id) {
        return oppRepo.findById(tenantId, id);
    }

    public List<OpportunityRecord> list(UUID tenantId, int limit, UUID accountId) {
        return oppRepo.findAll(tenantId, limit, accountId);
    }

    @Transactional
    public OpportunityRecord update(UUID tenantId, UUID actorId, UUID id,
                                    UpdateOpportunityCommand cmd, long expectedVersion) {
        return oppRepo.update(tenantId, actorId, id, cmd, expectedVersion);
    }

    @Transactional
    public OpportunityRecord moveStage(UUID tenantId, UUID actorId, UUID id, UUID stageId,
                                       String status, String reason, long expectedVersion) {
        OpportunityRecord moved = oppRepo.moveStage(tenantId, actorId, id, stageId, status, reason,
                expectedVersion);
        timeline.record(tenantId, "OPPORTUNITY", id, "crm.opportunity.stage_changed",
                "Opportunity stage changed", "CRM_OPPORTUNITY", id, actorId, Instant.now());
        return moved;
    }

    public List<PipelineRecord> listPipelines(UUID tenantId) {
        return pipelineRepo.findAll(tenantId);
    }

    public PipelineRecord getPipeline(UUID tenantId, UUID pipelineId) {
        return pipelineRepo.findById(tenantId, pipelineId);
    }

    public List<StageRecord> listStages(UUID tenantId, UUID pipelineId) {
        return pipelineRepo.findStages(tenantId, pipelineId);
    }

    @Transactional
    public PipelineRecord createPipeline(UUID tenantId, UUID actorId,
                                         PipelineRepository.CreatePipelineCommand cmd) {
        return pipelineRepo.create(tenantId, actorId, cmd);
    }

    @Transactional
    public PipelineRecord updatePipeline(UUID tenantId, UUID actorId, UUID pipelineId,
                                         String name, String currencyCode, long expectedVersion) {
        return pipelineRepo.update(tenantId, actorId, pipelineId, name, currencyCode, expectedVersion);
    }
}
