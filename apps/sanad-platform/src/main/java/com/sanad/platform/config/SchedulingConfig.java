package com.sanad.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Conditional scheduling configuration.
 *
 * <p>Scheduling is disabled by default to reduce startup time and thread
 * pool overhead on constrained environments (Render free tier: 512MB RAM).
 * Enable explicitly via {@code SCHEDULING_ENABLED=true} when scheduled
 * tasks are required.</p>
 */
@Configuration
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true")
@EnableScheduling
public class SchedulingConfig {
}
