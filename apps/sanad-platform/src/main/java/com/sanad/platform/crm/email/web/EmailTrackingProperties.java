package com.sanad.platform.crm.email.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for email tracking (open pixel, click redirect).
 * Uses the same prefix as EmailProperties but is scoped to the web layer.
 */
@ConfigurationProperties(prefix = "snad.crm.email")
public class EmailTrackingProperties {
    private boolean trackingEnabled = true;
    private boolean clickTrackingEnabled = true;
    private String trackingBaseUrl = "";

    public boolean isTrackingEnabled() { return trackingEnabled; }
    public void setTrackingEnabled(boolean trackingEnabled) { this.trackingEnabled = trackingEnabled; }

    public boolean isClickTrackingEnabled() { return clickTrackingEnabled; }
    public void setClickTrackingEnabled(boolean clickTrackingEnabled) { this.clickTrackingEnabled = clickTrackingEnabled; }

    public String getTrackingBaseUrl() { return trackingBaseUrl; }
    public void setTrackingBaseUrl(String trackingBaseUrl) { this.trackingBaseUrl = trackingBaseUrl; }
}
