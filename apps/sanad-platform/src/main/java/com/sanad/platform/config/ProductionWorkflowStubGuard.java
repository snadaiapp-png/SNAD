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

/**
 * Fail-closed startup guard for production. Active by default in prod profile.
 *
 * <p>Can be explicitly disabled via {@code sanad.production-guard.enabled=false}
 * for CI test environments that use prod profile.</p>
 *
 * <p>Rules (all must pass for production startup to succeed):</p>
 * <ul>
 *   <li>Real {@link ConfirmedRecommendationCommandPort} bean exists
 *       (i.e. {@link CompositeConfirmedRecommendationCommandAdapter}).</li>
 *   <li>{@link StubConfirmedRecommendationCommandAdapter} bean is ABSENT.</li>
 *   <li>{@link WorkflowIntegrationPort} bean is bound to
 *       {@link HttpWorkflowIntegrationAdapter} (not a stub).</li>
 *   <li>{@link AiGatewayPort} bean is bound to {@link HttpAiGatewayAdapter}
 *       (not a stub).</li>
 *   <li>Service-auth configuration exists
 *       ({@code sanad.service-auth.jwt-secret} non-empty).</li>
 *   <li>Workflow and AI gateway URLs use HTTPS.</li>
 *   <li>Workflow and AI gateway URLs are not localhost or test domains.</li>
 * </ul>
 */
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
        // Detect stub bean presence by name
        this.stubBeanNames = java.util.stream.Stream.of(
                        ctx.getBeanNamesForType(StubConfirmedRecommendationCommandAdapter.class))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!guardEnabled) {
            log.warn("Production guard DISABLED via sanad.production-guard.enabled=false — " +
                    "this is only for CI test environments");
            return;
        }

        // 1. Stub adapter must be ABSENT
        if (!stubBeanNames.isEmpty()) {
            log.error("FATAL: Production started with StubConfirmedRecommendationCommandAdapter bean active: {}",
                    stubBeanNames);
            throw new IllegalStateException(
                    "Production startup refused: stub ConfirmedRecommendationCommandAdapter is active.");
        }

        // 2. Real command adapter must be the bound port
        if (!(commandPort instanceof CompositeConfirmedRecommendationCommandAdapter)) {
            log.error("FATAL: Production started with non-real ConfirmedRecommendationCommandPort: {}",
                    commandPort.getClass().getName());
            throw new IllegalStateException(
                    "Production startup refused: real ConfirmedRecommendationCommandPort not bound.");
        }

        // 3. Workflow port must be the HTTP adapter (not a stub)
        if (!(workflowPort instanceof HttpWorkflowIntegrationAdapter)) {
            log.error("FATAL: Production started with non-HTTP WorkflowIntegrationPort: {}",
                    workflowPort.getClass().getName());
            throw new IllegalStateException(
                    "Production startup refused: HttpWorkflowIntegrationAdapter not bound.");
        }

        // 4. AI port must be the HTTP adapter (not a stub)
        if (!(aiPort instanceof HttpAiGatewayAdapter)) {
            log.error("FATAL: Production started with non-HTTP AiGatewayPort: {}",
                    aiPort.getClass().getName());
            throw new IllegalStateException(
                    "Production startup refused: HttpAiGatewayAdapter not bound.");
        }

        // 5. Service-auth configuration must exist
        if (serviceAuthJwtSecret.isBlank() || serviceAuthJwtSecret.length() < 32) {
            log.error("FATAL: Production started without sanad.service-auth.jwt-secret (or secret too short).");
            throw new IllegalStateException(
                    "Production startup refused: sanad.service-auth.jwt-secret is missing or <32 chars.");
        }

        // 6. URLs must be present
        if (workflowEngineBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.workflow-engine.base-url.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.workflow-engine.base-url is not configured.");
        }
        if (aiGatewayBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.ai-gateway.base-url.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.ai-gateway.base-url is not configured.");
        }

        // 7. URLs must use HTTPS
        requireHttps(workflowEngineBaseUrl, "sanad.workflow-engine.base-url");
        requireHttps(aiGatewayBaseUrl, "sanad.ai-gateway.base-url");

        // 8. URLs must NOT be localhost or test domains
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
                        "Production startup refused: " + propertyName
                        + " must use HTTPS, got: " + scheme);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Production startup refused: " + propertyName
                    + " is not a valid URL: " + url, e);
        }
    }

    private void requireNotLocal(String url, String propertyName) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalStateException(
                        "Production startup refused: " + propertyName
                        + " has no host: " + url);
            }
            String lower = host.toLowerCase();
            if (lower.equals("localhost") || lower.equals("127.0.0.1")
                    || lower.endsWith(".localhost") || lower.endsWith(".local")
                    || lower.endsWith(".test") || lower.endsWith(".example")
                    || lower.endsWith(".invalid")) {
                throw new IllegalStateException(
                        "Production startup refused: " + propertyName
                        + " points to local/test host: " + host);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Production startup refused: " + propertyName
                    + " is not a valid URL: " + url, e);
        }
    }
}
