# SNAD Action Security Review

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Actions Used in quality-gate.yml

| Action | Reference | Pinned? | Permissions | Risk | Decision |
|---|---|---|---|---|---|
| actions/checkout@v4 | v4 (tag) | Partial (tag not SHA) | contents: read | LOW | Acceptable for now; pin to SHA in Stage 03 |
| actions/setup-java@v4 | v4 (tag) | Partial | none | LOW | Acceptable |
| actions/setup-node@v4 | v4 (tag) | Partial | none | LOW | Acceptable |
| actions/setup-python@v5 | v5 (tag) | Partial | none | LOW | Acceptable |
| actions/upload-artifact@v4 | v4 (tag) | Partial | none | LOW | Acceptable |

## Security Assessment

- No `pull_request_target` used (safe for fork PRs)
- No production secrets accessed
- No deployment jobs in quality-gate.yml
- No write permissions beyond contents: read
- No untrusted actions from third parties
- Docker images use official tags (postgres:16-alpine, zricethezav/gitleaks:v8.24.3)

## Recommendations for Stage 03

1. Pin all actions to commit SHA (not tags)
2. Add Dependabot for GitHub Actions
3. Consider using `actions/checkout@<sha>` with SHA recorded in comment
