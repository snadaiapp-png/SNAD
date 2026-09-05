package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.identity.PersonIdentifier;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — metadata-only view of a stored identity
 * document. Ciphertext, blind index and key versions are structural
 * persistence data and are deliberately absent from the API surface; the
 * plaintext value is write-only and can never be read back through this API.
 */
public record IdentifierMetadataResponse(
        UUID identifierId,
        String identifierType,
        String issuingCountryCode,
        String status
) {

    public static IdentifierMetadataResponse from(PersonIdentifier identifier) {
        return new IdentifierMetadataResponse(identifier.id(), identifier.identifierType(),
                identifier.issuingCountryCode(), identifier.status());
    }
}
