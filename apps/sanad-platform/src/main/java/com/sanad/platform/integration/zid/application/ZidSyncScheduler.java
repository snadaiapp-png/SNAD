package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidLinkStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled maintenance for the Zid integration:
 *
 * <ol>
 *   <li><b>Token refresh</b> — connections whose authorization token
 *       expires within the refresh window get a proactive refresh
 *       (documented Zid guidance: refresh on a calendar, never on a
 *       401). A failed refresh flips the connection to
 *       REAUTH_REQUIRED — never a partial token swap.</li>
 *   <li><b>Stale link-state cleanup</b> — expired/used OAuth states
 *       are purged opportunistically.</li>
 * </ol>
 *
 * <p>All scheduling is inert unless BOTH {@code scheduling.enabled=true}
 * (platform gate, SchedulingConfig) and {@code snad.zid.enabled=true}
 * (integration gate) are set — safe by default on free-tier hosts.</p>
 */
@Component
public class ZidSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZidSyncScheduler.class);

    /** Refresh when the authorization token expires within this window. */
    private static final java.time.Duration REFRESH_WINDOW = java.time.Duration.ofDays(60);

    private final ZidConnectionRepository connections;
    private final ZidAuthService authService;
    private final ZidLinkStateRepository linkStates;
    private final ZidIntegrationProperties properties;
    private final ZidSyncAuditService auditService;

    public ZidSyncScheduler(ZidConnectionRepository connections,
                            ZidAuthService authService,
                            ZidLinkStateRepository linkStates,
                            ZidIntegrationProperties properties,
                            ZidSyncAuditService auditService) {
        this.connections = connections;
        this.authService = authService;
        this.linkStates = linkStates;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Scheduled(
            fixedDelayString = "${snad.zid.refresh-worker-delay-ms:3600000}",
            initialDelayString = "${snad.zid.refresh-worker-initial-delay-ms:30000}")
    public void refreshDueTokens() {
        if (!properties.isEnabled()) {
            return;
        }
        List<ZidConnectionRecord> connected = connections.findAllConnected();
        for (ZidConnectionRecord connection : connected) {
            if (!needsRefresh(connection)) {
                continue;
            }
            try {
                ZidAuthService.TokenSet tokens = authService.refreshTokens(connection);
                connections.updateTokenSet(connection.id(),
                        new ZidConnectionRecord(
                                connection.id(), connection.tenantId(), connection.zidStoreId(),
                                connection.zidStoreName(), connection.status(),
                                tokens.authorizationTokenEnc(), tokens.managerTokenEnc(), tokens.refreshTokenEnc(),
                                tokens.tokenKeyVersion(), tokens.authorizationExpiresAt(), tokens.refreshExpiresAt(),
                                Instant.now(), connection.lastSyncAt(), connection.lastSyncError(),
                                connection.createdAt(), connection.updatedAt()));
                auditService.record(connection.tenantId(), connection.id(),
                        ZidSyncAuditService.SyncType.TOKEN_REFRESH, "tokens",
                        ZidSyncAuditService.Direction.PULL, ZidSyncAuditService.Status.SUCCESS,
                        1, 0, null, null);
                log.info("Zid tokens refreshed for connection {}", connection.id());
            } catch (RuntimeException e) {
                String reason = ZidSyncAuditService.redact(String.valueOf(e.getMessage()));
                connections.updateStatus(connection.id(),
                        ZidConnectionRecord.STATUS_REAUTH_REQUIRED, null);
                auditService.record(connection.tenantId(), connection.id(),
                        ZidSyncAuditService.SyncType.TOKEN_REFRESH, "tokens",
                        ZidSyncAuditService.Direction.PULL, ZidSyncAuditService.Status.FAILED,
                        0, 1, reason, null);
                log.info("Zid token refresh failed for connection {}: marked REAUTH_REQUIRED",
                        connection.id());
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${snad.zid.link-state-cleanup-delay-ms:900000}",
            initialDelayString = "${snad.zid.link-state-cleanup-initial-delay-ms:60000}")
    public void purgeExpiredLinkStates() {
        if (!properties.isEnabled()) {
            return;
        }
        int purged = linkStates.purgeTerminal(Instant.now().minus(properties.getLinkStateTtl()));
        if (purged > 0) {
            log.info("Purged {} terminal Zid link states", purged);
        }
    }

    private boolean needsRefresh(ZidConnectionRecord connection) {
        Instant expiresAt = connection.authorizationExpiresAt();
        return expiresAt != null && expiresAt.isBefore(Instant.now().plus(REFRESH_WINDOW));
    }
}
