package com.sanad.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CORS origin allowlist configuration.
 *
 * <p>Binds to {@code sanad.cors.*} properties. In production,
 * {@code SANAD_CORS_ALLOWED_ORIGINS} must be set to a comma-separated
 * list of exact HTTPS origins. Wildcards, HTTP origins (in production),
 * paths, query strings, fragments, and credentials in URIs are rejected
 * at startup.</p>
 *
 * <h3>Production example</h3>
 * <pre>{@code
 * SANAD_CORS_ALLOWED_ORIGINS=https://snad-app.vercel.app
 * }</pre>
 *
 * <h3>Development example</h3>
 * <pre>{@code
 * SANAD_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
 * }</pre>
 */
@ConfigurationProperties(prefix = "sanad.cors")
@Validated
public class CorsProperties {

    private static final Logger log = LoggerFactory.getLogger(CorsProperties.class);

    /**
     * Comma-separated list of exact allowed origins.
     * Set via {@code SANAD_CORS_ALLOWED_ORIGINS} environment variable.
     */
    private String allowedOrigins = "";

    /**
     * Parsed and validated origin list. Populated after bean construction.
     */
    private List<String> parsedOrigins = Collections.emptyList();

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Returns the validated, normalized list of allowed origins.
     * Only call after {@link #validateAndParse()} has been invoked.
     */
    public List<String> getParsedOrigins() {
        return parsedOrigins;
    }

    /**
     * Parse, normalize, and validate the configured origins.
     * Must be called after property binding (via @PostConstruct or explicit call).
     *
     * @param productionMode if true, enforces production-only rules
     *                      (HTTPS only, no localhost, no empty list)
     * @throws IllegalStateException if validation fails
     */
    public void validateAndParse(boolean productionMode) {
        List<String> rawOrigins = parseRawOrigins(allowedOrigins);
        List<String> validated = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String raw : rawOrigins) {
            String normalized = normalize(raw);

            validateNoWildcard(normalized, raw);
            validateUriSyntax(normalized, raw);
            validateNoPath(normalized, raw);
            validateNoQueryOrFragment(normalized, raw);
            validateNoCredentials(normalized, raw);
            validateScheme(normalized, raw, productionMode);
            validateNoDuplicate(normalized, raw, seen);

            validated.add(normalized);
            seen.add(normalized);
        }

        if (productionMode && validated.isEmpty()) {
            throw new IllegalStateException(
                "SANAD_CORS_ALLOWED_ORIGINS (sanad.cors.allowed-origins) must not be empty in production. "
                + "Set it to the exact HTTPS origin of the frontend, e.g. https://snad-app.vercel.app");
        }

        this.parsedOrigins = Collections.unmodifiableList(validated);
        log.info("CORS allowed origins (production={}): {}", productionMode, parsedOrigins);
    }

    // ─── Parsing ────────────────────────────────────────────────────

    private static List<String> parseRawOrigins(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return value.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ─── Normalization ──────────────────────────────────────────────

    private static String normalize(String origin) {
        // Strip a single trailing slash that represents the root path,
        // e.g. "https://snad-app.vercel.app/" → "https://snad-app.vercel.app"
        // but NOT "https://snad-app.vercel.app/api/"
        String normalized = origin;
        if (normalized.endsWith("/") && !normalized.endsWith("://")) {
            String withoutSlash = normalized.substring(0, normalized.length() - 1);
            // After removing the trailing '/', check if the path component is empty.
            // A bare origin has no path beyond the root, so stripping the root '/' is safe.
            try {
                URI uri = new URI(withoutSlash);
                String path = uri.getPath();
                if (path == null || path.isEmpty()) {
                    normalized = withoutSlash;
                }
                // If path is non-empty (e.g. "/api"), the trailing '/' was part of a
                // real path — do NOT strip it (will be caught by validateNoPath).
            } catch (URISyntaxException e) {
                // If we can't parse it, leave as-is — validateUriSyntax will catch it
            }
        }
        return normalized;
    }

    // ─── Validation ─────────────────────────────────────────────────

    private static void validateNoWildcard(String normalized, String raw) {
        if (normalized.contains("*")) {
            throw new IllegalStateException(
                "CORS origin contains wildcard '*': " + raw + ". "
                + "Only exact origins are permitted. Remove wildcards from SANAD_CORS_ALLOWED_ORIGINS.");
        }
    }

    private static void validateUriSyntax(String normalized, String raw) {
        try {
            URI uri = new URI(normalized);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException(
                    "CORS origin is not a valid URI (missing scheme or host): " + raw);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                "CORS origin has invalid URI syntax: " + raw + " — " + e.getMessage());
        }
    }

    private static void validateNoPath(String normalized, String raw) {
        try {
            URI uri = new URI(normalized);
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                throw new IllegalStateException(
                    "CORS origin must not contain a path: " + raw + ". "
                    + "Only scheme + host + port are allowed.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("CORS origin has invalid URI syntax: " + raw);
        }
    }

    private static void validateNoQueryOrFragment(String normalized, String raw) {
        try {
            URI uri = new URI(normalized);
            if (uri.getQuery() != null) {
                throw new IllegalStateException(
                    "CORS origin must not contain a query string: " + raw);
            }
            if (uri.getFragment() != null) {
                throw new IllegalStateException(
                    "CORS origin must not contain a fragment: " + raw);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("CORS origin has invalid URI syntax: " + raw);
        }
    }

    private static void validateNoCredentials(String normalized, String raw) {
        try {
            URI uri = new URI(normalized);
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException(
                    "CORS origin must not contain user credentials: " + raw);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("CORS origin has invalid URI syntax: " + raw);
        }
    }

    private static void validateScheme(String normalized, String raw, boolean productionMode) {
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IllegalStateException(
                    "CORS origin must use http or https scheme: " + raw + " (found: " + scheme + ")");
            }
            if (productionMode && "http".equals(scheme)) {
                throw new IllegalStateException(
                    "CORS origin uses http scheme in production: " + raw + ". "
                    + "Only https origins are allowed in production.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("CORS origin has invalid URI syntax: " + raw);
        }
    }

    private static void validateNoDuplicate(String normalized, String raw, Set<String> seen) {
        if (!seen.add(normalized)) {
            throw new IllegalStateException(
                "Duplicate CORS origin: " + raw);
        }
    }
}
