package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.PlatformCryptographyService;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HR Person Service — application-layer facade for HR Person identity operations.
 *
 * <p>Sensitive identifier flow is fixed as:
 * normalize → blind-index → encrypt → repository → PostgreSQL.</p>
 *
 * <p>Plaintext identifier values are never persisted or logged. Search uses
 * only a deterministic tenant+purpose-bound blind index.</p>
 */
public final class HrPersonService {

    private static final String IDENTIFIER_PURPOSE_PREFIX = "HR_PERSON_IDENTIFIER:";

    private final HrPersonRepository repository;
    private final PlatformCryptographyService crypto;
    private final IdentifierNormalizer normalizer;

    public HrPersonService(HrPersonRepository repository,
                           PlatformCryptographyService crypto,
                           IdentifierNormalizer normalizer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public HrPerson createPerson(UUID tenantId, String firstName, String middleName, String lastName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");

        HrPerson person = new HrPerson(
                UUID.randomUUID(),
                tenantId,
                null,
                firstName,
                middleName,
                lastName,
                buildDisplayName(firstName, middleName, lastName),
                0L);
        repository.savePerson(person);
        return person;
    }

    public void linkUser(UUID tenantId, UUID personId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        repository.linkUser(tenantId, personId, userId);
    }

    public PersonIdentifier addIdentifier(UUID tenantId, UUID personId,
                                          String identifierType,
                                          String issuingCountryCode,
                                          String plaintextValue) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(personId, "personId");

        String normalizedType = normalizer.normalizeIdentifierType(identifierType);
        String normalizedCountry = normalizer.normalizeCountryCode(issuingCountryCode);
        String normalizedValue = normalizer.normalizeValue(plaintextValue);
        String purpose = identifierPurpose(normalizedType, normalizedCountry);

        BlindIndex blindIndex = crypto.blindIndex(tenantId, purpose, normalizedValue);
        EncryptedValue encryptedValue = crypto.encrypt(tenantId, purpose, normalizedValue);

        PersonIdentifier identifier = new PersonIdentifier(
                UUID.randomUUID(),
                tenantId,
                personId,
                normalizedType,
                normalizedCountry,
                encryptedValue.ciphertext(),
                blindIndex.value(),
                encryptedValue.keyVersion(),
                blindIndex.keyVersion(),
                "ACTIVE");

        repository.saveIdentifier(identifier);
        return identifier;
    }

    public Optional<PersonIdentifier> findExactIdentifierMatch(UUID tenantId,
                                                                String identifierType,
                                                                String issuingCountryCode,
                                                                String plaintextValue) {
        Objects.requireNonNull(tenantId, "tenantId");

        String normalizedType = normalizer.normalizeIdentifierType(identifierType);
        String normalizedCountry = normalizer.normalizeCountryCode(issuingCountryCode);
        String normalizedValue = normalizer.normalizeValue(plaintextValue);
        String purpose = identifierPurpose(normalizedType, normalizedCountry);

        BlindIndex blindIndex = crypto.blindIndex(tenantId, purpose, normalizedValue);
        return repository.findActiveIdentifierByBlindIndex(
                tenantId,
                normalizedType,
                normalizedCountry,
                blindIndex.value());
    }

    // ==================== T8 — in-transaction variants (governed hire conversion) ====================

    /**
     * Canonical Person creation inside the CALLER's transaction (the
     * governed hire-conversion boundary, §7.1 step 4b). The G0 SQL path and
     * crypto authority are unchanged; only the transaction scope is
     * contributed by the caller, which commits or rolls back atomically.
     */
    public HrPerson createPersonWithinTransaction(Connection connection, UUID tenantId,
                                                  String firstName, String middleName, String lastName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");

        HrPerson person = new HrPerson(
                UUID.randomUUID(),
                tenantId,
                null,
                firstName,
                middleName,
                lastName,
                buildDisplayName(firstName, middleName, lastName),
                0L);
        return repository.savePersonWithinTransaction(connection, person);
    }

    /** Identifier storage inside the caller's transaction (same G0 crypto flow). */
    public PersonIdentifier addIdentifierWithinTransaction(Connection connection, UUID tenantId,
                                                           UUID personId, String identifierType,
                                                           String issuingCountryCode,
                                                           String plaintextValue) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(personId, "personId");

        String normalizedType = normalizer.normalizeIdentifierType(identifierType);
        String normalizedCountry = normalizer.normalizeCountryCode(issuingCountryCode);
        String normalizedValue = normalizer.normalizeValue(plaintextValue);
        String purpose = identifierPurpose(normalizedType, normalizedCountry);

        BlindIndex blindIndex = crypto.blindIndex(tenantId, purpose, normalizedValue);
        EncryptedValue encryptedValue = crypto.encrypt(tenantId, purpose, normalizedValue);

        PersonIdentifier identifier = new PersonIdentifier(
                UUID.randomUUID(),
                tenantId,
                personId,
                normalizedType,
                normalizedCountry,
                encryptedValue.ciphertext(),
                blindIndex.value(),
                encryptedValue.keyVersion(),
                blindIndex.keyVersion(),
                "ACTIVE");
        return repository.saveIdentifierWithinTransaction(connection, identifier);
    }

    /** Blind-index match inside the caller's transaction (§7.1 step 4). */
    public Optional<PersonIdentifier> findExactIdentifierMatchWithinTransaction(
            Connection connection, UUID tenantId, String identifierType,
            String issuingCountryCode, String plaintextValue) {
        Objects.requireNonNull(tenantId, "tenantId");

        String normalizedType = normalizer.normalizeIdentifierType(identifierType);
        String normalizedCountry = normalizer.normalizeCountryCode(issuingCountryCode);
        String normalizedValue = normalizer.normalizeValue(plaintextValue);
        String purpose = identifierPurpose(normalizedType, normalizedCountry);

        BlindIndex blindIndex = crypto.blindIndex(tenantId, purpose, normalizedValue);
        return repository.findActiveIdentifierByBlindIndexWithinTransaction(
                connection, tenantId, normalizedType, normalizedCountry, blindIndex.value());
    }

    private String identifierPurpose(String identifierType, String issuingCountryCode) {
        return IDENTIFIER_PURPOSE_PREFIX + identifierType + ":" +
                (issuingCountryCode == null ? "NONE" : issuingCountryCode);
    }

    private String buildDisplayName(String firstName, String middleName, String lastName) {
        if (middleName == null || middleName.isBlank()) {
            return firstName + " " + lastName;
        }
        return firstName + " " + middleName + " " + lastName;
    }
}
