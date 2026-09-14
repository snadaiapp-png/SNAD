package com.sanad.platform.hr.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 T7 — HrOfferVersion: IMMUTABLE commercial/employment offer history
 * (design §5.2, directive §T7.4).
 *
 * <p>A version represents the exact terms submitted for approval/extension.
 * Once a version has entered approval or has been extended, its material
 * terms are never mutated in place: changing material terms creates a NEW
 * version with a new identity/version number which repeats the governed
 * approval path before extension. The repository exposes only INSERT for
 * this table (no UPDATE path), and the database enforces append-only
 * semantics with an explicit guard trigger plus the
 * {@code UNIQUE (offer_id, version_number)} constraint (duplicate version
 * numbers are impossible, including under concurrent creation).</p>
 *
 * @param id                     immutable version identity
 * @param tenantId               tenant identity (FORCE RLS)
 * @param offerId                owning offer
 * @param versionNumber          1-based sequence; unique per offer (DB constraint)
 * @param predecessorVersionId   the version this successor revises (null for v1) —
 *                               the deterministic successor chain required by §T7.4
 * @param contractTerms          material terms snapshot (JSONB)
 * @param compensation           compensation draft snapshot (JSONB)
 * @param createdBy              actor that created the version
 * @param createdAt              creation timestamp
 */
public record HrOfferVersion(
        UUID id,
        UUID tenantId,
        UUID offerId,
        int versionNumber,
        UUID predecessorVersionId,
        String contractTerms,
        String compensation,
        UUID createdBy,
        OffsetDateTime createdAt) {

    public HrOfferVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(contractTerms, "contractTerms");
        Objects.requireNonNull(compensation, "compensation");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("HRM_OFFER_VERSION_NUMBER_INVALID: version numbers start at 1");
        }
    }
}
