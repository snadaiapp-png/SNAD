package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidLinkStateRepository;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Binds a Zid store to exactly one SANAD tenant.
 *
 * <p><b>Flow (verified against docs.zid.sa /authorization):</b></p>
 * <ol>
 *   <li>{@link #beginLink} — mints a cryptographically random {@code state}
 *       (256-bit), persists only its SHA-256 hash with a 15-minute TTL
 *       bound to the authenticated tenant+user, and returns the absolute
 *       authorize URL.</li>
 *   <li>Zid redirects the merchant to {@code SANAD_ZID_REDIRECT_URL}
 *       with {@code ?code=...&state=...}.</li>
 *   <li>{@link #completeLink} — hashes the incoming state, looks it up,
 *       verifies PENDING + TTL, claims it atomically (single use),
 *       exchanges the code, then verifies store identity by calling the
 *       verified orders endpoint and reading {@code store_id}. The store
 *       is bound with UNIQUE constraints so it can never belong to two
 *       tenants.</li>
 * </ol>
 *
 * <p>Connection status is always one of CONNECTED / REAUTH_REQUIRED /
 * PAUSED / FAILED and is exposed to the admin UI — never token
 * material.</p>
 */
@Service
public class ZidTenantBindingService {

    private static final Logger log = LoggerFactory.getLogger(ZidTenantBindingService.class);
    private static final int STATE_BYTES = 32;

    private final ZidLinkStateRepository linkStates;
    private final ZidConnectionRepository connections;
    private final ZidAuthService authService;
    private final ZidConnector connector;
    private final ZidIntegrationProperties properties;
    private final ZidSyncAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ZidTenantBindingService(ZidLinkStateRepository linkStates,
                                   ZidConnectionRepository connections,
                                   ZidAuthService authService,
                                   ZidConnector connector,
                                   ZidIntegrationProperties properties,
                                   ZidSyncAuditService auditService) {
        this.linkStates = linkStates;
        this.connections = connections;
        this.authService = authService;
        this.connector = connector;
        this.properties = properties;
        this.auditService = auditService;
    }

    public record LinkStart(String authorizationUrl, String state, Instant expiresAt) {
    }

    /** Mint a single-use link state and the merchant-facing authorize URL. */
    public LinkStart beginLink(UUID tenantId, UUID userId) {
        requireConfigured();
        byte[] raw = new byte[STATE_BYTES];
        secureRandom.nextBytes(raw);
        String state = HexFormat.of().formatHex(raw);
        Instant expiresAt = Instant.now().plus(properties.getLinkStateTtl());

        linkStates.insert(new ZidLinkStateRepository.ZidLinkStateRecord(
                UUID.randomUUID(), tenantId, userId, sha256(state), "PENDING", Instant.now(), expiresAt));

        String url = connector.buildAuthorizeUrl(properties.getClientId(), authService.resolveCallbackUrl(), state);
        return new LinkStart(url, state, expiresAt);
    }

    /**
     * Complete the binding. Throws
     * {@link ZidIntegrationException} with reason
     * {@code INVALID_LINK_STATE} for expired/replayed/unknown states,
     * {@code STORE_ALREADY_BOUND} when the store is already bound.
     */
    @Transactional
    public ZidConnectionRecord completeLink(UUID tenantId, String code, String state) {
        requireConfigured();
        ZidLinkStateRepository.ZidLinkStateRecord linkState = linkStates.findByStateHash(sha256(state))
                .orElseThrow(() -> new ZidIntegrationException(
                        ZidIntegrationException.Reason.INVALID_LINK_STATE, "Unknown link state"));

        if (!tenantId.equals(linkState.tenantId())) {
            // A state minted for tenant A cannot complete a request authenticated as tenant B.
            auditService.record(tenantId, null, ZidSyncAuditService.SyncType.LINK, "link_state",
                    ZidSyncAuditService.Direction.BINDING, ZidSyncAuditService.Status.FAILED,
                    0, 1, "Link state tenant mismatch", null);
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_LINK_STATE,
                    "Link state does not belong to the authenticated tenant");
        }
        if (linkState.expiresAt().isBefore(Instant.now())) {
            linkStates.markExpired(linkState.id());
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_LINK_STATE,
                    "Link state expired");
        }
        if (!linkStates.claimPending(linkState.id())) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_LINK_STATE,
                    "Link state already used or cancelled");
        }

        // Exchange the single-use authorization code (10-minute TTL documented).
        ZidAuthService.TokenSet tokens = authService.exchangeAuthorizationCode(tenantId, code);
        ZidAuthService.ZidCredentials credentials = authService.decryptTokenSet(tenantId, tokens);

        // Verify store identity through a verified endpoint before binding.
        String resolvedStoreId;
        String resolvedStoreName;
        try {
            Map<String, String> query = Map.of("page", "1", "per_page", "1");
            ZidConnector.ZidResponse response = connector.get(
                    new ZidConnector.ConnectionCredentials(
                            credentials.authorizationToken(), credentials.managerToken(), ""),
                    "managers/store/orders", query);
            if (!response.isSuccess() || response.body() == null || !response.body().has("orders")) {
                throw new ZidIntegrationException(ZidIntegrationException.Reason.STORE_IDENTITY_UNRESOLVED,
                        "Zid store identity could not be verified (status " + response.statusCode() + ")");
            }
            var firstOrder = response.body().path("orders").path(0);
            resolvedStoreId = String.valueOf(firstOrder.path("store_id").asLong(0));
            resolvedStoreName = firstOrder.path("store_name").asText(null);
            if (resolvedStoreId == null || "0".equals(resolvedStoreId)) {
                throw new ZidIntegrationException(ZidIntegrationException.Reason.STORE_IDENTITY_UNRESOLVED,
                        "Zid store id missing from identity probe");
            }
        } catch (ZidIntegrationException e) {
            auditService.record(tenantId, null, ZidSyncAuditService.SyncType.LINK, "store_identity",
                    ZidSyncAuditService.Direction.BINDING, ZidSyncAuditService.Status.FAILED,
                    0, 1, e.getMessage(), null);
            throw e;
        }

        // Enforce one-store-one-tenant (DB unique constraints are the hard stop).
        Optional<ZidConnectionRecord> existing = connections.findByStoreId(resolvedStoreId);
        if (existing.isPresent()) {
            auditService.record(tenantId, existing.get().id(), ZidSyncAuditService.SyncType.LINK, "binding",
                    ZidSyncAuditService.Direction.BINDING, ZidSyncAuditService.Status.CONFLICT,
                    0, 1, "Store already bound", null);
            throw new ZidIntegrationException(ZidIntegrationException.Reason.STORE_ALREADY_BOUND,
                    "This Zid store is already bound to a tenant");
        }

        ZidConnectionRecord record = new ZidConnectionRecord(
                UUID.randomUUID(), tenantId, resolvedStoreId, resolvedStoreName,
                ZidConnectionRecord.STATUS_CONNECTED,
                tokens.authorizationTokenEnc(), tokens.managerTokenEnc(), tokens.refreshTokenEnc(),
                tokens.tokenKeyVersion(),
                tokens.authorizationExpiresAt(), tokens.refreshExpiresAt(),
                Instant.now(), null, null, Instant.now(), Instant.now());
        connections.insert(record);

        auditService.record(tenantId, record.id(), ZidSyncAuditService.SyncType.LINK, "binding",
                ZidSyncAuditService.Direction.BINDING, ZidSyncAuditService.Status.SUCCESS,
                1, 0, null, null);
        log.info("Zid store {} bound to tenant {}", resolvedStoreId, tenantId);
        return record;
    }

    /** Unlink (operator-initiated). Tokens are destroyed, never logged. */
    @Transactional
    public void unlink(UUID tenantId, UUID connectionId) {
        ZidConnectionRecord record = connections.findById(connectionId)
                .filter(c -> c.tenantId().equals(tenantId))
                .orElseThrow(() -> new ZidIntegrationException(
                        ZidIntegrationException.Reason.TRUSTED_BINDING_NOT_FOUND,
                        "Connection not found for this tenant"));
        connections.deleteById(connectionId);
        auditService.record(tenantId, connectionId, ZidSyncAuditService.SyncType.LINK, "unlink",
                ZidSyncAuditService.Direction.BINDING, ZidSyncAuditService.Status.SUCCESS,
                1, 0, null, null);
        log.info("Zid store {} unbound from tenant {}", record.zidStoreId(), tenantId);
    }

    public Optional<ZidConnectionRecord> connectionForTenant(UUID tenantId, UUID connectionId) {
        return connections.findById(connectionId)
                .filter(c -> c.tenantId().equals(tenantId));
    }

    private void requireConfigured() {
        if (!properties.isEnabled()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.NOT_CONFIGURED,
                    "Zid integration is not enabled");
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time equality to avoid timing oracles on state comparison. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public Duration linkStateTtl() {
        return properties.getLinkStateTtl();
    }
}
