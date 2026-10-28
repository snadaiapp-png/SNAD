package com.sanad.platform.integration.zid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the Zid integration.
 *
 * <p>Every secret-bearing value is sourced from an environment variable
 * with NO default (fail-closed): the integration refuses to operate
 * unless an operator has provisioned the required material through the
 * approved secret store.</p>
 *
 * <p><b>Verification status of endpoint values (docs.zid.sa, accessed
 * 2026-10-28):</b></p>
 * <ul>
 *   <li>VERIFIED: OAuth base URL {@code https://oauth.zid.sa}, API base
 *       URL {@code https://api.zid.sa/v1}, the authorization-code flow
 *       (code is 10-minute single-use), the token response shape
 *       (token_type/expires_in/authorization/access_token/refresh_token),
 *       header families (Authorization + X-Manager-Token;
 *       Access-Token + Store-Id + Role: Manager), the orders endpoint
 *       {@code GET /managers/store/orders}, the products endpoint
 *       {@code GET /products/}, and embedded per-product
 *       {@code stocks[]} data.</li>
 *   <li>UNVERIFIED: the exact path suffixes of the authorize/token
 *       endpoints beneath the OAuth base URL, the webhook signature
 *       header name/format, and the customers list path. These carry
 *       documented defaults but MUST be confirmed against the partner
 *       dashboard before production enablement — see
 *       docs/zid-integration.md.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "snad.zid")
public class ZidIntegrationProperties {

    /**
     * Master switch. When false (default) every Zid controller returns
     * 503 and schedulers do nothing. Enable only after operator setup.
     */
    private boolean enabled = false;

    /** OAuth server base URL. Verified: https://oauth.zid.sa */
    private String oauthBaseUrl = "https://oauth.zid.sa";

    /** API base URL. Verified: https://api.zid.sa/v1 */
    private String apiBaseUrl = "https://api.zid.sa/v1";

    /**
     * UNVERIFIED: authorize path beneath the OAuth base URL. Confirm
     * from the partner dashboard before production use.
     */
    private String authorizePath = "/oauth2/authorize";

    /**
     * UNVERIFIED: token endpoint path beneath the OAuth base URL.
     * Confirm from the partner dashboard before production use.
     */
    private String tokenPath = "/oauth2/token";

    /** Zid OAuth client id (non-secret). Env: SANAD_ZID_CLIENT_ID. */
    private String clientId = "";

    /** Zid OAuth client secret. Env: SANAD_ZID_CLIENT_SECRET. Never logged. */
    private String clientSecret = "";

    /**
     * HMAC shared secret used to verify inbound webhook signatures.
     * Env: SANAD_ZID_WEBHOOK_SECRET. When blank the webhook endpoint
     * fails closed with 503 (not configured).
     */
    private String webhookSecret = "";

    /**
     * UNVERIFIED: header carrying the webhook signature. Confirm the
     * exact name from Zid's webhook registration UI before production.
     */
    private String webhookSignatureHeader = "X-Zid-Signature";

    /**
     * UNVERIFIED: customers list path beneath the API base URL. Left
     * null so the customer pull stays disabled until an operator
     * confirms the exact path and granted scope from the partner
     * dashboard.
     */
    private String customersPath;

    /**
     * Frontend route that receives the outcome of the OAuth callback.
     * Env: SANAD_ZID_LINK_REDIRECT_URL.
     */
    private String linkRedirectUrl = "/integrations/zid";

    /**
     * Absolute callback URL registered in the Zid partner dashboard.
     * Env: SANAD_ZID_REDIRECT_URL. Must exactly match an allowed
     * redirection URL (verified requirement).
     */
    private String redirectUrl;

    /** Outbound HTTP connect timeout for Zid API calls. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Outbound HTTP request timeout for Zid API calls. */
    private Duration requestTimeout = Duration.ofSeconds(25);

    /** Page size used when pulling products/orders. */
    private int pageSize = 50;

    /** Hard cap on pages pulled per sync run (protects rate budget). */
    private int maxPagesPerSync = 40;

    /** Link states older than this are rejected and eligible for cleanup. */
    private Duration linkStateTtl = Duration.ofMinutes(15);

    /** Webhook inbox retry policy: max attempts before FAILED_MANUAL. */
    private int webhookMaxAttempts = 5;

    /** Base delay for the webhook retry backoff (exponential). */
    private Duration webhookRetryBaseDelay = Duration.ofMinutes(1);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOauthBaseUrl() { return oauthBaseUrl; }
    public void setOauthBaseUrl(String oauthBaseUrl) { this.oauthBaseUrl = oauthBaseUrl; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getAuthorizePath() { return authorizePath; }
    public void setAuthorizePath(String authorizePath) { this.authorizePath = authorizePath; }

    public String getTokenPath() { return tokenPath; }
    public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getWebhookSignatureHeader() { return webhookSignatureHeader; }
    public void setWebhookSignatureHeader(String webhookSignatureHeader) {
        this.webhookSignatureHeader = webhookSignatureHeader;
    }

    public String getCustomersPath() { return customersPath; }
    public void setCustomersPath(String customersPath) { this.customersPath = customersPath; }

    public String getLinkRedirectUrl() { return linkRedirectUrl; }
    public void setLinkRedirectUrl(String linkRedirectUrl) { this.linkRedirectUrl = linkRedirectUrl; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getMaxPagesPerSync() { return maxPagesPerSync; }
    public void setMaxPagesPerSync(int maxPagesPerSync) { this.maxPagesPerSync = maxPagesPerSync; }

    public Duration getLinkStateTtl() { return linkStateTtl; }
    public void setLinkStateTtl(Duration linkStateTtl) { this.linkStateTtl = linkStateTtl; }

    public int getWebhookMaxAttempts() { return webhookMaxAttempts; }
    public void setWebhookMaxAttempts(int webhookMaxAttempts) { this.webhookMaxAttempts = webhookMaxAttempts; }

    public Duration getWebhookRetryBaseDelay() { return webhookRetryBaseDelay; }
    public void setWebhookRetryBaseDelay(Duration webhookRetryBaseDelay) {
        this.webhookRetryBaseDelay = webhookRetryBaseDelay;
    }
}
