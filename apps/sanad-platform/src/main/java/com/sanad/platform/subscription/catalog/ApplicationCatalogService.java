package com.sanad.platform.subscription.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    /**
     * Governed catalog lifecycle (G1-B / design spec §12.4). Widened
     * ADDITIVELY: the legacy trio ACTIVE/INACTIVE/DEPRECATED is preserved
     * verbatim (existing DEPRECATED rows are never rewritten) and DRAFT /
     * ARCHIVED are admitted for create/archive/restore flows. Mirror of the
     * DB CHECK constraint ck_applications_status after V20260914_1.
     */
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ACTIVE", "INACTIVE", "DEPRECATED", "DRAFT", "ARCHIVED");
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
        } else {
            String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unsupported application status: " + request.getStatus()
                        + " (allowed: " + String.join(", ", ALLOWED_STATUSES.stream().sorted().toList()) + ")");
            }
            request.setStatus(status);
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
        if (changes.getLocalizedName() != null) {
            existing.setLocalizedName(changes.getLocalizedName());
        }
        if (changes.getDescription() != null) {
            existing.setDescription(changes.getDescription());
        }
        if (changes.getCategory() != null && !changes.getCategory().isBlank()) {
            existing.setCategory(changes.getCategory());
        }
        if (changes.getStatus() != null) {
            String status = changes.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unsupported application status: " + changes.getStatus()
                        + " (allowed: " + String.join(", ", ALLOWED_STATUSES.stream().sorted().toList()) + ")");
            }
            existing.setStatus(status);
        }
        // Version is not part of the public ApplicationRequest contract; never
        // erase the persisted version merely because a UI update omits it.
        if (changes.getVersion() != null) {
            existing.setVersion(changes.getVersion());
        }
        existing.setDisplayOrder(changes.getDisplayOrder());
        if (changes.getIconKey() != null) {
            existing.setIconKey(changes.getIconKey());
        }
        existing.setProvisioningMode(changes.getProvisioningMode() == null
                ? existing.getProvisioningMode() : changes.getProvisioningMode());
        if (changes.getSupportedCountries() != null) {
            existing.setSupportedCountries(changes.getSupportedCountries());
        }
        if (changes.getDependencies() != null) {
            existing.setDependencies(changes.getDependencies());
        }
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
