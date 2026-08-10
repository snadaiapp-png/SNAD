package com.sanad.platform.crm.integration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Centralised PostgreSQL Direct availability policy for CRM-009 PostgreSQL tests.
 *
 * <p>Policy (PostgreSQL Direct — Docker/Testcontainers OUT_OF_SCOPE):</p>
 * <ul>
 *   <li><strong>Local development + PostgreSQL unavailable:</strong> tests skip
 *       gracefully with explicit reason (via JUnit {@code Assumptions}).</li>
 *   <li><strong>CI environment ({@code CI=true} or
 *       {@code CRM_009_POSTGRES_MANDATORY=true}) + PostgreSQL unavailable:</strong>
 *       tests <strong>FAIL</strong> by throwing {@link IllegalStateException}.
 *       Skipping is forbidden in CI because PostgreSQL acceptance is the
 *       authoritative gate for CRM-009 items 1-15.</li>
 *   <li><strong>Any environment + PostgreSQL available:</strong> tests execute.</li>
 * </ul>
 *
 * <p>This utility centralises the policy so individual test classes do not
 * re-implement the CI detection logic. It is invoked from each PostgreSQL
 * test class's {@code @BeforeAll} setup.</p>
 *
 * <p>Docker/Testcontainers are OUT_OF_SCOPE and DEPRECATED per PostgreSQL
 * Direct governance mandate.</p>
 */
public final class Crm009TestEnvironment {

    private Crm009TestEnvironment() {}

    /**
     * Verify that PostgreSQL Direct is available, enforcing the no-skip policy in CI.
     *
     * @param testClassName the calling test class name (for error messages)
     * @return {@code true} if PostgreSQL Direct is available and tests should proceed
     * @throws IllegalStateException if CI environment + PostgreSQL unavailable
     */
    public static boolean requirePostgreSqlDirectOrSkip(String testClassName) {
        boolean postgresAvailable;
        try {
            postgresAvailable = checkPostgreSqlDirectConnectivity();
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }

        if (postgresAvailable) {
            return true;
        }

        // PostgreSQL unavailable — check if we're in CI
        boolean isCi = isCiEnvironment();
        if (isCi) {
            throw new IllegalStateException(
                    "PostgreSQL Direct (localhost:5432) is MANDATORY for CRM-009 CI acceptance. "
                    + "Test class " + testClassName + " cannot be skipped in CI. "
                    + "Local development may skip these tests, but CI must execute them. "
                    + "Ensure PostgreSQL 17 is running on localhost:5432 with the 'sanad' database.");
        }
        // Local dev — skip gracefully
        return false;
    }

    /**
     * Check PostgreSQL Direct connectivity via JDBC.
     */
    private static boolean checkPostgreSqlDirectConnectivity() {
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/sanad");
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detect CI environment via standard env variables.
     */
    public static boolean isCiEnvironment() {
        return Boolean.parseBoolean(System.getenv("CI"))
                || Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS"))
                || Boolean.parseBoolean(System.getenv("CRM_009_POSTGRES_MANDATORY"))
                || "true".equalsIgnoreCase(System.getenv("JENKINS_HOME"))
                || System.getenv("GITLAB_CI") != null
                || System.getenv("CIRCLECI") != null
                || System.getenv("BUILD_NUMBER") != null;
    }
}
