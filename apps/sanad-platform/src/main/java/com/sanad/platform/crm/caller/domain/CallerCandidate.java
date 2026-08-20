package com.sanad.platform.crm.caller.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A match candidate for caller identification (G8-02 §9).
 *
 * <p>Minimal caller-card projection only — deliberately NOT a Customer 360
 * payload (no activities, no opportunities, no notes, no full timeline).
 */
public record CallerCandidate(
        UUID communicationMethodId,
        String ownerType,                // PERSON | ACCOUNT | LEAD
        UUID ownerId,
        String normalizedPhone,
        String phoneLabel,
        boolean preferred,
        boolean verified,
        String verificationStatus,
        String privacyClassification,    // PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED
        String entityLifecycleStatus,    // ACTIVE | INACTIVE | ARCHIVED (lead: ACTIVE when eligible)
        UUID contactId,
        UUID accountId,
        UUID leadId,
        String displayName,
        String accountName,
        UUID ownerUserId,
        Instant updatedAt,
        String matchSource) {            // CANONICAL_COMMUNICATION_METHOD | LEGACY_LEAD_PHONE

    public static final String SOURCE_CANONICAL = "CANONICAL_COMMUNICATION_METHOD";
    public static final String SOURCE_LEGACY_LEAD_PHONE = "LEGACY_LEAD_PHONE";
}
