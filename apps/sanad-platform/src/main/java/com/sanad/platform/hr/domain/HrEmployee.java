package com.sanad.platform.hr.domain;

import java.time.LocalDate;
import java.util.UUID;

public record HrEmployee(
    UUID id,
    UUID tenantId,
    UUID userId,
    String employeeNumber,
    String firstName,
    String lastName,
    String displayName,
    String email,
    String phone,
    UUID departmentId,
    UUID positionId,
    UUID managerId,
    String employmentType,
    String status,
    LocalDate hireDate,
    LocalDate terminationDate
) {}
