-- ============================================================
-- V20260815_16: Finance Module — Chart of Accounts, Journals, Invoices, Payments
--
-- Creates the Finance module schema:
--   - finance_accounts        (chart of accounts: assets, liabilities, equity, revenue, expenses)
--   - finance_journal_entries  (double-entry bookkeeping journal entries)
--   - finance_journal_lines    (individual debit/credit lines per journal entry)
--   - finance_invoices         (customer invoices with line items)
--   - finance_invoice_lines    (invoice line items)
--   - finance_payments         (payment records linked to invoices)
--
-- Design principles (same as V20260815_1/3/5/10/14):
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * State machines via CHECK constraints
--   * TIMESTAMPTZ timestamps
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Optimistic locking via version field
--   * No flyway_schema_history manipulation
-- ============================================================

-- ============================================================
-- STEP 1: finance_accounts — chart of accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_accounts (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(300)   NOT NULL,
    account_type    VARCHAR(20)    NOT NULL,
    parent_account_id UUID,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    description     TEXT,
    balance         NUMERIC(18,2)  NOT NULL DEFAULT 0,
    version_lock    BIGINT         NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_accounts PRIMARY KEY (id),
    CONSTRAINT uk_finance_accounts_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_finance_accounts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_finance_account_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_finance_account_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT fk_finance_account_parent FOREIGN KEY (tenant_id, parent_account_id)
        REFERENCES finance_accounts(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_finance_accounts_tenant_type ON finance_accounts(tenant_id, account_type);
CREATE INDEX IF NOT EXISTS idx_finance_accounts_tenant_status ON finance_accounts(tenant_id, status);

-- ============================================================
-- STEP 2: finance_journal_entries — double-entry journal headers
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_journal_entries (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    entry_number    VARCHAR(100)   NOT NULL,
    entry_date      DATE           NOT NULL,
    description     TEXT,
    reference_type  VARCHAR(50),
    reference_id    UUID,
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    posted_at       TIMESTAMP WITH TIME ZONE,
    posted_by       UUID,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_journal_entries PRIMARY KEY (id),
    CONSTRAINT uk_finance_journal_tenant_number UNIQUE (tenant_id, entry_number),
    CONSTRAINT uk_finance_journal_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_finance_journal_status CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    CONSTRAINT fk_finance_journal_posted_by FOREIGN KEY (tenant_id, posted_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_finance_journal_tenant_date ON finance_journal_entries(tenant_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_finance_journal_tenant_status ON finance_journal_entries(tenant_id, status);

-- ============================================================
-- STEP 3: finance_journal_lines — individual debit/credit lines
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_journal_lines (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    journal_entry_id UUID          NOT NULL,
    account_id      UUID            NOT NULL,
    debit_amount    NUMERIC(18,2)  NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(18,2)  NOT NULL DEFAULT 0,
    description     TEXT,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_journal_lines PRIMARY KEY (id),
    CONSTRAINT uk_finance_journal_lines_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_finance_jl_entry FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES finance_journal_entries(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_finance_jl_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES finance_accounts(tenant_id, id),
    CONSTRAINT ck_finance_jl_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0 AND
        (debit_amount > 0 OR credit_amount > 0) AND NOT (debit_amount > 0 AND credit_amount > 0))
);

CREATE INDEX IF NOT EXISTS idx_finance_jl_entry ON finance_journal_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_finance_jl_account ON finance_journal_lines(tenant_id, account_id);

-- ============================================================
-- STEP 4: finance_invoices — customer invoices
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_invoices (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    invoice_number  VARCHAR(100)   NOT NULL,
    customer_type    VARCHAR(50)    NOT NULL,
    customer_id     UUID,
    customer_name    VARCHAR(300),
    issue_date      DATE           NOT NULL,
    due_date        DATE,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    subtotal        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_amount    NUMERIC(18,2)  NOT NULL DEFAULT 0,
    paid_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    notes           TEXT,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_invoices PRIMARY KEY (id),
    CONSTRAINT uk_finance_invoices_tenant_number UNIQUE (tenant_id, invoice_number),
    CONSTRAINT uk_finance_invoices_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_finance_invoice_status CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED')),
    CONSTRAINT ck_finance_invoice_customer CHECK (customer_type IN ('CRM_ACCOUNT','CRM_CONTACT','MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_finance_invoices_tenant_status ON finance_invoices(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_finance_invoices_tenant_due ON finance_invoices(tenant_id, due_date) WHERE due_date IS NOT NULL;

-- ============================================================
-- STEP 5: finance_invoice_lines — invoice line items
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_invoice_lines (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    invoice_id      UUID            NOT NULL,
    line_number     INTEGER        NOT NULL,
    description     TEXT            NOT NULL,
    quantity        NUMERIC(18,4)  NOT NULL DEFAULT 1,
    unit_price      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(5,2)   NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_invoice_lines PRIMARY KEY (id),
    CONSTRAINT uk_finance_invoice_lines_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_finance_il_invoice FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES finance_invoices(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_finance_il_invoice ON finance_invoice_lines(invoice_id);

-- ============================================================
-- STEP 6: finance_payments — payment records
-- ============================================================
CREATE TABLE IF NOT EXISTS finance_payments (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    payment_number  VARCHAR(100)   NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(30)    NOT NULL,
    amount          NUMERIC(18,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    reference_type  VARCHAR(50),
    reference_id    UUID,
    invoice_id      UUID,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_payments PRIMARY KEY (id),
    CONSTRAINT uk_finance_payments_tenant_number UNIQUE (tenant_id, payment_number),
    CONSTRAINT uk_finance_payments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_finance_payment_status CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED')),
    CONSTRAINT ck_finance_payment_method CHECK (payment_method IN ('CASH','BANK_TRANSFER','CREDIT_CARD','DEBIT_CARD','CHEQUE','ONLINE','OTHER')),
    CONSTRAINT fk_finance_payment_invoice FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES finance_invoices(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_finance_payments_tenant_date ON finance_payments(tenant_id, payment_date);
CREATE INDEX IF NOT EXISTS idx_finance_payments_tenant_status ON finance_payments(tenant_id, status);

-- ============================================================
-- STEP 7: Enable RLS on all finance tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'finance_accounts',
        'finance_journal_entries',
        'finance_journal_lines',
        'finance_invoices',
        'finance_invoice_lines',
        'finance_payments'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
