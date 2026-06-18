package com.sanad.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SANAD Platform application entry point.
 *
 * <p>Stage 0 foundation: Spring Boot technical wiring only.
 * No business logic, no controllers, no repositories, no entities.</p>
 */
@SpringBootApplication
public class SanadPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SanadPlatformApplication.class, args);
    }
}
