package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G08 compatibility and full-regression guardrails.
 *
 * <p>This test is intentionally test-only. It pins the cross-module contracts that
 * must remain true while the authoritative CI jobs execute the full Maven,
 * PostgreSQL Direct, and web regression suites on the same exact SHA.</p>
 */
class R0C13G08CompatibilityRegressionTest {

    private static final Path REPOSITORY_CI =
            Path.of("../../.github/workflows/ci.yml");
    private static final Path FINANCE_REGRESSION =
            Path.of("src/test/java/com/sanad/platform/finance/FinanceModuleIntegrationTest.java");
    private static final Path COMMERCE_SIMULATED =
            Path.of("src/main/java/com/sanad/platform/commerce/application/SimulatedPaymentAdapter.java");
    private static final Path COMMERCE_PAYMENT_PORT =
            Path.of("src/main/java/com/sanad/platform/commerce/domain/PaymentGatewayPort.java");
    private static final Path PROD_CONFIG =
            Path.of("src/main/resources/application-prod.yml");
    private static final Path BILLING_ROOT =
            Path.of("src/main/java/com/sanad/platform/subscription/billing");

    @Test
    void r0c12CanonicalRegressionAndSkipClassificationContractMustRemainFailClosed() throws IOException {
        String ci = Files.readString(REPOSITORY_CI);

        assertThat(ci)
                .contains("name: R0C-12 Canonical Gate G")
                .contains("if: github.event_name == 'workflow_dispatch'")
                .contains("UNEXPLAINED_SKIPS")
                .contains("CommerceOrderPostgresConcurrencyTest")
                .contains("RbacAccessCheckPostgresAcceptanceTest")
                .contains("ModuleRegistryUatPostgresAcceptanceTest")
                .contains("FinanceModuleIntegrationTest")
                .contains("GATE_G_STATUS=PASS");
    }

    @Test
    void financeRegressionSuiteMustRemainMavenDiscoverable() throws IOException {
        assertThat(Files.isRegularFile(FINANCE_REGRESSION)).isTrue();

        String source = Files.readString(FINANCE_REGRESSION);
        assertThat(source)
                .contains("class FinanceModuleIntegrationTest")
                .contains("@Test");
    }

    @Test
    void commerceProductionSafetyMustRemainExplicitAndNonDefault() throws IOException {
        String simulated = Files.readString(COMMERCE_SIMULATED);
        String prod = Files.readString(PROD_CONFIG);

        assertThat(simulated)
                .contains("havingValue = \"simulated\"")
                .contains("matchIfMissing = false");

        assertThat(prod)
                .contains("provider: ${SANAD_COMMERCE_PAYMENT_PROVIDER:}")
                .doesNotContain("provider: simulated");
    }

    @Test
    void saasBillingMustRemainSeparatedFromCommercePaymentGatewayPort() throws IOException {
        String commercePort = Files.readString(COMMERCE_PAYMENT_PORT);
        assertThat(commercePort)
                .contains("UUID orderId")
                .doesNotContain("BillingPaymentProvider");

        try (Stream<Path> files = Files.walk(BILLING_ROOT)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                assertThat(Files.readString(path))
                        .as("R0C13 SaaS billing must not couple to Commerce PaymentGatewayPort: %s", path)
                        .doesNotContain("com.sanad.platform.commerce.domain.PaymentGatewayPort");
            }
        }
    }
}
