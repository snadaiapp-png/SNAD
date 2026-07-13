package com.sanad.platform.crm.configuration.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CustomFieldRepository {
    CustomFieldRecord findById(UUID tenantId, UUID fieldId);
    List<CustomFieldRecord> findAll(UUID tenantId, String entityType);
    CustomFieldRecord create(UUID tenantId, UUID actorId, CreateCustomFieldCommand command);
    CustomFieldRecord update(UUID tenantId, UUID actorId, UUID fieldId, UpdateCustomFieldCommand command, long expectedVersion);
    Map<String, Object> readValues(UUID tenantId, String entityType, UUID entityId, boolean includeSensitive);
    Map<String, Object> upsertValues(UUID tenantId, UUID actorId, String entityType, UUID entityId, Map<String, Object> values);
    List<Map<String, Object>> searchValues(UUID tenantId, String entityType, String fieldKey, String query, int limit);

    record CustomFieldRecord(UUID id, long version, String entityType, String fieldKey, String labelAr,
            String labelEn, String dataType, boolean sensitive, boolean searchable, boolean required,
            boolean active, java.time.Instant createdAt, java.time.Instant updatedAt) {}
    record CreateCustomFieldCommand(String entityType, String fieldKey, String labelAr, String labelEn,
            String dataType, Boolean sensitive, Boolean searchable, Boolean required) {}
    record UpdateCustomFieldCommand(String labelAr, String labelEn, Boolean sensitive,
            Boolean searchable, Boolean required) {}
}
