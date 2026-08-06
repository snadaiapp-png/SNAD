# CRM-003R — Comprehensive Post-Merge Review

## Authoritative current status

```text
REVIEW_DATE: 2026-07-26
ORIGINAL_STAGE: CRM-003
ORIGINAL_IMPLEMENTATION_PR: #502 / MERGED
ORIGINAL_FINAL_PR_HEAD: 5cba9afe92fbb6765119c16706fbaee49b06104b
ORIGINAL_MERGE_SHA: e441e18948a2ba9a9f0e3a018b1bbe4473e2d93f
CORRECTIVE_CONTROL_ISSUE: #771
CRM_003_IMPLEMENTATION: MERGED
CRM_G2_FINAL_CLOSURE: WITHHELD_PENDING_CRM_003R
LATER_STAGES: PRESERVED_AND_GOVERNED_SEPARATELY
```

This report is the current post-merge review record for CRM-003. It supersedes
older pre-push, pre-merge and merge-approval status declarations when they are
used as statements of current closure state.

## 1. Review scope

The review covered:

- PR #502 metadata, final head and merge identity;
- all changed files and exact-head GitHub Actions evidence;
- typed API DTOs and error contracts;
- tenant and RBAC boundaries;
- ETag/If-Match concurrency behavior;
- version-scoped database mutations;
- database-backed idempotency and exact HTTP replay;
- cursor codec, page request handling and endpoint pagination;
- OpenAPI and generated TypeScript governance;
- contract-test coverage;
- current-main non-regression state;
- documentary consistency and final stage-report completeness.

## 2. Verified implementation strengths

CRM-003 delivered substantial and valid platform capabilities:

1. A typed `/api/v2/crm` contract surface with explicit request and response
   DTOs and camelCase external contracts.
2. A sanitized CRM error envelope and stable error catalog.
3. Strong entity/version ETags and fail-closed If-Match validation.
4. Atomic SQL mutations where `tenant_id`, `id` and `expectedVersion` participate
   in the UPDATE predicate, preventing a read-check-write lost-update window.
5. Database-backed idempotency scoped by tenant, principal, endpoint and key.
6. Exact replay of response status, body, headers and content type.
7. A committed OpenAPI contract and generated TypeScript API artifact.
8. Contract, security, regression and deployment workflows.
9. Preservation of the v1 operational CRM experience while adding v2.

## 3. Exact-head workflow evidence

The actual final PR head was:

```text
5cba9afe92fbb6765119c16706fbaee49b06104b
```

The following pull-request workflows completed successfully on that exact head:

| Workflow | Run ID | Result |
|---|---:|---|
| CRM API Contract Validation | `29245507231` | success |
| CRM Authenticated Acceptance | `29245507175` | success |
| Playwright E2E & Visual Regression | `29245507610` | success |
| CI | `29245507618` | success |
| Web CI | `29245507209` | success |
| Security Baseline | `29245507649` | success |
| Security Scan (OWASP) | `29245507206` | success |
| CRM Deployment Readiness | `29245507289` | success |
| Backup Restore Validation | `29245507767` | success |
| Performance Baseline | `29245507644` | success |
| Compile Diagnostics | `29245507260` | success |
| CRM Web Lint Diagnostics | `29245507903` | success |
| SNAD Identity Governance | `29245507744` | success |
| Production Control Plane Validation | `29245507817` | success |
| Stage 07 Artifact Provenance | `29245507230` | success |
| Service Decomposition Validation | `29245507525` | success |
| Master Backlog Validation | `29245507224` | success |

Vercel also reported `success` on the final PR head.

The previously documented verification head
`ad1ce3d50096d338bc26cfc6c49829def92e8105` was one commit behind the actual
final head. The only intervening commit added the independent-verification
record itself; no implementation code changed. Nevertheless, the current record
uses the actual final head, which independently completed the full workflow
matrix.

## 4. Blocking correctness finding — cursor pagination does not progress

### Finding ID

```text
CRM003R-P0-001
SEVERITY: P0 CONTRACT CORRECTNESS
STATUS: OPEN
```

The current `CrmContractController` accepts a cursor but does not apply its
position to the query or result set:

1. `page(...)` calls `CursorCodec.decode(...)` only for validation.
2. The returned `DecodedCursor(sortValue, tieBreakerId, ...)` is discarded.
3. List endpoints request the same initial `limit + 1` rows from legacy services
   for every request.
