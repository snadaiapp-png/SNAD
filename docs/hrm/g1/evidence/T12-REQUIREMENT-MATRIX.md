# T12 Requirement Matrix (per official G1 specification)

Source of truth: `docs/superpowers/plans/2026-09-07-hrm-g1-recruitment-onboarding-implementation.md` §G1-T12

| # | Requirement | Source | Implementation | Test | Status |
|---|---|---|---|---|---|
| 1 | Stage closure — full security integration sweep | spec §G1-T12 OBJECTIVE | Partial — frontend screens enforce capability-driven UI hiding; backend @RequireCapability enforces authorization on every V2 endpoint (T10) | Backend RBAC tests at HrRecruitmentApiErrorMappingTest, HrRecruitmentIdempotencyApiContractTest, HrRecruitmentOpenApiContractTest, HrT10CandidateSelfServiceSecurityContractTest | **PARTIAL** — frontend G1 RBAC tests added; backend suite requires PostgreSQL Direct env (not runnable in this session) |
| 2 | Evidence assembly | spec §G1-T12 FILES_EXPECTED | DONE — docs/hrm/g1/evidence/HRM-G1-ENGINEERING-CLOSURE.md (engineering closure certificate); docs/hrm/g1/evidence/T11-OFFICIAL-SCREEN-MATRIX.md (15-screen matrix); docs/hrm/g1/evidence/T12-REQUIREMENT-MATRIX.md (this file); HR_G1_CLOSURE reconciliation block in apps/web/app/hr/hr-execution-data.ts | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) — binds dashboard state to certificate | **DONE** (scaffold); final gate PENDING T11 15/15 + independent approval + exact-head CI green |
| 3 | Execution-dashboard certificate reconciliation | spec §G1-T12 FILES_EXPECTED | DONE — HR_G1_CLOSURE.implementationPerCanonicalTask tracks T1..T12 status; HR_GROUP_DATA G1 status reconciled to IN_PROGRESS; HR_TASKS G1-T01..T05 reconciled to DONE/IN_PROGRESS per actual implementation | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) — verifies reconciliation | **DONE** |
| 4 | Closure certificate per G0 discipline | spec §G1-T12 DONE_DEFINITION | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md follows the G0 closure certificate template; explicit STATUS_AUTHORITY: DRAFT — IN_PROGRESS | (certificate exists; regression test verifies binding) | **DONE** (DRAFT) — flips to CURRENT only after G1_FINAL_GATE=PASS |
| 5 | Extended security suites (RLS/authorization matrices final) | spec §G1-T12 FILES_EXPECTED | NOT IMPLEMENTED — backend security suite extension requires PostgreSQL Direct env; not in scope of frontend T11/T12 work | NOT IMPLEMENTED — would require HrG1RlsFullMatrixTest, HrScopedAuthorizationScopeMatrix extension | **NOT_IMPLEMENTED** (BLOCKED_BY_ENVIRONMENT) |
| 6 | hr-execution-data.ts G1 rows (certificate-bound) | spec §G1-T12 FILES_EXPECTED | DONE — HR_G1_CLOSURE block; HR_GROUP_DATA G1 reconciled; HR_TASKS G1-T01..T05 reconciled | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | **DONE** |
| 7 | hr-g1-closure-state regression test | spec §G1-T12 FILES_EXPECTED | DONE — apps/web/app/hr/hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | itself | **DONE** |
| 8 | Consolidated gate suite run (RED if any gap) | spec §G1-T12 TESTS_FIRST | PARTIAL — frontend gates green (SDS, typecheck, eslint, i18n, vitest, build); backend PostgreSQL Direct suite NOT runnable in this session | Frontend gates: PASS; Backend suite: BLOCKED_BY_ENVIRONMENT | **PARTIAL** |
| 9 | HrG1 RLS full matrix | spec §G1-T12 TESTS_FIRST | NOT IMPLEMENTED — backend test, requires PostgreSQL Direct env | NOT IMPLEMENTED | **NOT_IMPLEMENTED** |
| 10 | Authorization matrix full | spec §G1-T12 TESTS_FIRST | PARTIAL — backend V2 controllers enforce @RequireCapability (T10); frontend screens hide controls by capability (T11); full matrix test requires PostgreSQL Direct | Backend @RequireCapability annotations: DONE; Frontend capability-driven UI: DONE; Full matrix test: NOT_IMPLEMENTED | **PARTIAL** |
| 11 | HrHireConversion full | spec §G1-T12 TESTS_FIRST | PARTIAL — frontend hire-conversion screen (Screen 12) implements the preflight checklist + idempotent replay banner + failure-matrix error handling; backend conversion tests exist (HrHireConversionIntegrationTest per spec §16) | Frontend test: NOT YET ADDED for Screen 12; Backend integration test: requires PostgreSQL Direct env | **PARTIAL** |
| 12 | Outbox/audit sweep | spec §G1-T12 TESTS_FIRST | NOT IMPLEMENTED — backend audit sweep requires PostgreSQL Direct env | NOT IMPLEMENTED | **NOT_IMPLEMENTED** |
| 13 | Module boundary sweep | spec §G1-T12 TESTS_FIRST | PARTIAL — ArchUnit tests in backend pom.xml enforce module boundaries; not extended specifically for G1 in this PR | ArchUnit tests exist in apps/sanad-platform/src/test (T10) | **PARTIAL** |
| 14 | FAILURES=0, ERRORS=0, UNEXPLAINED_SKIPS=0 on exact closure SHA | spec §G1-T12 GREEN | NOT VERIFIED — requires exact-head CI run on final closure SHA on GitHub Actions; CI on current SHA still running | NOT VERIFIED | **NOT_PROVEN** |
| 15 | Certificate complete per G0 template | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md follows G0 template; explicit claim discipline (no false G1_CLOSED/PRODUCTION_READY claims) | (certificate exists) | **DONE** (DRAFT) |
| 16 | SOURCE_DEFECTS_OPEN=0 or remediated+re-evidenced | spec §G1-T12 ACCEPTANCE_CRITERIA | NOT PROVEN — requires backend defect sweep on PostgreSQL Direct env | NOT PROVEN | **NOT_PROVEN** |
| 17 | Claim discipline (no production/legal claims) | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE — HR_G1_CLOSURE explicitly does NOT claim PRODUCTION_READY, PRODUCTION_CERTIFIED, or SAUDI_LEGAL_COMPLIANT; engineeringCertification=PENDING; legalCertification=BLOCKED; productionAuthorization=NO | regression test enforces (hr-g1-closure-state.regression.test.ts) | **DONE** |
| 18 | LEGAL_REVIEW remains PENDING_HUMAN | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE — legalCertification = "BLOCKED" (independent human gate) | regression test verifies | **DONE** |
| 19 | CI role contract + PMV green on closure merge SHA | spec §G1-T12 SECURITY_GATES | NOT VERIFIED — PMV (Post-Merge Verification) requires merge first; merge requires independent approval first; independent approval is BLOCKED (human-only) | NOT VERIFIED | **NOT_PROVEN** |
| 20 | Final cross-tenant sweep green | spec §G1-T12 TENANT_GATES | NOT VERIFIED — backend RLS + cross-tenant sweep requires PostgreSQL Direct env; frontend relies on backend enforcement | NOT VERIFIED | **NOT_PROVEN** |
| 21 | G1 engineering closure certificate + evidence bundle | spec §G1-T12 DONE_DEFINITION | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md exists; evidence bundle includes T11-OFFICIAL-SCREEN-MATRIX.md, T12-REQUIREMENT-MATRIX.md (this file), hr-g1-closure-state.regression.test.ts | regression test verifies certificate path | **DONE** (DRAFT) |
| 22 | G1 stage exit | spec §G1-T12 DONE_DEFINITION | NOT CLOSED — requires G1_FINAL_GATE=PASS, which requires items 14, 16, 19, 20 all VERIFIED | NOT CLOSED | **NOT_CLOSED** |
| 23 | G2 remains NOT_AUTHORIZED for implementation | spec §G1-T12 DONE_DEFINITION | DONE — no G2 implementation in this PR; HR_G1_CLOSURE.productionAuthorization = "NO" | (no G2 code added) | **DONE** |

