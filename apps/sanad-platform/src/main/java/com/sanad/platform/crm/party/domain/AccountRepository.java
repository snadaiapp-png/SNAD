package com.sanad.platform.crm.party.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for Account persistence.
 * Domain-layer interface — no JDBC, no Spring, no SQL.
 * Branch: crm/004-modular-domain-architecture
 */
public interface AccountRepository {

    AccountRecord findById(UUID tenantId, UUID accountId);

    List<AccountRecord> findAll(UUID tenantId, int limit, String search);

    AccountRecord create(UUID tenantId, UUID actorId, CreateAccountCommand command);

    AccountRecord update(UUID tenantId, UUID actorId, UUID accountId, UpdateAccountCommand command, long expectedVersion);

    AccountRecord archive(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion);

    AccountRecord restore(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion);

    // --- Value objects ---

    record AccountRecord(
            UUID id, long version, String displayName, String normalizedName,
            String accountType, String lifecycleStatus, String primaryCurrencyCode,
            String preferredLocale, String timeZone, String source,
            UUID parentAccountId, UUID ownerUserId,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}

    record CreateAccountCommand(
            String displayName, String accountType, UUID ownerUserId, UUID parentAccountId,
            String primaryCurrencyCode, String preferredLocale, String timeZone, String source) {}

    record UpdateAccountCommand(
            String displayName, UUID ownerUserId, UUID parentAccountId,
            String primaryCurrencyCode, String preferredLocale, String timeZone, String source) {}
}
