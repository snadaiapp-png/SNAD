package com.sanad.platform.hr.identity;

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
}
