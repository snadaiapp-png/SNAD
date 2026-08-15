package com.sanad.platform.management.health;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application / Backend health contributor (v20260816.1).
 *
 * <p>Reports safe runtime indicators: uptime, JVM heap utilization, available
 * processors, thread count. Thresholds are configurable via Governance Configuration.
 */
@Component
public class ApplicationSystemHealthContributor implements SystemHealthContributor {

    private static final double HEAP_WARNING_PCT = 80.0;
    private static final double HEAP_CRITICAL_PCT = 95.0;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    @Override
    public String componentId() { return "application"; }

    @Override
    public String componentType() { return "PLATFORM"; }

    @Override
    public String displayName() { return "Application / Backend"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();

        try {
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            long used = heap.getUsed();
            long max = heap.getMax();
            double heapPct = max > 0 ? (used * 100.0 / max) : 0;

            long uptimeMs = runtimeBean.getUptime();
            int processors = Runtime.getRuntime().availableProcessors();
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

            details.put("uptimeMs", uptimeMs);
            details.put("heapUsedMB", used / (1024 * 1024));
            details.put("heapMaxMB", max / (1024 * 1024));
            details.put("heapUsagePct", Math.round(heapPct * 100.0) / 100.0);
            details.put("availableProcessors", processors);
            details.put("threadCount", threadCount);

            SystemHealthModel.SystemHealthStatus status;
            String message;
            var severity = SystemHealthModel.SystemHealthComponent.Severity.INFO;
            if (heapPct >= HEAP_CRITICAL_PCT) {
                status = SystemHealthModel.SystemHealthStatus.UNHEALTHY;
                message = "Heap usage critical: " + Math.round(heapPct) + "%";
                severity = SystemHealthModel.SystemHealthComponent.Severity.ERROR;
            } else if (heapPct >= HEAP_WARNING_PCT) {
                status = SystemHealthModel.SystemHealthStatus.DEGRADED;
                message = "Heap usage high: " + Math.round(heapPct) + "%";
                severity = SystemHealthModel.SystemHealthComponent.Severity.WARN;
            } else {
                status = SystemHealthModel.SystemHealthStatus.HEALTHY;
                message = "Application running";
            }
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(), status, message,
                    Instant.now(), latency, details, Instant.now(), null, severity);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Application health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "APP_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
