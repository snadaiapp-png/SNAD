package com.sanad.platform.hr.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRepository {
    HrEmployee save(HrEmployee employee);
    Optional<HrEmployee> findById(UUID tenantId, UUID id);
    Optional<HrEmployee> findByUserId(UUID tenantId, UUID userId);
    List<HrEmployee> findAll(UUID tenantId, int limit, String search);
    List<HrEmployee> findActiveByDepartment(UUID tenantId, UUID departmentId);
    List<HrEmployee> findActiveByPosition(UUID tenantId, UUID positionId);
    List<HrEmployee> findActiveByUserIds(UUID tenantId, java.util.Collection<UUID> userIds);
    void delete(UUID tenantId, UUID id);
    long count(UUID tenantId);
}
