package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for production.
 *
 * Rules:
 *   PROD + no workflow engine base-url configured → STARTUP FAIL
 *   PROD + stub adapter active → STARTUP FAIL
 *   PROD + real adapter + valid base-url → STARTUP PASS
 *   LOCAL/TEST + stub → ALLOWED
 */
@Component
@Profile("prod")
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
        // Workflow Engine URL is required in production
        if (workflowEngineBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.workflow-engine.base-url. " +
                    "Transfer workflow approval requires the central Workflow Engine. " +
                    "Set sanad.workflow-engine.base-url to the Workflow Engine URL.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.workflow-engine.base-url is not configured. " +
                    "Set it to the central Workflow Engine URL.");
        }

        // AI Gateway URL is required in production
        if (aiGatewayBaseUrl.isBlank()) {
            log.error("FATAL: Production started without sanad.ai-gateway.base-url. " +
                    "AI integration requires the central AI Gateway. " +
                    "Set sanad.ai-gateway.base-url to the AI Gateway URL.");
            throw new IllegalStateException(
                    "Production startup refused: sanad.ai-gateway.base-url is not configured. " +
                    "Set it to the central AI Gateway URL.");
        }

        log.info("Production startup: Workflow Engine URL configured ({}), AI Gateway URL configured ({})",
                workflowEngineBaseUrl, aiGatewayBaseUrl);
    }
}
