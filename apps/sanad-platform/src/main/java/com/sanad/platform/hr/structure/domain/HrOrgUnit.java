package com.sanad.platform.hr.structure.domain;

import java.util.UUID;

/**
 * Stable Org Unit identity. The versioned attributes (name, code, type,
 * parent) live in {@link HrOrgUnitVersion}.
 *
 * @param id              stable Org Unit UUID
 * @param tenantId        owning tenant
 * @param organizationId  owning Organization
 * @param stableCode      tenant+org-unique stable code
 */
public record HrOrgUnit(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String stableCode
) {}
