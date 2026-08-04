package com.sanad.platform.crm.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TD-002-1 — Verifies that the {@link V1DeprecationHeaderFilter} adds RFC 8594
 * deprecation headers to V1 CRM responses and does not affect non-V1 responses.
 */
class V1DeprecationHeaderFilterTest {

    private final V1DeprecationHeaderFilter filter = new V1DeprecationHeaderFilter();
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    @DisplayName("V1 CRM GET path receives Deprecation, Sunset, and Link headers")
    void v1CrmPath_receivesDeprecationHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/crm/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(V1DeprecationHeaderFilter.VALUE_DEPRECATION, response.getHeader(V1DeprecationHeaderFilter.HEADER_DEPRECATION));
        assertEquals(V1DeprecationHeaderFilter.VALUE_SUNSET, response.getHeader(V1DeprecationHeaderFilter.HEADER_SUNSET));
        assertEquals(V1DeprecationHeaderFilter.VALUE_LINK_SUCCESSOR, response.getHeader(V1DeprecationHeaderFilter.HEADER_LINK));
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("V1 CRM POST path receives deprecation headers")
    void v1CrmPostPath_receivesDeprecationHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/crm/leads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(V1DeprecationHeaderFilter.VALUE_DEPRECATION, response.getHeader(V1DeprecationHeaderFilter.HEADER_DEPRECATION));
        assertEquals(V1DeprecationHeaderFilter.VALUE_SUNSET, response.getHeader(V1DeprecationHeaderFilter.HEADER_SUNSET));
        assertEquals(V1DeprecationHeaderFilter.VALUE_LINK_SUCCESSOR, response.getHeader(V1DeprecationHeaderFilter.HEADER_LINK));
    }

    @Test
    @DisplayName("V2 CRM path does NOT receive deprecation headers")
    void v2CrmPath_doesNotReceiveDeprecationHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/crm/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_DEPRECATION));
        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_SUNSET));
        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_LINK));
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Non-CRM path does NOT receive deprecation headers")
    void nonCrmPath_doesNotReceiveDeprecationHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_DEPRECATION));
        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_SUNSET));
        assertNull(response.getHeader(V1DeprecationHeaderFilter.HEADER_LINK));
    }

    @Test
    @DisplayName("Nested V1 CRM path (e.g. /api/v1/crm/accounts/{id}/master) receives headers")
    void nestedV1CrmPath_receivesDeprecationHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/crm/accounts/550e8400-e29b-41d4-a716-446655440000/master");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(V1DeprecationHeaderFilter.VALUE_DEPRECATION, response.getHeader(V1DeprecationHeaderFilter.HEADER_DEPRECATION));
        assertEquals(V1DeprecationHeaderFilter.VALUE_SUNSET, response.getHeader(V1DeprecationHeaderFilter.HEADER_SUNSET));
    }
}
