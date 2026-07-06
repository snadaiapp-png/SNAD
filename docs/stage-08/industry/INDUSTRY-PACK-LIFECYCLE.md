# SANAD Stage 08 — Industry Pack Lifecycle

**Document ID:** `SANAD-ST08-IND-LIFE-001`
**Track:** 8.4
**Date:** 2026-07-06

---

## 1. States

```text
Drafted
   ↓
Validated
   ↓
Signed
   ↓
Published
   ↓
Certified
   ↓
Installed (per tenant)
   ↓
Configured (per tenant)
   ↓
Upgraded
   ↓
Rolled Back (optional)
   ↓
Uninstalled (per tenant)
   ↓
Deprecated
   ↓
Sunset
```

---

## 2. Install Workflow

1. Tenant admin selects pack from Marketplace.
2. Manifest validated.
3. Permissions displayed; admin approves.
4. Migrations run (up).
5. Seed data loaded.
6. Roles and workflows registered.
7. Configuration defaults applied.
8. Tenant-specific configuration captured.
9. Audit record written.

---

## 3. Upgrade Workflow

1. New version published and certified.
2. Tenant admin notified.
3. Admin initiates upgrade.
4. Compatibility check (permissions, dependencies).
5. Migrations run (up).
6. Roles and workflows updated.
7. Audit record written.

---

## 4. Rollback Workflow

1. Admin initiates rollback to previous version.
2. Migrations run (down).
3. Roles and workflows reverted.
4. Configuration reverted to previous.
5. Audit record written.

---

## 5. Uninstall Workflow

1. Admin initiates uninstall.
2. Dependent apps warned.
3. Migrations run (down).
4. Roles and workflows removed.
5. Data retained by default (configurable: purge after retention).
6. Audit record written.
