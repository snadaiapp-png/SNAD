package com.sanad.platform.workshop.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.shared.api.exceptions.ConflictException;
import com.sanad.platform.shared.api.exceptions.ResourceNotFoundException;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.workshop.domain.Workshop;
import com.sanad.platform.workshop.dto.WorkshopDtos;
import com.sanad.platform.workshop.repository.WorkshopRepository;
import com.sanad.platform.workshop.repository.WorkshopWorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Transactional
    public WorkshopDtos.WorkshopResponse create(UUID tenantId, UUID userId,
                                                 WorkshopDtos.CreateWorkshopRequest request) {
        String code = request.code().trim().toUpperCase();
        if (workshops.existsByTenantIdAndCode(tenantId, code)) {
            throw new ConflictException("workshop", java.util.Map.of("code", code));
        }
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("tenant", tenantId));
        Organization organization = organizations.findByTenantIdAndId(tenantId, request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("organization", request.organizationId()));
        Workshop value = new Workshop(tenant, organization, code, request.name(), request.description(),
                request.plannedStart(), request.plannedEnd(), userId);
        return mapper.toResponse(workshops.saveAndFlush(value));
    }
}
