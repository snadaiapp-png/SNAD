package com.sanad.platform.hr.domain;

import java.util.UUID;

public record HrPosition(
    UUID id,
    UUID tenantId,
    String title,
    String code,
    String description,
    UUID departmentId,
    String grade,
    String status
) {}
