# HRM-G0 Evidence — 05 API/UI Cutover (WS5 Tasks 8–12)

Evidence record for WS5 Tasks 8–12 of the HRM-G0 foundation plan
(`docs/superpowers/plans/2026-08-27-hrm-g0-05-api-ui-cutover.md`, branch
`feat/hrm-g0-foundation`, PR #914). PostgreSQL Direct only (127.0.0.1:5432/sanad,
role `sanad`, `rolsuper=false`, `rolbypassrls=false`). No Docker, no
Testcontainers. No production targeting at any point.

## WS5 Task 8 — Typed web client + shared HR workspace

- Commit: `eb1cab6e` — `feat(hrm): add typed web client and workspace`
- `/hr` execution dashboard moved intact to `/hr/execution/page.tsx`.
- `lib/api/hr-v2-api.ts`: full 58-op canonical v2 surface, typed request/response
  DTOs mirroring backend records, `Idempotency-Key` on every POST,
  `expectedVersion` bodies where the API requires them, `HrmV2ApiError` +
  canonical error-envelope parsing.
- `hr-workspace.tsx`: authoritative nav (`/hr`, `/hr/employees`,
  `/hr/org-structure`, `/hr/jobs`, `/hr/positions`, `/hr/assignments`,
  `/hr/compliance`, `/hr/execution`), `aria-current`, nav landmark, UX-only
  capability gating.
- `hr-feedback.tsx`: Arabic safe errors for all canonical `HRM_*` codes and HTTP
  statuses; never exposes internals. `hr-compliance-badge.tsx`: GLOBAL_MODE is
  never rendered green.
- Evidence: `hr-v2-api.test.ts` 27/27, `hr-workspace.test.tsx` 5/5.
- Gates: `tsc --noEmit` PASS, lint 0 errors, production build PASS.

## WS5 Task 9 — Employee Directory + Employee 360

- Commit: `d742045d` — `feat(hrm): add employee directory and 360 profile`
- Directory: safe join of `listEmployments` + `listPeople`, search/status/
  classification filters, Arabic labels, leak-proof restricted-field test.
- Employee 360: 10 tabs, capability UX gating, audited private PII read on open,
  lazy compensation/audit loads with ref guards, lifecycle confirm dialog with
  `effectiveDate` + `expectedVersion` + `Idempotency-Key`, terminate labelled
  `إنهاء خدمة` (never `حذف`).
- Evidence: directory 7/7, 360 15/15; full suite 765/765 at commit time.

## WS5 Task 10 — Org structure / jobs / positions / assignments / compliance

- Commit: `d591fa69` — `feat(hrm): add operational structure and compliance workspace`
- Org structure: `asOf` effective-dated org chart; future-dated revisions hidden
  at today, visible at their effective date (pinned by test).
- Positions: occupancy DERIVED from ACTIVE assignments covering today; ended
  assignment does not occupy (pinned). Freeze/close with `Idempotency-Key`; no
  manual occupancy toggle (asserted absent).
- Assignments: transfer/change-manager with `expectedVersion` +
  `Idempotency-Key`; `409 HRM_CONCURRENCY_CONFLICT` renders the canonical
  conflict message, never success.
- Compliance: override request form (explicit rule reference; backend
  re-validates HARD rules), approve/reject/revoke gated by APPROVE capability,
  `422 HRM_COMPLIANCE_BLOCKED` rendered safely.
- `/hr` dashboard: authoritative counts, per-surface independent fetch (a 403 on
  one surface does not blank the dashboard), no mock data.
- Gates at `d591fa69`: full suite 786/786, `tsc` PASS, lint 0 errors, production
  build PASS emitting 9 HR routes.

## WS5 Task 11 — Deterministic tenant cutover + rollback

Artifacts: `scripts/hrm/g0-cutover-tenant.sql`,
`scripts/hrm/g0-rollback-tenant.sql`, `HrCutoverStateIntegrationTest.java`.

### Integration test (PostgreSQL Direct)

```text
TEST_CLASS = HrCutoverStateIntegrationTest
TOTAL = 2, PASSED = 2, FAILURES = 0, ERRORS = 0, SKIPPED = 0
migratingTenantCanReadV1ButCannotWriteV1      : GET 200; POST/PATCH 409 HRM_MIGRATION_REQUIRED
canonicalTransitionRequiresZeroUnresolvedRows : unresolved rows → BLOCKED (never CANONICAL);
                                                empty tenant → LEGACY (WS2 semantics)
```

