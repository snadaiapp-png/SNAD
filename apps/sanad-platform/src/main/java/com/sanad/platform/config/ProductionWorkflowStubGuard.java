package com.sanad.platform.config;

import com.sanad.platform.crm.integration.application.CompositeConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.HttpAiGatewayAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpWorkflowIntegrationAdapter;
import com.sanad.platform.crm.integration.orchestration.WorkflowIntegrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Fail-closed production startup guard for CRM central integrations. */
@Component
@Profile("prod")
public class ProductionWorkflowStubGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionWorkflowStubGuard.class);

    private final String workflowEngineBaseUrl;
    private final String aiGatewayBaseUrl;
    private final String serviceAuthJwtSecret;
    private final boolean guardEnabled;
    private final ConfirmedRecommendationCommandPort commandPort;
    private final WorkflowIntegrationPort workflowPort;
    private final AiGatewayPort aiPort;
    private final List<String> stubBeanNames;

    public ProductionWorkflowStubGuard(
            @Value("${sanad.workflow-engine.base-url:}") String workflowEngineBaseUrl,
            @Value("${sanad.ai-gateway.base-url:}") String aiGatewayBaseUrl,
            @Value("${sanad.service-auth.jwt-secret:}") String serviceAuthJwtSecret,
            @Value("${sanad.production-guard.enabled:true}") boolean guardEnabled,
            ConfirmedRecommendationCommandPort commandPort,
            WorkflowIntegrationPort workflowPort,
            AiGatewayPort aiPort,
            org.springframework.context.ApplicationContext ctx) {
        this.workflowEngineBaseUrl = workflowEngineBaseUrl == null ? "" : workflowEngineBaseUrl.strip();
        this.aiGatewayBaseUrl = aiGatewayBaseUrl == null ? "" : aiGatewayBaseUrl.strip();
        this.serviceAuthJwtSecret = serviceAuthJwtSecret == null ? "" : serviceAuthJwtSecret.strip();
        this.guardEnabled = guardEnabled;
        this.commandPort = commandPort;
        this.workflowPort = workflowPort;
        this.aiPort = aiPort;
        this.stubBeanNames = java.util.stream.Stream.of(
                        ctx.getBeanNamesForType(StubConfirmedRecommendationCommandAdapter.class))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!guardEnabled) {
            log.warn("Production guard DISABLED via sanad.production-guard.enabled=false — this is only for CI test environments");
            return;
        }
        if (!stubBeanNames.isEmpty()) {
            throw new IllegalStateException(
                    "Production startup refused: stub ConfirmedRecommendationCommandAdapter is active.");
        }
        if (!(commandPort instanceof CompositeConfirmedRecommendationCommandAdapter)) {
            throw new IllegalStateException(
                    "Production startup refused: real ConfirmedRecommendationCommandPort not bound.");
        }
        if (!(workflowPort instanceof HttpWorkflowIntegrationAdapter)) {
            throw new IllegalStateException(
                    "Production startup refused: HttpWorkflowIntegrationAdapter not bound.");
        }
        if (!(aiPort instanceof HttpAiGatewayAdapter)) {
            throw new IllegalStateException(
                    "Production startup refused: HttpAiGatewayAdapter not bound.");
        }
        if (serviceAuthJwtSecret.isBlank() || serviceAuthJwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Production startup refused: sanad.service-auth.jwt-secret is missing or <32 chars.");
        }
        if (workflowEngineBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Production startup refused: sanad.workflow-engine.base-url is not configured.");
        }
        if (aiGatewayBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Production startup refused: sanad.ai-gateway.base-url is not configured.");
        }
        requireHttps(workflowEngineBaseUrl, "sanad.workflow-engine.base-url");
        requireHttps(aiGatewayBaseUrl, "sanad.ai-gateway.base-url");
        requireNotLocal(workflowEngineBaseUrl, "sanad.workflow-engine.base-url");
        requireNotLocal(aiGatewayBaseUrl, "sanad.ai-gateway.base-url");
        log.info("Production startup verified: Workflow Engine={}, AI Gateway={}, Service Auth=ON",
                workflowEngineBaseUrl, aiGatewayBaseUrl);
    }

    private void requireHttps(String url, String propertyName) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)) {
                throw new IllegalStateException(
                        "Production startup refused: " + propertyName + " must use HTTPS, got: " + scheme);
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Production startup refused: " + propertyName + " is not a valid URL: " + url,
                    error);
        }
    }

    private void requireNotLocal(String url, String propertyName) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalStateException(
                        "Production startup refused: " + propertyName + " has no host: " + url);
            }
            String lower = host.toLowerCase();
            if (lower.equals("localhost") || lower.equals("127.0.0.1")
                    || lower.endsWith(".localhost") || lower.endsWith(".local")
                    || lower.endsWith(".test") || lower.endsWith(".example")
                    || lower.endsWith(".invalid")) {
                throw new IllegalStateException(
                        "Production startup refused: " + propertyName + " points to local/test host: " + host);
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Production startup refused: " + propertyName + " is not a valid URL: " + url,
                    error);
        }
    }
}
