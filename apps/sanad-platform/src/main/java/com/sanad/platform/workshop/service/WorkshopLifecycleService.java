package com.sanad.platform.workshop.service;

import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.workshop.repository.WorkshopRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkshopLifecycleService {
    private final WorkshopRepository workshops;
    private final WorkshopWorkItemRepository items;
    private final TenantRepository tenants;
    private final OrganizationRepository organizations;
    private final WorkshopAggregateLoader loader;
    private final WorkshopMapper mapper;

    public WorkshopLifecycleService(WorkshopRepository workshops,
                                    WorkshopWorkItemRepository items,
                                    TenantRepository tenants,
                                    OrganizationRepository organizations,
                                    WorkshopAggregateLoader loader,
                                    WorkshopMapper mapper) {
        this.workshops = workshops;
        this.items = items;
        this.tenants = tenants;
        this.organizations = organizations;
        this.loader = loader;
        this.mapper = mapper;
    }
}
