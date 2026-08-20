-- ============================================================
-- V20260820_4: Reconcile idempotency uniqueness contract
-- ============================================================
-- PROBLEM: V20260820_1 introduced a SECOND unique index
--   uk_commerce_orders_tenant_store_idempotency (tenant_id, store_id,
--   idempotency_key) alongside the ORIGINAL
--   uk_commerce_orders_idempotency (tenant_id, idempotency_key).
--
-- The CheckoutService ON CONFLICT clause targets the store-scoped
-- index, so a concurrent same-tenant / different-store insert with
-- the same idempotency_key would BYPASS the ON CONFLICT arbiter and
-- hit the original tenant-wide constraint as a DuplicateKeyException.
-- That violates the PostgreSQL-safe idempotency contract.
--
-- DECISION: pick ONE canonical idempotency scope. The product
-- contract is TENANT-scoped: an idempotency_key identifies one
-- logical order per tenant (not per store). The store-scoped
-- uniqueness is dropped, and CheckoutService is updated to use
-- ON CONFLICT (tenant_id, idempotency_key) DO NOTHING.
--
-- (IDEMPOTENCY_SCOPE_DEFINED=PASS,
--  IDEMPOTENCY_DB_CONSTRAINTS_CONSISTENT=PASS)
--
-- ALSO ADD: DB-level one-order-per-cart invariant
--   uk_commerce_orders_tenant_cart (tenant_id, cart_id)
--   WHERE cart_id IS NOT NULL
-- Two concurrent no-key checkouts on the same cart previously
-- both read cart.status=ACTIVE and proceeded. The unique index
-- now prevents both from creating an order — the loser's INSERT
-- hits the unique constraint and is rolled back.
-- (CART_SINGLE_CHECKOUT_DB_INVARIANT=PASS)
-- ============================================================

-- Drop the contradictory store-scoped index (added by V20260820_1).
-- Safe to drop because no production data was ever persisted with a
-- per-store idempotency-key collision that depended on this index.
DROP INDEX IF EXISTS uk_commerce_orders_tenant_store_idempotency;

-- Add DB-level one-order-per-cart invariant. Partial unique index
-- (WHERE cart_id IS NOT NULL) so that historical orders without a
-- cart_id (if any future flow creates them) are not constrained.
CREATE UNIQUE INDEX IF NOT EXISTS uk_commerce_orders_tenant_cart
    ON commerce_orders (tenant_id, cart_id)
    WHERE cart_id IS NOT NULL;
