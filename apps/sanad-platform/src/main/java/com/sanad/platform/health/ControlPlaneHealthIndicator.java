package com.sanad.platform.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies that the configured Control Plane tenant and its ADMIN role exist
 * from inside the application runtime network. This avoids exposing the
 * production database or requiring GitHub-hosted runners to resolve a private
 * database hostname.
 */
@Component("controlPlaneHealthIndicator")
@ConditionalOnExpression("'${SANAD_CONTROL_PLANE_TENANT_ID:}' != ''")
public class ControlPlaneHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final UUID tenantId;

    public ControlPlaneHealthIndicator(
            JdbcTemplate jdbcTemplate,
            @org.springframework.beans.factory.annotation.Value("${SANAD_CONTROL_PLANE_TENANT_ID}") UUID tenantId
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantId = tenantId;
    }

    @Override
    public Health health() {
        try {
            Integer tenantCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenants WHERE id = ? AND status = 'ACTIVE'",
                    Integer.class,
                    tenantId
            );
            Integer adminRoleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND code = 'ADMIN' AND status = 'ACTIVE'",
                    Integer.class,
                    tenantId
            );

            boolean tenantReady = tenantCount != null && tenantCount > 0;
            boolean adminReady = adminRoleCount != null && adminRoleCount > 0;
            if (tenantReady && adminReady) {
                return Health.up()
                        .withDetail("tenant", "ACTIVE")
                        .withDetail("adminRole", "ACTIVE")
                        .build();
            }

            return Health.down()
                    .withDetail("tenant", tenantReady ? "ACTIVE" : "MISSING_OR_INACTIVE")
                    .withDetail("adminRole", adminReady ? "ACTIVE" : "MISSING_OR_INACTIVE")
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
