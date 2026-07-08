# Stage 13 — Customer Onboarding Flow

**Date**: 2026-07-08

---

## Customer Journey Overview

```
1. Discovery → Customer visits https://snad-app.vercel.app/
2. Signup → Customer creates account (email + password)
3. Tenant Creation → Customer creates organization/tenant
4. User Invitation → Customer invites team members
5. Role Assignment → Customer assigns roles and permissions
6. Workspace Access → Customer accesses /workspace
7. Feature Adoption → Customer uses CRM, Control Plane, etc.
8. Support → Customer accesses help and support channels
```

## Onboarding Steps (Detailed)

### Step 1: Account Creation

```
Route: / (root)
UI: LoginScreen with "Sign in" form
Action: User enters email + password → clicks "Sign in"
Backend: POST /api/auth/login → returns access token + refresh cookie
State: AUTHENTICATED → redirect to /workspace
```

### Step 2: Tenant Provisioning

```
Route: /workspace
UI: ExecutiveShell with workspace dashboard
Action: If user has no tenant → TenantPicker shows available tenants
        If user has one tenant → automatic access
        If user has multiple tenants → TenantPicker for selection
Backend: GET /api/me → returns memberships + roleGrants
```

### Step 3: User Invitation

```
Route: /control-plane (admin only)
UI: Control Plane console with user management
Action: Admin enters email → selects role → sends invitation
Backend: POST /api/users/invite → creates pending membership
Email: Invitation sent to new user (when email delivery configured)
```

### Step 4: Role Assignment

```
Route: /control-plane
UI: User management table with role dropdown
Action: Admin selects role (ADMIN, MANAGER, USER, VIEWER)
Backend: PUT /api/memberships/{id}/role → updates role grant
RBAC: Role-based access control enforced on all API endpoints
```

### Step 5: Workspace Access

```
Route: /workspace
UI: ExecutiveShell with:
  - SNAD logo (links to /workspace)
  - LanguageSwitcher (ع | EN)
  - ThemeSwitcher (Light → Dark → System)
  - User profile menu
  - Navigation links
Content: Welcome message, user info, tenant info, session status
```

### Step 6: Bilingual Experience

```
Arabic (default):
  - lang="ar" dir="rtl"
  - All UI text in Arabic (168 translation keys)
  - RTL layout via logical CSS properties

English:
  - lang="en" dir="ltr"
  - All UI text in English (168 translation keys)
  - LTR layout via logical CSS properties

Switching: LanguageSwitcher in ExecutiveShell
Persistence: localStorage (snad.locale)
```

### Step 7: Theme Experience

```
Light (default):
  - data-theme="light"
  - Light surface tokens

Dark:
  - data-theme="dark"
  - Dark surface tokens

System:
  - Follows prefers-color-scheme
  - Updates dynamically on OS preference change

Switching: ThemeSwitcher in ExecutiveShell
Persistence: localStorage (snad.theme)
```

## Onboarding Verification

```
✅ Account creation works (login form functional)
✅ Tenant provisioning works (TenantPicker functional)
✅ User invitation UI available (Control Plane)
✅ Role assignment available (Control Plane)
✅ Workspace accessible post-login
✅ Bilingual switching works (ar ↔ en)
✅ Theme switching works (light/dark/system)
✅ All 6 routes return HTTP 200
```

## Onboarding Readiness

```
Customer Onboarding Flow: DOCUMENTED and TESTABLE
Bilingual support: VERIFIED (ar/en, 168 keys)
Theme support: VERIFIED (light/dark/system)
RTL/LTR: VERIFIED
```
