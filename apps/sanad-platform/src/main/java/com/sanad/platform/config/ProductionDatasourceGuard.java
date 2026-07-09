package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast guard that prevents the application from starting in the
 * {@code prod} profile if the datasource URL is missing or points to
 * an embedded H2 database.
 *
 * <p>When {@code DATABASE_URL} (or {@code SPRING_DATASOURCE_URL}) is
 * empty, Spring Boot's auto-configuration falls back to an embedded H2
 * in-memory database. But the prod profile explicitly sets the driver
 * to {@code org.postgresql.Driver}, which rejects H2 URLs with a
 * confusing error:</p>
 *
 * <pre>
 * Driver org.postgresql.Driver claims to not accept jdbcUrl: jdbc:h2:mem:...
 * </pre>
 *
 * <p>This guard intercepts startup BEFORE the datasource is created and
 * fails with a clear, actionable error message.</p>
 */
public class ProductionDatasourceGuard implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProductionDatasourceGuard.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProdProfile(environment)) {
            return;
        }

        // Resolve the effective datasource URL from all possible sources.
        // Priority: SPRING_DATASOURCE_URL > DATABASE_URL > RENDER_DATABASE_URL
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            datasourceUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            datasourceUrl = environment.getProperty("DATABASE_URL");
        }
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            datasourceUrl = environment.getProperty("RENDER_DATABASE_URL");
        }

        // Check if the URL is missing entirely
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException(
                    "FATAL: Production datasource URL is not configured. "
                    + "Set SPRING_DATASOURCE_URL or DATABASE_URL to a valid PostgreSQL JDBC URL "
                    + "(e.g., jdbc:postgresql://host:5432/dbname). "
                    + "H2 is not allowed in production.");
        }

        // Check if the URL points to H2
        String lowerUrl = datasourceUrl.toLowerCase();
        if (lowerUrl.startsWith("jdbc:h2:") || lowerUrl.contains("h2:mem:") || lowerUrl.contains("h2:file:")) {
            throw new IllegalStateException(
                    "FATAL: Invalid production datasource: H2 is not allowed in prod. "
                    + "The datasource URL must be a PostgreSQL JDBC URL "
                    + "(e.g., jdbc:postgresql://host:5432/dbname). "
                    + "Found URL starting with: " + maskUrl(datasourceUrl));
        }

        // Check if the URL is a raw PostgreSQL URI (not JDBC format)
        // Render provides DATABASE_URL in format: postgresql://user:pass@host:port/db
        // Spring Boot needs: jdbc:postgresql://host:port/db
        if (datasourceUrl.startsWith("postgresql://") || datasourceUrl.startsWith("postgres://")) {
            log.info("Production datasource URL detected in raw PostgreSQL format; "
                    + "RenderDatabaseUrlConverter will convert it to JDBC format.");
        }

        log.info("Production datasource guard: PASS (URL configured, not H2)");
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
     * Mask the URL for logging — show only the scheme and host, never credentials.
     */
    private static String maskUrl(String url) {
        // Show only the first 30 chars, and only the scheme/host part
        if (url.length() <= 30) {
            return url.replaceAll("://[^@]+@", "://***:***@");
        }
        return url.substring(0, 30).replaceAll("://[^@]+@", "://***:***@") + "...";
    }
}
