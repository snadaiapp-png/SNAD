-- R13-G07.0 design-conformance corrective:
-- adds the PROVIDER_UNAVAILABLE reconciliation classification.
--
-- Defect corrected: DisabledBillingPaymentProvider.queryPaymentState() throws
-- IllegalStateException and BillingReconciliationService used to map provider
-- unavailability into MISSING_PROVIDER_REFERENCE, creating false reconciliation
-- evidence in production DISABLED mode. Provider unavailability must fail
-- closed with the explicit sanitized PROVIDER_UNAVAILABLE classification.
--
-- Forward-only, non-destructive: the existing CHECK constraint is replaced by
-- the same value set plus PROVIDER_UNAVAILABLE. No column data changes, no
-- renames, no out-of-order migration.
ALTER TABLE subscription_billing_reconciliation_items
    DROP CONSTRAINT ck_subscription_billing_reconciliation_items_classification;

ALTER TABLE subscription_billing_reconciliation_items
    ADD CONSTRAINT ck_subscription_billing_reconciliation_items_classification CHECK (classification IN (
        'MATCHED','MISSING_FINANCE_INVOICE','MISSING_PROVIDER_REFERENCE','PROVIDER_UNAVAILABLE','AMOUNT_MISMATCH',
        'CURRENCY_MISMATCH','PROVIDER_PAID_FINANCE_PENDING','FINANCE_PAID_PROVIDER_UNCONFIRMED',
        'DUPLICATE_PROVIDER_EVENT','TENANT_BINDING_MISMATCH','SIGNATURE_REJECTED'
    ));
