package com.sanad.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SANAD Platform application entry point.
 *
 * <p>Stage 0 foundation: Spring Boot technical wiring only.
 * No business logic, no controllers, no repositories, no entities.</p>
 *
 * <p>{@link ConfigurationPropertiesScan} auto-discovers all
 * {@code @ConfigurationProperties} classes in this package hierarchy,
 * including {@code CorsProperties} and {@code SecurityProperties}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SanadPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SanadPlatformApplication.class, args);
    }
}
