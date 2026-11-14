package com.sanad.platform.integration.zid.dto;

import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;

import java.time.Instant;

/**
 * Connection status exposed to the admin UI. Contains ONLY
 * non-secret fields — no token material of any kind, encrypted or
 * otherwise.
 */
public record ZidConnectionStatusResponse(
        String status,
        String zidStoreId,
        String zidStoreName,
        Instant authorizationExpiresAt,
        Instant lastRefreshAt,
        Instant lastSyncAt,
        String lastSyncError,
        Instant linkedAt) {

    public static ZidConnectionStatusResponse notLinked() {
        return new ZidConnectionStatusResponse("NOT_LINKED", null, null,
                null, null, null, null, null);
    }

    public static ZidConnectionStatusResponse from(ZidConnectionRecord record) {
        return new ZidConnectionStatusResponse(
                record.status(),
                record.zidStoreId(),
                record.zidStoreName(),
                record.authorizationExpiresAt(),
                record.lastRefreshAt(),
                record.lastSyncAt(),
                record.lastSyncError(),
                record.createdAt());
    }
}
