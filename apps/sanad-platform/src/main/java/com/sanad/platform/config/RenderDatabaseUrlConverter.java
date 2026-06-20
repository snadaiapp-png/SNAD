package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Render's PostgreSQL connection string to Spring Boot JDBC
 * datasource properties.
 *
 * <p>Parses {@code RENDER_DATABASE_URL} using {@link URI#getRawUserInfo()}
 * and decodes percent-encoding exactly once via
 * {@link URLDecoder#decode(String, java.nio.charset.Charset)}.
 * Does NOT use form-urlencoded semantics (literal {@code +} is preserved
 * as {@code +}, not converted to space).</p>
 *
 * <p>Explicit {@code DATABASE_URL}, {@code DATABASE_USERNAME}, or
 * {@code DATABASE_PASSWORD} override the converter output.</p>
 *
 * <p>Activates only under the {@code prod} profile.</p>
 *
 * <p><strong>Security:</strong> Logs only a generic completion message.
 * Never logs host, database name, username, or connection string.</p>
 */
public class RenderDatabaseUrlConverter implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RenderDatabaseUrlConverter.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String renderUrl = environment.getProperty("RENDER_DATABASE_URL");
        if (renderUrl == null || renderUrl.isBlank()) {
            return;
        }

        if (!isProdProfile(environment)) {
            return;
        }

        try {
            ParsedUrl parsed = parse(renderUrl);

            String existingDbUrl = environment.getProperty("DATABASE_URL");
            String existingUsername = environment.getProperty("DATABASE_USERNAME");
            String existingPassword = environment.getProperty("DATABASE_PASSWORD");

            Map<String, Object> props = new HashMap<>();

            if (existingDbUrl != null && !existingDbUrl.isBlank()) {
                props.put("spring.datasource.url", existingDbUrl);
                props.put("sanad.database.url", existingDbUrl);
            } else {
                props.put("spring.datasource.url", parsed.jdbcUrl);
                props.put("sanad.database.url", parsed.jdbcUrl);
            }

            if (existingUsername != null && !existingUsername.isBlank()) {
                props.put("spring.datasource.username", existingUsername);
                props.put("sanad.database.username", existingUsername);
            } else {
                props.put("spring.datasource.username", parsed.username);
                props.put("sanad.database.username", parsed.username);
            }

            if (existingPassword != null && !existingPassword.isBlank()) {
                props.put("spring.datasource.password", existingPassword);
                props.put("sanad.database.password", existingPassword);
            } else {
                props.put("spring.datasource.password", parsed.password);
                props.put("sanad.database.password", parsed.password);
            }

            environment.getPropertySources()
                    .addFirst(new MapPropertySource("renderDatabaseUrlConversion", props));

            log.info("Render database configuration conversion completed");

        } catch (IllegalArgumentException e) {
            log.error("Render database configuration conversion failed: {}", e.getMessage());
            throw e;
        }
    }

    private boolean isProdProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        String profilesEnv = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        if (profilesEnv.isBlank()) {
            return false;
        }
        for (String p : profilesEnv.split(",")) {
            if ("prod".equals(p.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a Render PostgreSQL connection string.
     *
     * @param renderUrl URL in format postgresql://user:password@host:port/database
     * @return parsed components
     * @throws IllegalArgumentException if the URL is invalid or missing required components.
     *         Exception messages never contain credential values.
     */
    static ParsedUrl parse(String renderUrl) {
        URI uri;
        try {
            uri = new URI(renderUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: malformed URI", e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("postgresql") && !scheme.equals("postgres"))) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: scheme must be 'postgresql' or 'postgres'");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: host is missing");
        }

        int port = uri.getPort();
        if (port < 0) {
            port = 5432;
        }

        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: database name is missing");
        }
        String database = path.startsWith("/") ? path.substring(1) : path;

        // Use getRawUserInfo to avoid double-decoding by URI
        String rawUserInfo = uri.getRawUserInfo();
        if (rawUserInfo == null || rawUserInfo.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: credentials are missing");
        }

        int colonIndex = rawUserInfo.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: password is missing in credentials");
        }

        String rawUser = rawUserInfo.substring(0, colonIndex);
        String rawPass = rawUserInfo.substring(colonIndex + 1);

        if (rawUser.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: username is missing");
        }

        if (rawPass.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: password is missing");
        }

        // Decode percent-encoding exactly once.
        // URLDecoder.decode treats '+' as space in application/x-www-form-urlencoded,
        // but we want to preserve literal '+'. So we manually replace '+' with
        // '%2B' before decoding, then the literal '+' is preserved.
        String username = decodePreservingPlus(rawUser);
        String password = decodePreservingPlus(rawPass);

        // Build JDBC URL, preserving query parameters (e.g., sslmode=require)
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            jdbcUrl = jdbcUrl + "?" + query;
        }

        return new ParsedUrl(jdbcUrl, username, password, host, port, database);
    }

    /**
     * Decode percent-encoding while preserving literal '+' characters.
     *
     * <p>{@link URLDecoder#decode(String, String)} in Java treats '+' as
     * space (form-urlencoded semantics). To avoid this, we replace literal
     * '+' with '%2B' before decoding, so the '+' is preserved after decoding.</p>
     */
    private static String decodePreservingPlus(String encoded) {
        String withPlusEscaped = encoded.replace("+", "%2B");
        return URLDecoder.decode(withPlusEscaped, StandardCharsets.UTF_8);
    }

    static final class ParsedUrl {
        final String jdbcUrl;
        final String username;
        final String password;
        final String host;
        final int port;
        final String database;

        ParsedUrl(String jdbcUrl, String username, String password,
                  String host, int port, String database) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }
}
