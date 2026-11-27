package com.sanad.platform.integration.zid.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable row projection of {@code zid_store_connections}. Token
 * fields hold the ENCRYPTED payload strings produced by
 * {@code PlatformCryptographyService} — never plaintext.
 */
public record ZidConnectionRecord(
        UUID id,
        UUID tenantId,
        String zidStoreId,
        String zidStoreName,
        String status,
        String authorizationTokenEnc,
        String managerTokenEnc,
        String refreshTokenEnc,
        String tokenKeyVersion,
        Instant authorizationExpiresAt,
        Instant refreshExpiresAt,
        Instant lastRefreshAt,
        Instant lastSyncAt,
        String lastSyncError,
        Instant createdAt,
        Instant updatedAt) {

    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_REAUTH_REQUIRED = "REAUTH_REQUIRED";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_FAILED = "FAILED";
}
