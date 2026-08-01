package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.CreateCustomFieldRequest;
import com.sanad.platform.crm.web.UpdateCustomFieldValuesRequest;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyCustomFieldService {

    private final LegacySupport support;
    private final LegacyEncryptionService encryptionService;

    public LegacyCustomFieldService(LegacySupport support, LegacyEncryptionService encryptionService) {
        this.support = support;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public Map<String, Object> createCustomField(
            Authentication authentication, CreateCustomFieldRequest request) {
        UUID tenantId = support.tenantId(authentication);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String entityType = support.importEntityType(request.entityType());
        String dataType = support.customType(request.dataType());
        boolean sensitive = Boolean.TRUE.equals(request.sensitive());
        boolean searchable = Boolean.TRUE.equals(request.searchable());
        if (sensitive && searchable) {
            throw bad("Sensitive CRM custom fields cannot be searchable");
        }
        try {
            support.jdbc.update(
                    "INSERT INTO crm_custom_field_definitions " +
                            "(id,tenant_id,entity_type,field_key,label_ar,label_en,data_type,sensitive,searchable," +
                            "required,active,created_at) " +
                            "VALUES (:id,:tenantId,:entityType,:fieldKey,:labelAr,:labelEn,:dataType,:sensitive," +
                            ":searchable,:required,TRUE,:now)",
                    p().addValue("id", id).addValue("tenantId", tenantId)
                            .addValue("entityType", entityType)
                            .addValue("fieldKey", request.fieldKey().trim())
                            .addValue("labelAr", request.labelAr().trim())
                            .addValue("labelEn", request.labelEn().trim())
                            .addValue("dataType", dataType).addValue("sensitive", sensitive)
                            .addValue("searchable", searchable)
                            .addValue("required", Boolean.TRUE.equals(request.required()))
                            .addValue("now", Timestamp.from(now)));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CRM custom field key already exists or violates its constraints");
        }
        return support.one("crm_custom_field_definitions", tenantId, id, "CRM custom field not found");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCustomFields(
            Authentication authentication, String entityType) {
        UUID tenantId = support.tenantId(authentication);
        if (entityType == null || entityType.isBlank()) {
            return support.jdbc.queryForList(
                    "SELECT * FROM crm_custom_field_definitions " +
                            "WHERE tenant_id=:tenantId AND active=TRUE ORDER BY entity_type,field_key",
                    p().addValue("tenantId", tenantId));
        }
        return activeCustomDefinitions(tenantId, support.importEntityType(entityType));
    }

    @Transactional
    public Map<String, Object> upsertCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            UUID entityId, UpdateCustomFieldValuesRequest request) {
        UUID tenantId = support.tenantId(authentication);
        String entityType = support.importEntityType(requestedEntityType);
        upsertCustomFieldValuesInternal(
                tenantId, support.userId(authentication), entityType, entityId, request.values(), true);
        return readCustomFieldValuesInternal(tenantId, entityType, entityId, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            UUID entityId, boolean includeSensitive) {
        UUID tenantId = support.tenantId(authentication);
        String entityType = support.importEntityType(requestedEntityType);
        assertEntityExists(tenantId, entityType, entityId);
        return readCustomFieldValuesInternal(tenantId, entityType, entityId, includeSensitive);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchCustomFieldValues(
            Authentication authentication, String requestedEntityType,
            String fieldKey, String query, int requestedLimit) {
        UUID tenantId = support.tenantId(authentication);
        String entityType = support.importEntityType(requestedEntityType);
        String normalized = normalizeSearch(query);
        if (normalized.isBlank()) throw bad("CRM custom-field search query is required");
        return support.jdbc.queryForList(
                "SELECT value.entity_id,definition.field_key,definition.data_type,value.searchable_value " +
                        "FROM crm_custom_field_values value " +
                        "JOIN crm_custom_field_definitions definition " +
                        "ON definition.tenant_id=value.tenant_id AND definition.id=value.definition_id " +
                        "WHERE value.tenant_id=:tenantId AND value.entity_type=:entityType " +
                        "AND definition.field_key=:fieldKey AND definition.active=TRUE " +
                        "AND definition.searchable=TRUE AND definition.sensitive=FALSE " +
                        "AND value.searchable_value LIKE :query " +
                        "ORDER BY value.updated_at DESC,value.entity_id LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("fieldKey", fieldKey.trim())
                        .addValue("query", "%" + normalized + "%")
                        .addValue("limit", limit(requestedLimit)));
    }

    @Transactional
    public Map<String, Object> updateCustomField(
            Authentication authentication, UUID customFieldId,
            String labelAr, String labelEn, Boolean required,
            Boolean searchable, Boolean sensitive, long expectedVersion) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", customFieldId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(now))
                .addValue("labelAr", labelAr)
                .addValue("labelEn", labelEn)
                .addValue("required", required)
                .addValue("searchable", searchable)
                .addValue("sensitive", sensitive);
        StringBuilder sql = new StringBuilder("UPDATE crm_custom_field_definitions SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (labelAr != null) { sql.append(", label_ar = :labelAr"); }
        if (labelEn != null) { sql.append(", label_en = :labelEn"); }
        if (required != null) { sql.append(", required = :required"); }
        if (searchable != null) { sql.append(", searchable = :searchable"); }
        if (sensitive != null) { sql.append(", sensitive = :sensitive"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = support.jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return support.jdbc.queryForMap("SELECT * FROM crm_custom_field_definitions WHERE tenant_id = :tenantId AND id = :id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", customFieldId));
    }

    // ── Package-private methods (used by LegacyImportService) ──────────────

    void upsertCustomFieldValuesInternal(
            UUID tenantId, UUID actorId, String entityType, UUID entityId,
            Map<String, ?> requestedValues, boolean enforceRequired) {
        assertEntityExists(tenantId, entityType, entityId);
        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Map<String, Object> definition : activeCustomDefinitions(tenantId, entityType)) {
            definitions.put(String.valueOf(definition.get("field_key")), definition);
        }
        for (Map.Entry<String, ?> entry : requestedValues.entrySet()) {
            Map<String, Object> definition = definitions.get(entry.getKey());
            if (definition == null) {
                throw bad("Unknown active CRM custom field: " + entry.getKey());
            }
            UUID definitionId = asUuid(definition.get("id"));
            Object raw = entry.getValue();
            if (raw == null || raw instanceof String text && text.isBlank()) {
                support.jdbc.update(
                        "DELETE FROM crm_custom_field_values " +
                                "WHERE tenant_id=:tenantId AND definition_id=:definitionId AND entity_id=:entityId",
                        p().addValue("tenantId", tenantId).addValue("definitionId", definitionId)
                                .addValue("entityId", entityId));
                continue;
            }
            CustomValue value = customValue(String.valueOf(definition.get("data_type")), raw);
            boolean sensitive = Boolean.TRUE.equals(definition.get("sensitive"));
            boolean searchable = Boolean.TRUE.equals(definition.get("searchable"));
            String textValue = value.text();
            BigDecimal numberValue = value.number();
            Boolean booleanValue = value.bool();
            Date dateValue = value.date();
            Timestamp timestampValue = value.timestamp();
            if (sensitive) {
                textValue = encryptionService.encryptSensitive(value.display());
                numberValue = null;
                booleanValue = null;
                dateValue = null;
                timestampValue = null;
            }
            String searchableValue =
                    searchable && !sensitive ? normalizeSearch(value.display()) : null;
            support.jdbc.update(
                    "DELETE FROM crm_custom_field_values " +
                            "WHERE tenant_id=:tenantId AND definition_id=:definitionId AND entity_id=:entityId",
                    p().addValue("tenantId", tenantId).addValue("definitionId", definitionId)
                            .addValue("entityId", entityId));
            Instant now = Instant.now();
            support.jdbc.update(
                    "INSERT INTO crm_custom_field_values " +
                            "(id,tenant_id,definition_id,entity_type,entity_id,value_text,value_number,value_boolean," +
                            "value_date,value_timestamp,searchable_value,created_by,updated_by,created_at,updated_at) " +
                            "VALUES (:id,:tenantId,:definitionId,:entityType,:entityId,:text,:number,:bool,:date," +
                            ":timestamp,:searchable,:actorId,:actorId,:now,:now)",
                    p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("definitionId", definitionId).addValue("entityType", entityType)
                            .addValue("entityId", entityId).addValue("text", textValue)
                            .addValue("number", numberValue).addValue("bool", booleanValue)
                            .addValue("date", dateValue).addValue("timestamp", timestampValue)
                            .addValue("searchable", searchableValue).addValue("actorId", actorId)
                            .addValue("now", Timestamp.from(now)));
        }
        if (enforceRequired) assertRequiredCustomFields(tenantId, entityType, entityId);
    }

    Map<String, Object> readCustomFieldValuesInternal(
            UUID tenantId, String entityType, UUID entityId, boolean includeSensitive) {
        List<Map<String, Object>> rows = support.jdbc.queryForList(
                "SELECT definition.field_key,definition.data_type,definition.sensitive,definition.required," +
                        "value.value_text,value.value_number,value.value_boolean,value.value_date,value.value_timestamp " +
                        "FROM crm_custom_field_definitions definition " +
                        "LEFT JOIN crm_custom_field_values value " +
                        "ON value.tenant_id=definition.tenant_id AND value.definition_id=definition.id " +
                        "AND value.entity_id=:entityId " +
                        "WHERE definition.tenant_id=:tenantId AND definition.entity_type=:entityType " +
                        "AND definition.active=TRUE ORDER BY definition.field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("entityId", entityId));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object value = firstNonNull(
                    row.get("value_text"), row.get("value_number"), row.get("value_boolean"),
                    row.get("value_date"), row.get("value_timestamp"));
            if (Boolean.TRUE.equals(row.get("sensitive")) && value != null) {
                value = includeSensitive ? encryptionService.decryptSensitive(String.valueOf(value)) : "[REDACTED]";
            }
            result.put(String.valueOf(row.get("field_key")), value);
        }
        return result;
    }

    boolean hasRequiredCustomFields(UUID tenantId, String entityType) {
        return support.scalarLong(
                "SELECT COUNT(*) FROM crm_custom_field_definitions " +
                        "WHERE tenant_id=:tenantId AND entity_type=:entityType AND active=TRUE AND required=TRUE",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)) > 0;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private List<Map<String, Object>> activeCustomDefinitions(UUID tenantId, String entityType) {
        return support.jdbc.queryForList(
                "SELECT * FROM crm_custom_field_definitions " +
                        "WHERE tenant_id=:tenantId AND entity_type=:entityType AND active=TRUE ORDER BY field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType));
    }

    private void assertRequiredCustomFields(UUID tenantId, String entityType, UUID entityId) {
        List<String> missingFields = support.jdbc.queryForList(
                "SELECT definition.field_key FROM crm_custom_field_definitions definition " +
                        "LEFT JOIN crm_custom_field_values value " +
                        "ON value.tenant_id=definition.tenant_id AND value.definition_id=definition.id " +
                        "AND value.entity_id=:entityId " +
                        "WHERE definition.tenant_id=:tenantId AND definition.entity_type=:entityType " +
                        "AND definition.active=TRUE AND definition.required=TRUE AND value.id IS NULL " +
                        "ORDER BY definition.field_key",
                p().addValue("tenantId", tenantId).addValue("entityType", entityType)
                        .addValue("entityId", entityId), String.class);
        if (!missingFields.isEmpty()) {
            throw bad("Missing required CRM custom fields: " + String.join(", ", missingFields));
        }
    }

    private void assertEntityExists(UUID tenantId, String entityType, UUID entityId) {
        String table = ENTITY_TABLES.get(entityType);
        if (table == null) throw bad("Unsupported CRM entityType");
        if (support.scalarLong(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=:tenantId AND id=:entityId",
                p().addValue("tenantId", tenantId).addValue("entityId", entityId)) != 1) {
            throw missing("CRM entity not found");
        }
    }

    private CustomValue customValue(String requestedType, Object raw) {
        String dataType = support.customType(requestedType);
        String text = String.valueOf(raw).trim();
        try {
            return switch (dataType) {
                case "TEXT" -> new CustomValue(requireText(text, 4000), null, null, null, null, text);
                case "EMAIL" -> {
                    if (text.length() > 255 || !EMAIL.matcher(text).matches()) {
                        throw bad("Invalid CRM custom-field email");
                    }
                    yield new CustomValue(
                            text.toLowerCase(Locale.ROOT), null, null, null, null, text);
                }
                case "URL" -> {
                    java.net.URI uri = java.net.URI.create(text);
                    if (text.length() > 1000 || uri.getScheme() == null
                            || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                        throw bad("Invalid CRM custom-field URL");
                    }
                    yield new CustomValue(text, null, null, null, null, text);
                }
                case "NUMBER" -> {
                    BigDecimal number = new BigDecimal(text);
                    yield new CustomValue(null, number, null, null, null, number.toPlainString());
                }
                case "BOOLEAN" -> {
                    String normalized = text.toLowerCase(Locale.ROOT);
                    if (!Set.of("true", "false").contains(normalized)) {
                        throw bad("Invalid CRM custom-field boolean");
                    }
                    Boolean bool = Boolean.valueOf(normalized);
                    yield new CustomValue(null, null, bool, null, null, bool.toString());
                }
                case "DATE" -> {
                    LocalDate date = LocalDate.parse(text);
                    yield new CustomValue(null, null, null, Date.valueOf(date), null, date.toString());
                }
                case "DATETIME" -> {
                    Instant instant = OffsetDateTime.parse(text).toInstant();
                    yield new CustomValue(
                            null, null, null, null, Timestamp.from(instant), instant.toString());
                }
                default -> throw bad("Unsupported CRM custom-field dataType");
            };
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw bad("Invalid value for CRM custom-field type " + dataType);
        }
    }
}
