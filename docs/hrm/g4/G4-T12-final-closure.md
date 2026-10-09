# HRM G4-T12 — Engineering Final Closure Evidence (candidate)

**Status:** `CANDIDATE / BLOCKED — HRM_G4_ENGINEERING is NOT yet CLOSED`

## Scope and immutable provenance
- T8 Payroll API predecessor (closed): `db4a220950e0d7c682263534c4f1e224c13622b8`.
- T9 Payroll review UI predecessor (closed): `e6511a7bc1496086fa0f1fbc2c47383f516f7c2f`.
- T10 authenticated acceptance predecessor (closed): `c43bd6e11c590ff49b7547de174e9024a1531b78`.
- T11 Full Certification protected squash merge: PR [#1323](https://github.com/snadaiapp-png/SNAD/pull/1323), `39c7c4605af005bf40b3576500b6584d93c0fadb`.
- Post-merge Vercel release guard integration correction, independently reviewed and protected squash merged: PR [#1329](https://github.com/snadaiapp-png/SNAD/pull/1329), `33f1b0528e2617a1e5effde5b6d76e21a6246d8f`.
- This candidate certificate does **not** reopen T8/T9/T10 or modify their certified implementation.

## Verified post-merge deployment evidence on release SHA

Source: [exact commit checks](https://github.com/snadaiapp-png/SNAD/commit/33f1b0528e2617a1e5effde5b6d76e21a6246d8f/checks); [Vercel reconciliation job](https://github.com/snadaiapp-png/SNAD/actions/runs/37923495212/job/113796820778).

- 9 successful commit checks, 1 skipped, no failed/pending checks at observation.
- `Reconcile Vercel Production to exact main` completed `success`.
- Vercel production identity proof: `deploymentId=dpl_EU5jKrMG3mYK2rzgGWTrazwgASPF`, `sha=33f1b0528e2617a1e5effde5b6d76e21a6246d8f`, `state=READY`, `target=production`.
- Production alias identity gate: `PRODUCTION_ALIAS_CREATED_DEPLOYMENT = PASS`.
- Production readiness probes: `/hr/performance/goals` and `/hr/performance/reviews` each HTTP 200.
- Production evidence covers the verified release SHA and these observed gates; it does not imply all HRM payroll operational journeys or country statutory compliance are certified.

## Current exact-main certification evidence (2026-10-09)

Release SHA: `b3579096a28e5d1dc01ced5e314846f1e9e0e29f` (PR [#1336](https://github.com/snadaiapp-png/SNAD/pull/1336), protected merge). [Post-merge workflow](https://github.com/snadaiapp-png/SNAD/actions/runs/37976964734) on this exact SHA:

- JOB A — Frontend verification: **SUCCESS**
- JOB B — Backend compile: **SUCCESS**
- JOB C — PostgreSQL Direct integration: **SUCCESS**
- JOB D — HRM focused security/RLS: **SUCCESS**
- JOB E — Security & governance scans: **SUCCESS**
- JOB F — Final evidence aggregation: **SUCCESS**; log states `RESULT: PASS — all evidence invariants satisfied` and `PMV_FINAL_GATE=PASS`.
- [Vercel production reconcile](https://github.com/snadaiapp-png/SNAD/actions/runs/37976964783/job/113977590913): **SUCCESS**, deployment `dpl_BhqkWnWALP8fxsY8AmmumEjTf1EQ`, target `production`, `READY`, production alias SHA exactly `b3579096a28e5d1dc01ced5e314846f1e9e0e29f`.
- Dashboard roadmap reconciliation: PR #1336 merged; legacy G4 tasks are DONE and group is **IN_PROGRESS pending final closure**. User-visible screenshot shows HR program progress 88% (22/25).

## Closure blockers that must not be concealed

- The **G4-T12 certificate PR #1331 is still OPEN**, not merged; its exact-head Vercel Git status is **FAILURE** due to a misrouted Production deployment from the certification branch. Its green GitHub checks and approvals do not override the failed deployment status. Preserve `SNAD_RELEASE_GUARD`; Preview-only Vercel CLI is a separately governed path and its run has not been shown successful.
- The HR workspace has a payroll page at `/hr/payroll`, but its navigation link is conditional on the user's `HRM.PAYROLL.VIEW` capability. The user's current session has not been independently shown to include that capability. Verify role assignments and effective capability propagation with real authorized and unauthorized users before declaring operational closure; do not grant payroll visibility universally or bypass backend RBAC.
- Confirm authenticated payroll page, create/calculate/review/approve/export capability boundaries, tenant isolation and backend wiring against the deployed production release (or explicitly scope the closure to engineering only, not operational certification).
- This certificate is **evidence assembled, not final approval**. Amend it only after proof for outstanding requirements, fresh exact-head checks, independent review and protected merge.

**Closure state remains `HRM_G4_ENGINEERING = PENDING_FINAL_APPROVAL`**. Do not mark G4 DONE in the product dashboard until a genuine engineering closure certificate is merged; Saudi statutory payroll remains disabled.

## Explicitly separate statutory authority

`LEGAL_REVIEW = PENDING_HUMAN`  
`SA_COUNTRY_PACK = DRAFT`  
`SAUDI_STATUTORY_PAYROLL = DISABLED`

No WPS/GOSI/tax/bank/statutory claims; Accounting remains the sole journal and GL posting owner.
