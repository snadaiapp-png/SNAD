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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterControlPlaneTenantBindingTest {

    private static final UUID CONTROL_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_USER =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TARGET_TENANT =
            UUID.fromString("2c6149d8-e109-4ad5-9fbc-692327e675c2");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void controlPlanePrincipalMayTargetForeignTenantOnExecutiveApi() throws Exception {
        TestFixture fixture = fixture(CONTROL_TENANT, true);
        MockHttpServletRequest request = request(
                "/api/v1/executive/billing/invoices", TARGET_TENANT);

        fixture.filter.doFilter(request, fixture.response, fixture.chain);

        assertEquals(200, fixture.response.getStatus());
        verify(fixture.chain).doFilter(request, fixture.response);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }

    @Test
    void controlPlanePrincipalCannotSmuggleForeignTenantIntoNonTargetAwareExecutiveApi() throws Exception {
        TestFixture fixture = fixture(CONTROL_TENANT, true);
        MockHttpServletRequest request = request("/api/v1/executive/access-check/v2", TARGET_TENANT);

        fixture.filter.doFilter(request, fixture.response, fixture.chain);

        assertEquals(403, fixture.response.getStatus());
        verify(fixture.chain, never()).doFilter(request, fixture.response);
    }

    @Test
    void controlPlanePrincipalStillCannotBypassTenantBindingOutsideExecutiveApi() throws Exception {
        TestFixture fixture = fixture(CONTROL_TENANT, true);
        MockHttpServletRequest request = request("/api/v1/users", TARGET_TENANT);

        fixture.filter.doFilter(request, fixture.response, fixture.chain);

        assertEquals(403, fixture.response.getStatus());
        verify(fixture.chain, never()).doFilter(request, fixture.response);
        assertTrue(fixture.response.getContentAsString().contains("تعارض في هوية المستأجر"));
    }

    @Test
    void nonControlPlanePrincipalCannotTargetForeignTenantOnExecutiveApi() throws Exception {
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        TestFixture fixture = fixture(tenant, false);
        MockHttpServletRequest request = request(
                "/api/v1/executive/billing/invoices", TARGET_TENANT);

        fixture.filter.doFilter(request, fixture.response, fixture.chain);

        assertEquals(403, fixture.response.getStatus());
        verify(fixture.chain, never()).doFilter(request, fixture.response);
    }

    @Test
    void sameTenantRequestRemainsAllowedForNormalPrincipal() throws Exception {
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        TestFixture fixture = fixture(tenant, false);
        MockHttpServletRequest request = request("/api/v1/users", tenant);

        fixture.filter.doFilter(request, fixture.response, fixture.chain);

        assertEquals(200, fixture.response.getStatus());
        verify(fixture.chain).doFilter(request, fixture.response);
    }

    private static MockHttpServletRequest request(String path, UUID tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer owner-token");
        request.addParameter("tenantId", tenantId.toString());
        return request;
    }

    private static TestFixture fixture(UUID jwtTenantId, boolean controlPlaneTenant) {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        SessionVersionCache sessionVersionCache = mock(SessionVersionCache.class);
        ControlPlaneAccessGuard controlPlaneAccessGuard = mock(ControlPlaneAccessGuard.class);
        Claims claims = mock(Claims.class);

        when(tokenProvider.parseAndValidate("owner-token")).thenReturn(claims);
        when(claims.get("tenant_id", String.class)).thenReturn(jwtTenantId.toString());
        when(claims.getSubject()).thenReturn(OWNER_USER.toString());
        when(claims.get("email", String.class)).thenReturn("snad.ai.app@gmail.com");
        when(claims.get(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, Boolean.class)).thenReturn(false);
        when(claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM)).thenReturn(7L);
        when(sessionVersionCache.get(jwtTenantId, OWNER_USER)).thenReturn(7L);
        when(controlPlaneAccessGuard.isControlPlaneTenant(jwtTenantId)).thenReturn(controlPlaneTenant);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenProvider, sessionVersionCache, controlPlaneAccessGuard);
        return new TestFixture(filter, mock(FilterChain.class), new MockHttpServletResponse());
    }

    private record TestFixture(
            JwtAuthenticationFilter filter,
            FilterChain chain,
            MockHttpServletResponse response) {
    }
}
