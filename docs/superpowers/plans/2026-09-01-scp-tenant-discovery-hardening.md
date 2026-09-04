# SCP Tenant Discovery Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent production tenant metadata from being written to public GitHub Actions logs, preserve read-only diagnostics, and make the SCP smoke-identity recovery use an authoritative tenant derived from production state rather than a stale duplicated tenant secret.

**Architecture:** Add structural CI coverage for production tenant-enumeration/topology logging, then minimize the discovery workflow to boolean/count evidence. Update the smoke-identity reconcile path to resolve exactly one ACTIVE platform-admin tenant from the production database at runtime, mask the UUID before any use, validate the ACTIVE ADMIN assignment, and use that resolved tenant for Render/bootstrap/login without printing it.

**Tech Stack:** GitHub Actions YAML, Bash, PostgreSQL/psql, Python workflow-security scanner, pytest.

**Spec:** Incident evidence in PRs #927–#931 and current production recovery chain.

## Global Constraints

- No direct writes to `main`; protected PR merge only.
- Production DB diagnostics are read-only and fail closed.
- Never print tenant UUID/name/subdomain, admin email, DB host/name, passwords, tokens, or credentials.
- No Flyway migration changes, no RBAC weakening, no JWT changes.
- Reconcile remains explicit `mode=reconcile` + `confirm=reconcile`.

---

### Task 1: RED — lock unsafe production metadata logging

**Files:**
- Create: `tests/ci/fixtures/workflows/unsafe-production-tenant-inventory.yml`
- Modify: `tests/ci/test_workflow_security_policy.py`

- [ ] Add a fixture reproducing a Production workflow that prints tenant inventory and DB topology.
- [ ] Add tests requiring `production_tenant_inventory_logging` and `production_db_topology_logging` violations.
- [ ] Run Security Baseline on the exact branch head and confirm the new tests fail for the missing scanner behavior.

### Task 2: GREEN — extend structural scanner

**Files:**
- Modify: `scripts/ci/check_workflow_security.py`

- [ ] Detect production psql/SQL that emits tenant identity inventories (`id/name/subdomain` from `tenants`).
- [ ] Detect explicit logging of parsed production DB host/database metadata.
- [ ] Preserve safe monitoring/test fixtures.
- [ ] Run workflow-security tests and scanner to green.

### Task 3: Harden discovery and reconcile data flow

**Files:**
- Modify: `.github/workflows/scp-tenant-discovery.yml`
- Modify: `.github/workflows/scp-smoke-identity-reconcile.yml`
- Modify: `scripts/production/scp-smoke-identity-reconcile.sh`

- [ ] Replace full inventory logging with targeted counts/booleans only.
- [ ] Resolve the canonical tenant internally from exactly one ACTIVE `platform_admin` user, then verify the tenant is ACTIVE and the user has an ACTIVE ADMIN role assignment.
- [ ] Mask the resolved UUID immediately and never print it.
- [ ] Use the resolved tenant for Render env sync, bootstrap and login checks instead of the stale `CONTROL_PLANE_TENANT_ID` GitHub secret.
- [ ] Fail closed for zero/multiple candidates or missing ACTIVE ADMIN assignment.

### Task 4: Verify and release safely

- [ ] Run required PR checks on the exact head.
- [ ] Review diff for secrets, migrations and unrelated changes.
- [ ] Merge with expected-head protection only after required checks pass.
- [ ] Run reconcile with `mode=reconcile`, `confirm=reconcile`.
- [ ] Verify bootstrap 200, login 200, bootstrap disabled, final login 200.
- [ ] Run canonical production release with rollback enabled.
- [ ] Verify Render exact image, Flyway, security boundary, SCP smoke, Vercel/BFF, and `/executive` production behavior.
