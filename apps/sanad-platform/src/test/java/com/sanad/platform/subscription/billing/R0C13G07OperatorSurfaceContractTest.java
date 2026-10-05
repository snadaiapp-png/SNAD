package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G07 operator-surface contract.
 *
 * <p>Locks the first G07 vertical slice before runtime acceptance: canonical
 * granular capabilities, legacy EXECUTIVE_BILLING compatibility, fail-closed
 * control-plane access-check exposure and capability-specific API guards.</p>
 */
class R0C13G07OperatorSurfaceContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final List<String> G07_CAPABILITIES = List.of(
            "BILLING.READ",
            "BILLING.MANAGE",
            "BILLING.RECONCILE",
            "BILLING.REFUND",
            "BILLING.PROVIDER_ADMIN");

    private static Path canonicalG07Migration() throws Exception {
        Path migrationDirectory = ROOT.resolve("src/main/resources/db/migration");
        try (Stream<Path> migrations = Files.list(migrationDirectory)) {
            List<Path> matches = migrations
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith("__r0c13_g07_operator_capabilities.sql"))
                    .toList();

            assertThat(matches)
                    .as("exactly one canonical R0C13 G07 Flyway migration must exist")
                    .hasSize(1);
            return matches.getFirst();
        }
    }

    @Test
    void migrationSeedsAllGranularCapabilitiesAndPreservesLegacyBillingRoles() throws Exception {
        Path migration = canonicalG07Migration();
        assertThat(migration.getFileName().toString())
                .as("R0C13 G07 migration must use a Flyway versioned filename")
                .matches("V\\d+(?:_\\d+)*__r0c13_g07_operator_capabilities\\.sql");

        String sql = Files.readString(migration);

        for (String capability : G07_CAPABILITIES) {
            assertThat(sql).contains("'" + capability + "'");
        }
        assertThat(sql).contains("legacy_capability.code = 'EXECUTIVE_BILLING'");
        assertThat(sql).contains("INSERT INTO role_capabilities");
        assertThat(sql).contains("INSERT INTO access_scope_grants");
        assertThat(sql).contains("legacy_scope.status = 'ACTIVE'");
        assertThat(sql).contains("legacy_capability.code = 'EXECUTIVE_BILLING'");
        assertThat(sql).contains("existing.role_id IS NOT DISTINCT FROM legacy_scope.role_id");
        assertThat(sql).contains("existing.user_id IS NOT DISTINCT FROM legacy_scope.user_id");
        assertThat(sql).contains("R0C13 G07 canonical PLATFORM_OWNER tenant scope");
        assertThat(sql).contains("R0C13 G07 PLATFORM_OWNER tenant-scope reconciliation failed");
        assertThat(sql).contains("code = 'PLATFORM_OWNER'");
        assertThat(sql).doesNotContain("DELETE FROM role_capabilities");
        assertThat(sql).doesNotContain("DELETE FROM access_scope_grants");
        assertThat(sql).contains("DO $g07_owner$");
        assertThat(sql).contains("$g07_owner$;");
        assertThat(sql).contains("DO $g07_seed$");
        assertThat(sql).contains("$g07_seed$;");
        assertThat(sql).doesNotContain("\nDO $\n");
        assertThat(sql.lines().filter(line -> line.equals("$;")).count()).isZero();
    }

    @Test
    void controlPlaneAccessCheckPublishesTheCanonicalG07Capabilities() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/sanad/platform/subscription/rbac/ControlPlaneAccessService.java"));

        for (String capability : G07_CAPABILITIES) {
            assertThat(source).contains("\"" + capability + "\"");
        }
    }

    @Test
    void operatorApiUsesGranularCapabilityGuardsAndControlPlaneBoundary() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/sanad/platform/subscription/billing/api/BillingOperatorController.java"));

        assertThat(source).contains("@RequireCapability(\"BILLING.READ\")");
        assertThat(source).contains("@RequireCapability(\"BILLING.RECONCILE\")");
        assertThat(source).contains("@RequireCapability(\"BILLING.PROVIDER_ADMIN\")");
        assertThat(source).contains("accessGuard.require(authentication)");
        assertThat(source).contains("SecurityContextUtils.userId(authentication)");
        assertThat(source).doesNotContain("webhookSecret");
        assertThat(source).doesNotContain("providerPaymentRef");
    }
}
