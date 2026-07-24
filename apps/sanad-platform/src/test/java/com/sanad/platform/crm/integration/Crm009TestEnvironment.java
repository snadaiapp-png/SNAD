package com.sanad.platform.crm.integration;

import org.testcontainers.DockerClientFactory;

/**
 * Centralised Docker availability policy for CRM-009 PostgreSQL tests.
 *
 * <p>Policy:</p>
 * <ul>
 *   <li><strong>Local development + Docker unavailable:</strong> tests skip
 *       gracefully with explicit reason (via JUnit {@code Assumptions}).</li>
 *   <li><strong>CI environment ({@code CI=true} or
 *       {@code CRM_009_POSTGRES_MANDATORY=true}) + Docker unavailable:</strong>
 *       tests <strong>FAIL</strong> by throwing {@link IllegalStateException}.
 *       Skipping is forbidden in CI because PostgreSQL acceptance is the
 *       authoritative gate for CRM-009 items 1-15.</li>
 *   <li><strong>Any environment + Docker available:</strong> tests execute.</li>
 * </ul>
 *
 * <p>This utility centralises the policy so individual test classes do not
 * re-implement the CI detection logic. It is invoked from each PostgreSQL
 * test class's {@code @BeforeAll} setup.</p>
 */
public final class Crm009TestEnvironment {

    private Crm009TestEnvironment() {}

    /**
     * Verify that Docker is available, enforcing the no-skip policy in CI.
     *
     * @param testClassName the calling test class name (for error messages)
     * @return {@code true} if Docker is available and tests should proceed
     * @throws IllegalStateException if CI environment + Docker unavailable
     */
    public static boolean requireDockerOrSkip(String testClassName) {
        boolean docker;
        try {
            docker = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            docker = false;
        }

        if (docker) {
            return true;
        }

        // Docker unavailable — check if we're in CI
        boolean isCi = isCiEnvironment();
        if (isCi) {
            throw new IllegalStateException(
                    "Docker/PostgreSQL is MANDATORY for CRM-009 CI acceptance. "
                    + "Test class " + testClassName + " cannot be skipped in CI. "
                    + "Local development may skip these tests, but CI must execute them. "
                    + "Ensure the runner has Docker installed and the Docker socket is accessible.");
        }
        // Local dev — skip gracefully
        return false;
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
