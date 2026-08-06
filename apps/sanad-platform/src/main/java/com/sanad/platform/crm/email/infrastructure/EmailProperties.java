package com.sanad.platform.crm.email.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CRM email configuration properties.
 * <p>
 * Bound to {@code snad.crm.email.*} in application.yml.
 * Supports multiple providers: resend, smtp, http-proxy, local.
 */
@ConfigurationProperties(prefix = "snad.crm.email")
public class EmailProperties {

    /** Email provider selection: resend, smtp, http-proxy, local */
    private String provider = "local";

    /** Default sender address */
    private String fromAddress = "";

    /** Application base URL for tracking pixels and click redirects */
    private String applicationBaseUrl = "http://localhost:3000";

    /** Resend API key (when provider=resend) */
    private String resendApiKey = "";

    /** HTTP proxy endpoint URL (when provider=http-proxy) */
    private String proxyEndpoint = "";

    /** HTTP proxy bearer token (when provider=http-proxy) */
    private String proxyBearerToken = "";

    /** SMTP host (when provider=smtp) */
    private String smtpHost = "";

    /** SMTP port (when provider=smtp) */
    private int smtpPort = 587;

    /** SMTP username (when provider=smtp) */
    private String smtpUsername = "";

    /** SMTP password (when provider=smtp) */
    private String smtpPassword = "";

    /** SMTP TLS enabled (when provider=smtp) */
    private boolean smtpTls = true;

    /** Tracking base URL for open pixels and click redirects */
    private String trackingBaseUrl = "";

    /** Whether open tracking is enabled */
    private boolean trackingEnabled = true;

    /** Whether click tracking is enabled */
    private boolean clickTrackingEnabled = true;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getApplicationBaseUrl() { return applicationBaseUrl; }
    public void setApplicationBaseUrl(String applicationBaseUrl) { this.applicationBaseUrl = applicationBaseUrl; }
    public String getResendApiKey() { return resendApiKey; }
    public void setResendApiKey(String resendApiKey) { this.resendApiKey = resendApiKey; }
    public String getProxyEndpoint() { return proxyEndpoint; }
    public void setProxyEndpoint(String proxyEndpoint) { this.proxyEndpoint = proxyEndpoint; }
    public String getProxyBearerToken() { return proxyBearerToken; }
    public void setProxyBearerToken(String proxyBearerToken) { this.proxyBearerToken = proxyBearerToken; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }
    public boolean isSmtpTls() { return smtpTls; }
    public void setSmtpTls(boolean smtpTls) { this.smtpTls = smtpTls; }
    public String getTrackingBaseUrl() { return trackingBaseUrl; }
    public void setTrackingBaseUrl(String trackingBaseUrl) { this.trackingBaseUrl = trackingBaseUrl; }
    public boolean isTrackingEnabled() { return trackingEnabled; }
    public void setTrackingEnabled(boolean trackingEnabled) { this.trackingEnabled = trackingEnabled; }
    public boolean isClickTrackingEnabled() { return clickTrackingEnabled; }
    public void setClickTrackingEnabled(boolean clickTrackingEnabled) { this.clickTrackingEnabled = clickTrackingEnabled; }
}
