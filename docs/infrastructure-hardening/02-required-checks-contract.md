# SNAD Required Checks Contract

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Required Check for Branch Protection

### Primary Required Check

```
quality-gate
```

This is the aggregation job in `.github/workflows/quality-gate.yml`. It depends on all 10 mandatory jobs and fails if any of them does not succeed.

### Mandatory Jobs (must all pass for quality-gate to pass)

1. repository-policy
2. backend-tests
3. backend-postgres-integration
4. flyway-validation
5. frontend
6. python-tests
7. secret-scan
8. dependency-scan
9. container-smoke
10. security-regression

### Path Filter Bypass Prevention

quality-gate.yml triggers on ALL pull requests (no path filters). This ensures the `quality-gate` check is always present, even for documentation-only PRs.

Jobs that are not relevant to a PR's changes will still execute but may complete quickly. This is intentional — a missing required check is worse than a fast-passing check.

## Legacy Required Checks (to be consolidated)

The following checks are currently in GitHub Branch Protection but should be replaced by `quality-gate`:

```
CI
Web CI
Security Baseline
Compile Diagnostics
Backup Restore Validation
Master Backlog Validation
Service Decomposition Validation
```

## Migration Plan

1. Push quality-gate.yml to remote
2. Verify all jobs pass on a test PR
3. Add `quality-gate` to required_status_checks
4. Remove legacy checks from required_status_checks
5. Verify branch protection enforces only `quality-gate`
