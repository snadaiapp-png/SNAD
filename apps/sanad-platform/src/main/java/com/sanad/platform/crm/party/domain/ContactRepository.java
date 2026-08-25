package com.sanad.platform.crm.party.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository port for Contact persistence.
 * Domain-layer interface — no JDBC, no Spring, no SQL.
 */
public interface ContactRepository {

    ContactRecord findById(UUID tenantId, UUID contactId);

    /**
     * C5 canonical Contact-transfer primitive — tenant-scoped row lock.
     *
     * <p>Issues {@code SELECT ... FROM crm_contacts WHERE tenant_id = :tenantId
     * AND id = :contactId FOR UPDATE} so concurrent transfer attempts on the
     * same Contact serialize on the Contact row. Lock order is
     * <strong>CONTACT_ROW_FIRST</strong> — participant mutations must always
     * occur after this lock is held, never before.</p>
     *
     * @throws com.sanad.platform.crm.error.CrmContractException with
     *         {@code CRM_CONTACT_NOT_FOUND} when the Contact is absent or
     *         invisible under FORCE RLS in the current tenant scope.
     */
    ContactRecord findByIdForUpdate(UUID tenantId, UUID contactId);

    /**
     * C5 canonical Contact-transfer primitive — versioned owner update.
     *
     * <p>Issues a NARROW {@code UPDATE crm_contacts} that touches ONLY:</p>
     * <ul>
     *   <li>{@code owner_user_id}</li>
     *   <li>{@code updated_by}</li>
     *   <li>{@code updated_at}</li>
     *   <li>{@code version} (incremented by exactly 1)</li>
     * </ul>
     *
     * <p>Optimistic-lock contract:</p>
     * <pre>
     * UPDATE crm_contacts
     * SET owner_user_id = :newOwnerUserId,
     *     updated_by    = :actorId,
     *     updated_at    = :occurredAt,
     *     version       = version + 1
     * WHERE tenant_id = :tenantId AND id = :contactId
     *   AND version = :expectedVersion
     * </pre>
     *
     * <p>If rows changed == 0 (version mismatch or row invisible under FORCE
     * RLS), throws {@code CrmContractException(CRM_CONCURRENCY_CONFLICT)}.</p>
     *
     * <p>This primitive does NOT use {@code COALESCE} — the new owner is
     * mandatory and is set unconditionally. No other Contact business fields
     * may change. The legacy relationship backfill is NOT triggered by this
     * primitive (C8 owns structured events; C6 owns legacy PATCH
     * reconciliation).</p>
     */
    ContactRecord transferOwner(UUID tenantId,
                                UUID actorId,
                                UUID contactId,
                                UUID newOwnerUserId,
                                long expectedVersion,
                                Instant occurredAt);

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
