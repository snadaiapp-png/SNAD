package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrG2AccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrG2AccessScopeContractTest {

    @Test
    void selfScopeRejectsClientSelectedDifferentEmployment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID selfEmploymentId = UUID.randomUUID();
        UUID otherEmploymentId = UUID.randomUUID();

        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(tenantId), eq(userId)))
                .thenReturn(List.of(selfEmploymentId));

        HrG2AccessScopeService service = new HrG2AccessScopeService(jdbc);

        assertThat(service.requireSelfEmployment(tenantId, userId, selfEmploymentId))
                .isEqualTo(selfEmploymentId);
        assertThatThrownBy(() -> service.requireSelfEmployment(tenantId, userId, otherEmploymentId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SELF scope");
    }

    @Test
    void selfScopeFailsClosedWhenNoEmployeeIsLinked() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(tenantId), eq(userId)))
                .thenReturn(List.of());

        HrG2AccessScopeService service = new HrG2AccessScopeService(jdbc);

        assertThatThrownBy(() -> service.requireSelfEmployment(tenantId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active HR employee");
    }
}
