package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs the commit identity supplied by the deployment platform. */
@Component
public class StartupProvenanceLogger {

    private static final Logger log = LoggerFactory.getLogger("SANAD-STARTUP");

    @EventListener(ApplicationReadyEvent.class)
    public void logProvenance() {
        String renderCommit = System.getenv("RENDER_GIT_COMMIT");
        String fallbackCommit = System.getenv("GIT_COMMIT");
        String effectiveCommit = renderCommit != null && !renderCommit.isBlank()
                ? renderCommit
                : (fallbackCommit != null && !fallbackCommit.isBlank() ? fallbackCommit : "unknown");

        String implementationVersion = getClass().getPackage().getImplementationVersion();
        if (implementationVersion == null || implementationVersion.isBlank()) {
            implementationVersion = "unknown";
        }

        log.info(
                "SANAD Platform started: git_commit={} implementation_version={}",
                effectiveCommit,
                implementationVersion
        );
    }
}
