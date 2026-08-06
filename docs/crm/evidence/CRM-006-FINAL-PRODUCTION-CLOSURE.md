# CRM-006 Final Production Closure

> **Stage:** EXEC-PROMPT-CRM-006 — Contacts, People and Multi-Account Relationship Model  
> **Decision:** CLOSED_WITH_PRODUCTION_EVIDENCE  
> **Closure date:** 2026-07-27  
> **Authority:** This record supersedes stale pre-merge and pre-production status language in PR #520 comments and description. Historical blocker comments remain valid only for their original timestamp and SHA.

## 1. Final decision

CRM-006 is closed for its defined implementation and production-acceptance scope.
The decision is based on immutable repository, CI, migration, deployment and
production evidence. It is not based on the fact that PR #520 is merely closed,
and it does not hide the Production schema defect discovered after the original
merge.

```text
CRM_006_IMPLEMENTATION: CLOSED_ACCEPTED
CRM_006_REPOSITORY_MERGE: COMPLETE
CRM_006_PRODUCTION_SCHEMA: VERIFIED
CRM_006_PRODUCTION_RUNTIME: VERIFIED_ON_DESCENDANT_RELEASE
CRM_006_KNOWN_OPEN_DEFECTS_IN_SCOPE: 0
COMMERCIAL_GO_LIVE: NOT_INFERRED
PLATFORM_WIDE_RESIDUAL_RISKS: GOVERNED_SEPARATELY
```

"Known open defects in scope: 0" means that no unresolved CRM-006 defect is
recorded in the reviewed evidence and current open-issue search. It is not a
claim that future defects are impossible.

## 2. Implementation identity

| Field | Evidence |
|---|---|
| Implementation PR | #520 — `feat(crm): implement people and account relationships` |
| Exact feature head | `7812b6eb039140a5c56b414acbed52fcab727dcd` |
| Merge SHA | `d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275` |
| Merge state | PR #520 closed and merged |
| Review threads | 0 unresolved |
| Original migrations | `20260717.1`, `20260717.2`, `20260717.3` |

The delivered scope includes person-profile extensions, tenant-safe
multi-account contact relationships, built-in and custom roles, one active
primary relationship, lifecycle and validity dates, decision authority,
ownership, optimistic concurrency, relationship and ownership history, Audit,
Timeline, row-isolated import, Customer 360 compatibility and the bilingual
relationship workspace.

## 3. Exact-head verification

The exact feature head completed the complete required workflow set with
**20/20 successful runs**:

- CI — `29549335418`
- PostgreSQL Acceptance — `29549335430`
- CRM API Contract Validation — `29549335395`
- CRM Authenticated Acceptance — `29549335417`
- Security Baseline — `29549335422`
- Security Scan (OWASP) — `29549335423`
- Development Security Acceptance — `29549335399`
- Production Readiness Gate — `29549335431`
- CRM Deployment Readiness — `29549335437`
- CRM Modular Architecture Validation — `29549335426`
- Web CI — `29549335435`
- Playwright E2E & Visual Regression — `29549335421`
- Backup Restore Validation — `29549335425`
- Performance Baseline — `29549335416`
- Compile Diagnostics — `29549335398`
- CRM Web Lint Diagnostics — `29549335438`
- SNAD Identity Governance — `29549335409`
- Stage 07 Artifact Provenance — `29549335449`
- Service Decomposition Validation — `29549335420`
- Master Backlog Validation — `29549335454`

Inspected test evidence on the exact feature head recorded:

```text
CRM_OPENAPI_PATHS: 50
CRM_OPENAPI_OPERATIONS: 66
RUNTIME_OPENAPI_DRIFT: 0
GENERATED_TYPESCRIPT_DRIFT: 0
SUREFIRE_XML_SUITES: 92
ACTUAL_TESTCASES: 694
FAILURES: 0
ERRORS: 0
SKIPPED: 0
```

The relationship integration tests cover zero/one/multiple accounts, one active
primary relationship, built-in and custom roles, invalid dates, duplicates,
tenant isolation, stale If-Match rejection, profile ownership, Audit, Timeline,
history, archive/reactivation and non-merging of duplicate email identities.

## 4. Historical production blocker and root correction

