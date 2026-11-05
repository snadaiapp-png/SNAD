package com.sanad.platform.integration.zid;

import com.sanad.platform.integration.zid.application.ZidAuthService;
import com.sanad.platform.integration.zid.application.ZidSyncAuditService;
import com.sanad.platform.integration.zid.application.ZidTenantBindingService;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidLinkStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Binding lifecycle unit tests: state minting, tenant-binding
 * enforcement, single-use states, expiry, duplicate-store prevention,
 * and status transitions. No real Zid calls — the connector and auth
 * service are mocked.
 */
class ZidTenantBindingServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();

    private ZidLinkStateRepository linkStates;
    private ZidConnectionRepository connections;
    private ZidAuthService authService;
    private ZidConnector connector;
    private ZidIntegrationProperties properties;
    private ZidSyncAuditService auditService;
    private ZidTenantBindingService service;

    @BeforeEach
    void setUp() {
        linkStates = mock(ZidLinkStateRepository.class);
        connections = mock(ZidConnectionRepository.class);
        authService = mock(ZidAuthService.class);
        connector = mock(ZidConnector.class);
        properties = new ZidIntegrationProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client-id");
        properties.setLinkStateTtl(Duration.ofMinutes(15));
        auditService = mock(ZidSyncAuditService.class);
        service = new ZidTenantBindingService(linkStates, connections, authService,
                connector, properties, auditService);
    }

    @Test
    void beginLinkReturnsAuthorizeUrlAndMintsState() {
        when(authService.resolveCallbackUrl()).thenReturn("https://sanad.example/api/v1/integrations/zid/link/callback");
        when(connector.buildAuthorizeUrl(eq("test-client-id"), anyString(), anyString()))
                .thenReturn("https://oauth.zid.sa/oauth2/authorize?client_id=test-client-id&state=abc");

        ZidTenantBindingService.LinkStart start = service.beginLink(TENANT_A, USER_A);

        assertThat(start.authorizationUrl()).startsWith("https://oauth.zid.sa");
        assertThat(start.state()).hasSize(64); // 256-bit hex
        assertThat(start.expiresAt()).isAfter(Instant.now());
        Mockito.verify(linkStates).insert(any());
    }

    @Test
    void completeLinkRejectsUnknownState() {
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeLink(TENANT_A, "code", "unknown-state"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_LINK_STATE));
    }

    @Test
    void completeLinkRejectsStateMintedForAnotherTenant() {
        UUID otherTenant = UUID.randomUUID();
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.of(
                new ZidLinkStateRepository.ZidLinkStateRecord(UUID.randomUUID(), otherTenant, USER_A,
                        "hash", "PENDING", Instant.now(), Instant.now().plusSeconds(600))));
        assertThatThrownBy(() -> service.completeLink(TENANT_A, "code", "state"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_LINK_STATE));
    }

    @Test
    void completeLinkRejectsExpiredState() {
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.of(
                new ZidLinkStateRepository.ZidLinkStateRecord(UUID.randomUUID(), TENANT_A, USER_A,
                        "hash", "PENDING", Instant.now().minus(Duration.ofHours(1)),
                        Instant.now().minusSeconds(1))));
        assertThatThrownBy(() -> service.completeLink(TENANT_A, "code", "state"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_LINK_STATE));
        Mockito.verify(linkStates).markExpired(any());
    }

    @Test
    void completeLinkRejectsReplayedState() {
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.of(
                new ZidLinkStateRepository.ZidLinkStateRecord(UUID.randomUUID(), TENANT_A, USER_A,
                        "hash", "PENDING", Instant.now(), Instant.now().plusSeconds(600))));
        when(linkStates.claimPending(any())).thenReturn(false);
        assertThatThrownBy(() -> service.completeLink(TENANT_A, "code", "state"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_LINK_STATE));
    }

    @Test
    void completeLinkRejectsStoreAlreadyBoundToAnotherTenant() {
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.of(
                new ZidLinkStateRepository.ZidLinkStateRecord(UUID.randomUUID(), TENANT_A, USER_A,
                        "hash", "PENDING", Instant.now(), Instant.now().plusSeconds(600))));
        when(linkStates.claimPending(any())).thenReturn(true);
        when(authService.exchangeAuthorizationCode(eq(TENANT_A), eq("good-code")))
                .thenReturn(new ZidAuthService.TokenSet("ea", "em", "er", "v1",
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400)));
        when(authService.decryptTokenSet(eq(TENANT_A), any()))
                .thenReturn(new ZidAuthService.ZidCredentials("auth", "mgr", null));
        when(connector.get(any(), anyString(), any()))
                .thenReturn(new ZidConnector.ZidResponse(200, storeIdentityBody(3), Optional.empty()));
        when(connections.findByStoreId("3")).thenReturn(Optional.of(new ZidConnectionRecord(
                UUID.randomUUID(), UUID.randomUUID() /* different tenant */, "3", "store",
                ZidConnectionRecord.STATUS_CONNECTED, null, null, null, null,
                Instant.now(), Instant.now(), null, null, null, Instant.now(), Instant.now())));

        assertThatThrownBy(() -> service.completeLink(TENANT_A, "good-code", "state"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.STORE_ALREADY_BOUND));
        Mockito.verify(connections, Mockito.never()).insert(any());
    }

    @Test
    void completeLinkBindsVerifiedStoreIdentity() {
        when(linkStates.findByStateHash(anyString())).thenReturn(Optional.of(
                new ZidLinkStateRepository.ZidLinkStateRecord(UUID.randomUUID(), TENANT_A, USER_A,
                        "hash", "PENDING", Instant.now(), Instant.now().plusSeconds(600))));
        when(linkStates.claimPending(any())).thenReturn(true);
        when(authService.exchangeAuthorizationCode(eq(TENANT_A), eq("good-code")))
                .thenReturn(new ZidAuthService.TokenSet("ea", "em", "er", "v1",
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400)));
        when(authService.decryptTokenSet(eq(TENANT_A), any()))
                .thenReturn(new ZidAuthService.ZidCredentials("auth", "mgr", null));
        when(connector.get(any(), anyString(), any()))
                .thenReturn(new ZidConnector.ZidResponse(200, storeIdentityBody(3), Optional.empty()));
        when(connections.findByStoreId("3")).thenReturn(Optional.empty());

        ZidConnectionRecord bound = service.completeLink(TENANT_A, "good-code", "state");

        assertThat(bound.tenantId()).isEqualTo(TENANT_A);
        assertThat(bound.zidStoreId()).isEqualTo("3");
        assertThat(bound.status()).isEqualTo(ZidConnectionRecord.STATUS_CONNECTED);
        Mockito.verify(connections).insert(any());
    }

    @Test
    void unlinkRejectsConnectionOwnedByAnotherTenant() {
        UUID foreignConnectionId = UUID.randomUUID();
        when(connections.findById(foreignConnectionId)).thenReturn(Optional.of(new ZidConnectionRecord(
                foreignConnectionId, UUID.randomUUID() /* other tenant */, "3", "store",
                ZidConnectionRecord.STATUS_CONNECTED, null, null, null, null,
                Instant.now(), Instant.now(), null, null, null, Instant.now(), Instant.now())));
        assertThatThrownBy(() -> service.unlink(TENANT_A, foreignConnectionId))
                .isInstanceOf(ZidIntegrationException.class);
        Mockito.verify(connections, Mockito.never()).deleteById(any());
    }

    private com.fasterxml.jackson.databind.JsonNode storeIdentityBody(long storeId) {
        try {
            String json = "{\"orders\":[{\"id\":1,\"store_id\":" + storeId
                    + ",\"store_name\":\"متجر الاختبار\",\"order_status\":{\"name\":\"جديد\",\"code\":\"new\"}}]}";
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
