-- ============================================================
-- V20260820_1: Commerce Order Number Atomic Allocator
-- ============================================================
-- Problem: OrderService.generateOrderNumber() previously used
--   SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND order_number LIKE ?
--   → next = count + 1
-- This is unsafe under concurrency: two transactions can both read
-- count=N, both generate N+1, and one then hits a
-- DuplicateKeyException on uk_commerce_orders_tenant_number.
-- COUNT is also broken when historical rows are deleted (gaps reuse).
--
-- Solution: an atomic, monotonic allocator table
-- `commerce_order_number_sequences` with PRIMARY KEY (tenant_id, period)
-- where period = YYYYMM. Allocation is done via INSERT ... ON CONFLICT
-- DO UPDATE SET last_value = last_value + 1 RETURNING last_value
-- which is an atomic UPSERT in PostgreSQL.
--
-- Deleted / cancelled orders do NOT release their sequence number —
-- the allocator never decrements, so ORDER_NUMBER_NO_REUSE holds.
--
-- Multi-tenant: each (tenant_id, period) gets its own independent
-- counter, satisfying ORDER_NUMBER_MULTI_STORE (independent sequence
-- per tenant+month, format ORD-YYYYMM-NNNNN) and per-tenant isolation.
--
-- Idempotency: tighten the unique constraint on
-- commerce_orders.idempotency_key so that the DB enforces the
-- "one logical order per idempotency key" contract at the
-- (tenant_id, store_id, idempotency_key) scope. The existing
-- constraint uk_commerce_orders_idempotency (tenant_id, idempotency_key)
-- remains for backward compatibility with existing client contracts.
-- ============================================================

CREATE TABLE IF NOT EXISTS commerce_order_number_sequences (
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    period      VARCHAR(6)  NOT NULL,                 -- YYYYMM e.g. 202608
    last_value  BIGINT      NOT NULL,                 -- last allocated sequence value (starts at 0)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_commerce_order_number_sequences PRIMARY KEY (tenant_id, period)
);

-- Tighten idempotency uniqueness to (tenant_id, store_id, idempotency_key)
-- so the DB enforces "one logical order per idempotency key per store".
-- We use COALESCE-friendly plain UNIQUE because NULL idempotency_key rows
-- (orders created without a client-supplied key) must remain non-conflicting.
-- PostgreSQL lets a UNIQUE index contain multiple NULLs by default, and H2
-- in PostgreSQL mode follows the same rule.
CREATE UNIQUE INDEX IF NOT EXISTS uk_commerce_orders_tenant_store_idempotency
    ON commerce_orders (tenant_id, store_id, idempotency_key);
