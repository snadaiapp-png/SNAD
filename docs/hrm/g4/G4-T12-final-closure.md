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

## G4-T12 mandatory exit gates

Authoritative plan: `docs/superpowers/plans/2026-10-06-hrm-g4-payroll-integration-implementation.md`.

| Gate | Evidence | State |
|---|---|---|
| T11 protected merge | PR #1323, merge SHA `39c7c460...` | VERIFIED |
| Production reconcile when authorized | PR #1329, release SHA `33f1b052...`, live identity proof | VERIFIED |
| Post-Merge Main Verification JOB A — frontend | Independent exact-SHA A–F run, artifact | **UNVERIFIED** |
| JOB B — backend compile | Independent exact-SHA A–F run, artifact | **UNVERIFIED** |
| JOB C — PostgreSQL Direct full integration | Host-native PostgreSQL / Maven proof, exact-SHA run | **UNVERIFIED** |
| JOB D — HRM focused security/RLS | Tenant isolation, capability-denial tests, exact-SHA run | **UNVERIFIED** |
| JOB E — security/governance scans | Independent exact-SHA report | **UNVERIFIED** |
| JOB F — final evidence aggregation | Collect-all validator, immutable evidence manifest | **UNVERIFIED** |

**Evidence limitation:** GitHub's available commit-associated workflow-run lookup returned no independent `Post-Merge Main Verification` A–F workflow run for the inspected T11 merge SHA `39c7c460...` or current release SHA `33f1b052...`. General green checks, pre-merge tests and the production deploy **must not** be substituted for these six explicit gates. Do not assert that these gates failed: the current condition is evidence missing/unverified.

## Closure authority and next action

1. Locate and independently attest a completed exact-SHA `Post-Merge Main Verification` JOB A–F run on a qualifying merge/release SHA, or execute the authorized canonical run without inventing test outcomes. Maintain PostgreSQL Direct only (no Docker/Testcontainers).
2. Gather artifact manifest and JOB F validator result, including PostgreSQL Direct, HRM security/RLS and tenant-isolation proofs. Require zero failed mandatory gates.
3. Review evidence on the certificate's final exact PR head; obtain independent approval and protected squash merge.
4. Only after an authoritative exact-SHA evidence set may the closure record be amended to `HRM_G4_ENGINEERING = CLOSED`.

## Explicitly separate statutory authority

`LEGAL_REVIEW = PENDING_HUMAN`  
`SA_COUNTRY_PACK = DRAFT`  
`SAUDI_STATUTORY_PAYROLL = DISABLED`

No WPS/GOSI/tax/bank/statutory claims; Accounting remains the sole journal and GL posting owner.
