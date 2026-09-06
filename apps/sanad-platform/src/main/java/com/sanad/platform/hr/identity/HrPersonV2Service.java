package com.sanad.platform.hr.identity;

import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.audit.SensitiveReadAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — People v2 application service.
 *
 * <p>Orchestrates the 9 canonical People operations over the WS2 identity
 * persistence and the WS4 fail-closed sensitive-read audit:
 *
 * <ul>
 *   <li>directory operations (create/read/list/patch names) are safe-surface
 *       only — they never expose private PII fields</li>
 *   <li>the private PII read is RESTRICTED: it requires the HRM.PII.VIEW
 *       capability (coarse gate at the controller) and appends an immutable
 *       audit ledger row in the SAME transaction as the read — if the audit
 *       append fails, the read is refused and rolled back</li>
 *   <li>identity documents flow through the WS2 crypto pipeline
 *       (normalize → blind index → encrypt); plaintext values are write-only
 *       and are never returned by any operation</li>
 *   <li>name and private-profile mutations are guarded by explicit
 *       optimistic-concurrency versions; the server never invents versions
 *       or legally significant dates</li>
 * </ul>
 *
 * <p>Domain failures use the established {@code HRM_*} text-prefix
 * convention, projected to the canonical v2 error envelope by
 * {@code HrApiExceptionHandler}.
 */
@Service
public class HrPersonV2Service {

    public static final String SENSITIVE_READ_ACTION = "HR.SENSITIVE_READ.PERSON_PRIVATE";
    /** Fixed audit reason: client text is never trusted for audit evidence. */
    static final String AUDIT_REASON = "WS5.PEOPLE.PRIVATE_READ";
    static final String RESOURCE_TYPE = "HR_PERSON_PRIVATE";

    private final HrPersonRepository repository;
    private final HrPersonService personService;
    private final SensitiveReadAuditService sensitiveReadAuditService;
    private final DataSource dataSource;

    @Autowired
    public HrPersonV2Service(HrPersonRepository repository,
                             HrPersonService personService,
                             SensitiveReadAuditService sensitiveReadAuditService,
                             DataSource dataSource) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.personService = Objects.requireNonNull(personService, "personService");
        this.sensitiveReadAuditService = Objects.requireNonNull(sensitiveReadAuditService, "sensitiveReadAuditService");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public HrPerson createPerson(UUID tenantId, String firstName, String middleName, String lastName) {
        Objects.requireNonNull(tenantId, "tenantId");
        return personService.createPerson(tenantId,
                requireText(firstName, "firstName"), normalizeOptional(middleName), requireText(lastName, "lastName"));
    }

    public List<HrPerson> listPeople(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return repository.listPeople(tenantId);
    }

    public HrPerson getPerson(UUID tenantId, UUID personId) {
        requirePerson(tenantId, personId);
        return repository.findPersonById(tenantId, personId).orElseThrow(() -> notFound(personId));
    }

    public HrPerson patchPersonNames(UUID tenantId, UUID personId, String firstName,
                                     String middleName, String lastName, Long expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        requirePerson(tenantId, personId);
        String normalizedMiddle = normalizeOptional(middleName);
        String displayName = buildDisplayName(requireText(firstName, "firstName"),
                normalizedMiddle, requireText(lastName, "lastName"));
        boolean updated = repository.updatePersonNames(tenantId, personId,
                firstName, normalizedMiddle, lastName, displayName, expectedVersion);
        if (!updated) {
            throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: person version does not match expected "
                    + expectedVersion);
        }
        return repository.findPersonById(tenantId, personId).orElseThrow(() -> notFound(personId));
    }

    /**
     * RESTRICTED read — returns the private PII profile only after the
     * fail-closed sensitive-read audit has been appended in the same
     * database transaction as the read itself.
     */
    public HrPersonPrivate readPrivateWithAudit(UUID tenantId, UUID actorUserId, UUID correlationId,
                                                UUID requestId, UUID personId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(personId, "personId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantContext(connection, tenantId);
                if (!personExists(connection, personId)) {
                    connection.rollback();
                    throw notFound(personId);
                }
                sensitiveReadAuditService.recordOrThrow(connection,
                        new HrAuthenticatedContext(tenantId, actorUserId, correlationId, requestId),
                        SENSITIVE_READ_ACTION, RESOURCE_TYPE, personId, "PII", AUDIT_REASON);
                HrPersonPrivate profile = selectPrivate(connection, personId);
                connection.commit();
                return profile;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new IllegalStateException("HRM_PERSON_PRIVATE_READ_FAILED: " + failure.getMessage(), failure);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_PERSON_PRIVATE_READ_FAILED: " + e.getMessage(), e);
        }
    }

    public HrPersonPrivate patchPrivate(UUID tenantId, UUID personId, LocalDate dateOfBirth,
                                        String nationalityCountryCode, String maritalStatus,
                                        Long expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        requirePerson(tenantId, personId);
        return repository.savePrivate(new HrPersonPrivate(tenantId, personId, dateOfBirth,
                normalizeOptional(nationalityCountryCode), normalizeOptional(maritalStatus), expectedVersion),
                expectedVersion);
    }

    public PersonIdentifier addIdentifier(UUID tenantId, UUID personId, String identifierType,
                                          String issuingCountryCode, String plaintextValue) {
        requirePerson(tenantId, personId);
        return personService.addIdentifier(tenantId, personId, identifierType,
                issuingCountryCode, plaintextValue);
    }

    public void linkUser(UUID tenantId, UUID personId, UUID userId) {
        Objects.requireNonNull(userId, "userId");
        requirePerson(tenantId, personId);
        personService.linkUser(tenantId, personId, userId);
    }

    public boolean unlinkUser(UUID tenantId, UUID personId) {
        requirePerson(tenantId, personId);
        return repository.unlinkUser(tenantId, personId);
    }

    // ==================== internal helpers ====================

    private void requirePerson(UUID tenantId, UUID personId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(personId, "personId");
        if (repository.findPersonById(tenantId, personId).isEmpty()) {
            throw notFound(personId);
        }
    }

    private IllegalStateException notFound(UUID personId) {
        return new IllegalStateException("HRM_PERSON_NOT_FOUND: " + personId);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HRM_VALIDATION_FAILED: " + field + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null ? null : value.trim();
    }

    private String buildDisplayName(String firstName, String middleName, String lastName) {
        if (middleName == null || middleName.isBlank()) {
            return firstName + " " + lastName;
        }
        return firstName + " " + middleName + " " + lastName;
    }

    private boolean personExists(Connection connection, UUID personId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_people WHERE id = ?")) {
            ps.setObject(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private HrPersonPrivate selectPrivate(Connection connection, UUID personId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT tenant_id, person_id, date_of_birth, nationality_country_code, marital_status, version " +
                        "FROM hr_person_private WHERE person_id = ?")) {
            ps.setObject(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new HrPersonPrivate(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class),
                        rs.getObject("date_of_birth", LocalDate.class),
                        rs.getString("nationality_country_code"),
                        rs.getString("marital_status"),
                        rs.getLong("version"));
            }
        }
    }

    private void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }
}