4. `paginate(...)` always slices from index zero.
5. The next cursor is generated from the last row of that repeated first page.
6. Requested sort and direction are validated but are not applied to the
   underlying data query.
7. The custom-field list path does not implement cursor progression and returns
   an empty page descriptor.

### Impact

A client can submit the server-issued next cursor and receive page 1 again. This
creates duplicate records, non-progressing navigation and potentially an
infinite pagination loop. The behavior violates the advertised stable cursor
contract and cannot be classified as a performance-only deferral.

## 5. Blocking test gap

### Finding ID

```text
CRM003R-P0-002
SEVERITY: P0 ACCEPTANCE GAP
STATUS: OPEN
```

`CrmPaginationContractTest` verifies:

- cursor encoding and decoding;
- tenant, sort and direction binding;
- malformed-cursor rejection;
- limit normalization;
- sort allowlisting;
- construction of an ORDER BY string.

It does not execute a real endpoint or PostgreSQL query across two pages. It
therefore cannot detect repeated-page behavior, overlap, gaps, unstable ties,
filter loss or direction errors.

Required corrective acceptance must prove:

- page 2 differs from page 1;
- page overlap is zero;
- the next cursor advances;
- tied sort values are ordered by ID;
- asc and desc traversal work;
- filters remain active across pages;
- cursors remain tenant-bound;
- malformed or tampered cursors fail closed;
- a stable dataset can be traversed without gaps.

## 6. OpenAPI semantic parity finding

### Finding ID

```text
CRM003R-P1-001
SEVERITY: P1 CONTRACT DOCUMENTATION
STATUS: OPEN
```

Create endpoints declare `Idempotency-Key` through Spring request annotations as
`required=false`, while `CrmIdempotencyHttpSupport.begin(...)` rejects an absent
or blank key and the committed OpenAPI requires it.

The runtime behavior is correctly fail-closed, but the runtime-generated OpenAPI
may describe the header as optional unless the parameter is explicitly annotated
as required. The existing semantic drift check verifies operation inventory and
selected properties in the committed artifact; it must also compare runtime and
committed parameter location, name, requiredness and schema.

## 7. Documentary reconciliation findings

### Finding ID

```text
CRM003R-P1-002
SEVERITY: P1 GOVERNANCE DRIFT
STATUS: OPEN
```

The following records are not valid current closure declarations:

- `CRM-003-API-CONTRACT-EVIDENCE.md` still contains pending PR, head, merge and CI
  fields and describes CRM-G2 as pending independent verification.
- `CRM-003-FINAL-INDEPENDENT-VERIFICATION.md` identifies an intermediate head
  and grants approval for merge, not a final post-merge closure.
- The promised `CRM-G2-STAGE-REPORT.md` is absent.
- PR #502 body retains its original in-progress/open status text.

These are historical evidence of their time. This review and Issue #771 are the
current authority until CRM-003R is completed.

## 8. Corrective acceptance requirements

CRM-G2 may be declared finally closed only after one unchanged corrective head:

1. Implements real database keyset pagination for every endpoint advertising a
   cursor.
2. Uses an allowlisted database column for the requested sort field.
3. Applies direction and the decoded `(sortValue, tieBreakerId)` predicate.
4. Uses deterministic `(sortColumn, id)` ordering and `limit + 1` lookahead.
5. Implements or removes unsupported custom-field cursor semantics.
6. Adds PostgreSQL-backed endpoint traversal tests.
7. Aligns runtime and committed OpenAPI parameter semantics.
8. Reconciles all CRM-003 evidence with exact head and merge identities.
9. Creates the final CRM-G2 stage report.
10. Passes the complete required workflow matrix with zero failed, pending,
    skipped-critical or zero-test gates.

## 9. Governance decision

```text
EXEC_PROMPT_CRM_003_IMPLEMENTATION: MERGED
CRM_003_TECHNICAL_VALUE: SUBSTANTIAL_AND_PRESERVED
CRM_G2_FINAL_CLOSURE: NOT_APPROVED
CRM_003R: OPEN
CORRECTIVE_CONTROL_ISSUE: #771
RESIDUAL_P0_BLOCKERS: 2
RESIDUAL_P1_FINDINGS: 2
COMMERCIAL_GO_LIVE: NOT_INFERRED
LATER_CRM_STAGES: NOT_AUTOMATICALLY_REOPENED
```

The correct action is remediation, not rollback and not false closure.
