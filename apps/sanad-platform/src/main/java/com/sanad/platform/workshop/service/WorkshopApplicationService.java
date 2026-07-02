package com.sanad.platform.workshop.service;

import com.sanad.platform.user.repository.UserRepository;
import com.sanad.platform.workshop.repository.WorkshopActivityRepository;
import com.sanad.platform.workshop.repository.WorkshopAssignmentRepository;
import com.sanad.platform.workshop.repository.WorkshopDependencyRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkshopApplicationService {
    private final WorkshopAssignmentRepository assignments;
    private final WorkshopDependencyRepository dependencies;
    private final WorkshopActivityRepository activities;
    private final WorkshopWorkItemRepository items;
    private final UserRepository users;
    private final WorkshopAggregateLoader loader;
    private final WorkshopGraphPolicy graphPolicy;
    private final WorkshopMapper mapper;

    public WorkshopApplicationService(WorkshopAssignmentRepository assignments,
                                      WorkshopDependencyRepository dependencies,
                                      WorkshopActivityRepository activities,
                                      WorkshopWorkItemRepository items,
                                      UserRepository users,
                                      WorkshopAggregateLoader loader,
                                      WorkshopGraphPolicy graphPolicy,
                                      WorkshopMapper mapper) {
        this.assignments = assignments;
        this.dependencies = dependencies;
        this.activities = activities;
        this.items = items;
        this.users = users;
        this.loader = loader;
        this.graphPolicy = graphPolicy;
        this.mapper = mapper;
    }
}
