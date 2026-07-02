package com.sanad.platform.workshop.service;

import com.sanad.platform.shared.api.exceptions.ResourceNotFoundException;
import com.sanad.platform.workshop.domain.Workshop;
import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import com.sanad.platform.workshop.repository.WorkshopRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkshopAggregateLoader {

    private final WorkshopRepository workshopRepository;
    private final WorkshopWorkItemRepository workItemRepository;

    public WorkshopAggregateLoader(WorkshopRepository workshopRepository,
                                   WorkshopWorkItemRepository workItemRepository) {
        this.workshopRepository = workshopRepository;
        this.workItemRepository = workItemRepository;
    }

    public Workshop requireWorkshop(UUID tenantId, UUID workshopId) {
        return workshopRepository.findByTenantIdAndId(tenantId, workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("workshop", workshopId));
    }

    public WorkshopWorkItem requireItem(UUID tenantId, UUID workshopId, UUID itemId) {
        return workItemRepository.findByTenantIdAndWorkshopIdAndId(tenantId, workshopId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("work item", itemId));
    }
}
