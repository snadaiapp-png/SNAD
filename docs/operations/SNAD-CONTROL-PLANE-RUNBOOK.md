# SNAD Control Plane Runbook

## Overview
This runbook describes how the SNAD Control Plane works, how to provision tenants, and how to troubleshoot issues.

## How Control Plane Works

The Control Plane is the platform administration interface accessible only to users belonging to the configured Control Plane Tenant (`SANAD_CONTROL_PLANE_TENANT_ID`). It allows:
- Tenant provisioning (create new tenants with automatic subscription)
- Organization management (create, update, status changes)
- Membership management (create, update, role changes)
- Subscription management (plans, seats, billing cycle)
- System service monitoring
- Audit log review

## How to Create a Tenant

### Via Control Plane UI
1. Navigate to `/control-plane`
2. Click "Add Tenant"
3. Fill in: name, subdomain, admin email, admin name, billing email, plan, billing cycle, seats, trial days
4. Click "Create"
5. The system automatically:
   - Creates the tenant
   - Creates the admin user
   - Creates the primary organization
   - Creates the admin membership
   - Creates the admin role grants
   - Creates the subscription (TRIALING or ACTIVE)
6. The new tenant appears in the tenants list
7. The primary organization appears in the organizations tab

### Via API
```http
POST /api/v1/control-plane/tenants
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Acme Corp",
  "subdomain": "acme",
  "adminEmail": "admin@acme.com",
  "adminDisplayName": "Admin",
  "billingEmail": "billing@acme.com",
  "planCode": "STARTER",
  "billingCycle": "MONTHLY",
  "seatQuantity": 1,
  "trialDays": 14,
  "createDefaultOrganization": true
}
```

## How Subscription Is Created Automatically

When a tenant is created via the Control Plane:
1. `RegistrationProvisioner.provision()` creates: Tenant, Organization, Admin User, Membership, Role Grants
2. `AdminPlatformService.autoCreateSubscription()` resolves the plan (planId → planCode → STARTER)
3. `SaasAdministrationService.createSubscription()` creates the subscription record
4. Subscription status: TRIALING (if trialDays > 0) or ACTIVE (if trialDays = 0)
5. If ACTIVE, an initial invoice is generated automatically

## How to Add Organizations

1. Select a tenant in the Control Plane
2. Navigate to "Organizations & Memberships" tab
3. Click "Add Organization"
4. Fill in: name, description
5. The system checks:
   - Tenant exists
   - Subscription is active (TRIALING, ACTIVE, or PAST_DUE)
   - Organization count < maxOrganizations (from plan)
6. If checks pass: organization created
7. If checks fail: 409 Conflict with clear error message

## How to Add Memberships

1. Select a tenant → organization in the Control Plane
2. Click "Add Membership"
3. Fill in: email, display name, role code
4. The system checks:
   - Tenant exists
   - Organization exists
   - Subscription is active
   - User count < maxUsers (from plan, checked via seat_quantity)
5. If checks pass: membership created
6. If checks fail: 409 Conflict with clear error message

## Required Permissions

- `SANAD_CONTROL_PLANE_TENANT_ID` must be set in the backend environment
- The admin user must belong to the control plane tenant
- The admin user must have `ROLE.READ` and `ROLE.WRITE` capabilities
- `ControlPlaneAccessGuard` enforces tenant matching on every request

## How to Run Smoke Test

```bash
# Set environment variables
export PRODUCTION_BASE_URL=https://sanad-backend-mcrj.onrender.com
export CONTROL_PLANE_ADMIN_EMAIL=<email>
export CONTROL_PLANE_ADMIN_PASSWORD=<password>
export CONTROL_PLANE_TEST_EMAIL_DOMAIN=example.com

# Run smoke test
bash scripts/production/verify-control-plane-provisioning-smoke.sh
```

Or via GitHub Actions: Actions → Control Plane Provisioning Production Smoke → Run workflow

## How to Rollback

1. If a bad tenant was created, suspend it:
   ```http
   PATCH /api/v1/control-plane/tenants/{tenantId}/status
   {"status": "SUSPENDED", "reason": "Bad provisioning"}
   ```
2. If the code change caused issues:
   ```bash
   git revert -m 1 <merge-sha>
   git push origin main
   ```
3. Vercel auto-deploys the revert
4. Verify production health

## Troubleshooting

### "Control-plane access is not configured"
- `SANAD_CONTROL_PLANE_TENANT_ID` is not set in the backend environment
- Set it to the UUID of the control plane tenant

### "Control-plane tenant required"
- The authenticated user's tenant does not match `SANAD_CONTROL_PLANE_TENANT_ID`
- Ensure the admin user belongs to the control plane tenant

### "An active subscription is required for directory changes"
- The tenant was created without auto-subscription (old code)
- Manually create a subscription via `POST /api/v1/control-plane/subscriptions`

### "Organization limit for the subscription has been reached"
- The tenant's plan has a `max_organizations` limit
- Upgrade the plan or remove unused organizations

### "Seat limit for the subscription has been reached"
- The tenant's subscription `seat_quantity` has been exceeded
- Increase seats via `PATCH /api/v1/control-plane/subscriptions/{id}/seats`
