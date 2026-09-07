package com.sanad.platform.hr.identity;

import java.util.UUID;

/**
 * Person Identifier — encrypted identity document reference.
 *
 * <p>Stores the canonical ciphertext, blind index, and key-version metadata
 * for a sensitive identifier (National ID, Passport, Iqama, etc.). The raw
 * plaintext value is NEVER stored or returned from this type — only the
 * ciphertext and blind index are persisted.</p>
 *
 * <p>This is a Cycle 2 minimal skeleton. Real persistence and cryptographic
 * behavior is added in Cycle 4 GREEN.</p>
 *
 * @param id                       row UUID
 * @param tenantId                 owning tenant
 * @param personId                 owning Person
 * @param identifierType           normalized type (NATIONAL_ID, PASSPORT, ...)
 * @param issuingCountryCode       normalized ISO 3166-1 alpha-2 (nullable)
 * @param identifierCiphertext     AES-256-GCM ciphertext payload (versioned)
 * @param blindIndex               HMAC-SHA-256 deterministic blind index
 * @param encryptionKeyVersion     ciphertext key version
 * @param blindIndexKeyVersion     blind index key version (separate from ciphertext)
 * @param status                   ACTIVE / EXPIRED / REVOKED
 */
public record PersonIdentifier(
        UUID id,
        UUID tenantId,
        UUID personId,
        String identifierType,
        String issuingCountryCode,
        String identifierCiphertext,
        String blindIndex,
        String encryptionKeyVersion,
        String blindIndexKeyVersion,
        String status
) {}
