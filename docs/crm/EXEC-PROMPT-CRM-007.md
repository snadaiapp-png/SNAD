# EXEC-PROMPT-CRM-007 — Addresses and Communication Methods

## Current status

```text
EXEC-PROMPT-CRM-007: CLOSED
CRM-G3D: CLOSED
FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
PRODUCTION_PATH: Vercel -> BFF -> Render PostgreSQL
FINAL_EVIDENCE: docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md
EVIDENCE_PR: #689 / MERGED
ISSUE_563: CLOSED_COMPLETED
ISSUE_571: CLOSED_COMPLETED
UNEXPECTED_CRM_HTTP_5XX_AT_CLOSURE: 0
```

CRM-007 is closed on the immutable Production release shown above. This file is
now the current execution-status record; earlier candidate and blocked states
remain available in the pull-request and issue timelines and are not current
status declarations.

## Delivered scope

CRM-007 provides canonical tenant-scoped addresses and communication methods
for ACCOUNT and PERSON owners, including:

- tenant-safe CRUD and owner-scoped composite integrity;
- lifecycle, primary/preferred, verification and archive transitions;
- strong ETag/If-Match optimistic-concurrency contracts;
- idempotent create/import operations;
- privacy masking and separately governed sensitive reads;
- governed search, import and export;
- central Audit and Timeline integration;
- CRM-005 compatibility projections and legacy-row preservation;
- Arabic and English operational surfaces;
- deterministic OpenAPI and generated TypeScript contracts;
- PostgreSQL clean-install and upgrade verification;
- additive rollback and production verification runbooks.

## Final production evidence

```text
VERCEL_DEPLOYMENT: dpl_FtG7Pj4MUBNjEFjahPopscqKn7b9
RENDER_DEPLOYMENT: dep-d9gartok1i2s7388lprg
RENDER_IMAGE: ghcr.io/snadaiapp-png/snad-backend:4cedf631a3e61f39039615d93cd03c3111213eb9
RENDER_IMAGE_DIGEST: sha256:810e69e1c05668ebd9540b71554e13190c837d38004aa3a37dacbde7521cb2cd
ORCHESTRATOR_RUN: 29916836291 / SUCCESS
RECONCILIATION_RUN: 29917067433 / SUCCESS
CRM_G1_RUN: 29917230857 / SUCCESS
CRM_007_RUN: 29917314330 / SUCCESS
CRM_G1_ARTIFACT: 8528404489
CRM_007_ARTIFACT: 8528450065
```

The final chain proved Render health/liveness/readiness, read-only Flyway
postconditions, authenticated address and communication lifecycle behavior,
two-tenant isolation, current/stale concurrency validators and zero unexpected
CRM HTTP 5xx.

## Historical implementation baseline

CRM-007 began from the CRM-006/CRM-G1 baseline and introduced Flyway versions:

```text
20260717.100 — crm_addresses_communication_methods
20260717.101 — crm_addresses_communication_capabilities
```

A forward-only CRM-G1 reconciliation was required because production had
historically recorded migration `20260717.6` as a baseline rather than applying
its SQL. The correction did not use Flyway repair, schema-history edits,
destructive rollback or manual production SQL.

## Post-closure control

CRM-007 closure is evidence for release `4cedf631...`; later releases must keep
CRM-007 regression tests, tenant isolation, ETag behavior and API-contract drift
checks enabled. CRM-008R may harden shared CRM concurrency infrastructure, but
it does not reopen or invalidate the historical CRM-007 closure.
