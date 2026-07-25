# CRM-008R — Corrective Remediation Execution Plan

> **Control issue:** #725  
> **Authorized base:** `d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4`  
> **Authorized branch:** `fix/crm-008r-corrective-remediation-20260726`  
> **Status:** `WP-00 / DRAFT / NO MERGE / NO DEPLOYMENT`

## Objective

Correct the verified post-CRM-008 findings without rewriting CRM-007/CRM-008 history or modifying CRM-009 behavior outside strict compatibility requirements.

The remediation closes four control gaps:

1. Pre-read-only `If-Match` validation is replaced by atomic persistence enforcement.
2. Unbounded CRM-008 list reads followed by Java truncation are replaced by PostgreSQL cursor pagination.
3. CRM-007/CRM-008 authoritative documentation is reconciled with the as-built repository and production record.
4. CRM-008B receives one consolidated final closure-evidence record.

## Immutable controls

```text
BASE_SHA: d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4
CRM_007_FINAL_RELEASE: 4cedf631a3e61f39039615d93cd03c3111213eb9
CRM_008_ORIGINAL_MERGE: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
ISSUE: #725
MERGE_AUTHORIZED: NO
DEPLOYMENT_AUTHORIZED: NO
```

- No new business feature.
- No manual Production SQL.
- No Flyway repair or schema-history edit.
- No test skip, timeout inflation, retry masking, weaker assertion, or `continue-on-error`.
- No final-status update to Issue #597 or PR #691 before the corrective merge SHA exists.

## WP-00 — Traceability inventory

### Atomic concurrency inventory

| Surface | Current controller pattern | Required authority | Test gate |
|---|---|---|---|
| Team update | Read → validate ETag → write | SQL CAS or row lock with expected token | same-ETag two-writer race |
| Membership update/end | Read list → validate ETag → write | tenant-scoped atomic expected token | update/end race |
| Queue update | Read → validate ETag → write | SQL CAS or row lock | same-ETag two-writer race |
| Territory mutation | Read → validate ETag → write | atomic expected token | update/removal race |
| Rule activation | Parent ETag checked before activation | lock/CAS parent rule version | concurrent activation race |
| Transfer submit/decision/cancel | Read → validate ETag → transition | state + expected token in one transaction | no double execution |

The implementation must add every additional CRM-008 `If-Match` write found by repository search before WP-01 closure.

### Pagination inventory

| Endpoint | Current risk | Required replacement |
|---|---|---|
| `GET /api/v2/crm/teams` | unbounded fetch + Java `limit` | PostgreSQL keyset cursor |
| `GET /api/v2/crm/queues` | unbounded fetch + Java `limit` | PostgreSQL keyset cursor |
| `GET /api/v2/crm/transfers` | unbounded fetch + Java `limit` and no next cursor | PostgreSQL keyset cursor |
| Other CRM-008 ownership collections | to be inventoried | same rule when unbounded |

## WP-01 — Atomic expected-version enforcement

The controller-level ETag validation is advisory only. The final mutation must prove the expected token inside the database transaction.

Required behavior:

```text
CURRENT TOKEN MATCHES → one mutation succeeds
CURRENT TOKEN STALE   → HTTP 412 / RFC 7807
SAME TOKEN RACE       → exactly one success
CROSS TENANT TOKEN    → fail closed
```

Accepted implementation strategies:

- `UPDATE ... WHERE tenant_id=? AND id=? AND version=?`, verifying one affected row; or
- `SELECT ... FOR UPDATE`, verify expected token after lock, then mutate in the same transaction.

No operation may rely solely on an earlier unlocked read.

## WP-02 — Cursor pagination

Use opaque keyset cursors bound to:

- tenant;
- active filters;
- deterministic ordering;
- unique tie-breaker.

Repository queries must request `pageSize + 1` rows and emit `nextCursor` only when an extra row exists. Invalid, tampered, cross-tenant, or filter-mismatched cursors fail with a governed 400 response.

## WP-03 — Documentation reconciliation

Mandatory files:

- `docs/crm/CRM-CURRENT-BASELINE.md`
- `docs/crm/EXEC-PROMPT-CRM-007.md`

CRM-007 current status must become:

```text
EXEC-PROMPT-CRM-007: CLOSED
CRM-G3D: CLOSED
FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
FINAL_EVIDENCE: docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md
```

Historical candidate data remains in a clearly labeled history section.

## WP-04 — CRM-008B closure evidence

Create `docs/crm/crm-008/evidence/CRM-008B-FINAL-CLOSURE.md` initially as `PENDING`. It may become `PASS` only after:

- exact corrective merge SHA;
- Vercel and Render exact-SHA deployment;
- PostgreSQL/Flyway read-only proof;
- production stale-ETag and cursor proof;
- two-tenant isolation;
- zero unexplained CRM 5xx;
- immutable workflow/artifact IDs and digests;
- Product, QA and Security approval records.

## WP-05 — Verification matrix

The unchanged candidate SHA must pass all required repository checks and at minimum:

- PostgreSQL Acceptance;
- CI / Maven full suite;
- CRM Authenticated Acceptance;
- CRM API Contract Validation;
- CRM G1 Schema Isolation;
- Security Baseline and OWASP;
- Development Security Acceptance;
- Auth Session Reliability;
- Web CI and Playwright;
- Performance Baseline;
- Backup Restore;
- CRM Modular Architecture;
- Production Readiness.

```text
OPEN_FAILURES: 0
OPEN_REVIEW_THREADS: 0
UNEXPLAINED_HTTP_5XX: 0
EXPECTED_HEAD_SHA: UNCHANGED
```

## WP-06 — Protected merge and production closure

1. Mark PR ready only after the exact-head gate passes.
2. Merge with `expected_head_sha`.
3. Deploy frontend and backend from the exact merge SHA.
4. Execute production concurrency, pagination, isolation and health proof.
5. Finalize `CRM-008B-FINAL-CLOSURE.md`.
6. Add final-status blocks to Issue #597 and PR #691 while preserving historical bodies.
7. Close Issue #725 only after all evidence is reviewed and immutable.

## Acceptance criteria

```text
AC-R-01 Atomic CAS/lock enforcement covers every CRM-008 If-Match write.
AC-R-02 Same-ETag races have one winner and stale requests return 412.
AC-R-03 Teams, Queues and Transfers use database cursor pagination.
AC-R-04 No target endpoint truncates an unbounded Java collection.
AC-R-05 Cursor integrity, tenant binding and filter binding pass.
AC-R-06 CRM-007 and CRM baseline documents match current authoritative state.
AC-R-07 CRM-008B closure evidence is complete.
AC-R-08 Required checks pass on one unchanged SHA.
AC-R-09 Merge uses expected-head protection.
AC-R-10 Exact merge SHA passes production proof with zero unexplained 5xx.
```

## Current phase

```text
CURRENT_PHASE: WP-00_TRACEABILITY
IMPLEMENTATION_COMPLETE: NO
MERGE_AUTHORIZED: NO
DEPLOYMENT_AUTHORIZED: NO
NEXT_GATE: CODE_AND_TEST_CORRECTIONS
```
