package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for production.
 *
 * Only activates when sanad.production-guard.enabled=true (set in real production
 * deployment, not in CI test environments that use prod profile for integration tests).
 *
 * Rules:
 *   PROD + guard enabled + no workflow-engine base-url → STARTUP FAIL
 *   PROD + guard enabled + no ai-gateway base-url → STARTUP FAIL
 *   PROD + guard disabled (or not set) → SKIP (CI/test environments)
 *   LOCAL/TEST → never activated (Profile=prod only)
 */
@Component
@Profile("prod")
@ConditionalOnProperty(name = "sanad.production-guard.enabled", havingValue = "true")
public class ProductionWorkflowStubGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionWorkflowStubGuard.class);

    private final String workflowEngineBaseUrl;
    private final String aiGatewayBaseUrl;

    public ProductionWorkflowStubGuard(
            @Value("${sanad.workflow-engine.base-url:}") String workflowEngineBaseUrl,
            @Value("${sanad.ai-gateway.base-url:}") String aiGatewayBaseUrl) {
        this.workflowEngineBaseUrl = workflowEngineBaseUrl == null ? "" : workflowEngineBaseUrl.strip();
        this.aiGatewayBaseUrl = aiGatewayBaseUrl == null ? "" : aiGatewayBaseUrl.strip();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (workflowEngineBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.workflow-engine.base-url. " +
                    "Transfer workflow approval requires the central Workflow Engine.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.workflow-engine.base-url is not configured.");
        }

        if (aiGatewayBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.ai-gateway.base-url. " +
                    "AI integration requires the central AI Gateway.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.ai-gateway.base-url is not configured.");
        }

        log.info("Production startup: Workflow Engine URL={}, AI Gateway URL={}",
                workflowEngineBaseUrl, aiGatewayBaseUrl);
    }
}
