# Tenant and Subscription Validation

## 1. Tenant Lifecycle
```
Create → Activate → Suspend → Archive → Reactivate
```
- **Create**: Via admin API or registration
- **Activate**: Status = ACTIVE
- **Suspend**: Status = SUSPENDED (blocks user login)
- **Archive**: Status = ARCHIVED (soft delete)
- **Status**: OPERATIONAL

## 2. Subscription Lifecycle
```
Plan Selection → Subscription Creation → Trial → Activation →
Module Entitlements → Usage Limits → Renewal → Upgrade/Downgrade →
Suspension → Expiration → Reactivation
```

### Database Tables (V19)
- `saas_plans`: Plan definitions (code, name, price, billing cycle)
- `saas_plan_entitlements`: Module/capability entitlements per plan
- `tenant_subscriptions`: Active subscriptions per tenant
- `subscription_change_events`: Audit trail of subscription changes
- `billing_invoices`: Billing records

### API Endpoints (Control Plane)
- `POST /api/v1/control-plane/plans` — Create plan
- `GET /api/v1/control-plane/plans` — List plans
- `POST /api/v1/control-plane/subscriptions` — Create subscription
- `PATCH /api/v1/control-plane/subscriptions/{id}/change-plan` — Change plan
- `POST /api/v1/control-plane/billing/invoices/{id}/mark-paid` — Mark invoice paid

## 3. Tenant Isolation Verification

| Test | Local (H2) | CI (PostgreSQL) | Production |
|------|-----------|-----------------|------------|
| Cross-tenant read blocked | PASS | PASS | PASS |
| Cross-tenant write blocked | PASS | PASS | PASS |
| Tenant selector mismatch | PASS | PASS | PASS |
| Suspended user blocked | PASS | PASS | PASS |
| Archived tenant blocked | PASS | PASS | PASS |
| Membership enforcement | PASS | PASS | PASS |

## 4. Subscription Enforcement
- **Backend enforcement**: YES — RBAC checks capability access
- **Frontend enforcement**: YES — UI hides modules not entitled
- **API bypass**: NOT POSSIBLE — backend RBAC is authoritative
- **Status**: OPERATIONAL

## 5. Gaps
- No automated subscription expiration handler
- No webhook for payment failures
- No proration logic for plan changes
- No usage tracking against limits

