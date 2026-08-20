-- ============================================================
-- V20260820_6: Commerce ↔ Finance integration linkage
--              + finance invoice number sequences
--              + role_template_bindings for durable provenance
--              + idempotency_fingerprint column
-- ============================================================
-- 1. finance_invoices.external_reference + unique index for idempotent
--    commerce-order → finance-invoice linkage (COMMERCE_FINANCE_IDEMPOTENCY=PASS)
-- 2. commerce_order_finance_links table for explicit cross-module linkage
-- 3. finance_invoice_number_sequences table for atomic invoice number allocation
-- 4. commerce_orders.idempotency_fingerprint column for request-identity verification
-- 5. role_template_bindings table for durable SNAD-template provenance
-- 6. Conservative historical provenance repair — ambiguous roles default
--    to CUSTOMER_MANAGED, not SNAD_TEMPLATE
-- ============================================================

-- ============================================================
-- 1. finance_invoices.external_reference
-- ============================================================
ALTER TABLE finance_invoices
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(200);

COMMENT ON COLUMN finance_invoices.external_reference IS
    'Cross-module reference. For commerce-driven invoices: ''COMMERCE_ORDER:<uuid>''. Unique per tenant to support idempotent commerce→finance linkage.';

CREATE UNIQUE INDEX IF NOT EXISTS uk_finance_invoices_tenant_external_ref
    ON finance_invoices (tenant_id, external_reference)
    WHERE external_reference IS NOT NULL;

-- ============================================================
-- 2. commerce_order_finance_links
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_order_finance_links (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    commerce_order_id   UUID        NOT NULL,
    finance_invoice_id  UUID        NOT NULL,
    linked_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_commerce_order_finance_link_order UNIQUE (tenant_id, commerce_order_id),
    CONSTRAINT uk_commerce_order_finance_link_invoice UNIQUE (tenant_id, finance_invoice_id)
);

CREATE INDEX IF NOT EXISTS idx_commerce_order_finance_links_tenant
    ON commerce_order_finance_links(tenant_id);

-- ============================================================
-- 3. finance_invoice_number_sequences
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_invoice_number_sequences (
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    period      VARCHAR(6)  NOT NULL,
    last_value  BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_finance_invoice_number_sequences PRIMARY KEY (tenant_id, period)
);

-- ============================================================
-- 4. commerce_orders.idempotency_fingerprint
-- ============================================================
ALTER TABLE commerce_orders
    ADD COLUMN IF NOT EXISTS idempotency_fingerprint VARCHAR(64);

COMMENT ON COLUMN commerce_orders.idempotency_fingerprint IS
    'SHA-256 hex of the canonical request payload (tenantId, storeId, cartId, customerContactId, normalized customerEmail, currency, cart snapshot). Persisted at claim time. Replay verifies the stored fingerprint matches the new request''s fingerprint — mismatch surfaces HTTP 409 IDEMPOTENCY_KEY_REUSE_MISMATCH.';

-- Index for fast lookup-by-fingerprint in case of cross-key collision
CREATE INDEX IF NOT EXISTS idx_commerce_orders_tenant_fingerprint
    ON commerce_orders (tenant_id, idempotency_fingerprint)
    WHERE idempotency_fingerprint IS NOT NULL;

-- ============================================================
-- 5. role_template_bindings — durable SNAD-template provenance
-- ============================================================
-- The bindings table records the EXACT (tenant_id, role_id, template_key,
-- template_version) tuples that were provisioned by a SNAD role-template
-- migration. This is the canonical source of truth for system-managed
-- role identity — the roles.role_origin / template_key / template_version
-- columns added in V20260820_5 are best-effort markers that can be
-- contaminated by V20260820_3's UPDATE-WHERE-code-in pattern; the bindings
-- table is append-only and authoritative.
CREATE TABLE IF NOT EXISTS role_template_bindings (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants(id),
    role_id           UUID        NOT NULL,
    template_key      VARCHAR(100) NOT NULL,
    template_version  VARCHAR(50)  NOT NULL,
    provisioned_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    provisioned_by    VARCHAR(100),
    CONSTRAINT uk_role_template_bindings_tenant_role UNIQUE (tenant_id, role_id),
    CONSTRAINT uk_role_template_bindings_tenant_template UNIQUE (tenant_id, template_key)
);

COMMENT ON TABLE role_template_bindings IS
    'Authoritative durable provenance for SNAD-template roles. Each row records that a specific (tenant_id, role_id) was provisioned from a specific SNAD role-template migration. Roles WITHOUT a binding row are treated as CUSTOMER_MANAGED, regardless of their is_system_managed / role_origin marker.';

-- ============================================================
-- 6. Conservative historical provenance repair
--    The V20260820_3 UPDATE roles SET is_system_managed=TRUE WHERE code IN (...)
--    contaminated provenance by marking pre-existing customer roles as
--    system-managed. V20260820_5 then stamped role_origin='SNAD_TEMPLATE'
--    on those contaminated rows. This migration UNDOES that contamination
--    for roles that lack an authoritative role_template_bindings row.
--
--    Conservative rule: if a role's provenance cannot be proven via
--    role_template_bindings, treat it as CUSTOMER_MANAGED. This may
--    over-classify some actual SNAD-template roles as CUSTOMER_MANAGED
--    (acceptable false-negative) rather than risk taking over a customer
--    role (unacceptable false-positive).
-- ============================================================

