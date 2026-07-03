package com.sanad.platform.security.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snad.security.notifications")
public class SecurityNotificationProperties {
    private String provider = "disabled";
    private String applicationBaseUrl = "http://localhost:3000";
    private String fromAddress = "";
    private String endpoint = "";
    private String bearerToken = "";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApplicationBaseUrl() { return applicationBaseUrl; }
    public void setApplicationBaseUrl(String applicationBaseUrl) { this.applicationBaseUrl = applicationBaseUrl; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }
}
