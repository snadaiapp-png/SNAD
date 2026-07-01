package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.dto.AuditEventResponse;
import com.sanad.platform.audit.mapper.AuditEventMapper;
import com.sanad.platform.audit.repository.AuditEventRepository;
import com.sanad.platform.security.tenant.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05 §22 — Tenant-scoped audit query service.
 *
 * <p>Reads audit events for the current tenant. The tenant ID is
 * taken from {@link TenantResolver#requireTenantId()} (verified
 * TenantContext), never from client-supplied parameters.</p>
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;
    private final AuditEventMapper mapper;
    private final TenantResolver tenantResolver;
    private final AuditIntegrityVerificationService integrityService;

    public AuditQueryService(AuditEventRepository repository,
                              AuditEventMapper mapper,
                              TenantResolver tenantResolver,
                              AuditIntegrityVerificationService integrityService) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantResolver = tenantResolver;
        this.integrityService = integrityService;
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> list(
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            AuditOutcome outcome,
            String correlationId,
            Instant from,
            Instant to,
            Pageable pageable) {
        UUID tenantId = tenantResolver.requireTenantId();
        Page<AuditEvent> page = repository.findFiltered(
                tenantId, actorUserId, action, resourceType, resourceId,
                outcome, correlationId, from, to, pageable);
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse getById(UUID id) {
        UUID tenantId = tenantResolver.requireTenantId();
        AuditEvent event = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Audit event not found with id: " + id));
        return mapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public AuditIntegrityVerificationService.VerificationResult verifyIntegrity() {
        UUID tenantId = tenantResolver.requireTenantId();
        return integrityService.verifyChain(tenantId);
    }
}
