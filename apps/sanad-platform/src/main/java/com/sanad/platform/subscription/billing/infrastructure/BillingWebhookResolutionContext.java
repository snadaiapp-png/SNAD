package com.sanad.platform.subscription.billing.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Short-lived verified-provider RLS context used only between successful
 * signature verification and trusted payment-binding resolution.
 *
 * <p>This is not an authorization mechanism. Callers must cryptographically
 * verify the provider envelope first. The context is cleared immediately after
 * the stored binding has yielded the authoritative tenant id.</p>
 */
@Component
public class BillingWebhookResolutionContext {

    private final JdbcTemplate jdbc;

    public BillingWebhookResolutionContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void applyVerifiedProviderForCurrentTransaction(String providerCode) {
        requireTransaction();
        String provider = normalizeProvider(providerCode);
        jdbc.queryForObject(
                "SELECT set_config('app.billing_webhook_verified', 'true', true)",
                String.class);
        jdbc.queryForObject(
                "SELECT set_config('app.billing_provider', ?, true)",
                String.class, provider);

        String verified = jdbc.queryForObject(
                "SELECT current_setting('app.billing_webhook_verified', true)",
                String.class);
        String appliedProvider = jdbc.queryForObject(
                "SELECT current_setting('app.billing_provider', true)",
                String.class);
        if (!"true".equals(verified) || !provider.equals(appliedProvider)) {
            throw new IllegalStateException(
                    "Verified billing webhook RLS context could not be established");
        }
    }

    public void clearForCurrentTransaction() {
        requireTransaction();
        jdbc.queryForObject(
                "SELECT set_config('app.billing_webhook_verified', 'false', true)",
                String.class);
        jdbc.queryForObject(
                "SELECT set_config('app.billing_provider', '', true)",
                String.class);
    }

    private static String normalizeProvider(String providerCode) {
        if (providerCode == null || !providerCode.matches("^[A-Z0-9_]{2,40}$")) {
            throw new IllegalArgumentException("providerCode must be an uppercase provider identifier");
        }
        return providerCode;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "BillingWebhookResolutionContext requires an active transaction");
        }
    }
}
