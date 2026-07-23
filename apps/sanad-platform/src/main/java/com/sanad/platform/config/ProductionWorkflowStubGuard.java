package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for production. Active by default in prod profile.
 *
 * Can be explicitly disabled via sanad.production-guard.enabled=false
 * for CI test environments that use prod profile.
 *
 * Rules:
 *   PROD + missing Workflow URL → FAIL
 *   PROD + missing AI URL → FAIL
 *   PROD + valid URLs → PASS
 *   CI with sanad.production-guard.enabled=false → SKIP
 */
@Component
@Profile("prod")
public class ProductionWorkflowStubGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionWorkflowStubGuard.class);

    private final String workflowEngineBaseUrl;
    private final String aiGatewayBaseUrl;
    private final boolean guardEnabled;

    public ProductionWorkflowStubGuard(
            @Value("${sanad.workflow-engine.base-url:}") String workflowEngineBaseUrl,
            @Value("${sanad.ai-gateway.base-url:}") String aiGatewayBaseUrl,
            @Value("${sanad.production-guard.enabled:true}") boolean guardEnabled) {
        this.workflowEngineBaseUrl = workflowEngineBaseUrl == null ? "" : workflowEngineBaseUrl.strip();
        this.aiGatewayBaseUrl = aiGatewayBaseUrl == null ? "" : aiGatewayBaseUrl.strip();
        this.guardEnabled = guardEnabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!guardEnabled) {
            log.warn("Production guard DISABLED via sanad.production-guard.enabled=false — " +
                    "this is only for CI test environments");
            return;
        }

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

        log.info("Production startup verified: Workflow Engine={}, AI Gateway={}",
                workflowEngineBaseUrl, aiGatewayBaseUrl);
    }
}
