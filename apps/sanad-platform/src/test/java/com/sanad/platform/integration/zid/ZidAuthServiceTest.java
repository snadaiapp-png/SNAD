package com.sanad.platform.integration.zid;

import com.sanad.platform.integration.zid.application.ZidAuthService;
import com.sanad.platform.integration.zid.application.ZidSyncAuditService;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Zid OAuth lifecycle against the VERIFIED token
 * response contract (token_type / expires_in / authorization /
 * access_token / refresh_token). Uses mock cryptography — no real key
 * material and no real client secrets are involved.
 */
class ZidAuthServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SECRET_AUTH = "auth-plaintext-token-value-for-test";
    private static final String SECRET_MGR = "manager-plaintext-token-value-for-test";
    private static final String SECRET_REFRESH = "refresh-plaintext-token-value-for-test";

    private ZidConnector connector;
    private ZidIntegrationProperties properties;
    private FakeCryptography cryptography;
    private ZidAuthService service;

    /** Deterministic in-memory stand-in for the AES-GCM platform service. */
    static class FakeCryptography implements PlatformCryptographyService {
        @Override
        public EncryptedValue encrypt(UUID tenantId, String purpose, String plaintext) {
            return new EncryptedValue("enc:v1:[" + purpose + "]" + plaintext, "v1", "AES/GCM/NoPadding");
        }

        @Override
        public String decrypt(UUID tenantId, String purpose, EncryptedValue value) {
            String marker = "enc:v1:[" + purpose + "]";
            if (!value.ciphertext().startsWith(marker)) {
                throw new IllegalStateException("purpose-bound decryption violated");
            }
            return value.ciphertext().substring(marker.length());
        }

        @Override
        public BlindIndex blindIndex(UUID tenantId, String purpose, String normalizedValue) {
            return new BlindIndex("idx:" + purpose + ":" + normalizedValue, "v1", "HmacSHA256");
        }
    }

    @BeforeEach
    void setUp() {
        connector = mock(ZidConnector.class);
        properties = new ZidIntegrationProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        cryptography = new FakeCryptography();
        properties.setRedirectUrl("https://sanad.example/api/v1/integrations/zid/link/callback");
        service = new ZidAuthService(connector, properties, cryptography);
    }

    @Test
    void exchangeParsesVerifiedResponseShapeAndEncryptsEveryToken() {
        when(connector.postTokenForm(eq("/oauth2/token"), any()))
                .thenReturn(new ZidConnector.ZidResponse(200, tokenBody(), java.util.Optional.empty()));

        ZidAuthService.TokenSet tokens = service.exchangeAuthorizationCode(TENANT, "one-time-code");

        assertThat(tokens.authorizationTokenEnc()).isEqualTo("enc:v1:[zid:authorization-token]" + SECRET_AUTH);
        assertThat(tokens.managerTokenEnc()).isEqualTo("enc:v1:[zid:manager-token]" + SECRET_MGR);
        assertThat(tokens.refreshTokenEnc()).isEqualTo("enc:v1:[zid:refresh-token]" + SECRET_REFRESH);
        assertThat(tokens.authorizationExpiresAt()).isAfter(Instant.now());
        assertThat(tokens.refreshExpiresAt()).isAfter(Instant.now().plusSeconds(3600));
    }

    @Test
    void exchangeSendsClientCredentialsOnlyServerSide() {
        when(connector.postTokenForm(eq("/oauth2/token"), any()))
                .thenReturn(new ZidConnector.ZidResponse(200, tokenBody(), java.util.Optional.empty()));
        service.exchangeAuthorizationCode(TENANT, "one-time-code");
        verify(connector).postTokenForm(eq("/oauth2/token"), Mockito.argThat(form ->
                "authorization_code".equals(form.get("grant_type"))
                        && "one-time-code".equals(form.get("code"))
                        && "test-client-id".equals(form.get("client_id"))
                        && "test-client-secret".equals(form.get("client_secret"))));
    }

    @Test
    void refreshDecryptsStoredTokenAndReplacesAtomically() {
        ZidConnectionRecord connection = new ZidConnectionRecord(
                UUID.randomUUID(), TENANT, "3", "store",
                ZidConnectionRecord.STATUS_CONNECTED,
                "enc:v1:[zid:authorization-token]" + SECRET_AUTH,
                "enc:v1:[zid:manager-token]" + SECRET_MGR,
                "enc:v1:[zid:refresh-token]" + SECRET_REFRESH,
                "v1", Instant.now(), Instant.now(), null, null, null,
                Instant.now(), Instant.now());
        when(connector.postTokenForm(eq("/oauth2/token"), any()))
                .thenReturn(new ZidConnector.ZidResponse(200, tokenBody(), java.util.Optional.empty()));

        ZidAuthService.TokenSet tokens = service.refreshTokens(connection);

        assertThat(tokens.refreshTokenEnc()).contains(SECRET_REFRESH);
        verify(connector).postTokenForm(eq("/oauth2/token"), Mockito.argThat(form ->
                "refresh_token".equals(form.get("grant_type"))
                        && SECRET_REFRESH.equals(form.get("refresh_token"))));
    }

    @Test
    void connectionWithoutRefreshTokenFailsClosed() {
        ZidConnectionRecord connection = new ZidConnectionRecord(
                UUID.randomUUID(), TENANT, "3", "store",
                ZidConnectionRecord.STATUS_CONNECTED,
                "enc:v1:a", "enc:v1:m", null, "v1",
                Instant.now(), null, null, null, null, Instant.now(), Instant.now());
        assertThatThrownBy(() -> service.refreshTokens(connection))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.TOKEN_REJECTED));
        verify(connector, never()).postTokenForm(anyString(), any());
    }

    @Test
    void failsClosedWhenIntegrationDisabled() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.exchangeAuthorizationCode(TENANT, "code"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.NOT_CONFIGURED));
    }

    @Test
    void failsClosedWhenClientCredentialsMissing() {
        properties.setClientSecret("");
        assertThatThrownBy(() -> service.exchangeAuthorizationCode(TENANT, "code"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.NOT_CONFIGURED));
    }

    @Test
    void malformedTokenResponseIsRejected() {
        when(connector.postTokenForm(eq("/oauth2/token"), any()))
                .thenReturn(new ZidConnector.ZidResponse(200,
                        new ObjectMapper().createObjectNode(), java.util.Optional.empty()));
        assertThatThrownBy(() -> service.exchangeAuthorizationCode(TENANT, "code"))
                .isInstanceOf(ZidIntegrationException.class);
    }

    @Test
    void decryptCredentialsRoundTripsWithoutPlaintextAtRest() {
        ZidConnectionRecord connection = new ZidConnectionRecord(
                UUID.randomUUID(), TENANT, "3", "store",
                ZidConnectionRecord.STATUS_CONNECTED,
                "enc:v1:[zid:authorization-token]" + SECRET_AUTH,
                "enc:v1:[zid:manager-token]" + SECRET_MGR,
                "enc:v1:[zid:refresh-token]" + SECRET_REFRESH,
                "v1", Instant.now(), Instant.now(), null, null, null,
                Instant.now(), Instant.now());
        ZidAuthService.ZidCredentials credentials = service.decryptCredentials(connection);
        assertThat(credentials.authorizationToken()).isEqualTo(SECRET_AUTH);
        assertThat(credentials.managerToken()).isEqualTo(SECRET_MGR);
        assertThat(credentials.zidStoreId()).isEqualTo("3");
    }

    @Test
    void errorMessagesNeverContainTokenMaterial() {
        when(connector.postTokenForm(eq("/oauth2/token"), any()))
                .thenReturn(new ZidConnector.ZidResponse(400, null, java.util.Optional.empty()));
        String message = null;
        try {
            service.exchangeAuthorizationCode(TENANT, "code");
        } catch (ZidIntegrationException e) {
            message = ZidSyncAuditService.redact(e.getMessage());
        }
        assertThat(message).doesNotContain(SECRET_AUTH).doesNotContain(SECRET_MGR)
                .doesNotContain(SECRET_REFRESH).doesNotContain("test-client-secret");
    }

    private com.fasterxml.jackson.databind.JsonNode tokenBody() {
        try {
            String json = "{"
                    + "\"token_type\":\"Bearer\","
                    + "\"expires_in\":94608000,"
                    + "\"authorization\":\"" + SECRET_AUTH + "\","
                    + "\"access_token\":\"" + SECRET_MGR + "\","
                    + "\"refresh_token\":\"" + SECRET_REFRESH + "\""
                    + "}";
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
