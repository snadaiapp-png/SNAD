package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * Stage 05A.2.9.1 §2 — Logs the exact deployed provenance at startup
 * so operators can verify which Git commit and JAR artifact the
 * running instance was built from.
 *
 * <p>Logged values (no secrets):</p>
 * <ul>
 *   <li>{@code RENDER_GIT_COMMIT} — the Git commit SHA Render
 *       injected at deploy time (or "unknown" if not set).</li>
 *   <li>{@code GIT_COMMIT} — fallback env var for non-Render envs.</li>
 *   <li>Application implementation version (from the JAR manifest
 *       {@code Implementation-Version} attribute).</li>
 *   <li>JAR file path — proves the app loaded from the repackaged
 *       Spring Boot JAR, not a stale classpath.</li>
 * </ul>
 *
 * <p>This component exists to make the "which commit is running?"
 * question answerable WITHOUT guessing from artifact filenames or
 * Render dashboard state. The log line is emitted exactly once at
 * {@link ApplicationReadyEvent}.</p>
 */
@Component
public class StartupProvenanceLogger {

    private static final Logger log = LoggerFactory.getLogger("SANAD-STARTUP");

    @EventListener(ApplicationReadyEvent.class)
    public void logProvenance() {
        String renderCommit = System.getenv("RENDER_GIT_COMMIT");
        String gitCommit = System.getenv("GIT_COMMIT");
        String effectiveCommit = renderCommit != null && !renderCommit.isBlank()
                ? renderCommit
                : (gitCommit != null && !gitCommit.isBlank() ? gitCommit : "unknown");

        String implVersion = getClass().getPackage().getImplementationVersion();
        if (implVersion == null || implVersion.isBlank()) {
            implVersion = "unknown";
        }

        String jarPath = "unknown";
        try {
            ProtectionDomain pd = getClass().getProtectionDomain();
            if (pd != null) {
                CodeSource cs = pd.getCodeSource();
                if (cs != null) {
                    URL location = cs.getLocation();
                    if (location != null) {
                        jarPath = location.getPath();
                    }
                }
            }
        } catch (Exception e) {
            // Non-critical — provenance logging must not block startup.
            log.debug("Could not resolve JAR path: {}", e.getMessage());
        }

        log.info(
                "SANAD Platform started — git_commit={} impl_version={} jar={}",
                effectiveCommit, implVersion, jarPath);
    }
}
