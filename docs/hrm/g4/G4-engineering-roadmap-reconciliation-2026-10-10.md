# HRM G4 — Engineering closure state reconciliation (2026-10-10)

Scope: **ENGINEERING ONLY**; roadmap G4 group status DONE records implementation and verified release gates, **not** operational authorization.

## Immutable evidence
- T12 closure evidence PR #1331 merged to main at `ce2df5797181eedd0fa4c2f74c602703726ccb09`.
- A–F Post-Merge Main Verification: https://github.com/snadaiapp-png/SNAD/actions/runs/37996632871 ; JOB F `PMV_FINAL_GATE=PASS`.
- Vercel production exact-SHA reconcile: https://github.com/snadaiapp-png/SNAD/actions/runs/37996632890 ; deployment `dpl_6QQcoScMr2nw7Lzxg6BdibiK2fy2` READY.
- G4 payroll workspace navigation fix PR #1354 merged at `91ca493b3bff2ff3cd4e8b194fd33d0fdf51cde7`.
- Exact merge-SHA A–F: https://github.com/snadaiapp-png/SNAD/actions/runs/38009551568 ; Production reconcile: https://github.com/snadaiapp-png/SNAD/actions/runs/38009551583 ; deployment `dpl_5LK3Fiqj83SW7gPXEiaxLp3knF78` READY.
- Newer main revisions require their own exact-SHA release checks; do not reuse prior deployment proof as identity proof for a new SHA.

## Separation of authority
`HRM_G4_ENGINEERING = DONE`
`HRM_G4_PRODUCTION_ROLE_SMOKE = UNVERIFIED`
`HRM_G4_OPERATIONAL_CERTIFICATION = PENDING`
`LEGAL_REVIEW = PENDING_HUMAN`
`SA_COUNTRY_PACK = DRAFT`
`SAUDI_STATUTORY_PAYROLL = DISABLED`

G4 roadmap DONE must not be interpreted as a green operational payroll certification. Overall HR program remains 88% (22/25 legacy tasks), because G5 is not completed.

## Production RBAC acceptance (read-only)
1. Using an explicitly authorized production test identity, inspect `me.capabilities` for `HRM.PAYROLL.VIEW`, verify visible navigation and successful GET /hr/payroll and GET /api/v2/hr/payroll/runs.
2. Using an identity without the capability, verify that payroll link is absent and backend GET denies access (403; no payroll data).
3. Verify tenant boundary with authorized least-privilege accounts only and synthetic test data; do not mutate or expose real payroll records.
4. Record time, deployed SHA, sanitized evidence, identity role (not credentials), independent reviewer approval; only then issue an operational certificate.

Never broaden payroll RBAC, disable authorization, claim WPS/GOSI statutory readiness, or run payment transactions to make a smoke test green.
