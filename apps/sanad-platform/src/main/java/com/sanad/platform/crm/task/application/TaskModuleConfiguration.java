package com.sanad.platform.crm.task.application;

import com.sanad.platform.crm.task.domain.TaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Task bounded context.
 * <p>
 * Provides the {@link TaskUseCases} bean. The {@link TaskRepository}
 * bean is auto-discovered via {@code @Repository} on the JDBC
 * implementation.
 * <p>
 * Branch: feature/crm-tasks
 */
@Configuration
public class TaskModuleConfiguration {

    @Bean
    public TaskUseCases taskUseCases(TaskRepository repo) {
        return new TaskUseCases(repo);
    }
}
