package com.sanad.platform.hr.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * HR Person Repository — persistence port for {@link HrPerson} and
 * {@link PersonIdentifier}.
 *
 * <p>Repository contract abstracting JDBC operations. Implementations
 * (e.g., {@link JdbcHrPersonRepository}) must enforce tenant-scoped
 * persistence and never leak identifiers across tenant boundaries.</p>
 *
 * <p>This is a Cycle 2 minimal skeleton — methods throw
 * {@link UnsupportedOperationException}. Real persistence is added in
 * Cycle 4 GREEN.</p>
 */
public interface HrPersonRepository {

    /**
     * Persist a new {@link HrPerson}.
     *
     * @param person the Person to save
     */
    void savePerson(HrPerson person);

    /**
     * Find a Person by id within a tenant.
     *
     * @param tenantId the tenant scope
     * @param personId the Person id
     * @return the Person, or empty if not found
     */
    Optional<HrPerson> findPersonById(UUID tenantId, UUID personId);

    /**
     * Persist a new {@link PersonIdentifier}.
     *
     * @param identifier the identifier to save
     */
    void saveIdentifier(PersonIdentifier identifier);

    /**
     * Find an ACTIVE identifier by exact match on tenant + type + issuer +
     * blind index.
     *
     * @param tenantId           the tenant scope
     * @param identifierType     normalized identifier type
     * @param issuingCountryCode normalized issuing country (may be {@code null})
     * @param blindIndex         the deterministic blind index
     * @return the matching ACTIVE identifier, or empty if not found
     */
    Optional<PersonIdentifier> findActiveIdentifierByBlindIndex(
            UUID tenantId,
            String identifierType,
            String issuingCountryCode,
            String blindIndex);
}
