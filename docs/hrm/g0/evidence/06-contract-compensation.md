# HRM-G0 — WS6 Contract & Compensation Foundation Verification Evidence

Task: WS6 Task 5 — WS6 verification gate
Directive: SNAD HRM-G0 MASTER MODULE COMPLETION & FINAL CLOSURE DIRECTIVE (Phase B)
Branch: `feat/hrm-g0-foundation` (PR #914 — OPEN / DRAFT / UNMERGED)

## 1. Scope verified

WS6 Tasks 1–4 implemented (all RED checkpoints committed before GREEN; clean
RED evidence in commit messages):

| Task | Deliverable | Commit |
|------|-------------|--------|
| WS6-1 | `V20260904_2` contract/compensation schema (btree_gist temporal exclusions, FORCE RLS, structural CHECKs) | `832f7e30` (RED `a0a3e98c`) |
| WS6-2 | Employment contract aggregate + service + repository + country terms validator + authorization port | `b860d9d4` |
| WS6-3 | Compensation package/component domain + service + repository (independent capabilities, sensitive-read audit, amount-free events) | `5683752c` |
| WS6-4 | `HrContractCompensationBoundaryTest` (Payroll/Accounting boundary) | (this commit) |

## 2. Complete WS6 suite (PostgreSQL Direct, local run)

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| HrEmploymentContractIntegrationTest | 6 | 0 | 0 |
| HrContractCountryPolicyIntegrationTest | 7 | 0 | 0 |
| HrCompensationIntegrationTest | 7 | 0 | 0 |
| HrCompensationAuthorizationAuditIntegrationTest | 5 | 0 | 0 |
| HrContractCompensationBoundaryTest | 2 | 0 | 0 |
| HrModuleBoundaryArchitectureTest | 6 | 0 | 0 |
| **TOTAL (WS6 suite)** | **33** | **0** | **0** |

## 3. Verbatim gate results

- `CONTRACT_HISTORY = PASS` — at most one overlapping ACTIVE primary contract
  per employment (btree_gist exclusion at the database); versions of one
  contract never overlap; amendment creates a NEW version and supersedes the
  old one (`SUPERSEDED`, `effective_to = new.effectiveFrom − 1`); historical
  effective terms are IMMUTABLE (no update-terms operation on the repository;
  DB trigger `HRM_CONTRACT_VERSION_IMMUTABLE` rejects term-column UPDATEs).
- `COMPENSATION_HISTORY = PASS` — at most one overlapping ACTIVE package per
  employment (btree_gist); revision creates a successor package via the
  predecessor chain; historical amounts immutable; one BASE_SALARY max;
  amount XOR percentage; positive values.
- `SENSITIVE_READ_AUDIT = PASS` (compensation scope) — component amounts are
  returned only after `SensitiveReadAuditService.recordOrThrow` succeeds
  (fail closed; `HRM_SCOPE_DENIED` without the independent capability and no
  audit row for denied reads); the audit row carries identifiers/
  classification/reason only — amount values never copied (sentinel-proven).
- `PAYROLL_ACCOUNTING_BOUNDARY = PASS` — no dependency from
  `hr.contract`/`hr.compensation` onto payroll/accounting/finance
  infrastructure (ArchUnit, production classes only); comment-stripped source
  scan finds NO executable payroll concepts (payslips, payroll runs,
  statutory deduction calculators, GOSI calculation engines, WPS files, GL /
  journal-entry posting, bank payment execution).
- `AMOUNT_LEAKAGE = NONE` — tested assertions prove compensation amounts do
  not appear in `hr_audit_ledger` rows, `hr_domain_event_outbox`
  `HRM.COMPENSATION.CHANGED.v1` payloads, or history-without-amounts reads.
- `GLOBAL_MODE_MARKING = PASS` — Global Mode accepts the generic terms schema
  only; jurisdiction-specific terms require a legally reviewed pack
  (`HRM_CONTRACT_TERMS_NOT_CERTIFIED`); executable content in country terms
  rejected (`HRM_CONTRACT_TERMS_INVALID`); Global Mode responses expose
  `LOCAL_COMPLIANCE_UNVERIFIED` — no statutory result is returned.
- `COUNTRY_POLICY_HOOK = PASS` — contract activation/amendment resolves the
  labor jurisdiction via `CountryPolicyResolver` and evaluates
  `HRM.CONTRACT.*` operations through the compliance engine before mutation.
- `FLYWAY_TERMINAL = 20260904.2` — `V20260904_2` is forward-only and
  additive; CRM migration-version contract tests extended
  (`CrmFlywayHistoryAssertionTest` re-run 5/0/0 locally).

## 4. Result

`WS6_CONTRACT_COMPENSATION = PASS`
