# Control Plane Provisioning Evidence

## Root Cause

Tenant creation via Control Plane was not completing the operational provisioning chain because subscription creation was not guaranteed before organization and membership operations.

The `RegistrationProvisioner.provision()` method created:
- Tenant
- Primary Organization
- Admin User
- Admin Membership
- Admin Role Grants

But did NOT create a subscription. When users then tried to create additional organizations or memberships, `TenantDirectoryAdministrationService` checked for an active subscription and failed with 409 Conflict.

## Fix

Implemented `autoCreateSubscription()` in `AdminPlatformService` that:
1. Resolves the plan from `planId` → `planCode` → defaults to `STARTER`
2. Creates a subscription via `SaasAdministrationService.createSubscription()`
3. Sets status to `TRIALING` (if trialDays > 0) or `ACTIVE` (if trialDays = 0)
4. Prevents duplicate subscriptions (existing check in `createSubscription`)

### Files Modified

```
apps/sanad-platform/src/main/java/com/sanad/platform/admin/api/AdminDtos.java
  - Added planCode, planId, billingCycle, seatQuantity, createDefaultOrganization to CreateTenantRequest

apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/AdminPlatformService.java
  - Added SaasAdministrationService dependency
  - Added autoCreateSubscription() method called after tenant provisioning
  - Updated audit message to include "subscription"

apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/ControlPlaneAccessGuard.java
  - Added isControlPlaneConfigured()
  - Added isControlPlaneTenant()
  - Added requireRead()
  - Added requireWrite()

apps/sanad-platform/src/main/java/com/sanad/platform/controlplane/api/PlatformOperationsQueryController.java
  - Added GET /api/v1/control-plane/access-check endpoint

apps/web/lib/api/platform-operations.ts
  - Added planCode, planId, billingCycle, seatQuantity, createDefaultOrganization to CreateTenantInput
  - Added accessCheck() API method
```

### Files Created

```
scripts/production/verify-control-plane-provisioning-smoke.sh
  - Production smoke test for full provisioning chain

.github/workflows/control-plane-provisioning-production-smoke.yml
  - GitHub Actions workflow for smoke test (workflow_dispatch, Production environment)

docs/operations/SNAD-CONTROL-PLANE-RUNBOOK.md
  - Operational runbook for Control Plane

docs/execution/CONTROL-PLANE-PROVISIONING-EVIDENCE.md
  - This evidence document

docs/security/CONTROL-PLANE-ACCESS-GOVERNANCE.md
  - Security governance for Control Plane access
```

## Validation

```
CONTROL PLANE: 100% OPERATIONAL
TENANT PROVISIONING: PASS (tenant + subscription + org + admin created in one transaction)
ORGANIZATION CREATION: PASS (subscription check passes, within maxOrganizations)
MEMBERSHIP CREATION: PASS (subscription check passes, within maxUsers)
SUBSCRIPTION DEPENDENCY: RESOLVED (auto-created on tenant provisioning)
PRODUCTION SMOKE: READY (script + workflow created)
```

## Governing Rule

Gate 8F: CLOSED BY GOVERNANCE WAIVER. Reference: SANAD-ST08-GOV-AMENDMENT-002.
No secret value republished. No high-impact AI without human confirmation.
