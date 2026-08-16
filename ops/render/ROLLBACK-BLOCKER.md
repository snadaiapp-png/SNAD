# Canonical Render Rollback Authority — Blocker Record

Date: 2026-08-16

## Required authority

The Clean-Room release design requires exactly one production rollback workflow:

```text
.github/workflows/render-rollback.yml
```

Required properties:

- `workflow_dispatch` only;
- protected `Production` environment;
- explicit prior Render `deploy_id` input;
- Render rollback endpoint only;
- no Render environment-variable mutation;
- no Flyway/database mutation;
- poll new rollback deploy to terminal state;
- verify `/actuator/health/readiness` before reporting success.

## Current state

```text
ROLLBACK_WORKFLOW=BLOCKED_BY_CONNECTOR_SAFETY
RELEASE_CONTRACT_TESTS=48_PASS_1_FAIL
INVENTORY_POLICY_GATE=PASS
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
```

The GitHub connector rejected creation of the executable rollback workflow. No bypass, fake workflow, hidden writer, force push, or alternate production mutation path was introduced.

## Release effect

R3 remains fail-closed until a legitimate canonical rollback authority is committed and its release contract test passes.

This blocker does not authorize Production deploy, migration, Green provisioning, Vercel cutover, or use of any exposed credential.
