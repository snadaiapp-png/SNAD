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
 * forward-only migration that installs the same logic.
 */
class HrBackfillSourceOfTruthTest {

    private static final String FINAL_MIGRATION =
            "apps/sanad-platform/src/main/resources/db/migration/" +
            "V20260902_1__finalize_hr_backfill_closure.sql";

    @Test
    void authoritativeScriptsAreChecksummedByFinalDeploymentMigration() throws Exception {
        Path root = findRepositoryRoot();
        Path migrationPath = root.resolve(FINAL_MIGRATION);

        assertThat(migrationPath)
                .as("Task 6 final forward-only closure migration must exist")
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
            String hash = sha256(script);
            String fileName = script.getFileName().toString();
            assertThat(migration)
                    .as("Final migration must pin SHA-256 of authoritative script %s", fileName)
                    .contains("-- SOURCE_SHA256 " + fileName + " " + hash);
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
