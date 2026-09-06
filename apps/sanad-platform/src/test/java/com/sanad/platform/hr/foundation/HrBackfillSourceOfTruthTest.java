package com.sanad.platform.hr.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prevents the reviewed Task 6 scripts from drifting away from the final
 * repeatable Flyway deployment unit. The repeatable migration is a new,
 * forward repository change (old versioned migrations remain immutable),
 * is tracked by Flyway history, and executes after the versioned migrations.
 */
class HrBackfillSourceOfTruthTest {

    private static final String FINAL_MIGRATION =
            "apps/sanad-platform/src/main/resources/db/migration/" +
            "R__finalize_hr_backfill_closure.sql";

    @Test
    void authoritativeScriptsAreEmbeddedAndChecksummedByFinalDeploymentMigration() throws Exception {
        Path root = findRepositoryRoot();
        Path migrationPath = root.resolve(FINAL_MIGRATION);

        assertThat(migrationPath)
                .as("Task 6 final repeatable Flyway closure migration must exist")
                .exists();

        String migration = Files.readString(migrationPath, StandardCharsets.UTF_8);
        List<String> scripts = List.of(
                "scripts/hrm/g0-backfill-precheck.sql",
                "scripts/hrm/g0-backfill.sql",
                "scripts/hrm/g0-reconcile.sql"
        );

        for (String relative : scripts) {
            Path script = root.resolve(relative);
            assertThat(script).as("Authoritative Task 6 script must exist: %s", relative).exists();
            String scriptText = Files.readString(script, StandardCharsets.UTF_8);
            String hash = sha256(script);
            String fileName = script.getFileName().toString();

            assertThat(scriptText)
                    .as("Authoritative Task 6 business logic must not depend on CURRENT_DATE: %s", fileName)
                    .doesNotContain("CURRENT_DATE");

            assertThat(migration)
                    .as("Final migration must pin SHA-256 of authoritative script %s", fileName)
                    .contains("-- SOURCE_SHA256 " + fileName + " " + hash)
                    .as("Final migration must embed the exact authoritative script %s", fileName)
                    .contains(scriptText);
        }
    }

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++, current = current.getParent()) {
            if (Files.exists(current.resolve("scripts/hrm/g0-backfill.sql")) &&
                    Files.exists(current.resolve("apps/sanad-platform/pom.xml"))) {
                return current;
            }
        }
        throw new AssertionError("Unable to locate repository root from user.dir=" + System.getProperty("user.dir"));
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
