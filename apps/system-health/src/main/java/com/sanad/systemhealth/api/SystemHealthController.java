package com.sanad.systemhealth.api;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system-health")
public class SystemHealthController {

    private final String applicationName;
    private final String applicationVersion;

    public SystemHealthController(
            @Value("${spring.application.name}") String applicationName,
            @Value("${info.app.version:unknown}") String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping
    public SystemHealthSnapshot snapshot() {
        Runtime runtime = Runtime.getRuntime();

        ComponentHealth runtimeHealth = new ComponentHealth(
                "jvm-runtime",
                "HEALTHY",
                "The independent System Health runtime is responding.");

        RuntimeFacts facts = new RuntimeFacts(
                ManagementFactory.getRuntimeMXBean().getUptime(),
                runtime.availableProcessors(),
                runtime.totalMemory(),
                runtime.freeMemory(),
                runtime.maxMemory());

        return new SystemHealthSnapshot(
                applicationName,
                applicationVersion,
                "FOUNDATION",
                "HEALTHY",
                Instant.now(),
                List.of(runtimeHealth),
                facts);
    }

    @GetMapping("/live")
    public ResponseEntity<Void> live() {
        return ResponseEntity.noContent().build();
    }

    public record SystemHealthSnapshot(
            String application,
            String version,
            String maturity,
            String status,
            Instant observedAt,
            List<ComponentHealth> components,
            RuntimeFacts runtime) {
    }

    public record ComponentHealth(String id, String status, String message) {
    }

    public record RuntimeFacts(
            long uptimeMilliseconds,
            int availableProcessors,
            long totalMemoryBytes,
            long freeMemoryBytes,
            long maxMemoryBytes) {
    }
}
