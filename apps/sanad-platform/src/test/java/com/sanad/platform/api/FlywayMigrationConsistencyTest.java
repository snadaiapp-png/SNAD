package com.sanad.platform.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §8 — Verifies Flyway migration source-level consistency
 * without requiring a running database.
 *
 * <p>This test runs as part of the standard Maven test phase (no
 * {@code RUN_TENANT_POSTGRES_TESTS} guard) and catches the following
 * regressions:</p>
 * <ul>
 *   <li><b>Duplicate V15</b> — both a Java V15 and a SQL V15 exist.</li>
 *   <li><b>Duplicate versioned migrations</b> — the same version appears
 *       in both {@code db/migration} and {@code db/migration-pg-only}.</li>
 *   <li><b>Duplicate versions within a directory</b> — two files in the
 *       same directory claim the same version number.</li>
 *   <li><b>V15 is Java</b> — V15 must be a Java migration
 *       ({@code V15__seed_admin_role_and_capabilities.sql}), not SQL.
 *       This matches the production database's
 *       {@code flyway_schema_history} record (type=SQL,
 *       description=seed admin role and capabilities).</li>
 *   <li><b>SQL V15 is absent</b> — the removed
 *       {@code V15__seed_admin_role_and_capabilities.sql} must NOT
 *       reappear in the source tree.</li>
 *   <li><b>Reconciler V20260702_1 exists</b> — the reconciler migration
 *       that replaced SQL V15's ADMIN role seeding must be present.</li>
 * </ul>
 *
 * <p>If any of these checks fail, the CI gate blocks the merge —
 * preventing a repeat of the "Detected applied migration not resolved
 * locally: 15" incident.</p>
 */
class FlywayMigrationConsistencyTest {

    private static final Path MIGRATION_DIR = Paths.get(
            "src/main/resources/db/migration");
    private static final Path PG_ONLY_DIR = Paths.get(
            "src/main/resources/db/migration-pg-only");
    private static final Path JAVA_MIGRATION_DIR = Paths.get(
            "src/main/java/com/sanad/platform/config/migration");

    private static final Pattern SQL_VERSION_PATTERN =
            Pattern.compile("^V(.+?)__.+\\.sql$");
    private static final Pattern JAVA_VERSION_PATTERN =
            Pattern.compile("^V(.+?)__.+\\.java$");

    @Test
    @DisplayName("noDuplicateV15: exactly one V15 migration (Java, matching production)")
    void noDuplicateV15() throws IOException {
        List<String> sqlV15 = listFiles(MIGRATION_DIR, "V15__.*\\.sql");
        sqlV15.addAll(listFiles(PG_ONLY_DIR, "V15__.*\\.sql"));
        List<String> javaV15 = listFiles(JAVA_MIGRATION_DIR, "V15__.*\\.java");

        // V15 must be Java (matching production). SQL V15 must NOT exist.
        assertThat(sqlV15)
                .as("SQL V15 must NOT exist (production has Java V15)")
                .isEmpty();

        assertThat(javaV15)
                .as("Java V15 must exist (matching production)")
                .hasSize(1);

        assertThat(javaV15.get(0))
                .as("Java V15 must be the production seed_rbac_roles_and_capabilities")
                .contains("V15__seed_rbac_roles_and_capabilities.java");
    }

    @Test
    @DisplayName("reconcilerV20260702_2Exists: reconciler migration present")
    void reconcilerV20260702_2Exists() throws IOException {
        List<String> reconciler = listFiles(MIGRATION_DIR,
                "V20260702_2__reconcile_admin_role_and_capabilities.sql");
        assertThat(reconciler)
                .as("V20260702_2 reconciler migration must exist in db/migration/")
                .hasSize(1);
    }

    @Test
    @DisplayName("noCrossDirectoryDuplicateVersions: no version exists in both migration dirs")
    void noCrossDirectoryDuplicateVersions() throws IOException {
        Set<String> sqlVersions = extractVersions(MIGRATION_DIR, SQL_VERSION_PATTERN);
        Set<String> pgOnlyVersions = extractVersions(PG_ONLY_DIR, SQL_VERSION_PATTERN);

        Set<String> intersection = new HashSet<>(sqlVersions);
        intersection.retainAll(pgOnlyVersions);

        assertThat(intersection)
                .as("No versioned migration may exist in BOTH db/migration and "
                        + "db/migration-pg-only (would cause Flyway conflict)")
                .isEmpty();
    }

