package com.sanad.platform.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Stage 05A.1 §22 — Wraps POST/PUT/PATCH requests so the body can be read
 * multiple times (once by {@link IdempotencyCommandInterceptor} for the
 * fingerprint, and again by the controller method).
 *
 * <p>The wrapper eagerly reads the entire request body into a byte array
 * on construction, then serves subsequent {@code getInputStream()} and
 * {@code getReader()} calls from that cached array. This is necessary
 * because the servlet input stream can only be consumed once.</p>
 *
 * <p>The filter is registered at {@link Ordered#HIGHEST_PRECEDENCE} so it
 * runs before any other filter that might consume the body. It only wraps
 * POST/PUT/PATCH requests to {@code /api/**} — GET and DELETE have no body.</p>
 *
 * <p>Conditional on {@link IdempotencyCommandInterceptor} so that slice
 * tests (e.g. {@code @WebMvcTest}) which don't load the interceptor also
 * skip this filter.</p>
 */
@Component
@ConditionalOnBean(IdempotencyCommandInterceptor.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachedBodyRequestFilter extends OncePerRequestFilter {

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
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(wrapped, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Wrapper that caches the request body in memory so it can be re-read.
     */
    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        public byte[] getCachedBody() {
            return cachedBody;
        }

        public String getCachedBodyAsString() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
        }
    }

    private static class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        CachedServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }
    }
}
