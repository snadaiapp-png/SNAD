# SNAD CI Quality Gate Report

Branch: infra/02a-debt-closure
Commit SHA: (pending)
Generated At: 2026-06-30

## Quality Gate Workflow

File: .github/workflows/quality-gate.yml
Trigger: pull_request, push to main, workflow_dispatch
Permissions: contents: read
Concurrency: quality-gate-${{ github.ref }}, cancel-in-progress: true

## Mandatory Jobs (11 + 1 aggregation)

| Job | Check Name | Purpose | Local Status | Remote Status |
|---|---|---|---|---|
| 1 | repository-policy | JSON/YAML/env/conflict/migration validation | PASS | NOT_RUN |
| 2 | backend-tests | ./mvnw clean test | PASS (434 tests) | NOT_RUN |
| 3 | backend-postgres-integration | PostgreSQL 16 + full test suite | NOT_RUN (no Docker) | NOT_RUN |
| 4 | flyway-validation | Fresh DB + all migrations + health check | NOT_RUN (no Docker) | NOT_RUN |
| 5 | frontend | npm ci + lint + brand + test + build | PASS (238 tests) | NOT_RUN |
| 6 | python-tests | pytest | PASS (165 tests) | NOT_RUN |
| 7 | secret-scan | Gitleaks v8.24.3 current-tree | PASS (0 findings) | NOT_RUN |
| 8 | dependency-scan | OWASP Dependency-Check | NOT_RUN | NOT_RUN |
| 9 | container-smoke | Docker build + non-root + healthcheck | NOT_RUN (no Docker) | NOT_RUN |
| 10 | security-regression | Tenant isolation + token + credential tests | PASS (via backend tests) | NOT_RUN |
| 11 | quality-gate | Aggregation — all 10 must succeed | NOT_RUN | NOT_RUN |

## Known Limitations

- Remote CI not yet executed (branch not pushed)
- Docker not available locally (CI is authority for Docker-dependent checks)
- Migration immutability is part of repository-policy job (not separate job)
