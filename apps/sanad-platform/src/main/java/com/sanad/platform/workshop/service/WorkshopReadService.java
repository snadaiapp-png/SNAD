package com.sanad.platform.workshop.service;

import com.sanad.platform.workshop.dto.WorkshopDtos;
import com.sanad.platform.workshop.repository.WorkshopActivityRepository;
import com.sanad.platform.workshop.repository.WorkshopDependencyRepository;
import com.sanad.platform.workshop.repository.WorkshopRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkshopReadService {
    private final WorkshopRepository workshops;
    private final WorkshopWorkItemRepository items;
    private final WorkshopDependencyRepository dependencies;
    private final WorkshopActivityRepository activities;
    private final WorkshopAggregateLoader loader;
    private final WorkshopMapper mapper;

    public WorkshopReadService(WorkshopRepository workshops,
                               WorkshopWorkItemRepository items,
                               WorkshopDependencyRepository dependencies,
                               WorkshopActivityRepository activities,
                               WorkshopAggregateLoader loader,
                               WorkshopMapper mapper) {
        this.workshops = workshops;
        this.items = items;
        this.dependencies = dependencies;
        this.activities = activities;
        this.loader = loader;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<WorkshopDtos.WorkshopResponse> list(UUID tenantId, Pageable pageable) {
        return workshops.findByTenantId(tenantId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkshopDtos.WorkshopResponse get(UUID tenantId, UUID workshopId) {
        return mapper.toResponse(loader.requireWorkshop(tenantId, workshopId));
    }

    @Transactional(readOnly = true)
    public WorkshopDtos.BoardResponse board(UUID tenantId, UUID workshopId) {
        var workshop = loader.requireWorkshop(tenantId, workshopId);
        return new WorkshopDtos.BoardResponse(
                mapper.toResponse(workshop),
                items.findByTenantIdAndWorkshopIdOrderBySequenceNoAscCreatedAtAsc(tenantId, workshopId)
                        .stream().map(mapper::toResponse).toList(),
                List.of(),
                dependencies.findByTenantIdAndWorkshopId(tenantId, workshopId)
                        .stream().map(mapper::toResponse).toList(),
                activities.findByTenantIdAndWorkshopId(tenantId, workshopId)
                        .stream().map(mapper::toResponse).toList());
    }
}
