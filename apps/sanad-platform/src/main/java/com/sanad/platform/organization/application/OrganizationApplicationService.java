package com.sanad.platform.organization.application;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationApplicationService {

    private final OrganizationRepository organizationRepository;

    public Organization create(UUID tenantId, String name, String description) {
        if (organizationRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new IllegalStateException("Organization already exists for tenant");
        }

        Organization org = new Organization();
        org.setTenantId(tenantId);
        org.setName(name);
        org.setDescription(description);
        org.setStatus(OrganizationStatus.ACTIVE);

        return organizationRepository.save(org);
    }

    public Organization get(UUID tenantId, UUID id) {
        return organizationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalStateException("Organization not found"));
    }

    public List<Organization> list(UUID tenantId) {
        return organizationRepository.findByTenantId(tenantId);
    }

    public Organization suspend(UUID tenantId, UUID id) {
        Organization org = get(tenantId, id);
        org.setStatus(OrganizationStatus.INACTIVE);
        return organizationRepository.save(org);
    }

    public Organization activate(UUID tenantId, UUID id) {
        Organization org = get(tenantId, id);
        org.setStatus(OrganizationStatus.ACTIVE);
        return organizationRepository.save(org);
    }
}