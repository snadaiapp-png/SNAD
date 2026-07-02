package com.sanad.platform.crm.platform;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sanad.crm.platform")
public class CrmPlatformProperties {

    private final Messaging messaging = new Messaging();
    private final Search search = new Search();
    private final Storage storage = new Storage();
    private final Antivirus antivirus = new Antivirus();
    private final Workflow workflow = new Workflow();
    private final Notifications notifications = new Notifications();
    private final AiGateway aiGateway = new AiGateway();
    private final Webhooks webhooks = new Webhooks();

    public Messaging getMessaging() { return messaging; }
    public Search getSearch() { return search; }
    public Storage getStorage() { return storage; }
    public Antivirus getAntivirus() { return antivirus; }
    public Workflow getWorkflow() { return workflow; }
    public Notifications getNotifications() { return notifications; }
    public AiGateway getAiGateway() { return aiGateway; }
    public Webhooks getWebhooks() { return webhooks; }

    public static class Messaging {
        private boolean enabled;
        private int batchSize = 100;
        private int maxAttempts = 12;
        private Duration pollInterval = Duration.ofSeconds(2);
        private String exchange = "snad.crm.events";
        private String deadLetterExchange = "snad.crm.dlx";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getDeadLetterExchange() { return deadLetterExchange; }
        public void setDeadLetterExchange(String deadLetterExchange) { this.deadLetterExchange = deadLetterExchange; }
    }

    public static class Search {
        private boolean enabled;
        private URI endpoint = URI.create("http://opensearch:9200");
        private String indexPrefix = "snad-crm";
        private Duration timeout = Duration.ofSeconds(10);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
        public String getIndexPrefix() { return indexPrefix; }
        public void setIndexPrefix(String indexPrefix) { this.indexPrefix = indexPrefix; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    public static class Storage {
        private boolean enabled;
        private URI endpoint = URI.create("http://minio:9000");
        private String region = "us-east-1";
        private String bucket = "snad-crm";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyleAccess = true;
        private Duration presignDuration = Duration.ofMinutes(10);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
        public Duration getPresignDuration() { return presignDuration; }
        public void setPresignDuration(Duration presignDuration) { this.presignDuration = presignDuration; }
    }

    public static class Antivirus {
        private boolean enabled;
        private String host = "clamav";
        private int port = 3310;
        private Duration timeout = Duration.ofSeconds(30);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    public static class Workflow {
        private boolean enabled;
        private int timerBatchSize = 100;
        private Duration pollInterval = Duration.ofSeconds(5);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTimerBatchSize() { return timerBatchSize; }
        public void setTimerBatchSize(int timerBatchSize) { this.timerBatchSize = timerBatchSize; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    }

    public static class Notifications {
        private boolean enabled;
        private String from = "no-reply@snad.local";
        private int batchSize = 100;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class AiGateway {
        private boolean enabled;
        private URI endpoint = URI.create("http://ai-gateway:8090");
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(20);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    public static class Webhooks {
        private boolean enabled;
        private Duration timeout = Duration.ofSeconds(10);
        private int maxAttempts = 8;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }
}
