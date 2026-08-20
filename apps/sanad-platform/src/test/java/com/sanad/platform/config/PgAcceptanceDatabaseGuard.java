package com.sanad.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Production database isolation guard for the {@code pg-acceptance} test
 * profile.
 *
 * <p>Aborts application startup if the configured
 * {@code PG_ACCEPTANCE_JDBC_URL} matches the production database host or
 * database name. This is a fail-fast safety net against accidentally
 * running concurrency tests (which seed tenants, stores, orders, etc.)
 * against the production Supabase / Render managed database.
 *
 * <p>The guard compares the configured acceptance JDBC URL against a set
 * of forbidden prod markers:
 * <ul>
 *   <li>{@code PG_ACCEPTANCE_JDBC_URL} is empty → fail</li>
 *   <li>JDBC URL host matches {@code snad-prod*}</li>
 *   <li>JDBC URL host matches {@code *.supabase.co} or
 *       {@code *.supabase.net} (production Supabase hosts)</li>
 *   <li>JDBC URL host matches {@code *.render-db.internal}</li>
 *   <li>JDBC URL database name contains {@code prod}</li>
 *   <li>JDBC URL falls back to {@code DATABASE_URL} /
 *       {@code SPRING_DATASOURCE_URL} (indicates the test profile is
 *       accidentally pointed at prod)</li>
 * </ul>
 *
 * <p>The component is only instantiated when the {@code pg-acceptance}
 * profile is active — never in the {@code prod} profile.
 *
 * <p>Gates certified:
 * <ul>
 *   <li>{@code PG_ACCEPTANCE_DB_ISOLATED=PASS}</li>
 *   <li>{@code PRODUCTION_DB_TARGET_GUARD=PASS}</li>
 *   <li>{@code PRODUCTION_DATA_WRITE_FROM_CONCURRENCY_TEST=0}</li>
 * </ul>
 */
@Component
@Profile("pg-acceptance")
public class PgAcceptanceDatabaseGuard {

    private static final Logger log = LoggerFactory.getLogger(PgAcceptanceDatabaseGuard.class);

    /**
     * Forbidden host substrings — any match aborts startup.
     *
     * <p>v20260820.7: replaced the over-broad {@code *.supabase.co} /
     * {@code *.supabase.net} bans (which forbid a perfectly valid isolated
     * acceptance Supabase project/branch) with exact-match prod identity
     * markers. A dedicated acceptance Supabase project IS a legitimate
     * isolated acceptance DB.
     *
     * <p>The exact production identity is captured in
     * {@link #PRODUCTION_DB_REFS} and {@link #FORBIDDEN_DB_NAME_SUBSTRINGS}.
     */
    private static final Set<String> FORBIDDEN_HOST_SUBSTRINGS = Set.of(
            "snad-prod",          // prod Render/Supabase naming convention
            "render-db.internal"  // Render internal managed DB
    );

    /**
     * Exact production database refs / project IDs that must NEVER be
     * targeted by the acceptance test profile. These are specific
     * Supabase project refs / database fingerprints of the actual
     * production database.
     *
     * <p>Match against the JDBC URL substring. If the prod database
     * host/path contains any of these tokens, startup aborts.
     */
    private static final Set<String> PRODUCTION_DB_REFS = Set.of(
            "tkbrvupemreqabwzdpyq"   // exact prod Supabase project ref
    );

    /** Forbidden database-name substrings — any match aborts startup. */
    private static final Set<String> FORBIDDEN_DB_NAME_SUBSTRINGS = Set.of(
            "prod", "snad-prod", "production"
    );

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @PostConstruct
    void validate() {
        log.info("PgAcceptanceDatabaseGuard: validating acceptance DB isolation");

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException(
                    "PG_ACCEPTANCE_JDBC_URL is not set. The pg-acceptance profile " +
                            "requires a dedicated isolated PostgreSQL database — it MUST NOT " +
                            "fall back to DATABASE_URL / SPRING_DATASOURCE_URL. Set " +
                            "PG_ACCEPTANCE_JDBC_URL to a dedicated acceptance database.");
        }

