package com.sanad.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SANAD Platform application entry point.
 *
 * <p>Stage 0 foundation: Spring Boot technical wiring only.
 * No business logic, no controllers, no repositories, no entities.</p>
 *
 * <p>{@link ConfigurationPropertiesScan} auto-discovers all
 * {@code @ConfigurationProperties} classes in this package hierarchy,
 * including {@code CorsProperties} and {@code SecurityProperties}.</p>
 *
 * <p>Production optimizations:
 * <ul>
 *   <li>{@code @EnableScheduling} moved to {@link SchedulingConfig} which is
 *       disabled by default to avoid scheduler thread pool overhead during
 *       cold starts. Enable via {@code SCHEDULING_ENABLED=true}.</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SanadPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SanadPlatformApplication.class, args);
    }
}
