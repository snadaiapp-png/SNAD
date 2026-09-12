package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G06 RED contracts.
 *
 * <p>These source-level contracts define the required settlement/reconciliation
 * convergence before production implementation exists. The RED commit is kept
 * in lineage; the GREEN implementation must satisfy these without introducing
 * direct subscription lifecycle writers or direct Finance table writes from
 * the billing bounded context.</p>
 */
class R0C13G06RequiredContractsRedTest {

    private static final Path SETTLEMENT =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/application/BillingSettlementService.java");
    private static final Path RECONCILIATION =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/application/BillingReconciliationService.java");
    private static final Path WEBHOOK =
            Path.of("src/main/java/com/sanad/platform/subscription/billing/application/BillingWebhookService.java");

    @Test
    void settlementAndReconciliationAuthoritiesMustExist() {
        assertThat(Files.isRegularFile(SETTLEMENT))
                .as("G06 RED: settlement authority is absent")
                .isTrue();
        assertThat(Files.isRegularFile(RECONCILIATION))
                .as("G06 RED: reconciliation authority is absent")
                .isTrue();
    }

    @Test
    void webhookMustDelegateVerifiedProviderEventsToSettlementAuthority() throws Exception {
        String source = Files.readString(WEBHOOK);
        assertThat(source)
                .contains("BillingSettlementService")
                .contains("handleVerifiedProviderEvent");
    }

    @Test
    void settlementMustConvergeFinanceBeforeCanonicalBillingRecovery() throws Exception {
        assertThat(Files.isRegularFile(SETTLEMENT)).isTrue();
        String source = Files.readString(SETTLEMENT);
        assertThat(source)
                .contains("recordSettlement(")
                .contains("billing_invoices")
                .contains("evaluateAndTransition(")
                .doesNotContain("UPDATE tenant_subscriptions")
                .doesNotContain("finance_payments")
                .doesNotContain("finance_invoices");
    }

    @Test
    void reconciliationMustBeReadOnlyOutsideItsEvidenceTables() throws Exception {
        assertThat(Files.isRegularFile(RECONCILIATION)).isTrue();
        String source = Files.readString(RECONCILIATION);
        assertThat(source)
                .contains("subscription_billing_reconciliation_runs")
                .contains("subscription_billing_reconciliation_items")
                .doesNotContain("UPDATE billing_invoices")
                .doesNotContain("UPDATE tenant_subscriptions")
                .doesNotContain("UPDATE finance_")
                .doesNotContain("INSERT INTO finance_");
    }
}
