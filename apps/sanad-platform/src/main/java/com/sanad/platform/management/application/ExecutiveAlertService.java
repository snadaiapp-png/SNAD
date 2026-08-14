package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link ExecutiveAlert} management.
 *
 * <p>Supports deduplication: only one alert per (source_entity, type).
 * If an alert already exists for the same source, it is NOT duplicated.
 */
@Service
public class ExecutiveAlertService {

    private final ExecutiveAlertRepository alertRepo;
    private final ManagementAuditRepository auditRepo;

    public ExecutiveAlertService(
            ExecutiveAlertRepository alertRepo,
            ManagementAuditRepository auditRepo) {
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
    }

    /**
     * Create an alert if one doesn't already exist for the same source entity + type.
     * This is idempotent: calling with the same source + type returns the existing alert.
     */
    @Transactional
    public ExecutiveAlert createOrGetExisting(
            UUID tenantId, ExecutiveAlert.AlertType type, ExecutiveAlert.Severity severity,
            ExecutiveAlert.SourceEntityType sourceType, UUID sourceId,
            String title, String description, UUID createdBy) {
        // Check for existing alert (deduplication)
        var existing = alertRepo.findBySource(tenantId, sourceType, sourceId, type);
        if (existing.isPresent()) {
            return existing.get();
        }
        var alert = ExecutiveAlert.create(
                tenantId, type, severity, sourceType, sourceId, title, description, createdBy
        );
        var saved = alertRepo.save(alert);
        auditRepo.save(ManagementAuditEntry.create(
                tenantId, createdBy,
                ManagementAuditEntry.EntityType.ESCALATION, saved.id(),
                ManagementAuditEntry.Action.CREATE, null, saved.status().name(), null, null
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ExecutiveAlert> findById(UUID tenantId, UUID id) {
        return alertRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<ExecutiveAlert> findByTenant(UUID tenantId, int limit) {
        return alertRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<ExecutiveAlert> findOpenAlerts(UUID tenantId, int limit) {
        return alertRepo.findByTenantAndStatus(tenantId, ExecutiveAlert.Status.OPEN, limit);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ExecutiveAlert> findBySource(
            UUID tenantId, ExecutiveAlert.SourceEntityType sourceType,
            UUID sourceId, ExecutiveAlert.AlertType type) {
        return alertRepo.findBySource(tenantId, sourceType, sourceId, type);
    }

    @Transactional
    public ExecutiveAlert acknowledge(UUID tenantId, UUID id, UUID userId) {
        var alert = alertRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        return alertRepo.save(alert.acknowledge(userId));
    }

    @Transactional
    public ExecutiveAlert resolve(UUID tenantId, UUID id, String resolution, UUID userId) {
        var alert = alertRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        return alertRepo.save(alert.resolve(userId, resolution));
    }

    @Transactional
    public ExecutiveAlert dismiss(UUID tenantId, UUID id, String reason, UUID userId) {
        var alert = alertRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        return alertRepo.save(alert.dismiss(userId, reason));
    }
}
