package com.sanad.platform.ops.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Integration point that dispatches operational alerts based on health indicator results.
 * <p>
 * Wraps existing health indicators and emits alerts when the health status degrades.
 * This component bridges the HealthIntelligenceService with the alerting infrastructure.
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
@Component
public class HealthCheckAlertIntegration {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckAlertIntegration.class);

    private final OperationalAlertPort alertPort;
    private volatile String lastStatus = "UP";

    public HealthCheckAlertIntegration(OperationalAlertPort alertPort) {
        this.alertPort = alertPort;
    }

    /**
     * Evaluate a health result and dispatch an alert if the status has degraded.
     *
     * @param componentName the name of the health component (e.g., "database", "redis")
     * @param health        the Spring Boot Health result
     */
    public void evaluateAndAlert(String componentName, Health health) {
        String currentStatus = health.getStatus().getCode();
        if ("DOWN".equals(currentStatus) || "OUT_OF_SERVICE".equals(currentStatus)) {
            if (!currentStatus.equals(lastStatus)) {
                String severity = "DOWN".equals(currentStatus)
                        ? OperationalAlertCategories.SEVERITY_CRITICAL
                        : OperationalAlertCategories.SEVERITY_ERROR;

                OperationalAlertPort.OperationalAlert alert = OperationalAlertPort.OperationalAlert.builder()
                        .severity(severity)
                        .category(OperationalAlertCategories.CATEGORY_HEALTH_DEGRADED)
                        .summary("Health check failed: " + componentName + " is " + currentStatus)
                        .details(health.toString())
                        .service(componentName)
                        .environment(alertPort.isEnabled() ? "production" : "development")
                        .occurredAt(Instant.now())
                        .build();

                alertPort.dispatch(alert);
            }
        }
        lastStatus = currentStatus;
    }
}
