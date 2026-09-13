package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G07 RBAC contract — RED first.
 *
 * <p>The first G07 slice is additive capability governance only. It must seed
 * the approved BILLING.* capabilities, preserve EXECUTIVE_BILLING callers by
 * mirroring existing legacy grants, and must not broaden EXECUTIVE_VIEW into
 * billing mutation authority.</p>
 */
class R0C13G07RbacContractsTest {

    private static final Path G07_RBAC_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260913_1__r0c13_g07_billing_rbac.sql");

    @Test
    void g07MustDefineAllApprovedGranularBillingCapabilities() throws IOException {
        assertThat(G07_RBAC_MIGRATION)
                .as("R13-G07 RBAC migration must exist before the capability gate can turn GREEN")
                .exists();

        String migration = Files.readString(G07_RBAC_MIGRATION);
        assertThat(migration)
                .contains("'BILLING.READ'")
                .contains("'BILLING.MANAGE'")
                .contains("'BILLING.RECONCILE'")
                .contains("'BILLING.REFUND'")
                .contains("'BILLING.PROVIDER_ADMIN'");
    }

    @Test
    void legacyExecutiveBillingGrantMustRemainBackwardCompatible() throws IOException {
        assertThat(G07_RBAC_MIGRATION).exists();

        String migration = Files.readString(G07_RBAC_MIGRATION);
        assertThat(migration)
                .contains("EXECUTIVE_BILLING")
                .contains("role_capabilities")
                .contains("BILLING.MANAGE")
                .contains("BILLING.RECONCILE")
                .contains("BILLING.REFUND")
                .contains("BILLING.PROVIDER_ADMIN");
    }

    @Test
    void g07RbacMigrationMustBeAdditiveAndMustNotBroadenExecutiveView() throws IOException {
        assertThat(G07_RBAC_MIGRATION).exists();

        String normalized = Files.readString(G07_RBAC_MIGRATION).toUpperCase(java.util.Locale.ROOT);
        assertThat(normalized)
                .doesNotContain("DELETE FROM ACCESS_CAPABILITIES")
                .doesNotContain("DELETE FROM ROLE_CAPABILITIES")
                .doesNotContain("UPDATE ACCESS_CAPABILITIES SET CODE")
                .doesNotContain("EXECUTIVE_VIEW");
    }
}
