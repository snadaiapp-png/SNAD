-- ============================================================
-- V20260816_7: ERP Core Platform — capabilities + tables
--
-- Creates ERP.* capabilities and all ERP tables:
--   erp_items, erp_suppliers, erp_warehouses, erp_inventory_balances,
--   erp_inventory_movements, erp_inventory_reservations,
--   erp_purchase_requisitions, erp_purchase_requisition_items,
--   erp_purchase_orders, erp_purchase_order_items,
--   erp_goods_receipts, erp_goods_receipt_items,
--   erp_inventory_transfers, erp_inventory_transfer_items,
--   erp_inventory_adjustments
--
-- H2 compatibility: pure DDL, runs on PG and H2.
-- ============================================================

-- Seed ERP.* capabilities (ERP module already registered in V20260814_1)
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('ERP.VIEW',        'ERP View',        'View ERP items, suppliers, warehouses, inventory, procurement'),
    ('ERP.WRITE',        'ERP Write',       'Create and update ERP items, suppliers, warehouses, inventory'),
    ('ERP.ADMIN',        'ERP Admin',       'Full administrative access including lifecycle and configuration'),
    ('ERP.APPROVE',      'ERP Approve',     'Approve purchase requisitions, purchase orders, inventory adjustments'),
    ('ERP.INVENTORY',    'ERP Inventory',   'Manage inventory, stock movements, reservations, transfers, adjustments'),
    ('ERP.PROCUREMENT',  'ERP Procurement', 'Manage suppliers, purchase requisitions, purchase orders, goods receipts')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants (idempotent)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('ERP.VIEW','ERP.WRITE','ERP.ADMIN','ERP.APPROVE','ERP.INVENTORY','ERP.PROCUREMENT')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- erp_items
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_items (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(50)    NOT NULL,
    sku                 VARCHAR(100),
    name                VARCHAR(300)   NOT NULL,
    description         TEXT,
    item_type           VARCHAR(30)     NOT NULL DEFAULT 'GOODS',
    unit_of_measure     VARCHAR(20)     NOT NULL DEFAULT 'EACH',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    track_inventory     BOOLEAN         NOT NULL DEFAULT TRUE,
    reorder_level       NUMERIC(18,4)   NOT NULL DEFAULT 0,
    reorder_quantity    NUMERIC(18,4)   NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_items PRIMARY KEY (id),
    CONSTRAINT uk_erp_items_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_erp_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_erp_items_type CHECK (item_type IN ('GOODS','SERVICE','DIGITAL','RAW_MATERIAL','FINISHED_GOOD')),
    CONSTRAINT ck_erp_items_uom CHECK (unit_of_measure IN ('EACH','KG','G','L','M','CM','BOX','PACK','UNIT')),
    CONSTRAINT ck_erp_items_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_erp_items_tenant_status ON erp_items(tenant_id, status);

-- ============================================================
-- erp_suppliers
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_suppliers (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    supplier_code       VARCHAR(50)    NOT NULL,
    name                VARCHAR(300)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    contact_email       VARCHAR(300),
    contact_phone       VARCHAR(50),
    address             TEXT,
    tax_number          VARCHAR(100),
    payment_terms       VARCHAR(100),
    currency           VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_suppliers PRIMARY KEY (id),
    CONSTRAINT uk_erp_suppliers_tenant_code UNIQUE (tenant_id, supplier_code),
    CONSTRAINT uk_erp_suppliers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_erp_suppliers_status CHECK (status IN ('PENDING','ACTIVE','INACTIVE','BLOCKED','ARCHIVED'))
);

-- ============================================================
-- erp_warehouses
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_warehouses (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(50)    NOT NULL,
    name                VARCHAR(200)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    location            VARCHAR(500),
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_warehouses PRIMARY KEY (id),
    CONSTRAINT uk_erp_warehouses_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_erp_warehouses_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_erp_warehouses_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

-- ============================================================
-- erp_inventory_balances
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_inventory_balances (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    on_hand             NUMERIC(18,4)   NOT NULL DEFAULT 0,
    reserved            NUMERIC(18,4)   NOT NULL DEFAULT 0,
    incoming            NUMERIC(18,4)   NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_inv_balances PRIMARY KEY (id),
    CONSTRAINT uk_erp_inv_balances UNIQUE (tenant_id, warehouse_id, item_id),
    CONSTRAINT uk_erp_inv_balances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_inv_wh FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT fk_erp_inv_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_inv_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_erp_inv_reserved CHECK (reserved >= 0)
);

-- ============================================================
-- erp_inventory_movements (append-only ledger)
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_inventory_movements (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    movement_type       VARCHAR(30)     NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        UUID,
    reason              VARCHAR(500),
    performed_by        UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_inv_movements PRIMARY KEY (id),
    CONSTRAINT uk_erp_inv_movements_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_im_wh FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT fk_erp_im_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_im_type CHECK (movement_type IN (
        'RECEIPT','ISSUE','TRANSFER_OUT','TRANSFER_IN',
        'ADJUSTMENT_IN','ADJUSTMENT_OUT','RESERVATION','RELEASE',
        'FULFILLMENT','RETURN'))
);
CREATE INDEX IF NOT EXISTS idx_erp_im_tenant_wh_item ON erp_inventory_movements(tenant_id, warehouse_id, item_id);

-- ============================================================
-- erp_inventory_reservations
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_inventory_reservations (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    source              VARCHAR(100),
    external_reference  VARCHAR(200),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    expires_at          TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_inv_reservations PRIMARY KEY (id),
    CONSTRAINT uk_erp_inv_res_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_ir_wh FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT fk_erp_ir_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_ir_status CHECK (status IN ('PENDING','RESERVED','CONFIRMED','RELEASED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_erp_ir_qty CHECK (quantity > 0)
);

-- ============================================================
-- erp_purchase_requisitions + items
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_purchase_requisitions (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    requisition_number  VARCHAR(60)     NOT NULL,
    requester_id        UUID,
    reason              VARCHAR(500),
    priority            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_pr PRIMARY KEY (id),
    CONSTRAINT uk_erp_pr_tenant_number UNIQUE (tenant_id, requisition_number),
    CONSTRAINT uk_erp_pr_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_erp_pr_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_erp_pr_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CONVERTED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS erp_purchase_requisition_items (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    requisition_id      UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    required_date       DATE,
    estimated_unit_cost NUMERIC(18,2),
    notes               VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_pri PRIMARY KEY (id),
    CONSTRAINT uk_erp_pri_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_pri_pr FOREIGN KEY (tenant_id, requisition_id)
        REFERENCES erp_purchase_requisitions(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_erp_pri_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_pri_qty CHECK (quantity > 0)
);

-- ============================================================
-- erp_purchase_orders + items
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_purchase_orders (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    po_number           VARCHAR(60)     NOT NULL,
    supplier_id         UUID,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    subtotal            NUMERIC(18,2)   NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total               NUMERIC(18,2)   NOT NULL DEFAULT 0,
    expected_date       DATE,
    created_by          UUID,
    approved_by         UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_po PRIMARY KEY (id),
    CONSTRAINT uk_erp_po_tenant_number UNIQUE (tenant_id, po_number),
    CONSTRAINT uk_erp_po_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_po_supplier FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES erp_suppliers(tenant_id, id),
    CONSTRAINT ck_erp_po_status CHECK (status IN (
        'DRAFT','SUBMITTED','APPROVED','SENT',
        'PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS erp_purchase_order_items (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    po_id               UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    unit_cost           NUMERIC(18,2)   NOT NULL,
    received_quantity   NUMERIC(18,4)   NOT NULL DEFAULT 0,
    line_total          NUMERIC(18,2)   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_poi PRIMARY KEY (id),
    CONSTRAINT uk_erp_poi_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_poi_po FOREIGN KEY (tenant_id, po_id)
        REFERENCES erp_purchase_orders(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_erp_poi_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_poi_qty CHECK (quantity > 0),
    CONSTRAINT ck_erp_poi_received CHECK (received_quantity >= 0)
);

-- ============================================================
-- erp_goods_receipts + items
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_goods_receipts (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    receipt_number      VARCHAR(60)     NOT NULL,
    po_id               UUID,
    warehouse_id        UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    received_by         UUID,
    posted_at           TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_gr PRIMARY KEY (id),
    CONSTRAINT uk_erp_gr_tenant_number UNIQUE (tenant_id, receipt_number),
    CONSTRAINT uk_erp_gr_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_gr_po FOREIGN KEY (tenant_id, po_id)
        REFERENCES erp_purchase_orders(tenant_id, id),
    CONSTRAINT fk_erp_gr_wh FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT ck_erp_gr_status CHECK (status IN ('DRAFT','POSTED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS erp_goods_receipt_items (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    receipt_id          UUID            NOT NULL,
    po_item_id          UUID,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_gri PRIMARY KEY (id),
    CONSTRAINT uk_erp_gri_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_gri_gr FOREIGN KEY (tenant_id, receipt_id)
        REFERENCES erp_goods_receipts(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_erp_gri_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_gri_qty CHECK (quantity > 0)
);

-- ============================================================
-- erp_inventory_transfers + items
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_inventory_transfers (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    transfer_number     VARCHAR(60)     NOT NULL,
    from_warehouse_id   UUID            NOT NULL,
    to_warehouse_id     UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    requested_by        UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_it PRIMARY KEY (id),
    CONSTRAINT uk_erp_it_tenant_number UNIQUE (tenant_id, transfer_number),
    CONSTRAINT uk_erp_it_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_it_from FOREIGN KEY (tenant_id, from_warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT fk_erp_it_to FOREIGN KEY (tenant_id, to_warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT ck_erp_it_status CHECK (status IN ('DRAFT','SUBMITTED','IN_TRANSIT','RECEIVED','CANCELLED')),
    CONSTRAINT ck_erp_it_wh CHECK (from_warehouse_id <> to_warehouse_id)
);

CREATE TABLE IF NOT EXISTS erp_inventory_transfer_items (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    transfer_id         UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity            NUMERIC(18,4)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_iti PRIMARY KEY (id),
    CONSTRAINT uk_erp_iti_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_iti_transfer FOREIGN KEY (tenant_id, transfer_id)
        REFERENCES erp_inventory_transfers(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_erp_iti_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_iti_qty CHECK (quantity > 0)
);

-- ============================================================
-- erp_inventory_adjustments
-- ============================================================
CREATE TABLE IF NOT EXISTS erp_inventory_adjustments (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    adjustment_number   VARCHAR(60)     NOT NULL,
    warehouse_id        UUID            NOT NULL,
    item_id             UUID            NOT NULL,
    quantity_delta      NUMERIC(18,4)   NOT NULL,
    reason_code         VARCHAR(50),
    notes               VARCHAR(500),
    requested_by        UUID,
    approved_by         UUID,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_erp_ia PRIMARY KEY (id),
    CONSTRAINT uk_erp_ia_tenant_number UNIQUE (tenant_id, adjustment_number),
    CONSTRAINT uk_erp_ia_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_erp_ia_wh FOREIGN KEY (tenant_id, warehouse_id)
        REFERENCES erp_warehouses(tenant_id, id),
    CONSTRAINT fk_erp_ia_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES erp_items(tenant_id, id),
    CONSTRAINT ck_erp_ia_status CHECK (status IN ('PENDING','APPROVED','POSTED','REJECTED'))
);
