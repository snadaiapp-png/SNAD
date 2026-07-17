package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.AddressRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CommunicationMethodRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CreateAddressCommand;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CreateCommunicationMethodCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Search, governed export and row-isolated import operations for CRM-007. */
@Service
public class AddressCommunicationOperationsService {
    private static final int MAX_SEARCH_LIMIT = 200;
    private static final int MAX_EXPORT_ROWS = 5_000;
    private static final int MAX_IMPORT_ROWS = 500;

    private static final String ADDRESS_COLUMNS = "id,version,owner_type,owner_id,address_type,label," +
            "raw_formatted_address,line1,line2,line3,district,city,state_region,postal_code,country_code," +
            "country_extension_json,latitude,longitude,primary_address,verified,verification_source,status," +
            "valid_from,valid_to,created_at,updated_at,archived_at";
    private static final String COMMUNICATION_COLUMNS = "id,version,owner_type,owner_id,method_type,raw_value," +
            "normalized_value,display_value,label,preferred,verified,verification_status,verified_at," +
            "privacy_classification,consent_state_reference,usage_purpose,status,valid_from,valid_to," +
            "created_at,updated_at,archived_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final AddressCommunicationUseCases useCases;
    private final ObjectMapper mapper;
    private final TransactionTemplate rowTransaction;

    public AddressCommunicationOperationsService(
            NamedParameterJdbcTemplate jdbc,
            AddressCommunicationUseCases useCases,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.useCases = useCases;
        this.mapper = mapper;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<AddressRecord> searchAddresses(
            UUID tenantId,
            String query,
            String ownerType,
            String addressType,
            String countryCode,
            String status,
            int limit) {
        String sql = "SELECT " + ADDRESS_COLUMNS + " FROM crm_party_addresses " +
                "WHERE tenant_id=:tenantId " +
                "AND (:query IS NULL OR LOWER(COALESCE(label,'') || ' ' || COALESCE(raw_formatted_address,'') || ' ' || " +
                "COALESCE(line1,'') || ' ' || COALESCE(line2,'') || ' ' || COALESCE(district,'') || ' ' || " +
                "COALESCE(city,'') || ' ' || COALESCE(state_region,'') || ' ' || COALESCE(postal_code,'')) LIKE :query) " +
                "AND (:ownerType IS NULL OR owner_type=:ownerType) " +
                "AND (:addressType IS NULL OR address_type=:addressType) " +
                "AND (:countryCode IS NULL OR country_code=:countryCode) " +
                "AND (:status IS NULL OR status=:status) " +
                "ORDER BY updated_at DESC,id DESC LIMIT :limit";
        return jdbc.query(sql, searchParams(tenantId, query, ownerType, addressType, countryCode, status, limit),
                (rs, rowNum) -> address(rs));
    }

    public List<CommunicationMethodRecord> searchCommunicationMethods(
            UUID tenantId,
            String query,
            String ownerType,
            String methodType,
            String verificationStatus,
            String status,
            int limit) {
        String sql = "SELECT " + COMMUNICATION_COLUMNS + " FROM crm_communication_methods " +
                "WHERE tenant_id=:tenantId " +
                "AND (:query IS NULL OR LOWER(COALESCE(label,'') || ' ' || COALESCE(display_value,'') || ' ' || " +
                "COALESCE(normalized_value,'')) LIKE :query) " +
                "AND (:ownerType IS NULL OR owner_type=:ownerType) " +
                "AND (:methodType IS NULL OR method_type=:methodType) " +
                "AND (:verificationStatus IS NULL OR verification_status=:verificationStatus) " +
                "AND (:status IS NULL OR status=:status) " +
                "ORDER BY updated_at DESC,id DESC LIMIT :limit";
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("query", pattern(query))
                        .addValue("ownerType", upper(ownerType))
                        .addValue("methodType", upper(methodType))
                        .addValue("verificationStatus", upper(verificationStatus))
                        .addValue("status", upper(status))
                        .addValue("limit", bounded(limit, MAX_SEARCH_LIMIT)),
                (rs, rowNum) -> communication(rs));
    }

