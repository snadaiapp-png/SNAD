package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.PlatformCryptographyService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
                throw new IllegalStateException("HR Person persistence operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire HR Person database connection", e);
        }
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
