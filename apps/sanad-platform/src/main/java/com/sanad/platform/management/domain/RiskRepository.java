package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRepository {
    Risk save(Risk risk);
    Optional<Risk> findById(UUID tenantId, UUID id);
    Optional<Risk> findByCode(UUID tenantId, String code);
    List<Risk> findByTenant(UUID tenantId, int limit);
    List<Risk> findByTenantAndStatus(UUID tenantId, Risk.Status status, int limit);
    List<Risk> findByTenantAndSeverity(UUID tenantId, Risk.Severity severity, int limit);
    void deleteById(UUID tenantId, UUID id);
}
