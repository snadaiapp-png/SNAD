-- REM-P1-007 final closure backbone.
-- Provides executable, tenant-scoped persistence for the four governed
-- end-to-end business processes. Compatible with PostgreSQL and H2
-- PostgreSQL mode and intentionally uses no database-specific UUID functions.

CREATE TABLE bp_process_runs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    process_code VARCHAR(64) NOT NULL,
    external_reference VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    gross_amount NUMERIC(24,6) NOT NULL,
    tax_amount NUMERIC(24,6) NOT NULL DEFAULT 0,
    settlement_amount NUMERIC(24,6) NOT NULL,
    quantity NUMERIC(24,6) NOT NULL DEFAULT 0,
    sku VARCHAR(120),
    starting_inventory NUMERIC(24,6),
    ending_inventory NUMERIC(24,6),
    debit_total NUMERIC(24,6) NOT NULL DEFAULT 0,
    credit_total NUMERIC(24,6) NOT NULL DEFAULT 0,
    payment_net NUMERIC(24,6) NOT NULL DEFAULT 0,
    financial_reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    analytics_consistent BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_bp_process_runs PRIMARY KEY (id),
    CONSTRAINT uk_bp_process_runs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_bp_process_runs_reference UNIQUE (tenant_id, process_code, external_reference),
    CONSTRAINT fk_bp_process_runs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_bp_process_runs_code CHECK (process_code IN (
        'SALES-ORDER-TO-CASH',
        'PROCUREMENT-PROCURE-TO-PAY',
        'HR-HIRE-TO-PAY',
        'COMMERCE-ORDER-TO-REFUND'
    )),
    CONSTRAINT ck_bp_process_runs_status CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_bp_process_runs_currency CHECK (CHAR_LENGTH(currency_code) = 3),
    CONSTRAINT ck_bp_process_runs_amounts CHECK (
        gross_amount >= 0 AND tax_amount >= 0 AND settlement_amount >= 0 AND quantity >= 0
        AND debit_total >= 0 AND credit_total >= 0
    )
);
CREATE INDEX idx_bp_process_runs_tenant_process ON bp_process_runs (tenant_id, process_code, created_at DESC);

CREATE TABLE bp_process_steps (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    step_code VARCHAR(80) NOT NULL,
    domain_code VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount NUMERIC(24,6),
    quantity_delta NUMERIC(24,6),
    evidence_json TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_process_steps PRIMARY KEY (id),
    CONSTRAINT uk_bp_process_steps_sequence UNIQUE (tenant_id, run_id, sequence_no),
    CONSTRAINT fk_bp_process_steps_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_bp_process_steps_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id),
    CONSTRAINT ck_bp_process_steps_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_bp_process_steps_status CHECK (status IN ('COMPLETED','APPROVED','RECONCILED')),
    CONSTRAINT ck_bp_process_steps_domain CHECK (domain_code IN (
        'CRM','SALES','INVENTORY','ACCOUNTING','PAYMENTS','PURCHASING',
        'WORKFLOW','HR','PAYROLL','ECOMMERCE','RETURNS','ANALYTICS'
    ))
);
CREATE INDEX idx_bp_process_steps_run ON bp_process_steps (tenant_id, run_id, sequence_no);

CREATE TABLE bp_inventory_balances (
    tenant_id UUID NOT NULL,
    sku VARCHAR(120) NOT NULL,
    on_hand NUMERIC(24,6) NOT NULL,
    reserved NUMERIC(24,6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_inventory_balances PRIMARY KEY (tenant_id, sku),
    CONSTRAINT fk_bp_inventory_balances_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_bp_inventory_balances_nonnegative CHECK (on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand)
);

CREATE TABLE bp_inventory_movements (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    sku VARCHAR(120) NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity NUMERIC(24,6) NOT NULL,
    on_hand_after NUMERIC(24,6) NOT NULL,
    reserved_after NUMERIC(24,6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_inventory_movements PRIMARY KEY (id),
    CONSTRAINT fk_bp_inventory_movements_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id),
    CONSTRAINT ck_bp_inventory_movements_type CHECK (movement_type IN ('RESERVE','RELEASE','SHIP','RECEIVE','RETURN')),
    CONSTRAINT ck_bp_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_bp_inventory_movements_balance CHECK (on_hand_after >= 0 AND reserved_after >= 0 AND reserved_after <= on_hand_after)
);

CREATE TABLE bp_ledger_entries (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    account_code VARCHAR(80) NOT NULL,
    debit_amount NUMERIC(24,6) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(24,6) NOT NULL DEFAULT 0,
    entry_group VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_bp_ledger_entries_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id),
    CONSTRAINT ck_bp_ledger_entries_sides CHECK (
        debit_amount >= 0 AND credit_amount >= 0
        AND ((debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0))
    )
);
CREATE INDEX idx_bp_ledger_entries_run ON bp_ledger_entries (tenant_id, run_id, entry_group);

CREATE TABLE bp_payment_events (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    payment_type VARCHAR(40) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(24,6) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_payment_events PRIMARY KEY (id),
    CONSTRAINT fk_bp_payment_events_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id),
    CONSTRAINT ck_bp_payment_events_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT ck_bp_payment_events_amount CHECK (amount > 0),
    CONSTRAINT ck_bp_payment_events_status CHECK (status IN ('AUTHORIZED','SETTLED','REFUNDED'))
);

CREATE TABLE bp_workflow_approvals (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    approval_code VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    approved_by UUID NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_workflow_approvals PRIMARY KEY (id),
    CONSTRAINT uk_bp_workflow_approval UNIQUE (tenant_id, run_id, approval_code),
    CONSTRAINT fk_bp_workflow_approval_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id),
    CONSTRAINT ck_bp_workflow_approval_status CHECK (status = 'APPROVED')
);

CREATE TABLE bp_analytics_snapshots (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    metric_code VARCHAR(100) NOT NULL,
    metric_value NUMERIC(24,6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bp_analytics_snapshots PRIMARY KEY (id),
    CONSTRAINT uk_bp_analytics_metric UNIQUE (tenant_id, run_id, metric_code),
    CONSTRAINT fk_bp_analytics_run_same_tenant FOREIGN KEY (tenant_id, run_id)
        REFERENCES bp_process_runs (tenant_id, id)
);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT uuid_val, code, name, description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
    (CAST('a0000010-0000-0000-0000-000000000001' AS uuid),
     'BUSINESS_PROCESS.READ', 'Read Business Process Evidence',
     'Read tenant-scoped integrated business-process runs and reconciliations'),
    (CAST('a0000010-0000-0000-0000-000000000002' AS uuid),
     'BUSINESS_PROCESS.EXECUTE', 'Execute Business Process Evidence',
     'Execute governed integrated business-process transactions')
) AS t(uuid_val, code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = t.code);
