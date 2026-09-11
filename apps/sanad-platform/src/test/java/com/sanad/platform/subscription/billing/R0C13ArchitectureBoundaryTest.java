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
    private static final Path SAAS_ADMIN_SERVICE =
            Path.of("src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java");
    private static final Path BASE_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path BILLING_TEST_PROVIDER =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/infrastructure/TestBillingPaymentProvider.java");
    private static final Path BILLING_MODE_GUARD =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/config/BillingProviderModeGuard.java");
    private static final Path COMMERCE_PAYMENT_PORT =
            Path.of("src/main/java/com/sanad/platform/commerce/domain/PaymentGatewayPort.java");
    private static final Path SPRING_FACTORIES =
            Path.of("src/main/resources/META-INF/spring.factories");
    private static final Path SECURITY_CONFIG =
            Path.of("src/main/java/com/sanad/platform/security/config/SecurityConfig.java");
    private static final Path BILLING_WEBHOOK_SERVICE =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/application/BillingWebhookService.java");
    private static final Path G05_WEBHOOK_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260911_2__r0c13_verified_webhook_resolution.sql");

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
    void billingInvoiceIssuanceMustJoinFinancePortInProductionPath() throws IOException {
        String source = Files.readString(SAAS_ADMIN_SERVICE);
        assertThat(source)
                .contains("SubscriptionFinancePort subscriptionFinancePort")
                .contains("subscriptionFinancePort.ensureInvoice(subscription.tenantId(), invoiceId)");
    }

    @Test
    void r0c13ProviderMustDefaultDisabledAndNeverDefaultTestOrLive() throws IOException {
        String base = Files.readString(BASE_CONFIG);
        String prod = Files.readString(PROD_CONFIG);
        assertThat(base).contains("mode: ${R0C13_PROVIDER_MODE:DISABLED}");
        assertThat(prod).contains("mode: ${R0C13_PROVIDER_MODE:DISABLED}");
        assertThat(base).doesNotContain("R0C13_PROVIDER_MODE:TEST");
        assertThat(prod).doesNotContain("R0C13_PROVIDER_MODE:TEST");
        assertThat(prod).doesNotContain("R0C13_PROVIDER_MODE:LIVE");
    }

    @Test
    void r0c13TestProviderMustBeExplicitAndNonProductionOnly() throws IOException {
        String source = Files.readString(BILLING_TEST_PROVIDER);
        assertThat(source)
                .contains("@Profile(\"!prod\")")
                .contains("havingValue = \"TEST\"")
                .contains("matchIfMissing = false");
    }

    @Test
    void r0c13ProviderModeGuardMustBeRegisteredAsEarlyStartupGuard() throws IOException {
        String factories = Files.readString(SPRING_FACTORIES);
        assertThat(factories)
                .contains("com.sanad.platform.subscription.billing.config.BillingProviderModeGuard");
    }

    @Test
    void r0c13LiveProviderModeMustFailClosedAtStartup() throws IOException {
        String source = Files.readString(BILLING_MODE_GUARD);
        assertThat(source)
                .contains("case \"LIVE\" -> throw new IllegalStateException")
                .contains("Live payment collection requires a separate human activation gate");
    }

    @Test
    void commercePaymentContractMustRemainItsOwnLegacyBoundedContext() throws IOException {
        String source = Files.readString(COMMERCE_PAYMENT_PORT);
        assertThat(source)
                .contains("UUID orderId")
                .contains("BigDecimal amount")
                .doesNotContain("BillingPaymentProvider")
                .doesNotContain("billingInvoiceId");
    }

    @Test
    void webhookIngressMustBeJwtFreeButSignatureGuarded() throws IOException {
        String security = Files.readString(SECURITY_CONFIG);
        String service = Files.readString(BILLING_WEBHOOK_SERVICE);
        assertThat(security)
                .contains("\"/api/v1/billing/provider/webhook\"")
                .contains("permitAll()");
        assertThat(service)
                .contains("provider.verifyAndParseEvent(payload, signatureHeader)")
                .contains("@Transactional");
    }

    @Test
    void webhookTenantResolutionMustUseSelectOnlyVerifiedProviderRlsContext() throws IOException {
        String migration = Files.readString(G05_WEBHOOK_MIGRATION);
        assertThat(migration)
                .contains("FOR SELECT")
                .contains("app.billing_webhook_verified")
                .contains("app.billing_provider")
                .doesNotContain("BYPASSRLS")
                .doesNotContain("DISABLE ROW LEVEL SECURITY");
    }

    @Test
    void webhookPersistenceMustNeverStoreRawPayloadOrCardFields() throws IOException {
        String service = Files.readString(BILLING_WEBHOOK_SERVICE);
        assertThat(service)
                .doesNotContain("raw_payload")
                .doesNotContain("payload_body")
                .doesNotContain("card_number")
                .doesNotContain("\"cvc\"")
                .doesNotContain("\"cvv\"")
                .contains("payloadSha256");
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
