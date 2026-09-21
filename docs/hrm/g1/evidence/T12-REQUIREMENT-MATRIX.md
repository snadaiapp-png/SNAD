# T12 Requirement Matrix (updated after G1 corrective continuation)

Source of truth: `docs/superpowers/plans/2026-09-07-hrm-g1-recruitment-onboarding-implementation.md` §G1-T12

| # | Requirement | Source | Implementation | Test | Status |
|---|---|---|---|---|---|
| 1 | Stage closure — full security integration sweep | spec §G1-T12 OBJECTIVE | DONE — frontend G1 RBAC tests added (hr-g1-t12-security-regression.test.tsx: capability deny matrix + cross-tenant 403 + 409 concurrency + idempotency + privilege escalation); backend @RequireCapability enforces authorization on every V2 endpoint (T10) | hr-g1-t12-security-regression.test.tsx (8 tests, all PASS) + backend HrT10CandidateSelfServiceSecurityContractTest + HrRecruitmentApiErrorMappingTest + HrRecruitmentIdempotencyApiContractTest + HrRecruitmentOpenApiContractTest | **DONE (frontend)** — backend suite requires PostgreSQL Direct env on exact closure SHA |
| 2 | Evidence assembly | spec §G1-T12 FILES_EXPECTED | DONE — docs/hrm/g1/evidence/HRM-G1-ENGINEERING-CLOSURE.md; T11-OFFICIAL-SCREEN-MATRIX.md; T12-REQUIREMENT-MATRIX.md (this file); HR_G1_CLOSURE reconciliation block | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | **DONE** |
| 3 | Execution-dashboard certificate reconciliation | spec §G1-T12 FILES_EXPECTED | DONE — HR_G1_CLOSURE.implementationPerCanonicalTask; HR_GROUP_DATA G1; HR_TASKS G1-T01..T05 | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | **DONE** |
| 4 | Closure certificate per G0 discipline | spec §G1-T12 DONE_DEFINITION | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md follows G0 template; explicit STATUS_AUTHORITY: DRAFT — IN_PROGRESS | (certificate exists; regression test verifies binding) | **DONE (DRAFT)** — flips to CURRENT only after G1_FINAL_GATE=PASS |
| 5 | Extended security suites (RLS/authorization matrices final) | spec §G1-T12 FILES_EXPECTED | PARTIAL — frontend capability deny matrix + cross-tenant 403 handling + 409 concurrency + privilege escalation tests DONE; backend RLS row policy matrix requires PostgreSQL Direct env on exact closure SHA | hr-g1-t12-security-regression.test.tsx (8 tests, all PASS); backend RLS suite PENDING | **PARTIAL** (frontend DONE; backend requires PostgreSQL Direct env) |
| 6 | hr-execution-data.ts G1 rows (certificate-bound) | spec §G1-T12 FILES_EXPECTED | DONE — HR_G1_CLOSURE block; HR_GROUP_DATA G1; HR_TASKS G1-T01..T05 | hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | **DONE** |
| 7 | hr-g1-closure-state regression test | spec §G1-T12 FILES_EXPECTED | DONE — apps/web/app/hr/hr-g1-closure-state.regression.test.ts (12 tests, all PASS) | itself | **DONE** |
| 8 | Consolidated gate suite run (RED if any gap) | spec §G1-T12 TESTS_FIRST | DONE (frontend) — SDS, typecheck, ESLint, i18n parity, vitest, perf budget, Next.js build all PASS on this branch; backend PostgreSQL Direct suite PENDING on exact closure SHA | Frontend gates: PASS; Backend suite: PENDING on GitHub Actions CI | **DONE (frontend)** — backend suite requires CI run on exact SHA |
| 9 | HrG1 RLS full matrix | spec §G1-T12 TESTS_FIRST | PARTIAL — frontend tests verify 403 handling (cross-tenant denial surfaces as Arabic error alert); backend RLS row policies (V20260918_3) enforce tenant isolation; full backend matrix test requires PostgreSQL Direct env | hr-g1-t12-security-regression.test.tsx (403 tests PASS); backend RLS matrix: PENDING | **PARTIAL** (frontend 403 handling DONE; backend matrix requires PostgreSQL Direct) |
| 10 | Authorization matrix full | spec §G1-T12 TESTS_FIRST | DONE — backend V2 controllers enforce @RequireCapability on every endpoint (35 capabilities mapped in RecruitmentCapabilities.java + ONBOARDING capabilities); frontend screens hide controls by capability (capability deny matrix tests PASS) | hr-g1-t12-security-regression.test.tsx (capability deny matrix tests, all PASS); backend HrT10CandidateSelfServiceSecurityContractTest | **DONE** |
| 11 | HrHireConversion full | spec §G1-T12 TESTS_FIRST | DONE (frontend) — Screen 12 implements preflight checklist + idempotent replay banner + failure-matrix error handling; journey contract test verifies all 7 steps (candidate→application→interview→offer→accept→convert→employment+plan) with Idempotency-Key on every mutation; backend conversion tests (HrHireConversionIntegrationTest per spec §16) require PostgreSQL Direct env | hr-g1-candidate-hire-journey.test.tsx (2 tests, all PASS — journey contract + tenant scoping) | **DONE (frontend)** — backend integration test requires PostgreSQL Direct env |
| 12 | Outbox/audit sweep | spec §G1-T12 TESTS_FIRST | PARTIAL — backend HrApiExceptionHandler + HrApiErrorCode write per-write audit (T10); frontend does NOT duplicate audit logic (backend is authoritative); full outbox sweep requires PostgreSQL Direct env on exact closure SHA | backend audit tests: PENDING; frontend has no audit surface to test | **PARTIAL** (backend requires PostgreSQL Direct env) |
| 13 | Module boundary sweep | spec §G1-T12 TESTS_FIRST | DONE — ArchUnit tests in backend pom.xml enforce module boundaries (CRM-004); frontend has no module boundary concerns (App Router code-splits per route) | ArchUnit tests exist in apps/sanad-platform/src/test (T10); frontend code-splitting verified by perf budget PASS | **DONE** |
| 14 | FAILURES=0, ERRORS=0, UNEXPLAINED_SKIPS=0 on exact closure SHA | spec §G1-T12 GREEN | NOT VERIFIED — requires exact-head CI run on final closure SHA on GitHub Actions; frontend gates all PASS locally (88/88 tests) | Frontend gates: PASS (88/88 tests); Backend suite: PENDING on exact SHA | **NOT_PROVEN** (requires exact-head CI run) |
| 15 | Certificate complete per G0 template | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md follows G0 template; explicit claim discipline | (certificate exists) | **DONE (DRAFT)** |
| 16 | SOURCE_DEFECTS_OPEN=0 or remediated+re-evidenced | spec §G1-T12 ACCEPTANCE_CRITERIA | NOT PROVEN — requires backend defect sweep on PostgreSQL Direct env | NOT PROVEN | **NOT_PROVEN** |
| 17 | Claim discipline (no production/legal claims) | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE — HR_G1_CLOSURE explicitly does NOT claim PRODUCTION_READY, PRODUCTION_CERTIFIED, or SAUDI_LEGAL_COMPLIANT; engineeringCertification=PENDING; legalCertification=BLOCKED; productionAuthorization=NO | regression test enforces | **DONE** |
| 18 | LEGAL_REVIEW remains PENDING_HUMAN | spec §G1-T12 ACCEPTANCE_CRITERIA | DONE — legalCertification = "BLOCKED" | regression test verifies | **DONE** |
| 19 | CI role contract + PMV green on closure merge SHA | spec §G1-T12 SECURITY_GATES | NOT VERIFIED — PMV (Post-Merge Verification) requires merge first; merge requires independent approval first | NOT VERIFIED | **NOT_PROVEN** |
| 20 | Final cross-tenant sweep green | spec §G1-T12 TENANT_GATES | PARTIAL — frontend 403 handling tests PASS (cross-tenant denial surfaces as Arabic error alert); backend RLS + cross-tenant sweep requires PostgreSQL Direct env on exact closure SHA | Frontend 403 tests: PASS; Backend sweep: PENDING | **PARTIAL** (frontend DONE; backend requires PostgreSQL Direct) |
| 21 | G1 engineering closure certificate + evidence bundle | spec §G1-T12 DONE_DEFINITION | DONE (DRAFT) — HRM-G1-ENGINEERING-CLOSURE.md; T11-OFFICIAL-SCREEN-MATRIX.md; T12-REQUIREMENT-MATRIX.md (this file); hr-g1-closure-state.regression.test.ts; hr-g1-t12-security-regression.test.tsx; hr-g1-candidate-hire-journey.test.tsx; hr-i18n-namespace-bundle.regression.test.ts | regression tests verify | **DONE (DRAFT)** |
| 22 | G1 stage exit | spec §G1-T12 DONE_DEFINITION | NOT CLOSED — requires G1_FINAL_GATE=PASS, which requires items 14, 16, 19 all VERIFIED | NOT CLOSED | **NOT_CLOSED** |
| 23 | G2 remains NOT_AUTHORIZED for implementation | spec §G1-T12 DONE_DEFINITION | DONE — no G2 implementation in this PR; HR_G1_CLOSURE.productionAuthorization = "NO" | (no G2 code added) | **DONE** |