    public byte[] exportAddresses(
            UUID tenantId,
            String query,
            String ownerType,
            String addressType,
            String countryCode,
            String status) {
        List<AddressRecord> rows = searchAddresses(
                tenantId, query, ownerType, addressType, countryCode, status, MAX_EXPORT_ROWS);
        StringBuilder csv = new StringBuilder("\uFEFFid,ownerType,ownerId,addressType,label,rawFormattedAddress,line1,line2,line3,district,city,stateRegion,postalCode,countryCode,primary,verified,status,validFrom,validTo\r\n");
        for (AddressRecord row : rows) {
            append(csv, row.id(), row.ownerType(), row.ownerId(), row.addressType(), row.label(),
                    row.rawFormattedAddress(), row.line1(), row.line2(), row.line3(), row.district(), row.city(),
                    row.stateRegion(), row.postalCode(), row.countryCode(), row.primaryAddress(), row.verified(),
                    row.status(), row.validFrom(), row.validTo());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportCommunicationMethods(
            UUID tenantId,
            String query,
            String ownerType,
            String methodType,
            String verificationStatus,
            String status,
            boolean exposeSensitive) {
        List<CommunicationMethodRecord> rows = searchCommunicationMethods(
                tenantId, query, ownerType, methodType, verificationStatus, status, MAX_EXPORT_ROWS);
        StringBuilder csv = new StringBuilder("\uFEFFid,ownerType,ownerId,methodType,value,label,preferred,verified,verificationStatus,privacyClassification,consentReference,usagePurpose,status,validFrom,validTo\r\n");
        for (CommunicationMethodRecord source : rows) {
            CommunicationMethodRecord row = useCases.masked(source, exposeSensitive);
            append(csv, row.id(), row.ownerType(), row.ownerId(), row.methodType(), row.displayValue(), row.label(),
                    row.preferred(), row.verified(), row.verificationStatus(), row.privacyClassification(),
                    row.consentStateReference(), row.usagePurpose(), row.status(), row.validFrom(), row.validTo());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportResult importAddresses(UUID tenantId, UUID actorId, List<AddressImportRow> rows) {
        List<AddressImportRow> cleanRows = requireRows(rows);
        UUID jobId = startJob(tenantId, actorId, "ADDRESS", cleanRows.size());
        List<ImportRowError> errors = new ArrayList<>();
        int succeeded = 0;
        for (int index = 0; index < cleanRows.size(); index++) {
            AddressImportRow row = cleanRows.get(index);
            try {
                rowTransaction.executeWithoutResult(status -> useCases.createAddress(
                        tenantId, actorId, row.ownerType(), row.ownerId(), row.command()));
                succeeded++;
            } catch (RuntimeException exception) {
                errors.add(error(jobId, tenantId, index + 1, row, exception));
            }
        }
        finishJob(jobId, tenantId, cleanRows.size(), succeeded, errors.size(), errors);
        return new ImportResult(jobId, cleanRows.size(), succeeded, errors.size(), List.copyOf(errors));
    }

    public ImportResult importCommunicationMethods(
            UUID tenantId, UUID actorId, List<CommunicationImportRow> rows) {
        List<CommunicationImportRow> cleanRows = requireRows(rows);
        UUID jobId = startJob(tenantId, actorId, "COMMUNICATION_METHOD", cleanRows.size());
        List<ImportRowError> errors = new ArrayList<>();
        int succeeded = 0;
        for (int index = 0; index < cleanRows.size(); index++) {
            CommunicationImportRow row = cleanRows.get(index);
            try {
                rowTransaction.executeWithoutResult(status -> useCases.createCommunicationMethod(
                        tenantId, actorId, row.ownerType(), row.ownerId(), row.command(), row.countryHint()));
                succeeded++;
            } catch (RuntimeException exception) {
                errors.add(error(jobId, tenantId, index + 1, row, exception));
            }
        }
        finishJob(jobId, tenantId, cleanRows.size(), succeeded, errors.size(), errors);
        return new ImportResult(jobId, cleanRows.size(), succeeded, errors.size(), List.copyOf(errors));
    }

    private UUID startJob(UUID tenantId, UUID actorId, String entityType, int totalRows) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_import_jobs (id,tenant_id,entity_type,status,total_rows,processed_rows," +
                        "succeeded_rows,failed_rows,requested_by,created_at,updated_at,mapping_json,started_at,attempt_count) " +
                        "VALUES (:id,:tenantId,:entityType,'RUNNING',:totalRows,0,0,0,:actorId,:now,:now," +
                        ":mappingJson,:now,1)",
                new MapSqlParameterSource()
                        .addValue("id", jobId).addValue("tenantId", tenantId)
                        .addValue("entityType", entityType).addValue("totalRows", totalRows)
                        .addValue("actorId", actorId).addValue("now", timestamp(now))
                        .addValue("mappingJson", "{\"format\":\"JSON_BATCH\",\"schema\":\"CRM007\"}"));
        return jobId;
    }

    private void finishJob(
            UUID jobId,
            UUID tenantId,
            int total,
            int succeeded,
            int failed,
            List<ImportRowError> errors) {
        Instant now = Instant.now();
        for (ImportRowError error : errors) {
            jdbc.update("INSERT INTO crm_import_errors (id,tenant_id,import_job_id,row_number,field_name," +
                            "error_code,message,raw_row,created_at) " +
                            "VALUES (:id,:tenantId,:jobId,:rowNumber,:fieldName,:errorCode,:message,:rawRow,:now)",
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("jobId", jobId).addValue("rowNumber", error.rowNumber())
                            .addValue("fieldName", error.fieldName()).addValue("errorCode", error.errorCode())
                            .addValue("message", error.message()).addValue("rawRow", error.rawRow())
                            .addValue("now", timestamp(now)));
        }
        jdbc.update("UPDATE crm_import_jobs SET status='COMPLETED',processed_rows=:total," +
                        "succeeded_rows=:succeeded,failed_rows=:failed,completed_at=:now,updated_at=:now," +
                        "last_error=:lastError WHERE tenant_id=:tenantId AND id=:jobId",
                new MapSqlParameterSource()
                        .addValue("jobId", jobId).addValue("tenantId", tenantId)
                        .addValue("total", total).addValue("succeeded", succeeded).addValue("failed", failed)
                        .addValue("now", timestamp(now))
                        .addValue("lastError", failed == 0 ? null : failed + " row(s) failed validation"));
    }

    private ImportRowError error(UUID jobId, UUID tenantId, int rowNumber, Object row, RuntimeException exception) {
        String code = exception instanceof CrmContractException crm ? crm.code().name() : "ROW_PROCESSING_FAILED";
        String message = exception.getMessage() == null ? "Row processing failed." : exception.getMessage();
        return new ImportRowError(rowNumber, null, code, truncate(message, 1000), serialize(row));
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }

    private static MapSqlParameterSource searchParams(
            UUID tenantId,
            String query,
            String ownerType,
            String addressType,
            String countryCode,
            String status,
            int limit) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("query", pattern(query))
                .addValue("ownerType", upper(ownerType))
                .addValue("addressType", upper(addressType))
                .addValue("countryCode", upper(countryCode))
                .addValue("status", upper(status))
                .addValue("limit", bounded(limit, limit > MAX_SEARCH_LIMIT ? MAX_EXPORT_ROWS : MAX_SEARCH_LIMIT));
    }

    private static <T> List<T> requireRows(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            throw validation("Import rows are required.");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw validation("An import batch cannot exceed " + MAX_IMPORT_ROWS + " rows.");
        }
        return rows;
    }

    private AddressRecord address(ResultSet rs) throws SQLException {
        return new AddressRecord(uuid(rs, "id"), rs.getLong("version"), rs.getString("owner_type"),
                uuid(rs, "owner_id"), rs.getString("address_type"), rs.getString("label"),
                rs.getString("raw_formatted_address"), rs.getString("line1"), rs.getString("line2"),
                rs.getString("line3"), rs.getString("district"), rs.getString("city"),
                rs.getString("state_region"), rs.getString("postal_code"), rs.getString("country_code"),
                rs.getString("country_extension_json"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                rs.getBoolean("primary_address"), rs.getBoolean("verified"), rs.getString("verification_source"),
                rs.getString("status"), localDate(rs, "valid_from"), localDate(rs, "valid_to"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "archived_at"));
    }

    private CommunicationMethodRecord communication(ResultSet rs) throws SQLException {
        return new CommunicationMethodRecord(uuid(rs, "id"), rs.getLong("version"), rs.getString("owner_type"),
                uuid(rs, "owner_id"), rs.getString("method_type"), rs.getString("raw_value"),
                rs.getString("normalized_value"), rs.getString("display_value"), rs.getString("label"),
                rs.getBoolean("preferred"), rs.getBoolean("verified"), rs.getString("verification_status"),
                instant(rs, "verified_at"), rs.getString("privacy_classification"),
                rs.getString("consent_state_reference"), rs.getString("usage_purpose"), rs.getString("status"),
                localDate(rs, "valid_from"), localDate(rs, "valid_to"), instant(rs, "created_at"),
                instant(rs, "updated_at"), instant(rs, "archived_at"));
    }

    private static void append(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(csv(values[index]));
        }
        csv.append("\r\n");
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String pattern(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int bounded(int value, int maximum) {
        return value <= 0 ? 50 : Math.min(value, maximum);
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static CrmContractException validation(String message) {
        return new CrmContractException(CrmErrorCode.VALIDATION_ERROR, message);
    }

    public record AddressImportRow(
            String ownerType,
            UUID ownerId,
            CreateAddressCommand command) {}

    public record CommunicationImportRow(
            String ownerType,
            UUID ownerId,
            String countryHint,
            CreateCommunicationMethodCommand command) {}

    public record ImportRowError(
            int rowNumber,
            String fieldName,
            String errorCode,
            String message,
            String rawRow) {}

    public record ImportResult(
            UUID jobId,
            int totalRows,
            int succeededRows,
            int failedRows,
            List<ImportRowError> errors) {}
}
