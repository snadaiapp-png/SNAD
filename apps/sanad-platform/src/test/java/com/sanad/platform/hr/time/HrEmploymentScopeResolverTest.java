package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrEmploymentScopeResolverTest {

    private JdbcTemplate jdbc;
    private HrEmploymentScopeResolver resolver;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        resolver = new HrEmploymentScopeResolver(jdbc);
    }

    @Test
    void selfEmploymentComesFromAuthenticatedTenantAndUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(UUID.class), eq(tenantId), eq(userId)))
                .thenReturn(employmentId);

        assertThat(resolver.requireSelfEmployment(tenantId, userId)).isEqualTo(employmentId);
    }

    @Test
    void missingActiveEmploymentFailsClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(UUID.class), eq(tenantId), eq(userId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> resolver.requireSelfEmployment(tenantId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active HR employment");
    }

    @Test
    void managerMayTargetOnlyAnActiveDirectReportInsideTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID targetEmploymentId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq(tenantId), eq(managerUserId), eq(targetEmploymentId)))
                .thenReturn(1);

        resolver.requireManagedEmployment(tenantId, managerUserId, targetEmploymentId);
    }

    @Test
    void nonDirectReportOrForeignTenantTargetFailsClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID targetEmploymentId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq(tenantId), eq(managerUserId), eq(targetEmploymentId)))
                .thenReturn(0);

        assertThatThrownBy(() -> resolver.requireManagedEmployment(
                tenantId, managerUserId, targetEmploymentId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }
}
