package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.config.BillingProviderModeGuard;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.infrastructure.DisabledBillingPaymentProvider;
import com.sanad.platform.subscription.billing.infrastructure.TestBillingPaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R0C13G04BillingProviderContractTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            DisabledBillingPaymentProvider.class,
                            TestBillingPaymentProvider.class);

    @Test
    void disabledAdapterRefusesEveryProviderOperation() {
        BillingPaymentProvider disabled = new DisabledBillingPaymentProvider();
        UUID tenant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();

        var customer = new BillingPaymentProvider.EnsureCustomerCommand(
                tenant, "customer-key", "customer-idem");
        var intent = new BillingPaymentProvider.CreatePaymentIntentCommand(
                tenant, invoice, "provider-customer", 10_350L, "SAR", "intent-idem");
        var query = new BillingPaymentProvider.QueryPaymentStateQuery(
                tenant, "provider-payment");
        var refund = new BillingPaymentProvider.RefundCommand(
                tenant, "provider-payment", 10_350L, "SAR", "refund-idem");

        assertThatThrownBy(() -> disabled.ensureProviderCustomer(customer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
        assertThatThrownBy(() -> disabled.createPaymentIntent(intent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
        assertThatThrownBy(() -> disabled.queryPaymentState(query))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
        assertThatThrownBy(() -> disabled.requestRefund(refund))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
        assertThatThrownBy(() -> disabled.verifyAndParseEvent(
                "{}".getBytes(StandardCharsets.UTF_8), "anything"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
    }

    @Test
    void testAdapterUsesMinorUnitsAndIdempotentReferences() {
        BillingPaymentProvider provider = new TestBillingPaymentProvider();
        UUID tenant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();

        var customerCommand = new BillingPaymentProvider.EnsureCustomerCommand(
                tenant, "acct-001", "customer-idem");
        var customer1 = provider.ensureProviderCustomer(customerCommand);
        var customer2 = provider.ensureProviderCustomer(customerCommand);
        assertThat(customer2).isEqualTo(customer1);

        var create = new BillingPaymentProvider.CreatePaymentIntentCommand(
                tenant, invoice, customer1.providerCustomerRef(),
                10_350L, "sar", "intent-idem");
        var first = provider.createPaymentIntent(create);
        var replay = provider.createPaymentIntent(create);

        assertThat(replay).isEqualTo(first);
        assertThat(first.amountMinor()).isEqualTo(10_350L);
        assertThat(first.currencyCode()).isEqualTo("SAR");
        assertThat(first.status()).isEqualTo(BillingPaymentProvider.PaymentStatus.PENDING);

        var queried = provider.queryPaymentState(
                new BillingPaymentProvider.QueryPaymentStateQuery(
                        tenant, first.providerPaymentRef()));
        assertThat(queried.amountMinor()).isEqualTo(10_350L);
        assertThat(queried.currencyCode()).isEqualTo("SAR");

        var refund = new BillingPaymentProvider.RefundCommand(
                tenant, first.providerPaymentRef(), 3_500L, "SAR", "refund-idem");
        assertThat(provider.requestRefund(refund))
                .isEqualTo(provider.requestRefund(refund));
    }

    @Test
    void testAdapterFailsClosedOnIdempotencyMismatch() {
        BillingPaymentProvider provider = new TestBillingPaymentProvider();
        UUID tenant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        String customer = provider.ensureProviderCustomer(
                new BillingPaymentProvider.EnsureCustomerCommand(
                        tenant, "acct-002", "customer-idem"))
                .providerCustomerRef();

        provider.createPaymentIntent(
                new BillingPaymentProvider.CreatePaymentIntentCommand(
                        tenant, invoice, customer, 10_000L, "SAR", "same-key"));

        assertThatThrownBy(() -> provider.createPaymentIntent(
                new BillingPaymentProvider.CreatePaymentIntentCommand(
                        tenant, invoice, customer, 10_001L, "SAR", "same-key")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency");
    }

    @Test
    void testAdapterVerifiesSignedEventAndEnvelopeCarriesNoTenantIdentity() throws Exception {
        TestBillingPaymentProvider provider = new TestBillingPaymentProvider();
        byte[] payload = (
                "{\"eventId\":\"evt_test_001\","
                        + "\"eventType\":\"payment.succeeded\","
                        + "\"paymentRef\":\"test_pi_001\","
                        + "\"tenantId\":\"UNTRUSTED-MUST-NOT-BE-RETURNED\"}")
                .getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload));

        var envelope = provider.verifyAndParseEvent(
                payload, "test-sha256=" + digest);

        assertThat(envelope.providerEventId()).isEqualTo("evt_test_001");
        assertThat(envelope.eventType()).isEqualTo("payment.succeeded");
        assertThat(envelope.providerPaymentRef()).isEqualTo("test_pi_001");
        assertThat(envelope.payloadSha256()).isEqualTo(digest);
        assertThat(envelope.toString()).doesNotContain("UNTRUSTED-MUST-NOT-BE-RETURNED");

        assertThatThrownBy(() -> provider.verifyAndParseEvent(payload, "test-sha256=deadbeef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void disabledAndTestBeansRequireExplicitModeProperty() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(BillingPaymentProvider.class));

        contextRunner
                .withPropertyValues("sanad.subscription.billing.provider.mode=DISABLED")
                .run(context -> {
                    assertThat(context).hasSingleBean(BillingPaymentProvider.class);
                    assertThat(context.getBean(BillingPaymentProvider.class))
                            .isInstanceOf(DisabledBillingPaymentProvider.class);
                });

        contextRunner
                .withPropertyValues("sanad.subscription.billing.provider.mode=TEST")
                .run(context -> {
                    assertThat(context).hasSingleBean(BillingPaymentProvider.class);
                    assertThat(context.getBean(BillingPaymentProvider.class))
                            .isInstanceOf(TestBillingPaymentProvider.class);
                });
    }

    @Test
    void providerModeGuardDefaultsDisabledAndRejectsTestInProdAndAllLive() {
        BillingProviderModeGuard guard = new BillingProviderModeGuard();

        MockEnvironment defaultEnvironment = new MockEnvironment();
        guard.postProcessEnvironment(defaultEnvironment, new SpringApplication(Object.class));

        MockEnvironment prodTest = new MockEnvironment()
                .withProperty("sanad.subscription.billing.provider.mode", "TEST");
        prodTest.setActiveProfiles("prod");
        assertThatThrownBy(() ->
                guard.postProcessEnvironment(prodTest, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST")
                .hasMessageContaining("prod");

        MockEnvironment live = new MockEnvironment()
                .withProperty("sanad.subscription.billing.provider.mode", "LIVE");
        assertThatThrownBy(() ->
                guard.postProcessEnvironment(live, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE")
                .hasMessageContaining("not authorized");

        MockEnvironment unknown = new MockEnvironment()
                .withProperty("sanad.subscription.billing.provider.mode", "MAGIC");
        assertThatThrownBy(() ->
                guard.postProcessEnvironment(unknown, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported");
    }
}
