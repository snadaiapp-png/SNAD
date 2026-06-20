package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Render's PostgreSQL connection string format to Spring Boot
 * JDBC datasource properties.
 *
 * <p>Render provides the database URL in PostgreSQL format:
 * <pre>
 *   postgresql://user:password@host:port/database
 * </pre>
 *
 * Spring Boot requires JDBC format:
 * <pre>
 *   jdbc:postgresql://host:port/database
 * </pre>
 *
 * <p>This {@link EnvironmentPostProcessor} runs early in the Spring Boot
 * startup sequence. It checks for {@code RENDER_DATABASE_URL} and, if
 * present, parses it using {@link URI}, extracts the components, and
 * sets the following properties <strong>only if they are not already
 * defined</strong> (explicit {@code DATABASE_URL},
 * {@code DATABASE_USERNAME}, or {@code DATABASE_PASSWORD} take
 * precedence):
 *
 * <ul>
 *   <li>{@code spring.datasource.url} → {@code jdbc:postgresql://host:port/database}</li>
 *   <li>{@code spring.datasource.username} → decoded user</li>
 *   <li>{@code spring.datasource.password} → decoded password</li>
 *   <li>{@code sanad.database.url} → same JDBC URL (for {@link ProductionDatabaseProperties})</li>
 *   <li>{@code sanad.database.username} → same username</li>
 *   <li>{@code sanad.database.password} → same password</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Credentials are never logged. If the
 * URL is malformed or missing required components, the application
 * fails startup with a message that does not include credentials.</p>
 */
public class RenderDatabaseUrlConverter implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RenderDatabaseUrlConverter.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String renderUrl = environment.getProperty("RENDER_DATABASE_URL");

        // Only convert if RENDER_DATABASE_URL is set and profile is prod
        if (renderUrl == null || renderUrl.isBlank()) {
            return;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = false;
        for (String profile : activeProfiles) {
            if ("prod".equals(profile)) {
                isProd = true;
                break;
            }
        }
        // Also check SPRING_PROFILES_ACTIVE env var
        if (!isProd) {
            String profilesEnv = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
            if (!profilesEnv.contains("prod")) {
                return;
            }
        }

        log.info("RenderDatabaseUrlConverter: converting RENDER_DATABASE_URL to JDBC format");

        try {
            ParsedUrl parsed = parse(renderUrl);

            // Check for explicit overrides
            String existingDbUrl = environment.getProperty("DATABASE_URL");
            String existingUsername = environment.getProperty("DATABASE_USERNAME");
            String existingPassword = environment.getProperty("DATABASE_PASSWORD");

            Map<String, Object> props = new HashMap<>();

            // Only set if not explicitly overridden
            if (existingDbUrl == null || existingDbUrl.isBlank()) {
                props.put("spring.datasource.url", parsed.jdbcUrl);
                props.put("sanad.database.url", parsed.jdbcUrl);
            } else {
                props.put("spring.datasource.url", existingDbUrl);
                props.put("sanad.database.url", existingDbUrl);
            }

            if (existingUsername == null || existingUsername.isBlank()) {
                props.put("spring.datasource.username", parsed.username);
                props.put("sanad.database.username", parsed.username);
            } else {
                props.put("spring.datasource.username", existingUsername);
                props.put("sanad.database.username", existingUsername);
            }

            if (existingPassword == null || existingPassword.isBlank()) {
                props.put("spring.datasource.password", parsed.password);
                props.put("sanad.database.password", parsed.password);
            } else {
                props.put("spring.datasource.password", existingPassword);
                props.put("sanad.database.password", existingPassword);
            }

            environment.getPropertySources()
                    .addFirst(new MapPropertySource("renderDatabaseUrlConversion", props));

            log.info("RenderDatabaseUrlConverter: conversion complete (host: {}, database: {})",
                    parsed.host, parsed.database);

        } catch (IllegalArgumentException e) {
            log.error("RenderDatabaseUrlConverter: failed to parse RENDER_DATABASE_URL: {}",
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Parse a Render PostgreSQL connection string.
     *
     * @param renderUrl URL in format postgresql://user:password@host:port/database
     * @return parsed components
     * @throws IllegalArgumentException if the URL is invalid or missing required components
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
            port = 5432; // PostgreSQL default port
        }

        // Extract database name from path
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: database name is missing");
        }
        String database = path.startsWith("/") ? path.substring(1) : path;

        // Extract and decode credentials from userInfo
        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: credentials are missing");
        }

        int colonIndex = userInfo.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: password is missing in credentials");
        }

        String encodedUser = userInfo.substring(0, colonIndex);
        String encodedPass = userInfo.substring(colonIndex + 1);

        if (encodedUser.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: username is missing");
        }

        if (encodedPass.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid RENDER_DATABASE_URL: password is missing");
        }

        String username = URLDecoder.decode(encodedUser, StandardCharsets.UTF_8);
        String password = URLDecoder.decode(encodedPass, StandardCharsets.UTF_8);

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        return new ParsedUrl(jdbcUrl, username, password, host, port, database);
    }

    /**
     * Parsed components of a Render PostgreSQL connection string.
     */
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
