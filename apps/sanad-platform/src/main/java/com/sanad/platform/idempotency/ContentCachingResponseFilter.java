package com.sanad.platform.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Stage 05A.2 §15 — Wraps POST/PUT/PATCH responses to {@code /api/**} with a
 * {@link ContentCachingResponseWrapper} so the {@link IdempotencyCommandInterceptor}
 * can read the response body in {@code afterCompletion} (for storage in the
 * idempotency record) before it is committed to the client.
 *
 * <p>Without this filter, the interceptor's {@code readCapturedResponseBody}
 * method returns an empty string for real HTTP responses (it only works for
 * MockMvc's {@code MockHttpServletResponse}). This means idempotency replay
 * would store an empty body and replay an empty response — breaking the
 * idempotency contract in production.</p>
 *
 * <p>After the filter chain completes, {@code copyBodyToResponse()} is called
 * to flush the cached bytes to the client. The interceptor's
 * {@code afterCompletion} runs BEFORE this flush, so it can read the buffered
 * bytes via {@code getContentAsByteArray()}.</p>
 *
 * <p>Conditional on {@link IdempotencyCommandInterceptor} so slice tests
 * (e.g. {@code @WebMvcTest}) which don't load the interceptor also skip this
 * filter. Registered just after {@link CachedBodyRequestFilter} (which is at
 * {@link Ordered#HIGHEST_PRECEDENCE}) so the request body is cached before
 * this filter wraps the response.</p>
 */
@Component
@ConditionalOnBean(IdempotencyCommandInterceptor.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ContentCachingResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        boolean shouldWrap = ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method))
                && uri != null && uri.startsWith("/api/");
        if (shouldWrap) {
            ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
            try {
                filterChain.doFilter(request, wrapped);
            } finally {
                wrapped.copyBodyToResponse();
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
