package com.sanad.platform.finance.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Finance Account — a chart of accounts entry.
 *
 * <p>Types: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
 *
 * <p>State machine: ACTIVE ↔ INACTIVE → ARCHIVED
 */
public record FinanceAccount(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        AccountType accountType,
        UUID parentAccountId,
        String currency,
        Status status,
        String description,
        java.math.BigDecimal balance,
        long versionLock,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum AccountType { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }
    public enum Status { ACTIVE, INACTIVE, ARCHIVED }

    public static FinanceAccount create(
            UUID tenantId, String code, String name, AccountType accountType,
            UUID parentAccountId, String currency, String description) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new FinanceAccount(
                UUID.randomUUID(), tenantId, code, name, accountType,
                parentAccountId, currency != null ? currency : "SAR",
                Status.ACTIVE, description,
                java.math.BigDecimal.ZERO,
                0, 0, now, now
        );
    }

    public FinanceAccount deactivate() {
        requireStatus(Status.ACTIVE, "deactivate");
        return withStatus(Status.INACTIVE);
    }

    public FinanceAccount archive() {
        requireStatus(Status.INACTIVE, "archive");
        return withStatus(Status.ARCHIVED);
    }

    private FinanceAccount withStatus(Status newStatus) {
        return new FinanceAccount(id, tenantId, code, name, accountType,
                parentAccountId, currency, newStatus, description, balance,
                versionLock + 1, version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
