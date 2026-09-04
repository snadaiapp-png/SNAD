package com.sanad.platform.subscription.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Catalog management for applications — create/update/list.
 *
 * <p>The catalog is data, not code: the executive console must derive its
 * application surfaces from this service (no hardcoded ERP/CRM/... lists).
 */
@Service
public class ApplicationCatalogService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String PROVISIONING_IMMEDIATE = "IMMEDIATE";

    private final ApplicationRepository repository;

    public ApplicationCatalogService(ApplicationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ApplicationEntity create(ApplicationEntity request) {
        String code = request.getCode() == null ? "" : request.getCode().trim().toUpperCase();
        if (code.isBlank()) {
            throw new IllegalArgumentException("Application code is required");
        }
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("Application code already exists: " + code);
        }
        request.setCode(code);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus(STATUS_ACTIVE);
        }
        if (request.getProvisioningMode() == null || request.getProvisioningMode().isBlank()) {
            request.setProvisioningMode(PROVISIONING_IMMEDIATE);
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            request.setCategory("MODULE");
        }
        request.setId(UUID.randomUUID());
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        repository.insert(request);
        return request;
    }

    @Transactional
    public ApplicationEntity update(UUID id, ApplicationEntity changes) {
        ApplicationEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown application: " + id));
        existing.setName(changes.getName());
        existing.setLocalizedName(changes.getLocalizedName());
        existing.setDescription(changes.getDescription());
        existing.setCategory(changes.getCategory());
        existing.setStatus(changes.getStatus() == null ? existing.getStatus() : changes.getStatus());
        existing.setVersion(changes.getVersion());
        existing.setDisplayOrder(changes.getDisplayOrder());
        existing.setIconKey(changes.getIconKey());
        existing.setProvisioningMode(changes.getProvisioningMode() == null
                ? existing.getProvisioningMode() : changes.getProvisioningMode());
        existing.setSupportedCountries(changes.getSupportedCountries());
        existing.setDependencies(changes.getDependencies());
        existing.setUpdatedAt(Instant.now());
        repository.update(existing);
        return existing;
    }

    @Transactional(readOnly = true)
    public List<ApplicationEntity> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ApplicationEntity> listAvailable() {
        return repository.findAvailable();
    }

    @Transactional(readOnly = true)
    public ApplicationEntity get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown application: " + id));
    }
}