The original CRM-006 merge was **not** sufficient for production closure.
Vercel quota and exact-SHA deployment failures initially blocked the production
gate. More importantly, read-only Production evidence later proved a Flyway
baseline gap:

- Production contained `20260717.6` as `BASELINE`.
- No successful `20260717.1` row existed.
- The five CRM Contact profile columns were absent.
- The four CRM Contact relationship tables were absent.

This was a real Production defect and is explicitly retained in the closure
record. It was corrected by:

| Field | Evidence |
|---|---|
| Corrective PR | #614 — `fix(crm): reconcile skipped Contact schema after Flyway baseline gap` |
| Corrective head | `661d83f1e1234ad6f492e994c598f5aab6e6c923` |
| Corrective merge | `2e5342c6ba5148868be749f99d954113a67f77c1` |
| Forward-only migration | `V20260721_1__reconcile_crm_contact_relationship_model_after_baseline_gap.sql` |
| History repair/manual SQL | Prohibited and not used |

The corrective head completed 17/17 returned required workflows successfully,
including CI, PostgreSQL, CRM API contract, authenticated acceptance, security,
architecture, deployment readiness, business-process E2E and CRM-G1 schema
isolation.

## 5. Production proof

The authoritative Production release is a descendant of the CRM-006 merge:

```text
CRM_006_MERGE_SHA: d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275
FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
GIT_COMPARE_STATUS: ahead
AHEAD_BY: 240
BEHIND_BY: 0
MERGE_BASE: d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275
```

Production execution evidence:

| Field | Evidence |
|---|---|
| Final release SHA | `4cedf631a3e61f39039615d93cd03c3111213eb9` |
| Vercel deployment | `dpl_FtG7Pj4MUBNjEFjahPopscqKn7b9` |
| Render deployment | `dep-d9gartok1i2s7388lprg` |
| Render image | `ghcr.io/snadaiapp-png/snad-backend:4cedf631a3e61f39039615d93cd03c3111213eb9` |
| Render image digest | `sha256:810e69e1c05668ebd9540b71554e13190c837d38004aa3a37dacbde7521cb2cd` |
| CRM-G1 workflow | `29917230857` / SUCCESS |
| CRM-G1 artifact | `8528404489` |
| CRM-G1 artifact digest | `sha256:7c714b35ed8d64824dec15561615dc5176d4688f67df03a6ca51612098baaedb` |
| CRM-007 dependent workflow | `29917314330` / SUCCESS |

The Production proof verified:

- Vercel and Render exact release identity.
- Render health, liveness and readiness.
- Flyway `20260721.1 / SQL / true`.
- Zero failed Flyway migrations.
- All five required Contact profile columns.
- All four CRM-006 relationship tables.
- Contact Create HTTP 201.
- Contact Detail HTTP 200.
- Cross-tenant access fail-closed HTTP 404.
- CRM-007 dependent lifecycle on the same release.
- Zero unexpected CRM HTTP 5xx during the final gate.

The immutable supporting record is
`docs/crm/evidence/CRM-G1-FINAL-PRODUCTION-CLOSURE.md`.

## 6. Closure boundaries

This decision closes CRM-006 only. It does not claim:

- that every future CRM defect is impossible;
- that later CRM stages are automatically accepted;
- that unrelated platform-wide security, disaster-recovery, repository,
  commercial or operational risks are closed;
- that source merge alone constitutes Production approval.

A new issue may reopen CRM-006 only with reproducible evidence tied to a concrete
SHA, environment, request, database state or failing test. Narrative concern
without evidence does not invalidate this closure; reproducible contradictory
evidence does.

## 7. Final closure statement

```text
DECISION: CLOSED_WITH_PRODUCTION_EVIDENCE
IMPLEMENTATION: VERIFIED
MIGRATION_GAP: DISCLOSED_AND_CORRECTED
PRODUCTION_SCHEMA: VERIFIED
PRODUCTION_RUNTIME: VERIFIED
TENANT_ISOLATION: PASS
UNEXPECTED_CRM_HTTP_5XX: 0
KNOWN_UNRESOLVED_CRM_006_DEFECTS: 0
FALSE_OR_UNSUPPORTED_CLAIMS: REMOVED
```
