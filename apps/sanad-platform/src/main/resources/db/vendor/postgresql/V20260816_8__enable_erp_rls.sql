-- V20260816_8: Enable RLS on ERP tables (PG-only)
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'erp_items','erp_suppliers','erp_warehouses',
        'erp_inventory_balances','erp_inventory_movements',
        'erp_inventory_reservations',
        'erp_purchase_requisitions','erp_purchase_requisition_items',
        'erp_purchase_orders','erp_purchase_order_items',
        'erp_goods_receipts','erp_goods_receipt_items',
        'erp_inventory_transfers','erp_inventory_transfer_items',
        'erp_inventory_adjustments'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
