package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.service.RegistrationProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutivePlatformLoginLinkTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JdbcTemplate jdbc;
    private PlatformAuditService audit;
    private Authentication authentication;
    private ExecutivePlatformService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(PlatformAuditService.class);
        authentication = mock(Authentication.class);
        service = new ExecutivePlatformService(jdbc, audit, mock(RegistrationProvisioner.class));
    }

    @Test
    void recordsAnOpenEventForAnActiveTenantWithoutCreatingCredentials() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ACTIVE")));

        service.recordTenantLoginLinkEvent(TENANT_ID, "OPEN", authentication);

        verify(audit).success(authentication, TENANT_ID, "TENANT_LOGIN_LINK_OPEN", "TENANT",
                TENANT_ID.toString(), "Executive opened tenant sign-in link", null,
                java.util.Map.of("action", "OPEN"));
    }

    @Test
    void rejectsLoginLinkEventsForNonActiveTenantsWithoutAuditSuccess() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ARCHIVED")));

        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "COPY", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    void rejectsUnknownLoginLinkActionsFailClosed() {
        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "IMPERSONATE", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    private static TenantResponse tenant(String status) {
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        return new TenantResponse(TENANT_ID, "Acme", null, "acme", status, null,
                "SA", "ar-SA", "Asia/Riyadh", "SAR", null, null, now, now);
    }
}
