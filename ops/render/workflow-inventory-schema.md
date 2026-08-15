# GitHub Workflow Forensic Inventory Schema

Every YAML file under `.github/workflows/` must receive one inventory row before it is archived, replaced, or retained.

## Required Fields

```text
path
name
state
trigger
writes_source
writes_render
writes_render_env
writes_database
runs_flyway
builds_image
deploys_image
reads_production
uses_production_environment
required_status_check
replacement
classification
risk
reason
```

## Allowed Classifications

- `KEEP`: current, non-conflicting, necessary workflow.
- `REPLACE`: function is required but current workflow must be superseded by canonical control plane.
- `ARCHIVE`: historical/diagnostic/recovery workflow no longer allowed to execute from `.github/workflows`.
- `DANGEROUS`: currently executable workflow capable of production/source/database mutation outside the canonical release gate.
- `UNRESOLVED`: insufficient evidence; never archive automatically until resolved.

## Risk Levels

- `CRITICAL`: can mutate production DB, Render environment/service, production routing, credentials/bootstrap state, or source branch without an exclusive canonical gate.
- `HIGH`: can deploy, publish mutable production artifacts, alter release state, or execute schema operations.
- `MEDIUM`: diagnostics with production credentials or workflows that can influence release decisions.
- `LOW`: read-only validation/build/test workflows with no production mutation authority.

## Detection Signals

The inventory implementation must inspect at least:

```text
RENDER_API_KEY
RENDER_SERVICE_ID
RENDER_DEPLOY_HOOK_URL
api.render.com
/deploys
/env-vars
/suspend
/resume
DATABASE_URL
PROD_JDBC_URL
PRODUCTION_DATABASE_URL
psql
pg_terminate_backend
flyway
migrate
baselineOnMigrate
clean
DROP
TRUNCATE
docker/build-push-action
packages: write
contents: write
git push
gh pr merge
gh issue close
```

Detection is evidence, not final classification. Context must be reviewed to distinguish read-only checks from mutation.

## Branch-Protection Gate

No workflow backing a required `main` status context may be archived until one of these is true:

1. its replacement emits the exact same required context and is proven successful; or
2. branch protection is deliberately migrated as a separate reviewed governance action.

Current verified required contexts include:

```text
Build Next.js Web
provenance
CRM Integration Tests
Maven Test Suite
CRM Deployment Readiness
Post-Merge Verification
Verify 8 tables, 26 indexes, and tenant isolation
```

## Canonical End State

One production writer per concern:

```text
IMAGE_BUILDER=1
DATABASE_MIGRATION_WRITER=1
RENDER_DEPLOY_WRITER=1
ROLLBACK_WRITER=1
PRODUCTION_SMOKE_WRITER=0
```

`production-smoke` must be read-only. Unrelated security, backup, frontend, mobile, and governance workflows may remain when validated; the objective is control-plane correctness, not an arbitrary total workflow count.
