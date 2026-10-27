package com.sanad.platform.integration.zid.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zid OAuth lifecycle: authorization-code exchange, atomic token-set
 * refresh, and credential decryption for outbound calls.
 *
 * <p><b>Verified contract (docs.zid.sa /authorization, 2026-10-28):</b></p>
 * <ul>
 *   <li>The authorization code is single-use and valid for 10 minutes.</li>
 *   <li>The token response carries {@code token_type}, {@code expires_in},
 *       {@code authorization} (Bearer), {@code access_token}
 *       (X-Manager-Token) and {@code refresh_token}.</li>
 *   <li>A refresh returns a NEW refresh token and revokes the previous
 *       one — the stored set is therefore replaced atomically in one
 *       UPDATE (see ZidConnectionRepository.updateTokenSet).</li>
 *   <li>PKCE is not part of Zid's flow; clients are confidential. The
 *       client secret only travels over the server-to-server token call
 *       and is never placed in a browser-facing URL.</li>
 * </ul>
 *
 * <p>Token material is encrypted at rest with the platform AES-GCM
 * service, purpose-bound to the owning tenant. Logs and error messages
 * produced here never include token or secret values.</p>
 */
@Service
public class ZidAuthService {

    private static final Logger log = LoggerFactory.getLogger(ZidAuthService.class);

    public static final String PURPOSE_AUTHORIZATION_TOKEN = "zid:authorization-token";
    public static final String PURPOSE_MANAGER_TOKEN = "zid:manager-token";
    public static final String PURPOSE_REFRESH_TOKEN = "zid:refresh-token";

    /** Documented refresh-token lifetime (1 year) — not present in the response body. */
    private static final Duration DEFAULT_REFRESH_LIFETIME = Duration.ofDays(365);

    private final ZidConnector connector;
    private final ZidIntegrationProperties properties;
    private final PlatformCryptographyService cryptography;

    public ZidAuthService(ZidConnector connector,
                          ZidIntegrationProperties properties,
                          PlatformCryptographyService cryptography) {
        this.connector = connector;
        this.properties = properties;
        this.cryptography = cryptography;
    }

    /** Decrypted, in-memory credentials for one API call. Never persisted or logged. */
    public record ZidCredentials(String authorizationToken, String managerToken, String zidStoreId) {
        public ZidConnector.ConnectionCredentials toConnectionCredentials() {
            return new ZidConnector.ConnectionCredentials(authorizationToken, managerToken, zidStoreId);
        }
    }

    /** Result of an authorization-code exchange or refresh. */
    public record TokenSet(String authorizationTokenEnc,
                           String managerTokenEnc,
                           String refreshTokenEnc,
                           String tokenKeyVersion,
                           Instant authorizationExpiresAt,
                           Instant refreshExpiresAt) {
    }

