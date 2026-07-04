package com.sanad.platform.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlPlaneHealthIndicatorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void reportsUpWhenTenantAndAdminRoleAreActive() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT_ID)))
                .thenReturn(1, 1);

        ControlPlaneHealthIndicator indicator = new ControlPlaneHealthIndicator(jdbcTemplate, TENANT_ID);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenAdminRoleIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT_ID)))
                .thenReturn(1, 0);

        ControlPlaneHealthIndicator indicator = new ControlPlaneHealthIndicator(jdbcTemplate, TENANT_ID);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenDatabaseQueryFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT_ID)))
                .thenThrow(new IllegalStateException("database unavailable"));

        ControlPlaneHealthIndicator indicator = new ControlPlaneHealthIndicator(jdbcTemplate, TENANT_ID);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
