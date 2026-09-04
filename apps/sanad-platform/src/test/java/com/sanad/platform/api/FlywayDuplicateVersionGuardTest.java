package com.sanad.platform.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prevents duplicate Flyway version numbers across all migration files.
 *
 * <p>This guard catches the class of error where two workstreams independently
 * create {@code V20260830_1__different_names.sql} — a scenario that causes
 * Flyway to fail at startup with "Found more than one migration with version
 * X". It scans the canonical migration directory and asserts that every
 * version number is globally unique regardless of the description suffix.</p>
 */
class FlywayDuplicateVersionGuardTest {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^V(\\d+(?:_\\d+)*)__", Pattern.CASE_INSENSITIVE);

    @Test
    void noDuplicateFlywayVersionsExist() throws IOException {
        Path migrationDir = resolveMigrationDirectory();
        assertThat(migrationDir).as("Migration directory must exist").isNotNull();

        Map<String, List<String>> versionToFiles = new HashMap<>();

        try (Stream<Path> files = Files.list(migrationDir)) {
            files.filter(p -> p.getFileName().toString().startsWith("V")
                            && p.getFileName().toString().endsWith(".sql"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        Matcher matcher = VERSION_PATTERN.matcher(filename);
                        if (matcher.find()) {
                            String version = normalizeVersion(matcher.group(1));
                            versionToFiles.computeIfAbsent(version, k -> new ArrayList<>())
                                    .add(filename);
                        }
                    });
        }

        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : versionToFiles.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.add("Version " + entry.getKey() + " claimed by: "
                        + entry.getValue());
            }
        }

        assertThat(duplicates)
                .as("Every Flyway version must be claimed by exactly one migration file. "
                        + "Duplicates cause Flyway startup failure with "
                        + "'Found more than one migration with version X'.")
                .isEmpty();
    }

    /**
     * Normalizes version strings so that {@code 20260830_1} and
     * {@code 20260830.1} map to the same key (Flyway treats them as
     * equivalent dot-separated versions).
     */
    private String normalizeVersion(String raw) {
        return raw.replace('_', '.');
    }

    private Path resolveMigrationDirectory() throws IOException {
        // Walk up from the working directory to find the migration directory.
        Path cwd = Paths.get("").toAbsolutePath();
        while (cwd != null) {
            Path candidate = cwd.resolve(
                    "apps/sanad-platform/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            candidate = cwd.resolve("src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cwd = cwd.getParent();
        }
        return null;
    }
}
