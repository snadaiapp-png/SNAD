package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical translation boundary between the mobile offline contract and the
 * PostgreSQL CRM schema. Keeping this mapping in one place prevents raw server
 * column names from leaking into SQLite and prevents legacy mobile aliases from
 * becoming invalid SQL column names on push.
 */
final class MobileSyncSchema {

    private MobileSyncSchema() {}

    static final Map<String, String> ENTITY_TABLES = Map.of(
        "account", "crm_accounts",
        "contact", "crm_contacts",
        "lead", "crm_leads",
        "opportunity", "crm_opportunities",
        "task", "crm_tasks",
        "note", "crm_notes",
        "activity", "crm_activities"
    );

    private static final Map<String, Map<String, String>> MOBILE_TO_DB = Map.of(
        "account", Map.ofEntries(
            Map.entry("name", "display_name"), Map.entry("display_name", "display_name"),
            Map.entry("status", "lifecycle_status"), Map.entry("lifecycle_status", "lifecycle_status"),
            Map.entry("owner_id", "owner_user_id"), Map.entry("owner_user_id", "owner_user_id"),
            Map.entry("source", "source"), Map.entry("account_type", "account_type"),
            Map.entry("parent_account_id", "parent_account_id"), Map.entry("primary_currency_code", "primary_currency_code"),
            Map.entry("preferred_locale", "preferred_locale"), Map.entry("time_zone", "time_zone")
        ),
        "contact", Map.ofEntries(
            Map.entry("account_id", "account_id"), Map.entry("first_name", "given_name"),
            Map.entry("given_name", "given_name"), Map.entry("last_name", "family_name"),
            Map.entry("family_name", "family_name"), Map.entry("email", "primary_email"),
            Map.entry("primary_email", "primary_email"), Map.entry("phone", "primary_phone"),
            Map.entry("primary_phone", "primary_phone"), Map.entry("status", "lifecycle_status"),
            Map.entry("lifecycle_status", "lifecycle_status"), Map.entry("owner_user_id", "owner_user_id"),
            Map.entry("preferred_locale", "preferred_locale"), Map.entry("time_zone", "time_zone"),
            Map.entry("consent_summary", "consent_summary")
        ),
        "lead", Map.ofEntries(
            Map.entry("display_name", "display_name"), Map.entry("company_name", "company_name"),
            Map.entry("email", "email"), Map.entry("phone", "phone"), Map.entry("source", "source"),
            Map.entry("status", "status"), Map.entry("owner_id", "owner_user_id"),
            Map.entry("owner_user_id", "owner_user_id"), Map.entry("queue_id", "queue_id"),
            Map.entry("score", "score")
        ),
        "opportunity", Map.ofEntries(
            Map.entry("account_id", "account_id"), Map.entry("contact_id", "contact_id"),
            Map.entry("pipeline_id", "pipeline_id"), Map.entry("stage_id", "stage_id"),
            Map.entry("title", "name"), Map.entry("name", "name"), Map.entry("amount", "amount"),
            Map.entry("currency_code", "currency_code"), Map.entry("probability", "probability"),
            Map.entry("forecast_category", "forecast_category"), Map.entry("close_date", "expected_close_date"),
            Map.entry("expected_close_date", "expected_close_date"), Map.entry("owner_user_id", "owner_user_id"),
            Map.entry("status", "status"), Map.entry("win_loss_reason", "win_loss_reason")
        ),
        "task", Map.ofEntries(
            Map.entry("title", "title"), Map.entry("description", "description"),
            Map.entry("related_type", "related_type"), Map.entry("related_id", "related_id"),
            Map.entry("assigned_to", "assignee_user_id"), Map.entry("assignee_user_id", "assignee_user_id"),
            Map.entry("owner_user_id", "owner_user_id"), Map.entry("status", "status"),
            Map.entry("priority", "priority"), Map.entry("start_at", "start_at"),
            Map.entry("due_date", "due_at"), Map.entry("due_at", "due_at"),
            Map.entry("completed_at", "completed_at"), Map.entry("result", "result")
        ),
        "note", Map.ofEntries(
            Map.entry("entity_type", "subject_type"), Map.entry("subject_type", "subject_type"),
            Map.entry("entity_id", "subject_id"), Map.entry("subject_id", "subject_id"),
            Map.entry("content", "body"), Map.entry("body", "body"),
            Map.entry("author_user_id", "author_user_id"), Map.entry("archived", "archived")
        ),
        "activity", Map.ofEntries(
            Map.entry("entity_type", "related_type"), Map.entry("related_type", "related_type"),
            Map.entry("entity_id", "related_id"), Map.entry("related_id", "related_id"),
            Map.entry("activity_type", "activity_type"), Map.entry("subject", "subject"),
            Map.entry("description", "body"), Map.entry("body", "body"),
            Map.entry("owner_user_id", "owner_user_id"), Map.entry("status", "status"),
            Map.entry("priority", "priority"), Map.entry("start_at", "start_at"),
            Map.entry("due_at", "due_at"), Map.entry("completed_at", "completed_at")
        )
    );

