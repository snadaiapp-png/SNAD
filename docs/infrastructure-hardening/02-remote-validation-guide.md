# SNAD Remote Validation Guide

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Prerequisites

1. Branch `infra/02a-debt-closure` must be pushed to remote
2. A pull request must be opened against `main`
3. GitHub Actions must be enabled on the repository

## Steps

### 1. Push the branch

```bash
git push -u origin infra/02a-debt-closure
```

### 2. Open a PR

```bash
gh pr create \
  --repo snadaiapp-png/SNAD \
  --base main \
  --head infra/02a-debt-closure \
  --title "infra: Stage 02 CI quality gates and debt closure" \
  --body "Stage 02 CI quality gate workflow, migration immutability, Maven Wrapper, debt closure documentation"
```

### 3. Monitor quality-gate workflow

```bash
gh pr checks <PR_NUMBER> --repo snadaiapp-png/SNAD --watch
```

### 4. Verify all 11 mandatory jobs pass

The following checks must all show `pass`:

- repository-policy
- backend-tests
- backend-postgres-integration
- flyway-validation
- frontend
- python-tests
- secret-scan
- dependency-scan
- container-smoke
- security-regression
- quality-gate (aggregation)

### 5. Record evidence

After all checks pass, record:
- Workflow Run ID
- Workflow Run URL
- Commit SHA
- All job results
- Duration

### 6. Update execution state

Update `02-execution-state.json` with:
- `remoteWorkflowRun: "PASS"`
- `remoteWorkflowRunId: "<run_id>"`
- `remoteWorkflowUrl: "<url>"`
- `status: "PASS"` (only if P0 debt is also closed)

## Important Notes

- Remote CI validation alone does NOT close P0 debt
- P0 debt (CD-00-P0-001, CD-00-P0-002) requires owner action on Issue #173
- Even with remote CI PASS, final status remains BLOCKED until P0 is closed