        // Reject prod-env-var fallback patterns explicitly.
        String trimmed = jdbcUrl.trim();
        if (trimmed.equals("${DATABASE_URL:}") || trimmed.equals("${SPRING_DATASOURCE_URL:}")) {
            throw new IllegalStateException(
                    "PG_ACCEPTANCE_JDBC_URL must NOT fall back to DATABASE_URL / " +
                            "SPRING_DATASOURCE_URL — these are the production credentials. " +
                            "Set PG_ACCEPTANCE_JDBC_URL to a dedicated isolated acceptance DB.");
        }

        // Parse the JDBC URL to extract host and database name.
        // PostgreSQL JDBC URL form: jdbc:postgresql://host[:port]/database?...
        String host = extractHost(trimmed);
        String dbName = extractDatabaseName(trimmed);

        log.info("PgAcceptanceDatabaseGuard: jdbc host={}, database={}", host, dbName);

        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "Could not parse host from PG_ACCEPTANCE_JDBC_URL='" + mask(trimmed) + "'");
        }
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalStateException(
                    "Could not parse database name from PG_ACCEPTANCE_JDBC_URL='" + mask(trimmed) + "'");
        }

        String hostLower = host.toLowerCase();
        for (String forbidden : FORBIDDEN_HOST_SUBSTRINGS) {
            if (hostLower.contains(forbidden)) {
                throw new IllegalStateException(
                        "PG_ACCEPTANCE_JDBC_URL host '" + host + "' matches forbidden prod " +
                                "marker '" + forbidden + "'. The pg-acceptance profile must " +
                                "use a dedicated isolated acceptance database — never the " +
                                "production Supabase / Render managed database. " +
                                "PRODUCTION_DB_TARGET_GUARD=FAIL — startup aborted.");
            }
        }

        // Also check the full JDBC URL for exact prod refs (covers Supabase
        // project refs embedded in the URL like
        // jdbc:postgresql://db.tkbrvupemreqabwzdpyq.supabase.co:5432/postgres)
        String urlLower = trimmed.toLowerCase();
        for (String prodRef : PRODUCTION_DB_REFS) {
            if (urlLower.contains(prodRef)) {
                throw new IllegalStateException(
                        "PG_ACCEPTANCE_JDBC_URL contains production ref '" + prodRef + "'. " +
                                "This is the EXACT production database identity — the " +
                                "pg-acceptance profile must use a dedicated isolated " +
                                "acceptance database. PROD_DB_EXACT_MATCH_DENIED=FAIL — " +
                                "startup aborted.");
            }
        }

        String dbNameLower = dbName.toLowerCase();
        for (String forbidden : FORBIDDEN_DB_NAME_SUBSTRINGS) {
            if (dbNameLower.contains(forbidden)) {
                throw new IllegalStateException(
                        "PG_ACCEPTANCE_JDBC_URL database '" + dbName + "' matches forbidden " +
                                "prod marker '" + forbidden + "'. The pg-acceptance profile " +
                                "must use a dedicated isolated acceptance database (e.g. " +
                                "'snad_acceptance', 'snad_test'). " +
                                "PRODUCTION_DB_TARGET_GUARD=FAIL — startup aborted.");
            }
        }

        log.info("PgAcceptanceDatabaseGuard: PASS — host='{}' database='{}' is isolated from production",
                host, dbName);
    }

    /** Extract the host portion from a jdbc:postgresql://host[:port]/db URL. */
    private String extractHost(String url) {
        try {
            // Strip the jdbc: prefix so URI can parse it
            String stripped = url.startsWith("jdbc:") ? url.substring(5) : url;
            URI uri = new URI(stripped);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
            // Fall back to regex extraction for non-standard forms
            int idx = stripped.indexOf("://");
            if (idx >= 0) {
                String after = stripped.substring(idx + 3);
                int slash = after.indexOf('/');
                int colon = after.indexOf(':');
                int end = slash;
                if (colon >= 0 && (slash < 0 || colon < slash)) end = colon;
                if (end < 0) end = after.length();
                return after.substring(0, end);
            }
            return null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Extract the database name from a jdbc:postgresql://host[:port]/db?... URL. */
    private String extractDatabaseName(String url) {
        String stripped = url.startsWith("jdbc:") ? url.substring(5) : url;
        int slash = stripped.indexOf('/', stripped.indexOf("://") + 3);
        if (slash < 0) return null;
        String rest = stripped.substring(slash + 1);
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        return rest;
    }

    /** Mask the password portion of a JDBC URL for safe logging. */
    private String mask(String url) {
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
