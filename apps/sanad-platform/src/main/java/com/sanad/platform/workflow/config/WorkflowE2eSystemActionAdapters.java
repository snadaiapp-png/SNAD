package com.sanad.platform.workflow.config;

import com.sanad.platform.workflow.application.WorkflowSystemActionAdapter;
import com.sanad.platform.workflow.application.WorkflowSystemActionAdapterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Workflow E2E system-action adapters — profile-gated deterministic
 * fixtures for the browser release gate. Activates only under the
 * {@code workflow-e2e} Spring profile; never in production.
 */
@Configuration
@Profile("workflow-e2e")
public class WorkflowE2eSystemActionAdapters {

    @Bean
    WorkflowSystemActionAdapter e2eAlwaysFailAdapter() {
        return new WorkflowSystemActionAdapterRegistry.E2EAlwaysFailAdapter();
    }
}
