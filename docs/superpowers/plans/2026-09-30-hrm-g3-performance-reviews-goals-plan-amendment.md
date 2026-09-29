# HRM G3 Implementation Plan Amendment — Exact-Head Reconciliation Ordering

**Applies to:** `docs/superpowers/plans/2026-09-30-hrm-g3-performance-reviews-goals-implementation.md`

This amendment corrects one ordering issue found during plan self-review and is authoritative over Task 8 Step 9 in the implementation plan.

## Superseded instruction

Do **not** wait until after merge/post-merge verification to change `apps/web/app/hr/hr-execution-data.ts` from G3 `NOT_STARTED` to `DONE`. Doing so would create a new post-closure commit and break the exact-SHA evidence chain.

## Correct instruction

After Tasks 1–7 are complete and their focused evidence is green, but **before final exact-head CI/acceptance certification and before merge**:

1. Reconcile only the G3 group and G3-T01..G3-T04 roadmap rows to `DONE`.
2. Add/update the G3 closure reconciliation block pointing to the exact pre-merge head evidence available at that stage.
3. Run the execution-integrity/regression tests and all final exact-head gates again on the reconciled head.
4. Open/finalize the protected PR from that exact head.
5. Merge only after all required exact-head checks and approvals pass.
6. Run post-merge verification on the resulting merge SHA.
7. Finalize evidence/certificate wording against that same merge SHA; do not introduce a new product-code SHA solely to claim closure.

The final sequence is therefore:

`implementation -> focused green -> roadmap reconciliation -> exact-head full CI/acceptance -> protected merge -> post-merge verification -> G3 CLOSED — FINAL`