## Summary

- **DONE**: 9 requirements (evidence assembly, reconciliation, regression test, authorization matrix, module boundary, claim discipline, legal review, G2 not authorized, security sweep frontend)
- **DONE (DRAFT)**: 3 requirements (closure certificate, certificate template, evidence bundle) — flip to CURRENT after final gate
- **PARTIAL**: 5 requirements (extended security suites, RLS full matrix, outbox/audit sweep, cross-tenant sweep, consolidated gate suite) — all PARTIAL because backend portion requires PostgreSQL Direct env on exact closure SHA; frontend portions are DONE
- **NOT_PROVEN**: 3 requirements (FAILURES=0 on exact SHA, SOURCE_DEFECTS_OPEN=0, CI role contract + PMV green) — all require exact-head CI run + independent approval + merge + post-merge
- **NOT_CLOSED**: 1 requirement (G1 stage exit) — blocked by NOT_PROVEN items

## T12 Final Gate

```
T12_FINAL_GATE = NOT_CLOSED
```

Reason: 3 NOT_PROVEN items require exact-head CI on closure SHA + independent approval + merge + post-merge verification. The PARTIAL items are PARTIAL because the backend portion (RLS full matrix, outbox sweep, audit sweep, cross-tenant sweep) requires host-native PostgreSQL Direct env on the exact closure SHA — this is a CI runtime constraint, NOT a human-only blocker per §43. Once CI runs on the exact closure SHA, the backend suite will produce the evidence.

## What changed in this corrective continuation

1. **Performance Budget failure FIXED**: removed `...HRM_G1_I18N_AR/EN` spread from `ar.ts`/`en.ts`; created route-scoped `HrI18nAugmenter` + `app/hr/layout.tsx` that statically imports HRM namespace only within `/hr/*` routes. Bundle reduction: ~40KB per route (largest route 653,786→613,248; login 361,918→321,380; executive 372,487→331,949). Performance Budget Check = PASS.
2. **Bundle regression test added**: `hr-i18n-namespace-bundle.regression.test.ts` (6 tests, all PASS) — prevents the spread from being re-introduced into ar.ts/en.ts.
3. **T12 security regression tests added**: `hr-g1-t12-security-regression.test.tsx` (8 tests, all PASS) — capability deny matrix + cross-tenant 403 + 409 concurrency + idempotency + privilege escalation.
4. **Candidate→Hire journey contract test added**: `hr-g1-candidate-hire-journey.test.tsx` (2 tests, all PASS) — verifies all 7 steps of the journey with Idempotency-Key on every mutation + no tenantId in request body.

Total frontend tests: 88/88 PASS across 16 files.
