package com.sanad.platform.crm.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TD-002-1 — V1 CRM API Deprecation Signaling.
 * <p>
 * Adds RFC 8594 {@code Deprecation} and {@code Sunset} response headers to
 * every response served from the V1 CRM surface ({@code /api/v1/crm/**}).
 * This is an advisory, additive-only filter: it preserves all existing V1
 * behaviour and does not alter routing, status codes, or response bodies.
 * <p>
 * <b>Headers added (RFC 8594 / draft):</b>
 * <ul>
 *   <li>{@code Deprecation: true} — signals that the resource is deprecated.</li>
 *   <li>{@code Sunset: 2026-12-31} — communicates the planned removal date.</li>
 *   <li>{@code Link: </api/v2/crm>; rel="successor-version"} — points clients
 *       to the V2 successor API root for endpoints that have a V2 equivalent.</li>
 * </ul>
 * <p>
 * This filter is {@code @Order}ed after the security and rate-limit filters so
 * that authentication/authorization decisions complete first. Headers are set
 * <em>before</em> {@code filterChain.doFilter(...)} so the response is not yet
 * committed — matching the convention used by
 * {@code StructuredLoggingMdcFilter} and {@code RateLimitFilter}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-deprecation-header">RFC 8594 (draft)</a>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class V1DeprecationHeaderFilter extends OncePerRequestFilter {

    /** RFC 8594 draft: literal {@code true} indicates a deprecated resource. */
    static final String HEADER_DEPRECATION = "Deprecation";
    static final String VALUE_DEPRECATION = "true";

    /** RFC 8594 draft: date after which the deprecated resource may be removed. */
    static final String HEADER_SUNSET = "Sunset";
    static final String VALUE_SUNSET = "Wed, 31 Dec 2026 23:59:59 GMT";

    /** RFC 8288 {@code Link} header pointing to the V2 successor API root. */
    static final String HEADER_LINK = "Link";
    static final String VALUE_LINK_SUCCESSOR = "</api/v2/crm>; rel=\"successor-version\"";

    /** V1 CRM API path prefix. All 16 V1 CRM controllers are mounted under this. */
    static final String V1_CRM_PATH_PREFIX = "/api/v1/crm";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith(V1_CRM_PATH_PREFIX)) {
            response.setHeader(HEADER_DEPRECATION, VALUE_DEPRECATION);
            response.setHeader(HEADER_SUNSET, VALUE_SUNSET);
            response.setHeader(HEADER_LINK, VALUE_LINK_SUCCESSOR);
        }
        filterChain.doFilter(request, response);
    }
}
