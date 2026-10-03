# G3 FINAL EVIDENCE MANIFEST

## Scope

This manifest records the actual engineering evidence used to close HRM G3
(Performance Reviews & Goals). It does not claim production authorization,
legal certification, or Saudi country-pack certification.

## Canonical implementation SHA

- Repository: `snadaiapp-png/SNAD`
- Branch: `main`
- Canonical implementation merge SHA: `99adf88046772fbe6492f5e74c4c5a184c6d0376`
- Main stability after final evidence reconciliation: PASS

## Required checks on the canonical SHA

All six protected required contexts completed successfully on
`99adf88046772fbe6492f5e74c4c5a184c6d0376`:

1. `Build Next.js Web` — SUCCESS
2. `provenance` — SUCCESS
3. `CRM Integration Tests` — SUCCESS
4. `Maven Test Suite` — SUCCESS
5. `CRM Deployment Readiness` — SUCCESS
6. `Verify 8 tables, 26 indexes, and tenant isolation` — SUCCESS

The sixth check was produced by the authorized manual
`workflow_dispatch` of `CRM G1 Schema Isolation`:

- Workflow run: `37088523419`
- Event: `workflow_dispatch`
- Branch: `main`
- Head SHA: `99adf88046772fbe6492f5e74c4c5a184c6d0376`
- Job/check ID: `111103669620`
- Result: `completed / success`

## G3 authenticated acceptance

- Workflow: `G3 Authenticated Acceptance`
- Run ID: `37078851136`
- Job ID: `111074707841`
- Head SHA: `99adf88046772fbe6492f5e74c4c5a184c6d0376`
- Result: SUCCESS
- Execution model: host-native PostgreSQL Direct + Spring Boot + Next.js + Playwright
- Browser journeys: 4 passed, 0 failed
  - employee goal view/update
  - employee self-review
  - manager team goals/reviews
  - unauthorized protected request returns 403

## PostgreSQL / security evidence

On the same canonical SHA:

- `Maven Test Suite` — SUCCESS
- `PostgreSQL Acceptance Tests` — SUCCESS
- `CRM Integration Tests` — SUCCESS
- `JOB C — PostgreSQL Direct integration` — SUCCESS
- `JOB D — HRM focused security/RLS` — SUCCESS
- `JOB E — Security & governance scans` — SUCCESS

The accepted execution path is PostgreSQL Direct. Docker and Testcontainers
are not closure authorities for G3.

## Post-merge verification

- Workflow: `Post-Merge Main Verification`
- Run ID: `37078851075`
- Head SHA: `99adf88046772fbe6492f5e74c4c5a184c6d0376`
- Result: SUCCESS

Jobs A-F all completed successfully:

- JOB A — Frontend verification
- JOB B — Backend compile
- JOB C — PostgreSQL Direct integration
- JOB D — HRM focused security/RLS
- JOB E — Security & governance scans
- JOB F — Final evidence aggregation

## Deployment status

The GitHub commit status for `99adf88046772fbe6492f5e74c4c5a184c6d0376` reports:

- Context: `Vercel`
- State: `success`
- Description: deployment completed

This is deployment evidence only and is not used as a substitute for
functional acceptance.

## Production smoke classification

`Production Smoke` is **NOT_APPLICABLE to the G3 engineering closure gate**.
The canonical G3 Task 8 plan requires exact-head required checks and
post-merge verification; production smoke belongs to the separate
Production / Go-Live governance path.

## Closure result

- Functional blockers: 0
- Test blockers: 0
- Security blockers: 0
- Tenant-isolation blockers: 0
- G3 evidence blockers: 0
- Total unresolved G3 blockers: 0

**G3 TASK 8 = PASS**

**G3 = CLOSED / GREEN**
