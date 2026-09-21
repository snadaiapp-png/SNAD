package com.sanad.platform.security.filter;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterControlPlaneTenantBindingTest {

    private static final UUID CONTROL_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_TENANT =
            UUID.fromString("2c6149d8-e109-4ad5-9fbc-692327e675c2");
    private static final UUID OWNER_USER =
            UUID.fromString("00000000-0000-0000-0000-000000000010");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controlPlaneOwnerMayTargetAnotherTenantOnExecutiveApi() throws Exception {
        Harness h = harness(true);
        MockHttpServletRequest request =
                request("GET", "/api/v1/executive/billing/invoices", TARGET_TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        h.filter.doFilterInternal(request, response, h.chain);

        verify(h.chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void controlPlaneTenantDoesNotBypassTenantBindingOutsideExecutiveNamespace() throws Exception {
        Harness h = harness(true);
        MockHttpServletRequest request =
                request("GET", "/api/v1/users", TARGET_TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        h.filter.doFilterInternal(request, response, h.chain);

        verify(h.chain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    @Test
    void nonControlPlaneTenantCannotTargetAnotherTenantEvenOnExecutiveApi() throws Exception {
        Harness h = harness(false);
        MockHttpServletRequest request =
                request("GET", "/api/v1/executive/billing/invoices", TARGET_TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        h.filter.doFilterInternal(request, response, h.chain);

        verify(h.chain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    private static Harness harness(boolean isControlPlaneTenant) {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        SessionVersionCache sessionVersionCache = mock(SessionVersionCache.class);
        ControlPlaneAccessGuard accessGuard = mock(ControlPlaneAccessGuard.class);
        FilterChain chain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);

        when(tokenProvider.parseAndValidate("token")).thenReturn(claims);
        when(claims.get("tenant_id", String.class)).thenReturn(CONTROL_TENANT.toString());
        when(claims.getSubject()).thenReturn(OWNER_USER.toString());
        when(claims.get("email", String.class)).thenReturn("snad.ai.app@gmail.com");
        when(claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM)).thenReturn(0);
        when(claims.get(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, Boolean.class)).thenReturn(false);
        when(sessionVersionCache.get(CONTROL_TENANT, OWNER_USER)).thenReturn(0L);
        when(accessGuard.isControlPlaneTenant(CONTROL_TENANT)).thenReturn(isControlPlaneTenant);

        return new Harness(
                new JwtAuthenticationFilter(tokenProvider, sessionVersionCache, accessGuard),
                chain
        );
    }

    private static MockHttpServletRequest request(String method, String path, UUID tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        request.addParameter("tenantId", tenantId.toString());
        return request;
    }

    private record Harness(JwtAuthenticationFilter filter, FilterChain chain) {}
}