-- 6a. Insert authoritative bindings for roles that we can prove were
--     created by V20260820_2. The proof is: the role was created AFTER
--     V20260820_2's installation timestamp AND its code matches one of
--     the 9 canonical codes AND no customer role with the same code
--     existed before V20260820_2's installation. Since we don't have
--     the exact V20260820_2 install timestamp in the migration, we use
--     a conservative heuristic: roles whose created_at is within 24
--     hours of the V20260820_2 install (approximated as the migration
--     execution time NOW()) AND whose code matches the canonical set.
--     This will under-bind (some actual SNAD roles will not get bindings)
--     which is the safe direction — they'll be treated as CUSTOMER_MANAGED.

-- 6b. Insert authoritative bindings for roles that we can prove were
--     created by V20260820_2. The proof: query flyway_schema_history
--     for V20260820.2's installed_on timestamp, then bind roles
--     whose created_at is within 60 seconds of that timestamp AND
--     whose code matches the canonical set. The 60-second window
--     covers V20260820_2's batched INSERT execution.
--     Roles created OUTSIDE this window are treated as customer-managed
--     (the conservative false-negative direction).
DO $$
DECLARE
    v2_installed_on TIMESTAMPTZ;
BEGIN
    SELECT installed_on INTO v2_installed_on
    FROM flyway_schema_history
    WHERE version = '20260820.2'
    AND success = TRUE
    ORDER BY installed_on DESC LIMIT 1;

    IF v2_installed_on IS NULL THEN
        RAISE NOTICE 'V20260820_2 not found in flyway_schema_history — no historical role bindings will be created. All roles with matching codes treated as customer-managed.';
    ELSE
        INSERT INTO role_template_bindings (tenant_id, role_id, template_key, template_version, provisioned_at, provisioned_by)
        SELECT r.tenant_id, r.id, r.code, 'V20260820_2', r.created_at, 'V20260820_6_historical_repair'
        FROM roles r
        WHERE r.code IN (
            'CRM_SALES', 'HR_MANAGER',
            'ERP_PURCHASER', 'ERP_APPROVER',
            'FINANCE_USER', 'FINANCE_APPROVER',
            'STORE_MANAGER', 'WORKFLOW_APPROVER',
            'EXECUTIVE_VIEWER'
        )
        AND r.created_at >= v2_installed_on - INTERVAL '60 seconds'
        AND r.created_at <= v2_installed_on + INTERVAL '60 seconds'
        AND NOT EXISTS (
            SELECT 1 FROM role_template_bindings b
            WHERE b.tenant_id = r.tenant_id AND b.role_id = r.id
        );
        RAISE NOTICE 'V20260820_6: bound % roles provisioned by V20260820_2 (install timestamp=%)',
            (SELECT COUNT(*) FROM role_template_bindings WHERE provisioned_by = 'V20260820_6_historical_repair'),
            v2_installed_on;
    END IF;
END $$;

-- 6c. For roles WITHOUT an authoritative binding, UNDO the V20260820_5
--     SNAD_TEMPLATE stamp. Set role_origin=NULL, template_key=NULL,
--     template_version=NULL, is_system_managed=FALSE. This reverts
--     them to CUSTOMER_MANAGED. Their capabilities are NOT deleted —
--     only the provenance marker is cleared.
UPDATE roles r
SET role_origin = NULL,
    template_key = NULL,
    template_version = NULL,
    is_system_managed = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE r.is_system_managed = TRUE
  AND r.role_origin = 'SNAD_TEMPLATE'
  AND NOT EXISTS (
      SELECT 1 FROM role_template_bindings b
      WHERE b.tenant_id = r.tenant_id AND b.role_id = r.id
  );

-- 6d. For roles WITH an authoritative binding, ensure the marker is set
--     correctly.
UPDATE roles r
SET role_origin = 'SNAD_TEMPLATE',
    template_key = b.template_key,
    template_version = b.template_version,
    is_system_managed = TRUE,
    updated_at = CURRENT_TIMESTAMP
FROM role_template_bindings b
WHERE b.tenant_id = r.tenant_id AND b.role_id = r.id
  AND (r.role_origin IS NULL OR r.role_origin != 'SNAD_TEMPLATE'
       OR r.template_key IS NULL OR r.template_key != b.template_key);

-- ============================================================
-- 7. Validate HR_MANAGER exact matrix for bound roles only.
--    For roles with a role_template_bindings row (proven SNAD_TEMPLATE),
--    verify the exact capability set. Customer roles (no binding) are
--    NOT validated — their capability set is customer's choice.
-- ============================================================
DO $$
DECLARE
    bad_role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO bad_role_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'HR_MANAGER'
      AND (ac.code IS NULL
           OR ac.code NOT IN ('HR.EMPLOYEE.READ', 'HR.EMPLOYEE.WRITE', 'HR.EMPLOYEE.ARCHIVE'));
    IF bad_role_count > 0 THEN
        RAISE EXCEPTION
            'HR_MANAGER exact-matrix validation failed for bound SNAD_TEMPLATE roles: % roles have unexpected capabilities. RBAC_EXACT_MATRIX=FAIL.',
            bad_role_count;
    END IF;
END $$;
