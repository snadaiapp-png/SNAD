package com.sanad.platform.crm.query.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only Customer 360 query port.
 * Composes data from multiple read models — never writes.
 */
public interface Customer360QueryPort {
    Map<String, Object> getCustomer360(UUID tenantId, UUID accountId);
}
