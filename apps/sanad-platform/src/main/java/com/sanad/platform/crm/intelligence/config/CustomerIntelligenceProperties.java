package com.sanad.platform.crm.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for CRM-010 Customer Intelligence.
 *
 * <p>Controls external data source providers (mock vs http vs disabled)
 * and scoring engine behavior.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "sanad.intelligence")
public class CustomerIntelligenceProperties {

    private ExternalProvider erp = new ExternalProvider("mock");
    private ExternalProvider hrm = new ExternalProvider("mock");
    private ExternalProvider pos = new ExternalProvider("mock");
    private ExternalProvider accounting = new ExternalProvider("mock");
    private ExternalProvider commerce = new ExternalProvider("mock");
    private Scoring scoring = new Scoring();

    public ExternalProvider getErp() { return erp; }
    public void setErp(ExternalProvider erp) { this.erp = erp; }

    public ExternalProvider getHrm() { return hrm; }
    public void setHrm(ExternalProvider hrm) { this.hrm = hrm; }

    public ExternalProvider getPos() { return pos; }
    public void setPos(ExternalProvider pos) { this.pos = pos; }

    public ExternalProvider getAccounting() { return accounting; }
    public void setAccounting(ExternalProvider accounting) { this.accounting = accounting; }

    public ExternalProvider getCommerce() { return commerce; }
    public void setCommerce(ExternalProvider commerce) { this.commerce = commerce; }

    public Scoring getScoring() { return scoring; }
    public void setScoring(Scoring scoring) { this.scoring = scoring; }

    public static class ExternalProvider {
        private String provider = "mock";
        private String baseUrl = "";
        private long timeoutMs = 5000;

        public ExternalProvider() {}

        public ExternalProvider(String provider) {
            this.provider = provider;
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

        public boolean isMock() { return "mock".equalsIgnoreCase(provider); }
        public boolean isDisabled() { return "disabled".equalsIgnoreCase(provider); }
        public boolean isHttp() { return "http".equalsIgnoreCase(provider); }
    }

    public static class Scoring {
        private int batchSize = 100;
        private int rescoreIntervalMinutes = 360;
        private double minConfidence = 0.6;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getRescoreIntervalMinutes() { return rescoreIntervalMinutes; }
        public void setRescoreIntervalMinutes(int rescoreIntervalMinutes) {
            this.rescoreIntervalMinutes = rescoreIntervalMinutes;
        }

        public double getMinConfidence() { return minConfidence; }
        public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }
    }
}
