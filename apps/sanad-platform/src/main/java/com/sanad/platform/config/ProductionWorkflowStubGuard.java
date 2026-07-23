package com.sanad.platform.config;

import com.sanad.platform.crm.ownership.domain.WorkflowPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fail-fast startup guard: if the active WorkflowPort is a stub, the application
 * MUST NOT start in production. If no WorkflowPort bean is available (because the
 * stub is excluded from prod), that's acceptable — the guard skips.
 */
@Component
@Profile("prod")
public class ProductionWorkflowStubGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionWorkflowStubGuard.class);

    private final Optional<WorkflowPort> workflowPort;

    public ProductionWorkflowStubGuard(Optional<WorkflowPort> workflowPort) {
        this.workflowPort = workflowPort;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (workflowPort.isEmpty()) {
            log.info("Production startup: no WorkflowPort bean loaded (stub excluded from prod). " +
                    "Transfer workflow approval will use inline single-approver path. " +
                    "Configure sanad.workflow-engine.base-url for full workflow integration.");
            return;
        }
        if (workflowPort.get().isStub()) {
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
