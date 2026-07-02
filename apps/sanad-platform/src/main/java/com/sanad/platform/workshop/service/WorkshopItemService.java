package com.sanad.platform.workshop.service;

import com.sanad.platform.workshop.domain.Workshop;
import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import com.sanad.platform.workshop.dto.WorkshopDtos;
import com.sanad.platform.workshop.repository.WorkshopActivityRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkshopItemService {
    private final WorkshopWorkItemRepository items;
    private final WorkshopActivityRepository activities;
    private final WorkshopAggregateLoader loader;
    private final WorkshopGraphPolicy graphPolicy;
    private final WorkshopMapper mapper;

    public WorkshopItemService(WorkshopWorkItemRepository items,
                               WorkshopActivityRepository activities,
                               WorkshopAggregateLoader loader,
                               WorkshopGraphPolicy graphPolicy,
                               WorkshopMapper mapper) {
        this.items = items;
        this.activities = activities;
        this.loader = loader;
        this.graphPolicy = graphPolicy;
        this.mapper = mapper;
    }

    @Transactional
    public WorkshopDtos.WorkItemResponse create(UUID tenantId, UUID userId, UUID workshopId,
                                                 WorkshopDtos.CreateWorkItemRequest request) {
        Workshop workshop = loader.requireWorkshop(tenantId, workshopId);
        workshop.ensureExecutionOpen();
        WorkshopWorkItem value = new WorkshopWorkItem(workshop.getTenant(), workshop, null,
                request.code(), request.title(), request.description(), request.priority(), request.dueAt(),
                request.estimatedMinutes(), request.sequenceNo() == null ? 0 : request.sequenceNo(), userId);
        return mapper.toResponse(items.saveAndFlush(value));
    }
}
