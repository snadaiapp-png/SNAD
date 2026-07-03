# Break-Glass Access Policy

**Status:** ACTIVE
**Date:** 2026-06-25

---

## Purpose

Defines the approved procedure for emergency production access when normal authentication mechanisms are unavailable.

## Prohibited Actions

The following are **NEVER** permitted, even in emergencies:

1. Password inputs through GitHub Actions `workflow_dispatch`
2. Direct production database mutation from GitHub Actions
3. Shared credentials in workflows
4. Unaudited production access
5. Permanent emergency permissions
6. Unpinned package installation while production secrets are in scope

## Approved Break-Glass Procedure

### Step 1: Authorization
- Require two-person approval (owner + one other authorized person)
- Document the emergency reason
- Record timestamp and participants

### Step 2: Temporary Access
- Use provider console (Render Dashboard, Supabase Dashboard)
- Use application-supported password reset (if available)
- Use provider-supported database administration tools
- All actions must be through the provider's native interface

### Step 3: Audit Record
- Record all actions taken
- Record all credentials accessed
- Record all changes made
- Create immutable audit log entry

### Step 4: Post-Emergency
- Expire all temporary access immediately
- Rotate all credentials that were accessed
- Verify application health post-rotation
- Review audit log for unauthorized actions
- Close the emergency with documented evidence

## Recurrence Prevention

The workflow security scanner (scripts/ci/check_workflow_security.py) is enforced by CI and will reject any workflow that:
- Accepts passwords via workflow_dispatch inputs
- Accesses production database directly
- Mutates user credentials from GitHub Actions
- Uses write-all permissions
- Contains force-push commands
