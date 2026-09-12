-- ============================================================
-- V20260912_2: R0C13 R13-G05 verified webhook resolution
--
-- Adds one narrowly-scoped SELECT policy to the existing FORCE-RLS
-- payment-attempt table. The policy is usable only inside a transaction
-- after application-level cryptographic verification has set both trusted
-- local GUCs:
--   app.billing_webhook_verified = true
--   app.billing_provider         = <configured provider>
--
-- It does NOT authorize INSERT/UPDATE/DELETE and does not accept tenant
-- identity from the webhook payload. The selected stored row yields the
-- authoritative tenant_id; application code immediately clears this context
-- and switches to ordinary app.tenant_id RLS for inbox/outbox mutations.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_policy p
        JOIN pg_class c ON c.oid = p.polrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname = 'subscription_billing_payment_attempts'
          AND p.polname = 'provider_webhook_resolution'
    ) THEN
        CREATE POLICY provider_webhook_resolution
            ON subscription_billing_payment_attempts
            FOR SELECT
            USING (
                NULLIF(current_setting('app.billing_webhook_verified', true), '') = 'true'
                AND provider = NULLIF(current_setting('app.billing_provider', true), '')
            );
    END IF;
END $$;

COMMENT ON POLICY provider_webhook_resolution
    ON subscription_billing_payment_attempts
    IS 'R0C13 G05 SELECT-only trusted provider-ref resolution after verified webhook signature; no tenant payload trust and no write permission.';
