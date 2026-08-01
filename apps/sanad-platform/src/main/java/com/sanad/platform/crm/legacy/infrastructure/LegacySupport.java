package com.sanad.platform.crm.legacy.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class LegacySupport {

    private static final Logger log = LoggerFactory.getLogger(LegacySupport.class);
    static final int MAX_IMPORT_BYTES = 10 * 1024 * 1024;
    static final int MAX_EXPANDED_XLSX_BYTES = 50 * 1024 * 1024;
    static final int MAX_IMPORT_ROWS = 10_000;
    static final int MAX_IMPORT_COLUMNS = 100;
    static final Duration IMPORT_LEASE = Duration.ofMinutes(2);
    static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final Set<String> CUSTOM_TYPES =
            Set.of("TEXT", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "EMAIL", "URL");
    static final Set<String> TABLES = Set.of(
            "crm_accounts", "crm_contacts", "crm_leads", "crm_pipelines",
            "crm_opportunities", "crm_activities", "crm_import_jobs",
            "crm_custom_field_definitions");
    static final Map<String, String> ENTITY_TABLES = Map.of(
            "ACCOUNT", "crm_accounts",
            "CONTACT", "crm_contacts",
            "LEAD", "crm_leads",
            "OPPORTUNITY", "crm_opportunities",
            "ACTIVITY", "crm_activities");
    static final Map<String, Set<String>> IMPORT_FIELDS = Map.of(
            "ACCOUNT", Set.of("displayName", "accountType", "primaryCurrencyCode",
                    "preferredLocale", "timeZone", "source", "ownerUserId", "parentAccountId"),
            "CONTACT", Set.of("accountId", "givenName", "familyName", "primaryEmail",
                    "primaryPhone", "preferredLocale", "timeZone", "ownerUserId", "consentSummary"),
            "LEAD", Set.of("displayName", "companyName", "email", "phone", "source",
                    "ownerUserId", "queueId", "score"),
            "OPPORTUNITY", Set.of("accountId", "contactId", "pipelineId", "stageId",
                    "name", "amount", "currencyCode", "expectedCloseDate", "ownerUserId"),
            "ACTIVITY", Set.of("activityType", "subject", "body", "relatedType",
                    "relatedId", "ownerUserId", "priority", "startAt", "dueAt"));
    static final Map<String, Set<String>> REQUIRED_IMPORT_FIELDS = Map.of(
            "ACCOUNT", Set.of("displayName"),
            "CONTACT", Set.of("givenName"),
            "LEAD", Set.of("displayName"),
            "OPPORTUNITY", Set.of("accountId", "pipelineId", "stageId", "name", "currencyCode"),
            "ACTIVITY", Set.of("subject"));

    final NamedParameterJdbcTemplate jdbc;
    final ObjectMapper objectMapper;
    final TransactionTemplate transaction;
    final TransactionTemplate requiresNew;

    public LegacySupport(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Authentication helpers ─────────────────────────────────────────────

    public UUID tenantId(Authentication authentication) {
        return contextValue(authentication, "tenant_id");
    }

    public UUID userId(Authentication authentication) {
        return contextValue(authentication, "user_id");
    }

    private UUID contextValue(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public Map<String, Object> one(String table, UUID tenantId, UUID id, String message) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported CRM table");
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM " + table + " WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", id));
        } catch (EmptyResultDataAccessException exception) {
            throw missing(message);
        }
    }

    public long scalarLong(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    public void timeline(
            UUID tenantId, String subjectType, UUID subjectId, String eventType,
            String summary, String sourceType, UUID sourceId, UUID actorId, Instant now) {
        jdbc.update(
                "INSERT INTO crm_timeline_events " +
                        "(id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) " +
                        "VALUES (:id,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("subjectType", subjectType).addValue("subjectId", subjectId)
                        .addValue("eventType", eventType).addValue("summary", summary)
                        .addValue("sourceType", sourceType).addValue("sourceId", sourceId)
                        .addValue("now", Timestamp.from(now)).addValue("actorId", actorId));
    }

    public MapSqlParameterSource context(
            UUID tenantId, UUID actorId, UUID id, Instant now) {
        return p().addValue("tenantId", tenantId).addValue("actorId", actorId)
                .addValue("id", id).addValue("now", Timestamp.from(now));
    }

    // ── Validation helpers ─────────────────────────────────────────────────

    public void validateOwner(UUID tenantId, UUID ownerId) {
        if (ownerId == null) return;
        if (scalarLong(
                "SELECT COUNT(*) FROM users WHERE tenant_id=:tenantId AND id=:ownerId AND status='ACTIVE'",
                p().addValue("tenantId", tenantId).addValue("ownerId", ownerId)) != 1) {
            throw bad("CRM owner must be an active user in the same tenant");
        }
    }

    public void validateRelated(UUID tenantId, String relatedType, UUID relatedId) {
        if (relatedType == null && relatedId == null) return;
        if (relatedType == null || relatedId == null) {
            throw bad("relatedType and relatedId must be supplied together");
        }
        String table = switch (relatedType.trim().toUpperCase(Locale.ROOT)) {
            case "ACCOUNT" -> "crm_accounts";
            case "CONTACT" -> "crm_contacts";
            case "LEAD" -> "crm_leads";
            case "OPPORTUNITY" -> "crm_opportunities";
            default -> throw bad("Unsupported CRM relatedType");
        };
        one(table, tenantId, relatedId, "Related CRM record not found");
    }

    // ── Entity-type helpers ─────────────────────────────────────────────────

    public String importEntityType(String value) {
        String normalized = upper(value);
        if (normalized == null || !ENTITY_TABLES.containsKey(normalized)) {
            throw bad("Unsupported CRM entityType");
        }
        return normalized;
    }

    public String customType(String value) {
        String normalized = upper(value);
        if (normalized == null || !CUSTOM_TYPES.contains(normalized)) {
            throw bad("Unsupported CRM custom-field dataType");
        }
        return normalized;
    }

    // ── Import file helpers ─────────────────────────────────────────────────

    public byte[] importFileBytes(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("CRM import file is required");
        if (file.getSize() > MAX_IMPORT_BYTES) throw bad("CRM import file exceeds 10 MB");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_IMPORT_BYTES) {
                throw bad("CRM import file size is invalid");
            }
            return bytes;
        } catch (IOException exception) {
            throw bad("Unable to read CRM import file");
        }
    }

    public String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "crm-import" : filename.trim();
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        return truncate(value, 255);
    }

    public String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize CRM data", exception);
        }
    }

    public Map<String, String> readMapping(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("Stored CRM import mapping is invalid", exception);
        }
    }

    // ── Error helpers ──────────────────────────────────────────────────────

    public String errorCode(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return switch (statusException.getStatusCode().value()) {
                case 400 -> "VALIDATION_ERROR";
                case 404 -> "REFERENCE_NOT_FOUND";
                case 409 -> "CONFLICT";
                default -> "ROW_REJECTED";
            };
        }
        if (exception instanceof org.springframework.dao.DataIntegrityViolationException) return "DATABASE_CONSTRAINT";
        return "ROW_PROCESSING_ERROR";
    }

    public String errorMessage(RuntimeException exception) {
        String message;
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null) {
            message = statusException.getReason();
        } else {
            message = exception.getMessage();
        }
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return truncate(message.replaceAll("[\\r\\n\\t]+", " "), 1000);
    }

    // ── Value parsing helpers ───────────────────────────────────────────────

    public String required(Map<String, String> values, String key, int max) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw bad(key + " is required");
        return requireText(value.trim(), max);
    }

    public UUID uuid(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw bad(field + " must be a UUID");
        }
    }

    public BigDecimal decimal(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw bad(field + " must be numeric");
        }
    }

    public Integer integer(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw bad(field + " must be an integer");
        }
    }

    public LocalDate localDate(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw bad(field + " must use ISO date format");
        }
    }

    public OffsetDateTime offsetDateTime(String value, String field, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(field + " is required");
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw bad(field + " must use ISO offset date-time format");
        }
    }

    public String currency(String value) {
        String currency = value(value, "SAR").trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw bad("currency must contain three letters");
        return currency;
    }

    // ── Lease check ─────────────────────────────────────────────────────────

    public boolean leaseExpired(Object value) {
        if (value == null) return true;
        Instant instant;
        if (value instanceof Timestamp timestamp) instant = timestamp.toInstant();
        else if (value instanceof OffsetDateTime offsetDateTime) instant = offsetDateTime.toInstant();
        else instant = Instant.parse(value.toString());
        return instant.isBefore(Instant.now());
    }

    // ── Static helpers ──────────────────────────────────────────────────────

    public static MapSqlParameterSource p() {
        return new MapSqlParameterSource();
    }

    public static int limit(int requested) {
        return Math.max(1, Math.min(requested, 200));
    }

    public static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw bad(field + " exceeds " + max);
        return result;
    }

    public static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String upper(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public static String requireText(String value, int max) {
        if (value.isBlank() || value.length() > max) {
            throw bad("CRM field length is invalid");
        }
        return value;
    }

    public static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String canonical(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    public static String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public static UUID asUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    public static Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    // ── Inner records ───────────────────────────────────────────────────────

    public record ParsedTable(List<String> headers, List<Map<String, String>> rows) { }

    public record ImportPayload(
            UUID id, UUID tenantId, String entityType, UUID actorId,
            String filename, String contentType, String sha256,
            String mappingJson, int totalRows, long processedRows, byte[] content) { }

    public record CustomValue(
            String text, BigDecimal number, Boolean bool,
            Date date, Timestamp timestamp, String display) { }

    public static final class ImportLeaseLostException extends RuntimeException {
        public ImportLeaseLostException() {
            super("CRM import worker lease was lost");
        }
    }
}
