package com.sanad.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
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
 *   <li>{@link UserDetailsServiceAutoConfiguration} is explicitly excluded
 *       because the platform uses a stateless JWT filter chain (see
 *       {@code SecurityConfig}). The auto-config would otherwise create an
 *       in-memory {@code UserDetailsService} with a generated password printed
 *       to the startup log. Excluding it removes that warning AND guarantees
 *       no production authentication path can fall back to the generated
 *       default user. {@code httpBasic()} and {@code formLogin()} are also
 *       not invoked on the platform's {@code SecurityFilterChain} — so even
 *       if a fallback {@code AuthenticationManager} were wired, no endpoint
 *       would accept BASIC or form credentials.</li>
 * </ul>
 * </p>
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@ConfigurationPropertiesScan
public class SanadPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SanadPlatformApplication.class, args);
    }
}

