# CRM Unified Integration Worklog — 2026-07-02

## Branch

`feature/crm-unified-real-runtime-20260702`

## Objective

Unify the scattered CRM construction branches into one controlled non-production implementation path and move CRM acceptance away from benchmark-only tables toward the real authenticated `/api/v1/crm/**` application flow.

## Completed in this integration branch

- Added `V70__create_unified_crm_core.sql` to avoid the draft `V16` / `V17` Flyway conflicts from PR #192 and PR #193.
- Added real CRM application tables for accounts, contacts, leads, pipelines, stages, opportunities, stage history, activities, timeline events, imports, and custom fields.
- Seeded active `CRM.%` capabilities and assigned them to active tenant `ADMIN` roles.
- Added a real CRM controller/service under `/api/v1/crm/**` for accounts, contacts, leads, conversion, pipelines, opportunities, activities, and timeline.
- Added `scripts/crm/real-crm-smoke.sh` and `.github/workflows/crm-real-smoke.yml` so acceptance can run against the authenticated application API rather than `crm_benchmark` tables.
- Replaced the `/crm` placeholder with an Arabic CRM workspace page that documents the live API flow and release status.
- Removed temporary backend and documentation marker files from the integration branch.

## Important implementation notes

- This is a controlled non-production build path only.
- The CRM UI is now more than a text placeholder, but it is still not a full data-bound product UI.
- The real smoke path requires `CRM_SMOKE_BEARER_TOKEN` / `CRM_BEARER_TOKEN`; it cannot be honestly marked PASS until run inside an environment with a real tenant/user session.
- The branch still requires exact-SHA backend compile, Flyway clean validation, Flyway upgrade validation from `main`, and frontend build evidence.

## Remaining blockers before merge

1. Execute backend compile and tests on the latest head SHA.
2. Execute Flyway validation on a clean database.
3. Execute Flyway upgrade validation from current `main` database state to `V70`.
4. Run authenticated real CRM smoke using a tenant user with the seeded `CRM.%` capabilities.
5. Build `apps/web` and verify `/crm` compiles under the Vercel root `apps/web`.
6. Decide whether to delete the remaining `apps/web/app/crm/placeholder.txt` marker after confirming no route-generation side effect.
7. Review unresolved production blockers: Issue #173, Issue #197, and the global production gate.

## Current decision

```text
CRM_BRANCH_AGGREGATION: PARTIAL — unified PR exists, old branches not merged independently
CRM_MIGRATION_CONFLICT_FIX: IMPLEMENTED THROUGH V70, PENDING FLYWAY VALIDATION
CRM_REAL_BUSINESS_API: PARTIAL — core flow exists, needs compile/smoke evidence
CRM_WEB_UI: PARTIAL — workspace route implemented, data-bound UI pending
CRM_REAL_SYSTEM_TESTING: PREPARED, NOT EXECUTED
CRM_PRODUCTION_READY: NO
```
