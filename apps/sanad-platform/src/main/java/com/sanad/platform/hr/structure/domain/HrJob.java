package com.sanad.platform.hr.structure.domain;

import java.util.UUID;

/**
 * Stable Job identity. The versioned attributes (title, description, grade)
 * live in {@link HrJobVersion}.
 *
 * @param id              stable Job UUID
 * @param tenantId        owning tenant
 * @param organizationId  owning Organization
 * @param stableCode      tenant+org-unique stable code
 */
public record HrJob(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String stableCode
) {}
