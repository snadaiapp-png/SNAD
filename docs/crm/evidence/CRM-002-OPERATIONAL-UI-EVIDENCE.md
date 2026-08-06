# CRM-002 — Operational UI Evidence

## Authoritative status

```text
CRM_002: CLOSED_COMPLETED
CRM_002A: CLOSED_COMPLETED
CRM_002B: CLOSED_COMPLETED
CRM_002G: ACCEPTED
CRM_G1_REPOSITORY_GATE: CLOSED
FINAL_VALIDATED_HEAD_SHA: dc1cc61a4e7505a6c4ad76c3644c1a8f25dc40f0
FINAL_GATE_MERGE_SHA: 89761eb9397e922b21917551299e2a2b9d478a86
FORMAL_RECONCILIATION_DATE: 2026-07-26
```

This record supersedes the former statement that CRM-002G was pending. The
terminal gate completed successfully on the exact PR head and was merged to
`main` on 2026-07-12.

The dedicated closure report is:

- [`CRM-002-FINAL-CLOSURE.md`](./CRM-002-FINAL-CLOSURE.md)

## 1. Governed implementation chain

| Stage | Pull request | Head SHA | Merge SHA | Result |
|---|---:|---|---|---|
| CRM-002 | #490 | `d6a56d8c6a34853ccd37c07d170cfefba68389bc` | `18aa875819f41de34d972c56a9d2e15695c50eb8` | Merged |
| CRM-002A | #492 | `983fa969dcf6f3103fcbaec26345d3ed67e97e4a` | `5c975079a1a22d003460fbef0dfbe9b36890dbf7` | Merged |
| CRM-002B | #493 | `eb93d6d4c77f71df96b350c4924189ab7f2da232` | `dd29d0e7c39f4c704b5e6968c24fdafe5b03165e` | Merged |
| CRM-002G | #501 | `dc1cc61a4e7505a6c4ad76c3644c1a8f25dc40f0` | `89761eb9397e922b21917551299e2a2b9d478a86` | Accepted and merged |

## 2. Operational UI delivered

CRM-002 restored the operational CRM experience and separated the operational
workspace from the governance command center. CRM-002A completed URL-based
routing and per-route data loading. CRM-002B added strict acceptance and E2E
coverage. CRM-002G repaired and executed the terminal acceptance matrix.

### Route inventory

| Route | Delivered behavior | Status |
|---|---|---|
| `/crm` | Server redirect to `/crm/overview` | Pass |
| `/crm/overview` | KPI dashboard backed by CRM APIs | Pass |
| `/crm/accounts` | List, create, archive and restore | Pass |
| `/crm/accounts/[accountId]` | Customer 360 detail | Pass |
| `/crm/contacts` | List, create and archive | Pass |
| `/crm/contacts/[contactId]` | Contact detail | Pass |
| `/crm/leads` | List, create, qualify, disqualify and convert | Pass |
| `/crm/leads/[leadId]` | Lead detail and conversion | Pass |
| `/crm/pipelines` | Pipeline and stage administration | Pass |
| `/crm/opportunities` | Pipeline board and list | Pass |
| `/crm/opportunities/[opportunityId]` | Opportunity detail and stage movement | Pass |
| `/crm/activities` | List, create and complete | Pass |
| `/crm/imports` | Upload, mapping, jobs and error download | Pass |
| `/crm/settings/custom-fields` | Custom-field administration | Pass |
| `/crm/command-center` | Independent governance shell | Pass |

The frontend consumes the governed `/api/v1/crm/*` boundary through the
existing `crmApi` client. No mock CRM dataset, parallel G1 schema, or replacement
backend API was introduced by CRM-002.

## 3. Acceptance coverage

The accepted package includes:

- Authenticated CRM happy-path acceptance.
- Two-tenant isolation and cross-tenant denial.
- Capability-based RBAC acceptance.
- Strict route, redirect, refresh and browser-history checks.
- Hydration and console-error detection.
- Accessibility checks on operational CRM routes.
- Import mapping and custom-field value workflows.
- Reproducible PostgreSQL seed data.
- Separate standard and authenticated Playwright configurations.
- Failure and success artifact publication from the authenticated workflow.

The final standard Playwright report recorded:

```text
EXPECTED: 174
UNEXPECTED: 0
FLAKY: 0
SKIPPED: 0
```

## 4. CRM-002G exact-head gate matrix

All required workflows completed successfully on exact head
`dc1cc61a4e7505a6c4ad76c3644c1a8f25dc40f0`.

| Gate | Run | Conclusion |
|---|---:|---|
| CRM Authenticated Acceptance | `29205033967` | success |
| Playwright E2E & Visual Regression | `29205034002` | success |
| CRM Deployment Readiness | `29205034027` | success |
| Backup Restore Validation | `29205034004` | success |
| Security Scan (OWASP) | `29205034003` | success |
| Security Baseline | `29205034007` | success |
| Development Security Acceptance | `29205034025` | success |
| Web CI | `29205033984` | success |
| CI | `29205033995` | success |
| Performance Baseline | `29205033979` | success |

Additional terminal conditions recorded on PR #501:

```text
FAILED_WORKFLOWS_ON_EXACT_HEAD: 0
IN_PROGRESS_WORKFLOWS_ON_EXACT_HEAD: 0
VERCEL_ON_FINAL_PR_HEAD: success
VERCEL_ON_MERGE_SHA: success
```

## 5. Current-main non-regression review

A formal reconciliation review was performed on 2026-07-26 against `main` SHA
`59a199dab80fa1b57e2b6e020bda8f58f852305d`.

Verified current state:

- `apps/web/app/crm/page.tsx` still performs a server-side redirect to
  `/crm/overview`.
- `apps/web/app/crm/(operational)/layout.tsx` still wraps operational routes in
  `CrmShell`, which retains authentication gating and URL-aware navigation.
- `/crm/command-center` remains outside the operational route-group shell.
- The current `main` Vercel status is `success`.
- The CRM-002G merge remains an ancestor of current `main`; the repository has
  advanced without removing the accepted CRM-002 foundation.

Later CRM stages substantially extend the backend, workflows, production
acceptance and ownership model. Those later changes do not reopen CRM-002.

## 6. Closure decision

```text
EXEC_PROMPT_CRM_002: ACCEPTED
EXEC_PROMPT_CRM_002A: ACCEPTED
EXEC_PROMPT_CRM_002B: ACCEPTED
EXEC_PROMPT_CRM_002G: ACCEPTED
CRM_002_STAGE: CLOSED_COMPLETED
CRM_G1_REPOSITORY_GATE: CLOSED
NEXT_PROMPT_AUTHORIZED_HISTORICALLY: EXEC-PROMPT-CRM-003
```

## 7. Known Limitations

```text
NONE for CRM-G1 requirements
```

This declaration is limited to the accepted CRM-G1 repository requirements and
does not waive later-stage requirements or commercial release controls.

## 8. Governance boundary

This evidence closes the CRM-002 repository-delivery stage. It does not by
itself grant commercial go-live approval and does not replace later production
closure evidence, including the CRM-G1 and CRM-007 production records.