    public TokenSet exchangeAuthorizationCode(UUID tenantId, String authorizationCode) {
        requireEnabled();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", authorizationCode);
        form.put("client_id", properties.getClientId());
        form.put("client_secret", properties.getClientSecret());
        form.put("redirect_uri", resolveCallbackUrl());

        ZidConnector.ZidResponse response = connector.postTokenForm(properties.getTokenPath(), form);
        if (!response.isSuccess() || response.body() == null) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_AUTHORIZATION_CODE,
                    "Zid token exchange failed (status " + response.statusCode() + ")");
        }
        return storeTokenSet(tenantId, response.body());
    }

    public TokenSet refreshTokens(ZidConnectionRecord connection) {
        requireEnabled();
        if (connection.refreshTokenEnc() == null) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TOKEN_REJECTED,
                    "Connection has no refresh token; operator re-authorization required");
        }
        String refreshToken = cryptography.decrypt(
                connection.tenantId(), PURPOSE_REFRESH_TOKEN,
                new EncryptedValue(connection.refreshTokenEnc(), connection.tokenKeyVersion(), "AES/GCM/NoPadding"));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", properties.getClientId());
        form.put("client_secret", properties.getClientSecret());

        ZidConnector.ZidResponse response = connector.postTokenForm(properties.getTokenPath(), form);
        if (!response.isSuccess() || response.body() == null) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TOKEN_REJECTED,
                    "Zid refresh call failed (status " + response.statusCode() + ")");
        }
        return storeTokenSet(connection.tenantId(), response.body());
    }

    public ZidCredentials decryptCredentials(ZidConnectionRecord connection) {
        if (connection.authorizationTokenEnc() == null || connection.managerTokenEnc() == null) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TOKEN_REJECTED,
                    "Connection token material is missing; re-authorization required");
        }
        String authorizationToken = cryptography.decrypt(
                connection.tenantId(), PURPOSE_AUTHORIZATION_TOKEN,
                new EncryptedValue(connection.authorizationTokenEnc(), connection.tokenKeyVersion(), "AES/GCM/NoPadding"));
        String managerToken = cryptography.decrypt(
                connection.tenantId(), PURPOSE_MANAGER_TOKEN,
                new EncryptedValue(connection.managerTokenEnc(), connection.tokenKeyVersion(), "AES/GCM/NoPadding"));
        return new ZidCredentials(authorizationToken, managerToken, connection.zidStoreId());
    }

    /** Decrypts a freshly-issued token set (in-memory only, used during link completion). */
    public ZidCredentials decryptTokenSet(UUID tenantId, TokenSet tokens) {
        String authorizationToken = cryptography.decrypt(
                tenantId, PURPOSE_AUTHORIZATION_TOKEN,
                new EncryptedValue(tokens.authorizationTokenEnc(), tokens.tokenKeyVersion(), "AES/GCM/NoPadding"));
        String managerToken = cryptography.decrypt(
                tenantId, PURPOSE_MANAGER_TOKEN,
                new EncryptedValue(tokens.managerTokenEnc(), tokens.tokenKeyVersion(), "AES/GCM/NoPadding"));
        return new ZidCredentials(authorizationToken, managerToken, null);
    }

    /**
     * Parse the verified token response shape and encrypt every member
     * with the tenant-bound purposes. The refresh expiry falls back to
     * the documented 1-year lifetime because the response body does not
     * carry a refresh expiry field.
     */
    private TokenSet storeTokenSet(UUID tenantId, JsonNode body) {
        String authorizationToken = textOrNull(body, "authorization");
        String managerToken = textOrNull(body, "access_token");
        String refreshToken = textOrNull(body, "refresh_token");
        if (authorizationToken == null || managerToken == null || refreshToken == null) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_AUTHORIZATION_CODE,
                    "Zid token response is missing required fields");
        }
        long expiresIn = body.path("expires_in").asLong(0);
        Instant authorizationExpiresAt = expiresIn > 0
                ? Instant.now().plusSeconds(expiresIn)
                : Instant.now().plus(Duration.ofDays(30));

        EncryptedValue authorizationEnc = cryptography.encrypt(tenantId, PURPOSE_AUTHORIZATION_TOKEN, authorizationToken);
        EncryptedValue managerEnc = cryptography.encrypt(tenantId, PURPOSE_MANAGER_TOKEN, managerToken);
        EncryptedValue refreshEnc = cryptography.encrypt(tenantId, PURPOSE_REFRESH_TOKEN, refreshToken);

        String keyVersion = authorizationEnc.keyVersion();
        log.info("Zid token set stored for tenant {} (expires at {})", tenantId, authorizationExpiresAt);
        return new TokenSet(authorizationEnc.ciphertext(), managerEnc.ciphertext(), refreshEnc.ciphertext(),
                keyVersion, authorizationExpiresAt, Instant.now().plus(DEFAULT_REFRESH_LIFETIME));
    }

    /**
     * The redirect URI registered for this deployment. Operators set
     * SANAD_ZID_REDIRECT_URL (wired into properties); it must exactly
     * match one of the allowed redirection URLs configured in the Zid
     * partner dashboard (verified requirement).
     */
    public String resolveCallbackUrl() {
        String configured = properties.getRedirectUrl();
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SANAD_ZID_REDIRECT_URL");
        }
        if (configured == null || configured.isBlank()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.NOT_CONFIGURED,
                    "SANAD_ZID_REDIRECT_URL is not configured");
        }
        return configured;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.NOT_CONFIGURED,
                    "Zid integration is not enabled");
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.NOT_CONFIGURED,
                    "Zid OAuth client credentials are not configured");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