    private static final Set<String> UUID_COLUMNS = Set.of(
        "parent_account_id", "owner_user_id", "account_id", "queue_id", "contact_id", "pipeline_id",
        "stage_id", "related_id", "assignee_user_id", "subject_id", "author_user_id"
    );
    private static final Set<String> DATE_COLUMNS = Set.of("expected_close_date");
    private static final Set<String> TIMESTAMP_COLUMNS = Set.of("start_at", "due_at", "completed_at");

    static String tableFor(String entityType) {
        return ENTITY_TABLES.get(normalizeType(entityType));
    }

    static String dbColumn(String entityType, String mobileField) {
        return MOBILE_TO_DB.getOrDefault(normalizeType(entityType), Map.of()).get(mobileField);
    }

    static LinkedHashMap<String, Object> toDatabaseValues(String entityType, JsonNode payload) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (payload == null || !payload.isObject()) return result;
        payload.fields().forEachRemaining(entry -> {
            String dbColumn = dbColumn(entityType, entry.getKey());
            if (dbColumn != null) result.put(dbColumn, jdbcValue(dbColumn, entry.getValue()));
        });
        return result;
    }

    static void addDerivedValuesForCreate(String entityType, LinkedHashMap<String, Object> values) {
        switch (normalizeType(entityType)) {
            case "account" -> {
                String name = stringValue(values.get("display_name"));
                if (name != null) values.putIfAbsent("normalized_name", normalize(name));
                values.putIfAbsent("account_type", "PROSPECT");
                values.putIfAbsent("lifecycle_status", "ACTIVE");
            }
            case "contact" -> {
                String first = stringValue(values.get("given_name"));
                String last = stringValue(values.get("family_name"));
                String display = joinName(first, last);
                if (display != null) {
                    values.putIfAbsent("display_name", display);
                    values.putIfAbsent("normalized_name", normalize(display));
                }
                String email = stringValue(values.get("primary_email"));
                if (email != null) values.putIfAbsent("normalized_email", normalize(email));
                values.putIfAbsent("lifecycle_status", "ACTIVE");
                values.putIfAbsent("consent_summary", "UNKNOWN");
            }
            case "lead" -> {
                String display = stringValue(values.get("display_name"));
                if (display != null) values.putIfAbsent("normalized_name", normalize(display));
                String email = stringValue(values.get("email"));
                if (email != null) values.putIfAbsent("normalized_email", normalize(email));
                values.putIfAbsent("status", "NEW");
            }
            case "opportunity" -> values.putIfAbsent("status", "OPEN");
            case "task" -> {
                values.putIfAbsent("status", "OPEN");
                values.putIfAbsent("priority", 50L);
            }
            case "note" -> values.putIfAbsent("archived", false);
            case "activity" -> {
                values.putIfAbsent("status", "OPEN");
                values.putIfAbsent("priority", 50L);
            }
            default -> { }
        }
    }

    static String bindExpression(String column) {
        if (UUID_COLUMNS.contains(column)) return "?::UUID";
        if (DATE_COLUMNS.contains(column)) return "?";
        if (TIMESTAMP_COLUMNS.contains(column)) return "?";
        return "?";
    }

    static Object jdbcValue(String column, JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (UUID_COLUMNS.contains(column)) return UUID.fromString(value.asText());
        if (DATE_COLUMNS.contains(column)) return Date.valueOf(value.asText());
        if (TIMESTAMP_COLUMNS.contains(column)) return Timestamp.from(Instant.parse(value.asText()));
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber() || value.isBigDecimal()) return value.decimalValue();
        return value.asText();
    }

    /** Convert canonical PostgreSQL row JSON to the stable mobile SQLite contract. */
    static JsonNode toMobilePayload(String entityType, JsonNode server, ObjectMapper mapper) {
        if (server == null || !server.isObject()) return server;
        ObjectNode out = mapper.createObjectNode();
        copyCommon(server, out);
        switch (normalizeType(entityType)) {
            case "account" -> {
                copy(server, out, "display_name", "name");
                copy(server, out, "account_type", "account_type");
                copy(server, out, "lifecycle_status", "status");
                copy(server, out, "owner_user_id", "owner_id");
                copy(server, out, "source", "source");
            }
            case "contact" -> {
                copy(server, out, "account_id", "account_id");
                copy(server, out, "given_name", "first_name");
                copy(server, out, "family_name", "last_name");
                copy(server, out, "primary_email", "email");
                copy(server, out, "primary_phone", "phone");
                copy(server, out, "lifecycle_status", "status");
            }
            case "lead" -> {
                copy(server, out, "display_name", "display_name");
                copy(server, out, "company_name", "company_name");
                copy(server, out, "email", "email"); copy(server, out, "phone", "phone");
                copy(server, out, "status", "status"); copy(server, out, "source", "source");
            }
            case "opportunity" -> {
                copy(server, out, "account_id", "account_id"); copy(server, out, "contact_id", "contact_id");
                copy(server, out, "pipeline_id", "pipeline_id"); copy(server, out, "stage_id", "stage_id");
                copy(server, out, "name", "title"); copy(server, out, "amount", "amount");
                copy(server, out, "currency_code", "currency_code"); copy(server, out, "expected_close_date", "close_date");
                copy(server, out, "status", "status");
            }
            case "task" -> {
                copy(server, out, "title", "title"); copy(server, out, "description", "description");
                copy(server, out, "status", "status"); copy(server, out, "due_at", "due_date");
                copy(server, out, "assignee_user_id", "assigned_to"); copy(server, out, "priority", "priority");
            }
            case "note" -> {
                copy(server, out, "subject_type", "entity_type"); copy(server, out, "subject_id", "entity_id");
                copy(server, out, "body", "content"); copy(server, out, "archived", "archived");
            }
            case "activity" -> {
                copy(server, out, "related_type", "entity_type"); copy(server, out, "related_id", "entity_id");
                copy(server, out, "activity_type", "activity_type"); copy(server, out, "body", "description");
                copy(server, out, "subject", "subject"); copy(server, out, "status", "status");
            }
            default -> { }
        }
        return out;
    }

    static String softDeleteSetClause(String entityType) {
        return switch (normalizeType(entityType)) {
            case "account", "contact" -> "lifecycle_status = 'ARCHIVED', archived_at = NOW()";
            case "lead", "opportunity", "activity" -> "status = 'ARCHIVED'";
            case "task" -> "status = 'CANCELLED'";
            case "note" -> "archived = TRUE";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    private static void copyCommon(JsonNode in, ObjectNode out) {
        copy(in, out, "id", "id"); copy(in, out, "tenant_id", "tenant_id");
        copy(in, out, "sync_version", "sync_version"); copy(in, out, "created_at", "created_at");
        copy(in, out, "updated_at", "updated_at"); copy(in, out, "last_synced_at", "last_synced_at");
    }

    private static void copy(JsonNode in, ObjectNode out, String from, String to) {
        if (in.has(from) && !in.get(from).isNull()) out.set(to, in.get(from));
    }

    private static String normalizeType(String value) { return value == null ? "" : value.toLowerCase(); }
    private static String normalize(String value) { return value.trim().toLowerCase(); }
    private static String stringValue(Object value) { return value == null ? null : value.toString(); }
    private static String joinName(String first, String last) {
        String value = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return value.isEmpty() ? null : value;
    }
}
