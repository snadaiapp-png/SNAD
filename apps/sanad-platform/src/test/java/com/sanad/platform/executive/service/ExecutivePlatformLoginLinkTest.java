package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService.AccessDecision;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService.CommercialAction;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService.TenantCommercialState;
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
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private JdbcTemplate jdbc;
    private PlatformAuditService audit;
    private Authentication authentication;
    private TenantCommercialStateService commercialState;
    private ExecutivePlatformService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(PlatformAuditService.class);
        authentication = mock(Authentication.class);
        commercialState = mock(TenantCommercialStateService.class);
        service = new ExecutivePlatformService(
                jdbc, audit, commercialState, mock(ExecutiveTenantProvisioningService.class));
    }

    @Test
    void recordsAnOpenEventOnlyWhenCanonicalCommercialStateAllowsLogin() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ACTIVE")));
        when(commercialState.resolve(TENANT_ID, "ACTIVE")).thenReturn(allowedState());

        service.recordTenantLoginLinkEvent(TENANT_ID, "OPEN", authentication);

        verify(commercialState).resolve(TENANT_ID, "ACTIVE");
        verify(audit).success(authentication, TENANT_ID, "TENANT_LOGIN_LINK_OPEN", "TENANT",
                TENANT_ID.toString(), "Executive opened tenant sign-in link", null,
                java.util.Map.of("action", "OPEN"));
    }

    @Test
    void rejectsActiveTenantWithoutEffectiveSubscription() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ACTIVE")));
        when(commercialState.resolve(TENANT_ID, "ACTIVE")).thenReturn(new TenantCommercialState(
                TENANT_ID, "ACTIVE", null, null, null,
                AccessDecision.NO_EFFECTIVE_SUBSCRIPTION,
                CommercialAction.CREATE_SUBSCRIPTION,
                "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION",
                false));

        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "COPY", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(error.getReason())
                            .contains("NO_EFFECTIVE_SUBSCRIPTION");
                });

        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    void rejectsActiveTenantWhenBillingStateSuspendsCommercialAccess() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ACTIVE")));
        when(commercialState.resolve(TENANT_ID, "ACTIVE")).thenReturn(new TenantCommercialState(
                TENANT_ID, "ACTIVE", SUBSCRIPTION_ID, "ACTIVE", "SUSPENDED",
                AccessDecision.SUBSCRIPTION_SUSPENDED,
                CommercialAction.NONE,
                "BILLING_STATE_SUSPENDED",
                false));

        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "OPEN", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    void rejectsLoginLinkEventsForNonActiveTenantsWithoutAuditSuccess() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(TENANT_ID)))
                .thenReturn(List.of(tenant("ARCHIVED")));

        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "COPY", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

        verify(commercialState, never()).resolve(any(), anyString());
        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    void rejectsUnknownLoginLinkActionsFailClosed() {
        assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "IMPERSONATE", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(commercialState, never()).resolve(any(), anyString());
        verify(audit, never()).success(any(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any());
    }

    private static TenantCommercialState allowedState() {
        return new TenantCommercialState(
                TENANT_ID, "ACTIVE", SUBSCRIPTION_ID, "ACTIVE", "CURRENT",
                AccessDecision.ACCESS_ALLOWED,
                CommercialAction.UPGRADE,
                null,
                true);
    }

    private static TenantResponse tenant(String status) {
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        return new TenantResponse(TENANT_ID, "Acme", null, "acme", status, null,
                "SA", "ar-SA", "Asia/Riyadh", "SAR", null, null, now, now);
    }
}