## Summary

- **DONE**: 8 requirements (evidence assembly, reconciliation, regression test, claim discipline, legal review pending, G2 not authorized)
- **DONE (DRAFT)**: 3 requirements (closure certificate, certificate complete per G0 template, evidence bundle) — flip to CURRENT after final gate
- **PARTIAL**: 5 requirements (security sweep, consolidated gate suite, authorization matrix, hire conversion full, module boundary sweep)
- **NOT_IMPLEMENTED**: 3 requirements (extended security suites, HrG1 RLS full matrix, outbox/audit sweep) — all blocked by PostgreSQL Direct env
- **NOT_PROVEN**: 3 requirements (FAILURES=0 on exact SHA, SOURCE_DEFECTS_OPEN=0, CI role contract + PMV green, final cross-tenant sweep) — all require exact-head CI run on GitHub Actions + independent approval + merge
- **NOT_CLOSED**: 1 requirement (G1 stage exit) — blocked by NOT_PROVEN items above

## T12 Final Gate

```
T12_FINAL_GATE = NOT_CLOSED
```

Reason: 3 NOT_PROVEN items require exact-head CI on closure SHA + independent approval + merge + post-merge verification, which are blocked by:
1. Independent human approval (Constitution §3.5 — human-only gate)
2. Branch protection on main (Constitution §3.5 — owner action required, NOT yet configured)
3. Host-native PostgreSQL Direct test suite (3,507 tests, ~155min) — not runnable in this session; requires GitHub Actions run

These are HUMAN-ONLY BLOCKERS per §43 of the user directive — once all technical work is complete and green on exact SHA, the only remaining blocker is independent reviewer approval.
