package com.sanad.platform.config;

import com.sanad.platform.crm.ownership.domain.WorkflowPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard: if the active WorkflowPort is a stub, the application
 * MUST NOT start in production. This prevents accidental production deployment
 * with InlineTransferWorkflowStubAdapter.
 */
@Component
@Profile("prod")
public class ProductionWorkflowStubGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionWorkflowStubGuard.class);

    private final WorkflowPort workflowPort;

    public ProductionWorkflowStubGuard(WorkflowPort workflowPort) {
        this.workflowPort = workflowPort;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (workflowPort.isStub()) {
            log.error("FATAL: Production started with a WorkflowPort STUB adapter. " +
                    "InlineTransferWorkflowStubAdapter must not be active in production. " +
                    "Configure sanad.workflow-engine.base-url and ensure the HTTP adapter is loaded.");
            throw new IllegalStateException(
                    "Production startup refused: WorkflowPort stub is active. " +
                    "Set sanad.workflow-engine.base-url to the central Workflow Engine URL.");
        }
        log.info("Production WorkflowPort verified: non-stub adapter active");
    }
}
