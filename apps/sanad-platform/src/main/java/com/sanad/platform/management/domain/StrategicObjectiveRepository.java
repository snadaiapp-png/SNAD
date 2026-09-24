package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link StrategicObjective} persistence.
 *
 * <p>Following the SNAD JdbcTemplate pattern (NOT JPA) for consistency
 * with the rest of the platform.
 */
public interface StrategicObjectiveRepository {

    StrategicObjective save(StrategicObjective objective);

    Optional<StrategicObjective> findById(UUID tenantId, UUID id);

    Optional<StrategicObjective> findByCode(UUID tenantId, String code);

    List<StrategicObjective> findByTenant(UUID tenantId, int limit);

    List<StrategicObjective> findByTenantAndStatus(UUID tenantId, StrategicObjective.Status status, int limit);

    List<StrategicObjective> findByParent(UUID tenantId, UUID parentId);

    List<StrategicObjective> findActiveObjectivesForPeriod(UUID tenantId, java.time.LocalDate asOf);

    void deleteById(UUID tenantId, UUID id);
}
