package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class CrossTenantAccessException extends OwnershipDomainException {
    public CrossTenantAccessException(String entity, UUID tenantId, UUID entityId) {
        super("Cross-tenant access rejected: entity=" + entity + " tenant=" + tenantId + " id=" + entityId);
    }
}
