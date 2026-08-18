package com.sanad.platform.hr.domain;

import java.util.UUID;

public record HrDepartment(
    UUID id,
    UUID tenantId,
    String name,
    String code,
    String description,
    UUID parentDepartmentId,
    String status
) {}
