package com.sanad.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

import com.sanad.platform.config.StartupTimelineLogger;

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
 *   <li>{@link BufferingApplicationStartup} is attached to capture per-step
 *       startup timing. The buffer is drained by
 *       {@link StartupTimelineLogger} on {@code ApplicationReadyEvent} and
 *       the top-N slowest steps are logged for cold-start profiling. The
 *       buffer capacity is sized to hold the full startup step tree for a
 *       SANAD cold start (~3-5k steps; drops oldest silently if exceeded).</li>
 * </ul>
 * </p>
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@ConfigurationPropertiesScan
public class SanadPlatformApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SanadPlatformApplication.class);
        // Buffer startup steps for cold-start profiling. Capacity is conservative
        // (2k) to bound memory overhead on Render Free. The buffer
        // is read by StartupTimelineLogger#onReady via getBufferedTimeline()
        // (non-draining). Overhead per step is ~1-2 microseconds, total
        // overhead <500ms even on Render Free.
        app.setApplicationStartup(new BufferingApplicationStartup(2_000));
        // Register the timeline logger programmatically so it can observe
        // the early ApplicationStartingEvent / ApplicationEnvironmentPreparedEvent
        // that fire BEFORE the ApplicationContext exists (and therefore before
        // any @Component @EventListener could be registered).
        app.addListeners(new StartupTimelineLogger());
        app.run(args);
    }
}
