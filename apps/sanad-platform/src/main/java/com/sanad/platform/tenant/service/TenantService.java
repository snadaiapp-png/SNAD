package com.sanad.platform.tenant.service;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.dto.CreateTenantRequest;
import com.sanad.platform.tenant.dto.TenantResponse;
import com.sanad.platform.tenant.dto.UpdateTenantRequest;
import com.sanad.platform.tenant.exception.TenantAlreadyExistsException;
import com.sanad.platform.tenant.mapper.TenantMapper;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the Tenant aggregate.
 *
 * <p>This is the entry point for all use cases involving Tenants. A
 * Tenant is the top-level isolation boundary in the SANAD platform —
 * unlike Organization, it has no parent aggregate, so its operations
 * are NOT tenant-scoped (the Tenant IS the scope).</p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>{@link #createTenant(CreateTenantRequest)} — register a new Tenant</li>
 *   <li>{@link #getTenant(UUID)} — fetch a single Tenant by ID</li>
 *   <li>{@link #listTenants()} — list all Tenants</li>
 *   <li>{@link #updateTenant(UUID, UpdateTenantRequest)} — rename a Tenant</li>
 *   <li>{@link #activateTenant(UUID)} — set status = ACTIVE</li>
 *   <li>{@link #deactivateTenant(UUID)} — set status = INACTIVE</li>
 *   <li>{@link #archiveTenant(UUID)} — soft delete via status = ARCHIVED</li>
 * </ul>
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
    }

    /**
     * Create a new Tenant. The subdomain must be globally unique.
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Objects.requireNonNull(request, "CreateTenantRequest must not be null");
        Objects.requireNonNull(request.getName(), "name must not be null");
        Objects.requireNonNull(request.getSubdomain(), "subdomain must not be null");

        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new TenantAlreadyExistsException(request.getSubdomain());
        }

        Tenant tenant = new Tenant(
                request.getName(),
                request.getSubdomain(),
                TenantStatus.ACTIVE
        );

        Tenant saved = tenantRepository.save(tenant);
        return tenantMapper.toResponse(saved);
    }

    /**
     * Fetch a single Tenant by ID.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public TenantResponse getTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantRepository.findById(tenantId)
                .map(tenantMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));
    }

    /**
     * List all Tenants.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toResponse)
                .toList();
    }

    /**
     * Update a Tenant's mutable field (name). Subdomain is immutable.
     */
    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "UpdateTenantRequest must not be null");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        tenant.setName(request.getName());
        Tenant saved = tenantRepository.save(tenant);
        return tenantMapper.toResponse(saved);
    }

    /**
     * Activate a Tenant (status = ACTIVE). Idempotent.
     */
    @Transactional
    public TenantResponse activateTenant(UUID tenantId) {
        return setStatus(tenantId, TenantStatus.ACTIVE);
    }

    /**
     * Deactivate a Tenant (status = SUSPENDED). Idempotent.
     */
    @Transactional
    public TenantResponse deactivateTenant(UUID tenantId) {
        return setStatus(tenantId, TenantStatus.SUSPENDED);
    }

    /**
     * Archive a Tenant (soft delete via status = ARCHIVED).
     *
     * <p>Idempotent: archiving an already-ARCHIVED Tenant returns the
     * current response without re-saving.</p>
     */
    @Transactional
    public TenantResponse archiveTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        if (tenant.getStatus() == TenantStatus.ARCHIVED) {
            return tenantMapper.toResponse(tenant);
        }

        tenant.setStatus(TenantStatus.ARCHIVED);
        Tenant saved = tenantRepository.save(tenant);
        return tenantMapper.toResponse(saved);
    }

    private TenantResponse setStatus(UUID tenantId, TenantStatus newStatus) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        tenant.setStatus(newStatus);
        Tenant saved = tenantRepository.save(tenant);
        return tenantMapper.toResponse(saved);
    }
}
