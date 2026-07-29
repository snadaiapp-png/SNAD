# CRM-007-DATA-005: Payment Model Validation

> **Task:** TASK 2 — ENTITY MODEL VALIDATION (Payment)
> **Date:** 2026-07-28
> **Status:** CONDITIONAL PASS

---

## Payment Scope

Payment processing is primarily in the ERP module. CRM tracks payment-related data through:

| Entity | CRM Table | Status |
|---|---|---|
| Opportunity Amount | `crm_opportunities.amount` | PASS |
| Currency | `crm_opportunities.currency_code` | PASS |
| Pipeline Currency | `crm_pipelines.currency_code` | PASS |

---

## CRM Payment Integration

### Opportunity Amount Tracking

| Field | Type | Purpose | Status |
|---|---|---|---|
| `amount` | NUMERIC(24,6) | Deal value | PASS |
| `currency_code` | VARCHAR(3) | Currency (SAR) | PASS |

### Customer 360 Payment View

The Customer 360 endpoint returns:
- Related opportunities with amounts
- Activity history
- Timeline events

---

## Payment Methods (ERP Scope)

| Method | CRM Integration | Status |
|---|---|---|
| Cash | N/A | ERP handles |
| Card | N/A | ERP handles |
| Bank Transfer | N/A | ERP handles |

---

## Payment States (ERP Scope)

| State | CRM Integration | Status |
|---|---|---|
| Pending | N/A | ERP handles |
| Partial | N/A | ERP handles |
| Paid | N/A | ERP handles |

---

## Future CRM Payment Table

If CRM-specific payment tracking is needed:

```sql
CREATE TABLE crm_payments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    opportunity_id UUID,
    amount NUMERIC(24,6) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_method VARCHAR(20),
    payment_status VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

**Status:** NOT IMPLEMENTED (deferred to future CRM stage)

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Amount tracking | PASS |
| Currency support | PASS |
| Customer linkage | PASS (via opportunities) |
| Job linkage | PASS (via activities) |
| Payment records consistent | CONDITIONAL (ERP integration pending) |

---

**Result:** CONDITIONAL PASS (Payment is ERP scope; CRM integration points verified)