Task 11 Step 5 backend gate (same run, PostgreSQL Direct):

```text
11 gate classes — TOTAL=125 PASSED=125 FAILURES=0 ERRORS=0 SKIPPED=0
(HrApiV2ContractTest 35, HrApiV2AuthorizationTest 3, People 26, Employment 11,
Assignment 15, Structure 12, Sensitive 11, HrOpenApiContractTest 1,
PlatformApiCountTest 4, HrV1CompatibilityIntegrationTest 5, CutoverState 2)
```

Frontend (unchanged since `d591fa69`, re-verified on this tree): full suite
786/786 PASS.

### Defects found and fixed by rehearsal (root-cause records)

1. `HrCutoverStateIntegrationTest` (TEST_DEFECT, session-restart partial write):
   missing `BeforeEach` import; `stateOf` used transaction-local `set_config`
   which evaporates under autocommit; `reconcileTenant` used `executeUpdate` on
   a result-returning call; fixture cleanup order violated
   `fk_hr_employment_status_periods_employment`. All fixed locally; no product
   code touched.
2. `g0-cutover-tenant.sql` / `g0-rollback-tenant.sql`
   (CUTOVER_SCRIPT_DEFECT): psql does not interpolate `:'var'` inside
   dollar-quoted DO bodies — freeze/rollback blocks received the literal string
   and aborted; and transaction-local `set_config(..., true)` evaporates between
   autocommit statements, breaking tenant-scope assertion/RLS for every later
   statement. Fixed: session-scoped binding + `_g0_tenant_ctx` temp table.
3. `g0-rollback-tenant.sql` (CUTOVER_SCRIPT_DEFECT): people deletion ran while
   the ledger still referenced them (`fk_hr_legacy_employee_mappings_person`).
   Fixed with FK-safe ordering: capture ledger-owned people → unlink
   employments → remove person private/identifier sub-records → clear ledger
   refs → delete captured people → LEGACY. No safety guard was weakened.

### Cutover rehearsal (rehearsal tenant, local PostgreSQL Direct)

```text
CUTOVER_REHEARSAL = PASS
TENANT            = c1100000-0000-4000-8000-000000000011 (synthetic, disposable)
INITIAL_STATE     = LEGACY
FINAL_STATE       = CANONICAL
BACKFILLED_ROWS   = 3 people / 3 assignments / 1 org unit + 1 ACTIVE org unit
                    version / 1 ACTIVE position version
RECONCILIATION    = all gates passed (hr_reconcile_tenant_report)
DUPLICATES        = 0 duplicate canonical persons
TENANT_LEAKAGE    = 0 (all canonical rows map 1:1 into the tenant ledger;
                    cross-tenant migration call denied 42501)
RERUN_RESULT      = refused fail-closed ("already CANONICAL"), state unchanged
RESULT            = PASS
```

### Rollback rehearsals (rehearsal tenants only)

```text
ROLLBACK_REHEARSAL         = PASS
A (ambiguous employer)     : 2 effective LEs → cutover BLOCKED, never CANONICAL;
                             rollback → LEGACY; legacy employees preserved (3/3).
B (partial failure)        : duplicate user_id fixture → cutover fail-closed
                             BLOCKED with exactly 1 canonical person migrated;
                             rollback → LEGACY.
INITIAL_STATE              = BLOCKED (B) / BLOCKED (A)
ROLLBACK_ALLOWED           = true (only before CANONICAL)
ROWS_REMOVED               = ledger-scoped only: assignments 1→0, people 1→0;
                             ledger refs cleared (0 dangles)
UNRELATED_DATA_PRESERVED   = legacy employees 3/3, status-period history 3/3,
                             legacy departments 1/1
TENANT_ISOLATION           = cross-tenant migration call denied (42501);
                             all cleanup tenant-scoped
FINAL_STATE                = LEGACY
CANONICAL_GUARD            = rollback refused on CANONICAL tenant; state unchanged
RERUN_SAFETY               = after operator data fix, full cutover rerun reached
                             CANONICAL with 3/3 people (idempotent structure reuse)
RESULT                     = PASS
```

## WS5 Task 12 — Preview / production rehearsal

(Appended when Task 12 evidence is produced.)

## CI records

```text
#3219  run 33901478062  sha 101e65c8  ALL GREEN
       (Maven Test Suite SUCCESS, CRM Integration SUCCESS,
        PostgreSQL Acceptance SUCCESS)
```
