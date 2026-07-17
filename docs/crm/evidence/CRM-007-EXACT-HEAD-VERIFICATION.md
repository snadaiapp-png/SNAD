# CRM-007 Exact-Head Verification

- Gate: CRM-G3D
- Pull request: #546
- Reconciled base: `b67fab8ce01543214bd1fd0047c19e27455a1f3c`
- Clean rebase source: `5986d857b5293b13f59333ec6a510a5da3a207b3`
- PostgreSQL expectation repair parent: `e621f8780ef5c5b02df07533bfa1032a65b46683`
- Verification candidate: the PR head commit containing this record.
- Verification mode: all required workflows must complete successfully on this one unchanged PR head.
- Migration allocation: `V20260717_100` and `V20260717_101`.
- Corrected migration ordering: Business Process `.4/.5`, CRM-G1 `.6`, CRM-007 `.100/.101`.
- Required evidence: Maven/Surefire, PostgreSQL clean and upgrade, tenant isolation, RBAC and masking, OpenAPI/type drift, Web CI, authenticated acceptance, Playwright, review-thread clearance, expected-head merge, production migration, Vercel READY, smoke tests, and runtime-error inspection.
- `/api/system/release`: tracked independently in issue #545 and excluded from the CRM-007 functional scope.
- Status: FINAL_EXACT_HEAD_VERIFICATION_RUNNING. This record does not authorize merge or production closure by itself.
