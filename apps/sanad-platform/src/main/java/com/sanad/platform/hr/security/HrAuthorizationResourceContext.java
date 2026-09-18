package com.sanad.platform.hr.security;

import java.time.LocalDate;
import java.util.UUID;

public record HrAuthorizationResourceContext(
        UUID tenantId,
        String resourceType,
        UUID resourceId,
        UUID personId,
        UUID employmentId,
        UUID assignmentId,
        UUID organizationId,
        UUID orgUnitId,
        UUID legalEntityId,
        String dataClassification,
        LocalDate resourceAsOf,
        UUID candidateId) {

    public HrAuthorizationResourceContext(
            UUID tenantId, String resourceType, UUID resourceId, UUID personId,
            UUID employmentId, UUID assignmentId, UUID organizationId, UUID orgUnitId,
            UUID legalEntityId, String dataClassification, LocalDate resourceAsOf) {
        this(tenantId, resourceType, resourceId, personId, employmentId, assignmentId,
                organizationId, orgUnitId, legalEntityId, dataClassification, resourceAsOf, null);
    }
}
