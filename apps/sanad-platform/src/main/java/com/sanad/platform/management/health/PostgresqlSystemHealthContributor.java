package com.sanad.platform.management.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL health contributor (v20260816.1).
 *
 * <p>Performs a lightweight {@code SELECT 1} through the governing datasource
 * and measures latency. Does NOT expose credentials.
 */
@Component
public class PostgresqlSystemHealthContributor implements SystemHealthContributor {

    private static final long LATENCY_WARNING_MS = 500;
    private static final long LATENCY_CRITICAL_MS = 2000;

    private final JdbcTemplate jdbc;

    public PostgresqlSystemHealthContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String componentId() { return "postgresql"; }

    @Override
    public String componentType() { return "PLATFORM"; }

    @Override
    public String displayName() { return "PostgreSQL Database"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("engine", "PostgreSQL");
        details.put("operation", "SELECT 1");
        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            long latency = System.currentTimeMillis() - start;
            details.put("latencyMs", latency);
            details.put("connectivity", true);

            SystemHealthModel.SystemHealthStatus status;
            String message;
            var severity = SystemHealthModel.SystemHealthComponent.Severity.INFO;
            if (latency >= LATENCY_CRITICAL_MS) {
                status = SystemHealthModel.SystemHealthStatus.UNHEALTHY;
                message = "Database latency critical: " + latency + "ms";
                severity = SystemHealthModel.SystemHealthComponent.Severity.ERROR;
            } else if (latency >= LATENCY_WARNING_MS) {
                status = SystemHealthModel.SystemHealthStatus.DEGRADED;
                message = "Database latency high: " + latency + "ms";
                severity = SystemHealthModel.SystemHealthComponent.Severity.WARN;
            } else {
                status = SystemHealthModel.SystemHealthStatus.HEALTHY;
                message = "Database responsive";
            }
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(), status, message,
                    Instant.now(), latency, details, Instant.now(), null, severity);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            details.put("connectivity", false);
            details.put("latencyMs", latency);
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Database query failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, details, null, "DB_QUERY_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
