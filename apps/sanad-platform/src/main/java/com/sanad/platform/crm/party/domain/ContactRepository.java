package com.sanad.platform.crm.party.domain;

import java.util.List;
import java.util.UUID;

/**
 * Repository port for Contact persistence.
 * Domain-layer interface — no JDBC, no Spring, no SQL.
 */
public interface ContactRepository {

    ContactRecord findById(UUID tenantId, UUID contactId);

    List<ContactRecord> findAll(UUID tenantId, int limit, UUID accountId, String search);

    ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand command);

    ContactRecord update(UUID tenantId, UUID actorId, UUID contactId, UpdateContactCommand command, long expectedVersion);

    ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion);

    ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion);

    record ContactRecord(
            UUID id, long version, UUID accountId, String givenName, String familyName,
            String displayName, String primaryEmail, String normalizedEmail,
            String primaryPhone, String preferredLocale, String timeZone,
            String lifecycleStatus, UUID ownerUserId, String consentSummary,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}

    record CreateContactCommand(
            UUID accountId, String givenName, String familyName, String primaryEmail,
            String primaryPhone, String preferredLocale, String timeZone,
            UUID ownerUserId, String consentSummary) {}

    record UpdateContactCommand(
            UUID accountId, String givenName, String familyName, String primaryEmail,
            String primaryPhone, String preferredLocale, String timeZone,
            UUID ownerUserId, String consentSummary) {}
}
