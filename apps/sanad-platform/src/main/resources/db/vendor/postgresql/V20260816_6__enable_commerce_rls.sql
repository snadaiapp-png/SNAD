-- ============================================================
-- V20260816_6: Enable RLS on commerce tables (PG-only)
-- ============================================================

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'commerce_stores',
        'commerce_store_domains',
        'commerce_products',
        'commerce_product_variants',
        'commerce_collections',
        'commerce_collection_products',
        'commerce_prices',
        'commerce_carts',
        'commerce_cart_items',
        'commerce_orders',
        'commerce_order_items',
        'commerce_order_status_history'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
