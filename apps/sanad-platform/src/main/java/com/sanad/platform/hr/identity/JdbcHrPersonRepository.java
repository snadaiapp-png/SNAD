package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.PlatformCryptographyService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link HrPersonRepository}.
 *
 * <p>Every operation establishes {@code app.tenant_id} on the same database
 * transaction used for the query/update. This preserves FORCE RLS semantics;
 * it does not bypass tenant isolation.</p>
 */
public final class JdbcHrPersonRepository implements HrPersonRepository {

    private final DataSource dataSource;
    @SuppressWarnings("unused")
    private final PlatformCryptographyService crypto;

    public JdbcHrPersonRepository(DataSource dataSource, PlatformCryptographyService crypto) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    @Override
    public void savePerson(HrPerson person) {
        Objects.requireNonNull(person, "person");
        inTenantTransaction(person.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_people " +
                            "(id, tenant_id, user_id, first_name, middle_name, last_name, display_name, version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                ps.setObject(1, person.id());
                ps.setObject(2, person.tenantId());
                if (person.userId() == null) {
                    ps.setNull(3, Types.OTHER);
                } else {
                    ps.setObject(3, person.userId());
                }
                ps.setString(4, person.firstName());
                ps.setString(5, person.middleName());
                ps.setString(6, person.lastName());
                ps.setString(7, person.displayName());
                ps.setLong(8, person.version());
                ps.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public Optional<HrPerson> findPersonById(UUID tenantId, UUID personId) {
        Objects.requireNonNull(personId, "personId");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, user_id, first_name, middle_name, last_name, display_name, version " +
                            "FROM hr_people WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, personId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapPerson(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void linkUser(UUID tenantId, UUID personId, UUID userId) {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(userId, "userId");
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_people SET user_id = ?, updated_at = NOW() " +
                            "WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, userId);
                ps.setObject(2, tenantId);
                ps.setObject(3, personId);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException("HR Person not found in tenant scope");
                }
                return null;
            }
        });
    }

    @Override
    public void saveIdentifier(PersonIdentifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        inTenantTransaction(identifier.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_person_identifiers " +
                            "(id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                            "identifier_ciphertext, blind_index, encryption_key_version, blind_index_key_version, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
                ps.setObject(1, identifier.id());
                ps.setObject(2, identifier.tenantId());
                ps.setObject(3, identifier.personId());
                ps.setString(4, identifier.identifierType());
                if (identifier.issuingCountryCode() == null) {
                    ps.setNull(5, Types.CHAR);
                } else {
                    ps.setString(5, identifier.issuingCountryCode());
                }
                ps.setString(6, identifier.identifierCiphertext());
                ps.setString(7, identifier.blindIndex());
                ps.setString(8, identifier.encryptionKeyVersion());
                ps.setString(9, identifier.blindIndexKeyVersion());
                ps.setString(10, identifier.status());
                ps.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public Optional<PersonIdentifier> findActiveIdentifierByBlindIndex(
            UUID tenantId, String identifierType, String issuingCountryCode, String blindIndex) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                            "identifier_ciphertext, blind_index, encryption_key_version, blind_index_key_version, status " +
                            "FROM hr_person_identifiers " +
                            "WHERE tenant_id = ? AND identifier_type = ? " +
                            "AND issuing_country_code IS NOT DISTINCT FROM ? " +
                            "AND blind_index = ? AND status = 'ACTIVE'")) {
                ps.setObject(1, tenantId);
                ps.setString(2, identifierType);
                if (issuingCountryCode == null) {
                    ps.setNull(3, Types.CHAR);
                } else {
                    ps.setString(3, issuingCountryCode);
                }
                ps.setString(4, blindIndex);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapIdentifier(rs)) : Optional.empty();
                }
            }
        });
    }

    // ==================== WS5 Task 3 slice 2 (People v2) ====================

    @Override
    public List<HrPerson> listPeople(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, user_id, first_name, middle_name, last_name, display_name, version " +
                            "FROM hr_people WHERE tenant_id = ? ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<HrPerson> people = new ArrayList<>();
                    while (rs.next()) {
                        people.add(mapPerson(rs));
                    }
                    return people;
                }
            }
        });
    }

    @Override
    public boolean updatePersonNames(UUID tenantId, UUID personId, String firstName,
                                     String middleName, String lastName, String displayName,
                                     long expectedVersion) {
        Objects.requireNonNull(personId, "personId");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_people SET first_name = ?, middle_name = ?, last_name = ?, display_name = ?, " +
                            "version = version + 1, updated_at = NOW() " +
                            "WHERE tenant_id = ? AND id = ? AND version = ?")) {
                ps.setString(1, firstName);
                if (middleName == null) {
                    ps.setNull(2, Types.VARCHAR);
                } else {
                    ps.setString(2, middleName);
                }
                ps.setString(3, lastName);
                ps.setString(4, displayName);
                ps.setObject(5, tenantId);
                ps.setObject(6, personId);
                ps.setLong(7, expectedVersion);
                return ps.executeUpdate() == 1;
            }
        });
    }

    @Override
    public Optional<HrPersonPrivate> findPrivate(UUID tenantId, UUID personId) {
        Objects.requireNonNull(personId, "personId");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT tenant_id, person_id, date_of_birth, nationality_country_code, marital_status, version " +
                            "FROM hr_person_private WHERE tenant_id = ? AND person_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, personId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapPrivate(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public HrPersonPrivate savePrivate(HrPersonPrivate profile, long expectedVersion) {
        Objects.requireNonNull(profile, "profile");
        return inTenantTransaction(profile.tenantId(), connection -> {
            Long currentVersion = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT version FROM hr_person_private WHERE tenant_id = ? AND person_id = ? FOR UPDATE")) {
                ps.setObject(1, profile.tenantId());
                ps.setObject(2, profile.personId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentVersion = rs.getLong(1);
                    }
                }
            }
            if (currentVersion != null && currentVersion != expectedVersion) {
                throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: private profile version " +
                        currentVersion + " does not match expected " + expectedVersion);
            }
            if (currentVersion == null && expectedVersion != 0) {
                throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: private profile does not exist; " +
                        "expected version must be 0");
            }
            long newVersion = (currentVersion == null ? 0 : currentVersion) + 1;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_person_private " +
                            "(person_id, tenant_id, date_of_birth, nationality_country_code, marital_status, version, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
                            "ON CONFLICT (person_id) DO UPDATE SET " +
                            "date_of_birth = EXCLUDED.date_of_birth, " +
                            "nationality_country_code = EXCLUDED.nationality_country_code, " +
                            "marital_status = EXCLUDED.marital_status, " +
                            "version = EXCLUDED.version, updated_at = NOW()")) {
                ps.setObject(1, profile.personId());
                ps.setObject(2, profile.tenantId());
                if (profile.dateOfBirth() == null) {
                    ps.setNull(3, Types.DATE);
                } else {
                    ps.setObject(3, profile.dateOfBirth());
                }
                if (profile.nationalityCountryCode() == null) {
                    ps.setNull(4, Types.CHAR);
                } else {
                    ps.setString(4, profile.nationalityCountryCode());
                }
                if (profile.maritalStatus() == null) {
                    ps.setNull(5, Types.VARCHAR);
                } else {
                    ps.setString(5, profile.maritalStatus());
                }
                ps.setLong(6, newVersion);
                try {
                    ps.executeUpdate();
                } catch (SQLException constraintViolation) {
                    throw mapPrivateConstraint(constraintViolation);
                }
            }
            return new HrPersonPrivate(profile.tenantId(), profile.personId(), profile.dateOfBirth(),
                    profile.nationalityCountryCode(), profile.maritalStatus(), newVersion);
        });
    }

    @Override
    public boolean unlinkUser(UUID tenantId, UUID personId) {
        Objects.requireNonNull(personId, "personId");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_people SET user_id = NULL, updated_at = NOW() " +
                            "WHERE tenant_id = ? AND id = ? AND user_id IS NOT NULL")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, personId);
                return ps.executeUpdate() == 1;
            }
        });
    }

    private HrPersonPrivate mapPrivate(ResultSet rs) throws SQLException {
        return new HrPersonPrivate(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getString("nationality_country_code"),
                rs.getString("marital_status"),
                rs.getLong("version"));
    }

    /** Deterministic projection of DB constraint rejections onto HRM_* error prefixes. */
    private IllegalStateException mapPrivateConstraint(SQLException e) {
        String sqlState = e.getSQLState();
        if ("23503".equals(sqlState)) {
            return new IllegalStateException("HRM_VALIDATION_FAILED: nationalityCountryCode is not a known country");
        }
        if ("23514".equals(sqlState)) {
            return new IllegalStateException("HRM_VALIDATION_FAILED: maritalStatus is not an allowed value");
        }
        return new IllegalStateException("HR Person private persistence failed: " + e.getMessage(), e);
    }

    private HrPerson mapPerson(ResultSet rs) throws SQLException {
        return new HrPerson(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                rs.getString("display_name"),
                rs.getLong("version"));
    }

    private PersonIdentifier mapIdentifier(ResultSet rs) throws SQLException {
        return new PersonIdentifier(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getString("identifier_type"),
                rs.getString("issuing_country_code"),
                rs.getString("identifier_ciphertext"),
                rs.getString("blind_index"),
                rs.getString("encryption_key_version"),
                rs.getString("blind_index_key_version"),
                rs.getString("status"));
    }

    private <T> T inTenantTransaction(UUID tenantId, SqlWork<T> work) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantContext(connection, tenantId);
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (e instanceof SQLException sqlException) {
                    throw translatePersistenceFailure(sqlException);
                }
                throw new IllegalStateException("HR Person persistence operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire HR Person database connection", e);
        }
    }

    /**
     * Deterministic projection of unique-index rejections (SQLSTATE 23505) onto
     * the canonical person-conflict semantics: a duplicate ACTIVE identity
     * value or a User already linked within the tenant. Any other constraint
     * failure keeps the generic persistence-failure surface.
     */
    private RuntimeException translatePersistenceFailure(SQLException e) {
        if ("23505".equals(e.getSQLState())) {
            return new IllegalStateException("HRM_PERSON_CONFLICT: identity value or user link already exists in tenant");
        }
        return new IllegalStateException("HR Person persistence operation failed", e);
    }

    private void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
