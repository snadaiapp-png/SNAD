-- ============================================================
-- V20260820_8: Add SETTLEMENT_FAILED order status
-- ============================================================
-- The v12 brief requires an explicit SETTLEMENT_FAILED state for orders
-- where settlement side-effects (inventory confirmation or finance posting)
-- failed. The order is NOT silently marked PAID — the operator can retry
-- settlement via the idempotent replay path of
-- POST /api/v1/stores/{storeId}/orders/{orderId}/settle.
--
-- This migration:
--   1. Drops the existing ck_commerce_orders_status CHECK constraint
--   2. Recreates it with SETTLEMENT_FAILED added
--   3. Updates the OrderStatus Java enum (handled in source — Java code
--      must also be updated to recognise the new status)
-- ============================================================

ALTER TABLE commerce_orders DROP CONSTRAINT IF EXISTS ck_commerce_orders_status;

ALTER TABLE commerce_orders ADD CONSTRAINT ck_commerce_orders_status
    CHECK (status IN ('PENDING','CONFIRMED','PAID','PROCESSING','COMPLETED','CANCELLED','SETTLEMENT_FAILED'));

COMMENT ON CONSTRAINT ck_commerce_orders_status ON commerce_orders IS
    'Order lifecycle status. SETTLEMENT_FAILED indicates an order where checkout created the order row but settlement side-effects (inventory confirmation or finance posting) failed. The operator can retry settlement via POST /api/v1/stores/{storeId}/orders/{orderId}/settle.';
