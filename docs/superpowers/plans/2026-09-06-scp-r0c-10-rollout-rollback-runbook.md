# R0C-10 Rollout / Rollback Runbook — Subscription Multiplicity Runtime

Date: 2026-09-06
Scope: `scp/r0c-10-subscription-multiplicity-runtime`
Model: `SUBSCRIPTION_MULTIPLICITY_MODEL=MODEL_B` (historical 0..N, effective 0..1)

---

## 1. Hard Invariant

```
OLD_BINARY + MULTIPLE_SUBSCRIPTION_ROWS = NEVER ALLOWED
```

An old (pre-R0C-10) binary must never run against a database where any tenant
has more than one `tenant_subscriptions` row — and, structurally, an old
binary can never CREATE that state (see §2). The rollout order below keeps
both halves of the invariant true at every instant.

## 2. Mixed-Version Safety (proven, not assumed)

Production has exactly ONE INSERT path into `tenant_subscriptions`:
`SaasAdministrationService.createSubscription` (verified by R0C-10 forensic
inventory; no bootstrap/registration/seed INSERT path exists in production
code).

- OLD binary guard: `COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?`
  counts ALL rows (including terminal history) → CONFLICT when any row exists.
  Therefore an old binary can never insert a second row for a tenant that
  already has history.
- Two concurrent old-binary creations for a history-less tenant are serialized
  by the storage invariant in either schema state (legacy `UNIQUE(tenant_id)`
  pre-migration; partial unique `uk_tenant_subscriptions_effective` after).

`OLD_BINARY_SECOND_ROW_CREATION_PATHS=0`.

## 3. Rollout Order (STAGES A–D)

### STAGE A — converged binary, gate OFF
- Deploy the R0C-10 binary (consumer-converged: billing/entitlement/impact/
  directory/creation-guard all resolve the EFFECTIVE subscription).
- `FEATURE_GATE_DEFAULT=OFF`: `sanad.scp.subscription.expired-successor.enabled`
  unset / `SANAD_SCP_EXPIRED_SUCCESSOR_ENABLED` unset → successor creation
  disabled. Observable behavior for existing single-row tenants is unchanged;
  terminal-history tenants remain fail-closed (R0C-9 dead end preserved).

### STAGE B — schema migration, gate still OFF
- Apply `V20260906_1__scp_subscription_multiplicity_model_b.sql`
  (drops `uk_tenant_subscriptions_tenant`, creates partial unique
  `uk_tenant_subscriptions_effective` + `idx_tenant_subscriptions_tenant_status`).
- Forward-only, additive-only, no data rewrite, no RLS change. Old and new
  binaries both function against the migrated schema (old binaries are
  guard-restricted to one row per tenant; the partial unique admits that).
- Do NOT proceed while any stage-A-era binary remains unproven.

### STAGE C — prove fleet compatibility
- Inventory ALL live instances of the platform binary; verify each runs the
  converged (R0C-10) code: no instance predates the MODEL_B guard.
- Verify no tenant has >1 historical row yet (they cannot, gate is OFF).
- Only after this proof is the feature enablement permitted.

### STAGE D — enable EXPIRED successor feature (NOT part of R0C-10 execution)
- Set the gate ON per environment (property or env var).
- Only the approved EXPIRED continuation path may now insert a successor row.
- R0C-10 delivers and certifies this readiness; production enablement is an
  explicit operator decision outside this task (`DO_NOT_ENABLE_PROD=YES`).

## 4. Rollback Boundaries

### Before any tenant has >1 historical row
- Rollback to a pre-multiplicity binary/schema is allowed ONLY with proven
  compatibility: the legacy `UNIQUE(tenant_id)` constraint can be restored
  (re-adding it succeeds only while every tenant has ≤1 row — verify first).
- The R0C-10 binary itself is rollback-compatible in both schema states.

### After ANY tenant has multiple subscription rows
```
ROLLBACK_TO_PRE_MULTIPLICITY_BINARY = FORBIDDEN
```
- Restoring `UNIQUE(tenant_id)` would require deleting historical rows —
  physical DELETE of subscription history is FORBIDDEN (frozen contract).
- Allowed rollback direction: only to a MULTPLICITY-COMPATIBLE binary
  (i.e., the R0C-10 converged binary or later).
- The feature kill-switch (gate OFF) may disable NEW successor creation at
  any time; existing historical rows remain and remain visible to
  ALL_HISTORY/reporting consumers.

## 5. Kill-Switch Reference

| Control | Effect | Data impact |
|---------|--------|-------------|
| `sanad.scp.subscription.expired-successor.enabled=false` / env unset | successor creation rejected (legacy fail-closed contract) | none; existing rows untouched |
| gate ON | EXPIRED continuation allowed | new immutable history rows only |

There is no mechanism in R0C-10 that deletes, mutates, or "repairs" historical
rows — rollback never requires one.

## 6. Verification Checklist per Stage

- STAGE A: `mvn` gate evidence (R0C-10 certification doc); gate readback false.
- STAGE B: `flyway_schema_history` contains `20260906.1`; `pg_indexes` shows
  `uk_tenant_subscriptions_effective` (partial) and
  `idx_tenant_subscriptions_tenant_status`; `pg_constraint` shows
  `uk_tenant_subscriptions_tenant` gone; RLS state on unaffected tables
  unchanged.
- STAGE C: binary-version inventory of all instances ≥ R0C-10; per-tenant row
  count = 1 everywhere.
- STAGE D: gate readback true in target environment only; successor creation
  attempted only for EXPIRED-history tenants; dunning/billing/entitlement
  dashboards unchanged for single-row tenants.
