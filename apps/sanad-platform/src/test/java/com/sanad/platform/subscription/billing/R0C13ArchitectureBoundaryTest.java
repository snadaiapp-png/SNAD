package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G01 architecture guardrails.
 *
 * <p>These tests are intentionally behavioral-no-op guards: they constrain the
 * future R0C13 production package without implementing it.</p>
 */
class R0C13ArchitectureBoundaryTest {

    private static final Path BILLING_ROOT =
            Path.of("src/main/java/com/sanad/platform/subscription/billing");
    private static final Path COMMERCE_SIMULATED =
            Path.of("src/main/java/com/sanad/platform/commerce/application/SimulatedPaymentAdapter.java");
    private static final Path PROD_CONFIG = Path.of("src/main/resources/application-prod.yml");
    private static final Path FINANCE_ADAPTER =
            Path.of("src/main/java/com/sanad/platform/finance/integration/SubscriptionFinanceAdapter.java");

    private static final Pattern DIRECT_FINANCE_SQL = Pattern.compile(
            "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+"
                    + "finance_(?:invoices|payments|journal_entries|journal_lines)\\b");

    private static final Pattern DIRECT_SUBSCRIPTION_STATE_SQL = Pattern.compile(
            "(?is)\\bupdate\\s+tenant_subscriptions\\s+set\\s+"
                    + "(?:status|billing_state)\\b");

    @Test
    void r0c13BillingMustNotWriteFinanceTablesDirectly() throws IOException {
        for (SourceFile source : billingProductionSources()) {
            assertThat(DIRECT_FINANCE_SQL.matcher(source.content()).find())
                    .as("R0C13 billing source must use the Finance integration port, not direct Finance SQL: %s",
                            source.path())
                    .isFalse();
        }
    }

    @Test
    void r0c13BillingMustNotWriteSubscriptionLifecycleOrBillingStateDirectly() throws IOException {
        for (SourceFile source : billingProductionSources()) {
            assertThat(DIRECT_SUBSCRIPTION_STATE_SQL.matcher(source.content()).find())
                    .as("R0C13 billing source must converge through SubscriptionCommandService/BillingStateService: %s",
                            source.path())
                    .isFalse();
        }
    }

    @Test
    void r0c13BillingMustNotReuseCommercePaymentGatewayPort() throws IOException {
        for (SourceFile source : billingProductionSources()) {
            assertThat(source.content())
                    .as("SaaS billing must use its own provider-neutral contract: %s", source.path())
                    .doesNotContain("com.sanad.platform.commerce.domain.PaymentGatewayPort");
        }
    }

    @Test
    void providerDomainMustNotUseBigDecimalForChargeOrRefundMoney() throws IOException {
        if (!Files.isDirectory(BILLING_ROOT)) {
            return;
        }
        try (Stream<Path> files = Files.walk(BILLING_ROOT)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.contains("provider") || n.contains("payment");
                    })
                    .toList()) {
                String source = Files.readString(path);
                assertThat(source)
                        .as("Provider payment boundary must use integer minor units, not BigDecimal: %s", path)
                        .doesNotContain("java.math.BigDecimal");
            }
        }
    }

    @Test
    void r0c13FinanceAdapterMustNotWriteJournalOrLedgerTables() throws IOException {
        String source = Files.readString(FINANCE_ADAPTER);
        assertThat(source)
                .doesNotContain("finance_journal_entries")
                .doesNotContain("finance_journal_lines")
                .doesNotContain("INSERT INTO finance_accounts");
    }

    @Test
    void commerceSimulatedProviderMustRemainExplicitOnly() throws IOException {
        String source = Files.readString(COMMERCE_SIMULATED);
        assertThat(source).contains("havingValue = \"simulated\"");
        assertThat(source).contains("matchIfMissing = false");
    }

    @Test
    void productionCommerceProviderMustNotDefaultToSimulated() throws IOException {
        String prod = Files.readString(PROD_CONFIG);
        assertThat(prod)
                .contains("provider: ${SANAD_COMMERCE_PAYMENT_PROVIDER:}")
                .doesNotContain("provider: simulated");
    }

    private static Iterable<SourceFile> billingProductionSources() throws IOException {
        if (!Files.isDirectory(BILLING_ROOT)) {
            return java.util.List.of();
        }
        try (Stream<Path> files = Files.walk(BILLING_ROOT)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return new SourceFile(path, Files.readString(path));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private record SourceFile(Path path, String content) {}
}
