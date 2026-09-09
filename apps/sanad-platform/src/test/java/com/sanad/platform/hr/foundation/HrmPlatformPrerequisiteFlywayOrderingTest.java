package com.sanad.platform.hr.foundation;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the 2026-09-08 production Flyway incident.
 *
 * <p>The HRM platform prerequisite migration first reached {@code main} after
 * production had already advanced to {@code 20260904.1}. Keeping it at the
 * historical version {@code 20260827.1} makes Flyway fail validation with
 * {@code outOfOrder=false}. The executable prerequisite must therefore be a
 * forward migration that still sorts before the dependent HRM wave beginning
 * at {@code 20260905.1}.</p>
 */
class HrmPlatformPrerequisiteFlywayOrderingTest {

    private static final String LATE_HISTORICAL_RESOURCE =
            "db/migration/V20260827_1__create_hrm_platform_country_and_employer_prerequisites.sql";
    private static final String FORWARD_RECONCILIATION_RESOURCE =
            "db/migration/V20260904_2__reconcile_hrm_platform_country_and_employer_prerequisites.sql";

    @Test
    void prerequisiteIsForwardOfProductionLedgerAndBeforeDependentHrmWave() throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        assertThat(loader.getResource(LATE_HISTORICAL_RESOURCE))
                .as("late historical migration must not remain executable")
                .isNull();

        try (InputStream stream = loader.getResourceAsStream(FORWARD_RECONCILIATION_RESOURCE)) {
            assertThat(stream)
                    .as("forward HRM prerequisite reconciliation migration")
                    .isNotNull();

            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("platform_countries")
                    .contains("legal_entities")
                    .contains("organization_legal_entities")
                    .contains("work_locations")
                    .contains("btree_gist")
                    .contains("FORCE ROW LEVEL SECURITY");
        }

        MigrationVersion productionHead = MigrationVersion.fromVersion("20260904.1");
        MigrationVersion prerequisite = MigrationVersion.fromVersion("20260904.2");
        MigrationVersion dependentHrmWave = MigrationVersion.fromVersion("20260905.1");

        assertThat(prerequisite.compareTo(productionHead)).isPositive();
        assertThat(prerequisite.compareTo(dependentHrmWave)).isNegative();
    }
}
