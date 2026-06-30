# SNAD Infrastructure Hardening — Stage 00 Command Matrix

All commands extracted from repository files (pom.xml, package.json, workflow files, Dockerfile, scripts).

## Build Commands

| Component | Official Command | Source | Local Result |
|---|---|---|---|
| Backend compile | `mvn -B compile` | `ci.yml` job `test` | NOT_RUN (no Maven locally) |
| Backend unit tests | `mvn -B test` | `ci.yml` job `test` | NOT_RUN (CI: 434+ passed) |
| Backend integration tests | `mvn -B verify` | `ci.yml` (Testcontainers) | NOT_RUN (CI: passes) |
| Backend package | `mvn -B package -DskipTests` | Dockerfile build stage | NOT_RUN (CI Docker build: passes) |
| Frontend install | `npm ci` | `web-ci.yml`, `package.json` | PASS |
| Frontend lint | `npm run lint` (eslint) | `web-ci.yml`, `package.json` | PASS |
| Frontend type-check | `npx tsc --noEmit` (implicit via `next build`) | `web-ci.yml` | PASS (via build) |
| Frontend unit tests | `npm test` (vitest run) | `web-ci.yml`, `package.json` | PASS (238 passed) |
| Frontend build | `npm run build` (next build) | `web-ci.yml`, `package.json` | PASS (6 routes) |
| Frontend brand check | `npm run brand:check` | `package.json` | PASS |
| Python tests | `python3 -m pytest tests/ -q` | `tests/` directory | PASS (165 passed) |

## Security Commands

| Component | Official Command | Source | Local Result |
|---|---|---|---|
| Secret scan (current tree) | `gitleaks detect --source . --no-git --config .gitleaks.toml --redact` | `security-baseline.yml` | PASS (0 findings) |
| Secret scan (history) | `gitleaks detect --source . --config .gitleaks.toml --redact` | `security-baseline.yml` | 8 raw (all classified) |
| Dependency scan | `mvn -B verify -Powasp-offline-gate` | `security-scan.yml` | NOT_RUN (CI: passes) |
| Workflow security | `python3 scripts/ci/check_workflow_security.py` | `security-baseline.yml` | PASS (via pytest) |

## Infrastructure Commands

| Component | Official Command | Source | Local Result |
|---|---|---|---|
| Docker build | `docker build -t sanad-backend .` (from `apps/sanad-platform/`) | Dockerfile | NOT_RUN (no Docker) |
| Docker Compose (dev) | `docker compose -f docker-compose.yml up -d --build` | `docker-compose.yml` | NOT_RUN |
| Docker Compose (prod) | `docker compose -f docker-compose.prod.yml --env-file .env up -d --build` | `docker-compose.prod.yml` | NOT_RUN |
| Health check | `curl -f http://localhost:8080/actuator/health` | Dockerfile HEALTHCHECK | NOT_RUN |
| Migration validation | `mvn -B test -Dtest=BackupRestoreValidation` (CI) or Flyway startup | `backup-restore-validation.yml` | NOT_RUN (CI: passes) |

## Validation Commands

| Component | Official Command | Source | Local Result |
|---|---|---|---|
| YAML validation | `python3 -c "import yaml; ..."` (custom) | This stage | PASS (all YAML valid) |
| JSON validation | `python3 -c "import json; ..."` (custom) | This stage | PASS (all JSON valid) |
| Master backlog | `python3 scripts/generate_master_execution_backlog.py` | `master-backlog-validation.yml` | PASS (via pytest) |
| Service decomposition | `python3 scripts/generate_service_decomposition.py` | `service-decomposition-validation.yml` | PASS (via pytest) |
