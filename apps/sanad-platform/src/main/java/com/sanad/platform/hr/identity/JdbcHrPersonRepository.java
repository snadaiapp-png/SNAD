package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.PlatformCryptographyService;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link HrPersonRepository}.
 *
 * <p>Uses raw JDBC to persist {@link HrPerson} and {@link PersonIdentifier}
 * rows. Identifier encryption/blind-index computation is delegated to
 * {@link PlatformCryptographyService} (the existing WS1 crypto service).</p>
 *
 * <p>This is a Cycle 2 minimal skeleton — methods throw
 * {@link UnsupportedOperationException}. Real persistence is added in
 * Cycle 4 GREEN.</p>
 */
public final class JdbcHrPersonRepository implements HrPersonRepository {

    private final DataSource dataSource;
    private final PlatformCryptographyService crypto;

    public JdbcHrPersonRepository(DataSource dataSource, PlatformCryptographyService crypto) {
        this.dataSource = dataSource;
        this.crypto = crypto;
    }

    @Override
    public void savePerson(HrPerson person) {
        throw new UnsupportedOperationException(
                "JdbcHrPersonRepository.savePerson — Cycle 2 skeleton, implement in Cycle 4");
    }

    @Override
    public Optional<HrPerson> findPersonById(UUID tenantId, UUID personId) {
        throw new UnsupportedOperationException(
                "JdbcHrPersonRepository.findPersonById — Cycle 2 skeleton, implement in Cycle 4");
    }

    @Override
    public void saveIdentifier(PersonIdentifier identifier) {
        throw new UnsupportedOperationException(
                "JdbcHrPersonRepository.saveIdentifier — Cycle 2 skeleton, implement in Cycle 4");
    }

    @Override
    public Optional<PersonIdentifier> findActiveIdentifierByBlindIndex(
            UUID tenantId, String identifierType, String issuingCountryCode, String blindIndex) {
        throw new UnsupportedOperationException(
                "JdbcHrPersonRepository.findActiveIdentifierByBlindIndex — Cycle 2 skeleton, implement in Cycle 4");
    }
}
