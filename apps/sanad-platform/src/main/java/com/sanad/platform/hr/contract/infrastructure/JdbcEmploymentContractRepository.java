package com.sanad.platform.hr.contract.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.contract.domain.EmploymentContract;
import com.sanad.platform.hr.contract.domain.EmploymentContractRepository;
import com.sanad.platform.hr.contract.domain.EmploymentContractStatus;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for employment contracts (WS6 Task 2).
 *
 * <p>Mirrors the JdbcEmploymentRepository transactional pattern: every
 * mutation executes its statements AND its evidence (audit fact + delivery
 * state + outbox event) on ONE connection inside a SHORT tenant-scoped
 * transaction ({@code SET LOCAL app.tenant_id}) — no REQUIRES_NEW; any
 * evidence failure rolls the mutation back atomically.</p>
 *
 * <p>Immutable history: no update-terms operation exists. Second line of
 * defense: the schema (V20260904_2) carries a guard trigger rejecting UPDATEs
 * to term columns on contract versions.</p>
 */
@Repository
public class JdbcEmploymentContractRepository implements EmploymentContractRepository {

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcEmploymentContractRepository(DataSource dataSource,
                                            HrTransactionalEvidenceWriter evidenceWriter,
                                            ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.evidenceWriter = evidenceWriter;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void createContractWithEvidence(EmploymentContract contract, EmploymentContractVersion firstVersion,
                                           HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(contract.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employment_contracts (id, tenant_id, employment_id, contract_number, "
                            + "is_primary, predecessor_contract_id) VALUES (?,?,?,?,?,?)")) {
                ps.setObject(1, contract.id());
                ps.setObject(2, contract.tenantId());
                ps.setObject(3, contract.employmentId());
                ps.setString(4, contract.contractNumber());
                ps.setBoolean(5, contract.isPrimary());
                ps.setObject(6, contract.predecessorContractId());
                ps.executeUpdate();
            }
            insertVersionRow(connection, firstVersion);
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public void amendVersionWithEvidence(UUID tenantId, UUID contractId, EmploymentContractVersion newVersion,
                                         LocalDate supersedeEffectiveTo,
                                         HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employment_contract_versions SET status = ?, effective_to = ? "
                            + "WHERE tenant_id = ? AND contract_id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, EmploymentContractStatus.SUPERSEDED.name());
                ps.setObject(2, java.sql.Date.valueOf(supersedeEffectiveTo));
                ps.setObject(3, tenantId);
                ps.setObject(4, contractId);
                ps.executeUpdate();
            }
            insertVersionRow(connection, newVersion);
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public void activateVersionWithEvidence(UUID tenantId, UUID contractId, int versionNumber,
                                            LocalDate effectiveFrom, LocalDate effectiveTo,
                                            HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employment_contract_versions SET status = ?, effective_from = ?, effective_to = ? "
                            + "WHERE tenant_id = ? AND contract_id = ? AND version_number = ? "
                            + "AND status IN ('DRAFT','PENDING_SIGNATURE')")) {
                ps.setString(1, EmploymentContractStatus.ACTIVE.name());
                ps.setObject(2, java.sql.Date.valueOf(effectiveFrom));
                ps.setObject(3, effectiveTo == null ? null : java.sql.Date.valueOf(effectiveTo));
                ps.setObject(4, tenantId);
                ps.setObject(5, contractId);
                ps.setInt(6, versionNumber);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException("HRM_CONTRACT_VERSION_NOT_ACTIVATABLE: contract " + contractId
                            + " version " + versionNumber + " is not in an activatable state");
                }
            }
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public void terminateVersionWithEvidence(UUID tenantId, UUID contractId, LocalDate effectiveDate,
                                             HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employment_contract_versions SET status = ?, effective_to = ? "
                            + "WHERE tenant_id = ? AND contract_id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, EmploymentContractStatus.TERMINATED.name());
                ps.setObject(2, java.sql.Date.valueOf(effectiveDate));
                ps.setObject(3, tenantId);
                ps.setObject(4, contractId);
                ps.executeUpdate();
            }
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public Optional<EmploymentContract> findContract(UUID tenantId, UUID contractId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, employment_id, contract_number, is_primary, predecessor_contract_id, created_at "
                            + "FROM hr_employment_contracts WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, contractId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<EmploymentContract>empty();
                    }
                    return Optional.of(new EmploymentContract(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            UUID.fromString(rs.getString("employment_id")),
                            rs.getString("contract_number"),
                            rs.getBoolean("is_primary"),
                            uuidOrNull(rs, "predecessor_contract_id"),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
        });
    }

    @Override
    public Optional<EmploymentContractVersion> findVersion(UUID tenantId, UUID versionId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_VERSION + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, versionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapVersion(rs)) : Optional.<EmploymentContractVersion>empty();
                }
            }
        });
    }

    @Override
    public Optional<EmploymentContractVersion> findVersionByNumber(UUID tenantId, UUID contractId, int versionNumber) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_VERSION + " WHERE tenant_id = ? AND contract_id = ? AND version_number = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, contractId);
                ps.setInt(3, versionNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapVersion(rs)) : Optional.<EmploymentContractVersion>empty();
                }
            }
        });
    }

    @Override
    public Optional<EmploymentContractVersion> findActivePrimaryVersion(UUID tenantId, UUID employmentId, LocalDate asOf) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_VERSION + " WHERE tenant_id = ? AND employment_id = ? AND is_primary = TRUE "
                            + "AND status = 'ACTIVE' AND effective_from <= ? "
                            + "AND (effective_to IS NULL OR effective_to >= ?) "
                            + "ORDER BY effective_from DESC LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                ps.setObject(3, java.sql.Date.valueOf(asOf));
                ps.setObject(4, java.sql.Date.valueOf(asOf));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapVersion(rs)) : Optional.<EmploymentContractVersion>empty();
                }
            }
        });
    }

    @Override
    public List<EmploymentContractVersion> findVersions(UUID tenantId, UUID contractId) {
        return inTenantTransaction(tenantId, connection -> {
            List<EmploymentContractVersion> versions = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    SELECT_VERSION + " WHERE tenant_id = ? AND contract_id = ? ORDER BY version_number")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, contractId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        versions.add(mapVersion(rs));
                    }
                }
            }
            return versions;
        });
    }

    // ==================== internals ====================

    private void insertVersionRow(Connection connection, EmploymentContractVersion version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employment_contract_versions (id, tenant_id, contract_id, employment_id, "
                        + "version_number, status, is_primary, contract_term_type, contract_start_date, "
                        + "contract_end_date, effective_from, effective_to, document_reference, country_terms, "
                        + "created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)")) {
            ps.setObject(1, version.id());
            ps.setObject(2, version.tenantId());
            ps.setObject(3, version.contractId());
            ps.setObject(4, version.employmentId());
            ps.setInt(5, version.versionNumber());
            ps.setString(6, version.status().name());
            ps.setBoolean(7, version.isPrimary());
            ps.setString(8, version.contractTermType());
            ps.setObject(9, java.sql.Date.valueOf(version.contractStartDate()));
            ps.setObject(10, version.contractEndDate() == null ? null : java.sql.Date.valueOf(version.contractEndDate()));
            ps.setObject(11, java.sql.Date.valueOf(version.effectiveFrom()));
            ps.setObject(12, version.effectiveTo() == null ? null : java.sql.Date.valueOf(version.effectiveTo()));
            ps.setString(13, version.documentReference());
            ps.setString(14, version.countryTerms() == null ? "{}" : version.countryTerms().toString());
            ps.setObject(15, version.createdBy());
            ps.executeUpdate();
        }
    }

    private void writeEvidence(Connection connection, HrAuditRecord auditRecord, DomainEventEnvelope event) {
        if (evidenceWriter != null) {
            evidenceWriter.writeEvidence(connection, auditRecord, event);
        }
    }

    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private interface SqlWorkVoid {
        void run(Connection connection) throws SQLException;
    }

    private void inTenantTransaction(UUID tenantId, SqlWorkVoid work) {
        inTenantTransaction(tenantId, connection -> {
            work.run(connection);
            return null;
        });
    }

    private <T> T inTenantTransaction(UUID tenantId, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                if ("23P01".equals(e.getSQLState())) {
                    throw new IllegalStateException("HRM_CONTRACT_OVERLAP: overlapping effective contract "
                            + "window violates the temporal exclusion constraint", e);
                }
                throw new IllegalStateException("HRM_CONTRACT_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CONTRACT_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private EmploymentContractVersion mapVersion(ResultSet rs) throws SQLException {
        JsonNode terms;
        try {
            String raw = rs.getString("country_terms");
            terms = raw == null ? null : objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new SQLException("country_terms is not valid JSON", e);
        }
        return new EmploymentContractVersion(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tenant_id")),
                UUID.fromString(rs.getString("contract_id")),
                UUID.fromString(rs.getString("employment_id")),
                rs.getInt("version_number"),
                EmploymentContractStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_primary"),
                rs.getString("contract_term_type"),
                rs.getDate("contract_start_date").toLocalDate(),
                dateOrNull(rs, "contract_end_date"),
                rs.getDate("effective_from").toLocalDate(),
                dateOrNull(rs, "effective_to"),
                rs.getString("document_reference"),
                terms,
                uuidOrNull(rs, "created_by"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static LocalDate dateOrNull(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static final String SELECT_VERSION =
            "SELECT id, tenant_id, contract_id, employment_id, version_number, status, is_primary, "
                    + "contract_term_type, contract_start_date, contract_end_date, effective_from, effective_to, "
                    + "document_reference, country_terms, created_by, created_at FROM hr_employment_contract_versions";
}
