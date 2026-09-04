package com.sanad.platform.hr.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRepository {
    HrEmployee save(HrEmployee employee);
    Optional<HrEmployee> findById(UUID tenantId, UUID id);
    List<HrEmployee> findAll(UUID tenantId, int limit, String search);
    long count(UUID tenantId);
}
