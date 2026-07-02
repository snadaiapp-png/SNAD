package com.sanad.platform.workshop.service;

import com.sanad.platform.workshop.repository.WorkshopActivityRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Service;

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
}
