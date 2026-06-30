# SNAD Infrastructure Hardening — Stage 00 Quality Gates

## Pull Request Gate

All of the following must pass before a PR can be merged:

| Check | Command | Required For |
|---|---|---|
| Backend compile | `mvn -B compile` | All PRs touching `apps/sanad-platform/**` |
| Backend tests | `mvn -B test` | All PRs touching `apps/sanad-platform/**` |
| Frontend lint | `npm run lint` | All PRs touching `apps/web/**` |
| Frontend type-check | (via `next build`) | All PRs touching `apps/web/**` |
| Frontend tests | `npm test` | All PRs touching `apps/web/**` |
| Frontend build | `npm run build` | All PRs touching `apps/web/**` |
| Migration validation | Flyway validate-on-migrate | All PRs touching `db/migration/**` |
| Secret scan | `gitleaks detect --no-git --config .gitleaks.toml` | All PRs |
| Dependency policy | OWASP Dependency-Check (CVSS threshold) | All PRs touching `pom.xml` or `package.json` |
| No unresolved conflicts | Git mergeable state | All PRs |
| No modification of historical migrations | Flyway migration immutability | All PRs touching `db/migration/**` |
| Identity governance | `npm run brand:check` | All PRs touching `apps/web/**` |
| Workflow security policy | `python3 scripts/ci/check_workflow_security.py` | All PRs touching `.github/workflows/**` |

## Release Candidate Gate

All of the following must pass before a commit is designated as a release candidate:

| Check | Command | Required |
|---|---|---|
| Integration tests | `mvn -B verify` (Testcontainers PostgreSQL) | YES |
| PostgreSQL tests | Testcontainers-based tests | YES |
| Container build | `docker build -t sanad-backend .` | YES |
| Health and readiness | `curl /actuator/health` returns `UP` | YES |
| Backup validation | `backup-restore-validation.yml` CI workflow | YES |
| Security regression | Tenant isolation + token revocation + credential rotation tests | YES |
| Smoke test | `production-smoke.yml` or manual equivalent | YES |
| Rollback procedure | Documented rollback steps for the release | YES |
| Python tests | `python3 -m pytest tests/ -q` | YES |
| Identity governance | `npm run brand:check` | YES |

## Production Gate

NOT AUTHORIZED at this stage. The following requirements must be met before production authorization is considered:

| Check | Status | Notes |
|---|---|---|
| Performance SLOs | NOT DEFINED | Must define p95/p99 latency targets per endpoint |
| Load test | NOT SUFFICIENT | k6 Health Baseline only (10 VUs, 60s); need full load test |
| Soak test | NOT EXISTS | Must run sustained load for 1+ hours |
| Failover test | NOT EXISTS | Single instance — no failover to test |
| Restore test | CI ONLY | backup-restore-validation passes in CI; no production restore tested |
| Observability | INSUFFICIENT | No metrics, no tracing, no dashboards |
| Alerting | NOT EXISTS | No alert rules or notification channels |
| Capacity headroom | NOT MEASURED | No baseline capacity data |
| Zero critical vulnerabilities | PARTIAL | OWASP passes with `completed_with_errors`; Issue #173 OPEN |
| Credential rotation | NOT STARTED | Issue #173 — owner access required |
| High availability | NOT EXISTS | Single Render instance |
| Multi-tenant isolation hardening | PARTIAL | App-level only; no Hibernate Filter or RLS |
| Audit trail | INSUFFICIENT | No audit_log table; log-based only |
| Pagination | NOT IMPLEMENTED | All list endpoints return full result sets |
