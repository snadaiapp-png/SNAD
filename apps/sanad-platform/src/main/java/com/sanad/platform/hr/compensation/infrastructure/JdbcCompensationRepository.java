package com.sanad.platform.hr.compensation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
import com.sanad.platform.hr.compensation.domain.CompensationRepository;
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
 * JDBC repository for compensation packages (WS6 Task 3).
 *
 * <p>Same transactional pattern as JdbcEmploymentContractRepository: short
 * tenant-scoped transactions; evidence on the SAME connection; exclusion
 * violation (23P01) translated to the deterministic HRM_COMPENSATION_OVERLAP
 * error. Component amounts are stored ONLY here — they never enter audit or
 * outbox payloads (the evidence writer receives pre-built, amount-free
 * records).</p>
 */
@Repository
public class JdbcCompensationRepository implements CompensationRepository {

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;

    @Autowired
    public JdbcCompensationRepository(DataSource dataSource, HrTransactionalEvidenceWriter evidenceWriter) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.evidenceWriter = evidenceWriter;
    }

    @Override
    public void createPackageWithEvidence(CompensationPackage pkg, HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(pkg.tenantId(), connection -> {
            insertPackageRow(connection, pkg);
            insertComponentRows(connection, pkg);
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public void revisePackageWithEvidence(UUID tenantId, UUID currentPackageId, CompensationPackage successor,
                                          LocalDate supersedeEffectiveTo, HrAuditRecord auditRecord,
                                          DomainEventEnvelope event) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_compensation_packages SET status = ?, effective_to = ? "
                            + "WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, CompensationPackage.STATUS_SUPERSEDED);
                ps.setObject(2, java.sql.Date.valueOf(supersedeEffectiveTo));
                ps.setObject(3, tenantId);
                ps.setObject(4, currentPackageId);
                ps.executeUpdate();
            }
            insertPackageRow(connection, successor);
            insertComponentRows(connection, successor);
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public void endPackageWithEvidence(UUID tenantId, UUID packageId, LocalDate effectiveTo,
                                       HrAuditRecord auditRecord, DomainEventEnvelope event) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_compensation_packages SET status = ?, effective_to = ? "
                            + "WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, CompensationPackage.STATUS_ENDED);
                ps.setObject(2, java.sql.Date.valueOf(effectiveTo));
                ps.setObject(3, tenantId);
                ps.setObject(4, packageId);
                ps.executeUpdate();
            }
            writeEvidence(connection, auditRecord, event);
        });
    }

    @Override
    public Optional<CompensationPackage> findPackage(UUID tenantId, UUID packageId) {
        return inTenantTransaction(tenantId, connection -> {
            Optional<CompensationPackage> pkg = queryPackage(connection,
                    "SELECT " + PACKAGE_COLUMNS + " FROM hr_compensation_packages WHERE tenant_id = ? AND id = ?",
                    tenantId, packageId);
            if (pkg.isEmpty()) {
                return Optional.<CompensationPackage>empty();
            }
            return Optional.of(withComponents(connection, pkg.orElseThrow()));
        });
    }

    @Override
    public Optional<CompensationPackage> findActivePackage(UUID tenantId, UUID employmentId, LocalDate asOf) {
        return inTenantTransaction(tenantId, connection -> {
            Optional<CompensationPackage> pkg = queryPackage(connection,
                    "SELECT " + PACKAGE_COLUMNS + " FROM hr_compensation_packages "
                            + "WHERE tenant_id = ? AND employment_id = ? AND status = 'ACTIVE' "
                            + "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) "
                            + "ORDER BY effective_from DESC LIMIT 1",
                    tenantId, employmentId, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
            if (pkg.isEmpty()) {
                return Optional.<CompensationPackage>empty();
            }
            return Optional.of(withComponents(connection, pkg.orElseThrow()));
        });
    }

    @Override
    public List<CompensationPackage> findPackageHistory(UUID tenantId, UUID employmentId) {
        return inTenantTransaction(tenantId, connection -> {
            List<CompensationPackage> history = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + PACKAGE_COLUMNS + " FROM hr_compensation_packages "
                            + "WHERE tenant_id = ? AND employment_id = ? ORDER BY effective_from")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(mapPackage(rs, List.of()));
                    }
                }
            }
            return history;
        });
    }

    // ==================== internals ====================

    private void insertPackageRow(Connection connection, CompensationPackage pkg) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_compensation_packages (id, tenant_id, employment_id, currency_code, pay_frequency, "
                        + "effective_from, effective_to, status, predecessor_package_id, version, created_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setObject(1, pkg.id());
            ps.setObject(2, pkg.tenantId());
            ps.setObject(3, pkg.employmentId());
            ps.setString(4, pkg.currencyCode());
            ps.setString(5, pkg.payFrequency());
            ps.setObject(6, java.sql.Date.valueOf(pkg.effectiveFrom()));
            ps.setObject(7, pkg.effectiveTo() == null ? null : java.sql.Date.valueOf(pkg.effectiveTo()));
            ps.setString(8, pkg.status());
            ps.setObject(9, pkg.predecessorPackageId());
            ps.setLong(10, pkg.version());
            ps.setObject(11, null);
            ps.executeUpdate();
        }
    }

    private void insertComponentRows(Connection connection, CompensationPackage pkg) throws SQLException {
        for (CompensationComponent component : pkg.components()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_compensation_components (id, tenant_id, package_id, component_type, code, "
                            + "amount, percentage) VALUES (?,?,?,?,?,?,?)")) {
                ps.setObject(1, component.id());
                ps.setObject(2, component.tenantId());
                ps.setObject(3, component.packageId());
                ps.setString(4, component.componentType().name());
                ps.setString(5, component.code());
                ps.setBigDecimal(6, component.amount());
                ps.setBigDecimal(7, component.percentage());
                ps.executeUpdate();
            }
        }
    }

    private CompensationPackage withComponents(Connection connection, CompensationPackage pkg) throws SQLException {
        List<CompensationComponent> components = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, package_id, component_type, code, amount, percentage "
                        + "FROM hr_compensation_components WHERE tenant_id = ? AND package_id = ? ORDER BY code")) {
            ps.setObject(1, pkg.tenantId());
            ps.setObject(2, pkg.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    components.add(new CompensationComponent(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            UUID.fromString(rs.getString("package_id")),
                            CompensationComponentType.valueOf(rs.getString("component_type")),
                            rs.getString("code"),
                            rs.getBigDecimal("amount"),
                            rs.getBigDecimal("percentage")));
                }
            }
        }
        return new CompensationPackage(pkg.id(), pkg.tenantId(), pkg.employmentId(), pkg.currencyCode(),
                pkg.payFrequency(), pkg.effectiveFrom(), pkg.effectiveTo(), pkg.status(),
                pkg.predecessorPackageId(), components, pkg.version(), pkg.createdAt());
    }

    private Optional<CompensationPackage> queryPackage(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapPackage(rs, List.of()));
            }
        }
    }

    private CompensationPackage mapPackage(ResultSet rs, List<CompensationComponent> components) throws SQLException {
        return new CompensationPackage(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tenant_id")),
                UUID.fromString(rs.getString("employment_id")),
                rs.getString("currency_code"),
                rs.getString("pay_frequency"),
                rs.getDate("effective_from").toLocalDate(),
                dateOrNull(rs, "effective_to"),
                rs.getString("status"),
                uuidOrNull(rs, "predecessor_package_id"),
                components,
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant());
    }

    private void writeEvidence(Connection connection, HrAuditRecord auditRecord, DomainEventEnvelope event) {
        if (evidenceWriter != null) {
            evidenceWriter.writeEvidence(connection, auditRecord, event);
        }
    }

    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    private interface QueryWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @SuppressWarnings("unchecked")
    private <T> T inTenantTransaction(UUID tenantId, QueryWork<T> work) {
        Object[] box = new Object[1];
        inTenantTransaction(tenantId, (SqlWork) connection -> box[0] = work.run(connection));
        return (T) box[0];
    }

    private void inTenantTransaction(UUID tenantId, SqlWork work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                work.run(connection);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                if ("23P01".equals(e.getSQLState())) {
                    throw new IllegalStateException("HRM_COMPENSATION_OVERLAP: overlapping ACTIVE compensation "
                            + "package violates the temporal exclusion constraint", e);
                }
                throw new IllegalStateException("HRM_COMPENSATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_COMPENSATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private static LocalDate dateOrNull(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static final String PACKAGE_COLUMNS =
            "id, tenant_id, employment_id, currency_code, pay_frequency, effective_from, effective_to, status, "
                    + "predecessor_package_id, version, created_at";
}
