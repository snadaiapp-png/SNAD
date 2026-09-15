package com.sanad.platform.hr.identity;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HR Person Repository — persistence port for {@link HrPerson} and
 * {@link PersonIdentifier}.
 *
 * <p>Implementations must enforce tenant-scoped persistence and never leak
 * identifiers across tenant boundaries.</p>
 */
public interface HrPersonRepository {

    void savePerson(HrPerson person);

    Optional<HrPerson> findPersonById(UUID tenantId, UUID personId);

    /** In-transaction lookup for the governed hire-conversion boundary (T8). */
    Optional<HrPerson> findPersonByIdWithinTransaction(java.sql.Connection connection,
                                                       UUID tenantId, UUID personId);

    /**
     * Link a tenant-scoped User to an existing Person.
     * Database constraints remain the authority for tenant congruence and
     * one-to-one uniqueness.
     */
    void linkUser(UUID tenantId, UUID personId, UUID userId);

    void saveIdentifier(PersonIdentifier identifier);

    Optional<PersonIdentifier> findActiveIdentifierByBlindIndex(
            UUID tenantId,
            String identifierType,
            String issuingCountryCode,
            String blindIndex);

    // ==================== WS5 Task 3 slice 2 (People v2) ====================

    /** Tenant-scoped directory listing — summaries only, never private PII. */
    List<HrPerson> listPeople(UUID tenantId);

    /**
     * Replaces the person name tuple and bumps the version, guarded by the
     * expected aggregate version. Returns {@code false} when the guarded
     * update matches no row (stale version or concurrently mutated).
     */
    boolean updatePersonNames(UUID tenantId, UUID personId, String firstName,
                              String middleName, String lastName, String displayName,
                              long expectedVersion);

    /** Private PII profile; empty when never written (implicit version 0). */
    Optional<HrPersonPrivate> findPrivate(UUID tenantId, UUID personId);

    // ==================== T8 — in-transaction (connection-scoped) variants ====================

    /**
     * Canonical Person creation participating in the CALLER's transaction
     * (the governed hire-conversion boundary, G1-T8 §7.1). Canonical SQL
     * stays in this G0 implementation; the conversion never writes
     * {@code hr_people} itself. Does NOT commit; does NOT close the
     * connection.
     */
    HrPerson savePersonWithinTransaction(Connection connection, HrPerson person);

    /** Identifier persistence inside the caller's transaction. */
    PersonIdentifier saveIdentifierWithinTransaction(Connection connection, PersonIdentifier identifier);

    /** Blind-index identity lookup inside the caller's transaction. */
    Optional<PersonIdentifier> findActiveIdentifierByBlindIndexWithinTransaction(
            Connection connection,
            UUID tenantId,
            String identifierType,
            String issuingCountryCode,
            String blindIndex);

    /**
     * Upsert of the private PII profile guarded by {@code expectedVersion}
     * (0 = the row must not exist yet). Returns the persisted row carrying
     * the new version. Throws {@link IllegalStateException} with an
     * {@code HRM_CONCURRENCY_CONFLICT} prefix on stale versions and
     * {@code HRM_VALIDATION_FAILED} on constraint-rejected field values.
     */
    HrPersonPrivate savePrivate(HrPersonPrivate profile, long expectedVersion);

    /**
     * Clears the optional user link. Returns {@code true} when a link was
     * removed, {@code false} when the person had no link (idempotent).
     */
    boolean unlinkUser(UUID tenantId, UUID personId);
}
