package com.sanad.platform.ops.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for operational alerting.
 * <p>
 * Binds to the {@code snad.ops.alerting} prefix in application.yml.
 * The {@code enabled} flag controls whether alerts are dispatched.
 * The {@code webhook-url} is the target endpoint for webhook-based providers
 * (Slack, PagerDuty, Teams, Opsgenie, or generic webhook).
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
@ConfigurationProperties(prefix = "snad.ops.alerting")
public class OperationalAlertProperties {

    /** Master switch for alerting. */
    private boolean enabled = false;

    /** The alerting provider: slack, pagerduty, teams, opsgenie, webhook, or disabled. */
    private String provider = "disabled";

    /** The webhook URL to POST alerts to. Required when provider is webhook/slack/teams/opsgenie. */
    private String webhookUrl = "";

    /** Bearer token for webhook authentication (optional). */
    private String bearerToken = "";

    /** Minimum severity to dispatch (TRACE, DEBUG, INFO, WARN, ERROR, CRITICAL). */
    private String minimumSeverity = "WARN";

    /** Application environment name. */
    private String environment = "production";

    /** Alert deduplication window in seconds. */
    private int deduplicationWindowSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }
    public String getMinimumSeverity() { return minimumSeverity; }
    public void setMinimumSeverity(String minimumSeverity) { this.minimumSeverity = minimumSeverity; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public int getDeduplicationWindowSeconds() { return deduplicationWindowSeconds; }
    public void setDeduplicationWindowSeconds(int deduplicationWindowSeconds) { this.deduplicationWindowSeconds = deduplicationWindowSeconds; }
}
