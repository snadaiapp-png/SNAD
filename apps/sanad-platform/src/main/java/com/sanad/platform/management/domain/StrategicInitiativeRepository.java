package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link StrategicInitiative} persistence. */
public interface StrategicInitiativeRepository {

    StrategicInitiative save(StrategicInitiative initiative);

    Optional<StrategicInitiative> findById(UUID tenantId, UUID id);

    Optional<StrategicInitiative> findByCode(UUID tenantId, String code);

    List<StrategicInitiative> findByObjective(UUID tenantId, UUID objectiveId);

    List<StrategicInitiative> findByTenant(UUID tenantId, int limit);

    List<StrategicInitiative> findByTenantAndStatus(UUID tenantId, StrategicInitiative.Status status, int limit);

    void deleteById(UUID tenantId, UUID id);
}
