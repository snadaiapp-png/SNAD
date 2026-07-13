package com.sanad.platform.crm.configuration.application;

import com.sanad.platform.crm.configuration.domain.CustomFieldRepository;
import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.CustomFieldRecord;
import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.CreateCustomFieldCommand;
import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.UpdateCustomFieldCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigurationUseCases {
    private final CustomFieldRepository repo;
    public ConfigurationUseCases(CustomFieldRepository repo) { this.repo = repo; }
    @Transactional
    public CustomFieldRecord createField(UUID tenantId, UUID actorId, CreateCustomFieldCommand cmd) { return repo.create(tenantId, actorId, cmd); }
    public CustomFieldRecord getField(UUID tenantId, UUID fieldId) { return repo.findById(tenantId, fieldId); }
    public List<CustomFieldRecord> listFields(UUID tenantId, String entityType) { return repo.findAll(tenantId, entityType); }
    @Transactional
    public CustomFieldRecord updateField(UUID tenantId, UUID actorId, UUID fieldId, UpdateCustomFieldCommand cmd, long expectedVersion) { return repo.update(tenantId, actorId, fieldId, cmd, expectedVersion); }
    public Map<String, Object> readValues(UUID tenantId, String entityType, UUID entityId, boolean includeSensitive) { return repo.readValues(tenantId, entityType, entityId, includeSensitive); }
    @Transactional
    public Map<String, Object> upsertValues(UUID tenantId, UUID actorId, String entityType, UUID entityId, Map<String, Object> values) { return repo.upsertValues(tenantId, actorId, entityType, entityId, values); }
    public List<Map<String, Object>> searchValues(UUID tenantId, String entityType, String fieldKey, String query, int limit) { return repo.searchValues(tenantId, entityType, fieldKey, query, limit); }
}
