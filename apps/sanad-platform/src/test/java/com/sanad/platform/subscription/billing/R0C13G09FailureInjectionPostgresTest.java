package com.sanad.platform.subscription.billing;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.subscription.billing.application.BillingOutbox;
import com.sanad.platform.subscription.billing.application.BillingWebhookService;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.domain.SubscriptionFinancePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("local")
class R0C13G09FailureInjectionPostgresTest extends R0C13G09PostgresSupport {

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("sanad.subscription.billing.provider.mode", () -> "TEST");
        registry.add("sanad.subscription.billing.provider.test-webhook-secret",
                () -> TEST_WEBHOOK_SECRET);
    }

    @Autowired private BillingWebhookService webhookService;

    @SpyBean private SubscriptionFinancePort financePort;
    @SpyBean private BillingOutbox outbox;
    @SpyBean private PlatformAuditWriter auditWriter;

    @Test
    void financeFailureAfterProviderSuccessDoesNotActivateSubscription() {
        doThrow(new IllegalStateException("G09 injected Finance failure"))
                .when(financePort)
                .recordSettlement(
                        eq(tenantId), eq(billingInvoiceId), eq(paymentAttemptId),
                        anyLong(), anyString());

        assertThatThrownBy(() -> sendEvent(
                "evt_g09_finance_" + compact(UUID.randomUUID()),
                "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("G09 injected Finance failure");

        BillingPaymentProvider.PaymentState providerState = provider.queryPaymentState(
                new BillingPaymentProvider.QueryPaymentStateQuery(
                        tenantId, providerPaymentRef));
        assertThat(providerState.status())
                .isEqualTo(BillingPaymentProvider.PaymentStatus.SUCCEEDED);
        assertLocalBillingRemainsUnpaid();
    }

    @Test
    void outboxFailureRollsBackRequiredLocalMutation() {
        doThrow(new IllegalStateException("G09 injected outbox failure"))
                .when(outbox)
                .emit(
                        eq(tenantId),
                        eq(BillingOutbox.TYPE_PAYMENT_SUCCEEDED),
                        eq(paymentAttemptId),
                        anyString(),
                        anyMap());

        assertThatThrownBy(() -> sendEvent(
                "evt_g09_outbox_" + compact(UUID.randomUUID()),
                "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("G09 injected outbox failure");

        assertLocalBillingRemainsUnpaid();
    }

    @Test
    void auditFailureRollsBackRequiredPrivilegedMutation() {
        doThrow(new IllegalStateException("G09 injected audit failure"))
                .when(auditWriter)
                .writeSuccess(
                        nullable(UUID.class),
                        nullable(UUID.class),
                        eq(tenantId),
                        eq("BILLING.WEBHOOK.ACCEPTED"),
                        eq("BILLING_PROVIDER_EVENT"),
                        anyString(),
                        anyString(),
                        nullable(Object.class),
                        nullable(Object.class),
                        anyString(),
                        any(Instant.class));

        assertThatThrownBy(() -> sendEvent(
                "evt_g09_audit_" + compact(UUID.randomUUID()),
                "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("G09 injected audit failure");

        assertLocalBillingRemainsUnpaid();
        assertThat(auditCount()).isZero();
    }

    @Test
    void providerTimeoutProducesRetryableStateNotPaid() {
        BillingPaymentProvider providerSpy = provider;
        doThrow(new IllegalStateException("G09 injected provider timeout"))
                .when(providerSpy)
                .queryPaymentState(any(BillingPaymentProvider.QueryPaymentStateQuery.class));

        String eventId = "evt_g09_timeout_" + compact(UUID.randomUUID());
        assertThatThrownBy(() -> sendEvent(eventId, "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("G09 injected provider timeout");

        assertLocalBillingRemainsUnpaid();
        assertThat(providerEventCount(eventId)).isZero();
    }

    @Test
    void unauthorizedRefundHasZeroProviderOrDomainSideEffect() {
        String eventId = "evt_g09_refund_" + compact(UUID.randomUUID());
        byte[] body = payload(eventId, "payment.refunded", providerPaymentRef);

        assertThatThrownBy(() ->
                webhookService.receive(body, "invalid-signature"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(financePort, never()).recordRefund(
                any(UUID.class), any(UUID.class), any(UUID.class),
                anyLong(), anyString());

        assertLocalBillingRemainsUnpaid();
        assertThat(providerEventCount(eventId)).isZero();
    }

    private void sendEvent(String eventId, String eventType) {
        byte[] body = payload(eventId, eventType, providerPaymentRef);
        webhookService.receive(body, sign(body));
    }
}