    @Test
    @DisplayName("noDuplicateVersionsWithinDirectory: no duplicate versions in the same directory")
    void noDuplicateVersionsWithinDirectory() throws IOException {
        checkNoDuplicatesIn(MIGRATION_DIR, SQL_VERSION_PATTERN);
        checkNoDuplicatesIn(PG_ONLY_DIR, SQL_VERSION_PATTERN);
        checkNoDuplicatesIn(JAVA_MIGRATION_DIR, JAVA_VERSION_PATTERN);
    }

    @Test
    @DisplayName("noDuplicateVersionsGlobally: no version appears more than once across ALL directories")
    void noDuplicateVersionsGlobally() throws IOException {
        // Stage 05A.2.9.1 §4 — Global duplicate version detection.
        // Collect ALL versions from ALL migration directories (SQL + Java)
        // and assert no version appears more than once. This catches the
        // V20260702_1 conflict between CRM and reconciler that would
        // otherwise cause Flyway to fail at runtime.
        Set<String> allVersions = new HashSet<>();
        Set<String> duplicates = new java.util.TreeSet<>();

        collectVersions(MIGRATION_DIR, SQL_VERSION_PATTERN, allVersions, duplicates);
        collectVersions(PG_ONLY_DIR, SQL_VERSION_PATTERN, allVersions, duplicates);
        collectVersions(JAVA_MIGRATION_DIR, JAVA_VERSION_PATTERN, allVersions, duplicates);

        assertThat(duplicates)
                .as("No migration version may appear more than once across ALL directories. "
                        + "Duplicates found: %s", duplicates)
                .isEmpty();
    }

    private void collectVersions(Path dir, Pattern pattern, Set<String> seen, Set<String> duplicates)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .forEach(name -> {
                        Matcher m = pattern.matcher(name);
                        if (m.matches()) {
                            String version = m.group(1);
                            if (!seen.add(version)) {
                                duplicates.add(version);
                            }
                        }
                    });
        }
    }

    @Test
    @DisplayName("v15_javaMigrationExists: Java V15 exists in config/migration (matching production)")
    void v15_javaMigrationExists() throws IOException {
        List<String> javaV15 = listFiles(JAVA_MIGRATION_DIR,
                "V15__seed_rbac_roles_and_capabilities.java");
        assertThat(javaV15)
                .as("Java V15 must exist in config/migration/ (matching production)")
                .hasSize(1);
    }

    @Test
    @DisplayName("reconcilerMigrationIsIdempotent: uses WHERE NOT EXISTS")
    void reconcilerMigrationIsIdempotent() throws IOException {
        List<String> reconciler = listFiles(MIGRATION_DIR,
                "V20260702_2__reconcile_admin_role_and_capabilities.sql");
        assertThat(reconciler).hasSize(1);

        String content = Files.readString(MIGRATION_DIR.resolve(
                "V20260702_2__reconcile_admin_role_and_capabilities.sql"));
        assertThat(content)
                .as("Reconciler must be idempotent (use WHERE NOT EXISTS)")
                .contains("WHERE NOT EXISTS");
        assertThat(content)
                .as("Reconciler must include tenant_id in role_capabilities INSERT "
                        + "(V8 schema requires NOT NULL tenant_id)")
                .contains("tenant_id");
        assertThat(content)
                .as("Reconciler must be forward-only (INSERT only, no UPDATE/DELETE)")
                .doesNotContain("DELETE FROM", "DROP TABLE", "DROP POLICY", "TRUNCATE");
    }

    // === Helpers ===

    private List<String> listFiles(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches(glob))
                    .forEach(result::add);
        }
        return result;
    }

    private Set<String> extractVersions(Path dir, Pattern pattern) throws IOException {
        Set<String> versions = new HashSet<>();
        if (!Files.isDirectory(dir)) {
            return versions;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .forEach(name -> {
                        Matcher m = pattern.matcher(name);
                        if (m.matches()) {
                            versions.add(m.group(1));
                        }
                    });
        }
        return versions;
    }

    private void checkNoDuplicatesIn(Path dir, Pattern pattern) throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .forEach(name -> {
                        Matcher m = pattern.matcher(name);
                        if (m.matches()) {
                            String version = m.group(1);
                            if (!seen.add(version)) {
                                duplicates.add(version);
                            }
                        }
                    });
        }
        assertThat(duplicates)
                .as("Duplicate migration versions in %s: %s", dir, duplicates)
                .isEmpty();
    }
}
