# SNAD Workflow Inventory

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Existing Workflows (35 total)

| Workflow | Trigger | Purpose | Deploys? | Risk |
|---|---|---|---|---|
| ci.yml | push/pr | Backend Maven tests | NO | LOW |
| web-ci.yml | push/pr/dispatch | Frontend build | NO | LOW |
| security-baseline.yml | pr/dispatch | Secret scan + security checks | NO | MEDIUM |
| security-scan.yml | schedule/push/pr/dispatch | OWASP scan | NO | MEDIUM |
| backup-restore-validation.yml | pr/dispatch | PostgreSQL backup/restore | NO | LOW |
| compile-diagnostics.yml | pr/dispatch | Maven compile | NO | LOW |
| master-backlog-validation.yml | pr/dispatch | Backlog generation | NO | LOW |
| service-decomposition-validation.yml | pr/dispatch | Service catalog | NO | LOW |
| render-blueprint-validation.yml | push/pr/dispatch | Render config validation | NO | LOW |
| performance-baseline.yml | pr/dispatch | k6 health baseline | NO | LOW |
| production-release.yml | push(main)/dispatch | Production deploy | YES | HIGH |
| backend-deploy.yml | workflow_dispatch | Backend deploy | YES | HIGH |
| production-smoke.yml | workflow_dispatch | Production smoke test | NO | MEDIUM |
| backend-production-smoke.yml | workflow_dispatch | Backend smoke | NO | MEDIUM |
| auth-tenant-production-acceptance.yml | workflow_dispatch | Auth acceptance | NO | MEDIUM |
| postgres-acceptance.yml | workflow_dispatch | PostgreSQL acceptance | NO | LOW |
| render-env-recovery.yml | workflow_dispatch | Render env recovery | NO | MEDIUM |
| render-production-preflight.yml | workflow_dispatch | Render preflight | NO | MEDIUM |
| cost-monitor.yml | schedule | Cost monitoring | NO | LOW |
| uptime-monitor.yml | schedule | Uptime monitoring | NO | LOW |
| pilot-synthetic-monitoring.yml | schedule | Synthetic monitoring | NO | LOW |
| metrics-collector-v2.yml | schedule | Metrics collection | NO | LOW |
| branch-reconciliation-inventory.yml | schedule/pr | Branch audit | NO | LOW |
| development-security-acceptance.yml | workflow_dispatch | Dev security | NO | LOW |
| smoke-test.yml | workflow_dispatch | Smoke test | NO | LOW |
| backup-verify.yml | schedule | Backup verification | NO | LOW |
| snad-identity-governance.yml | pr/push/dispatch | Identity check | NO | LOW |
| r12b-acceptance-orchestrator.yml | workflow_dispatch | R12B acceptance | NO | MEDIUM |
| control-plane-validation.yml | workflow_dispatch | Control plane | NO | LOW |
| nvd-bulk-feed-mirror-publisher.yml | schedule/dispatch | NVD feed | NO | LOW |
| nvd-database-maintenance.yml | (deprecated on:{}) | NVD maintenance | NO | LOW |
| nvd-feed-mirror-publisher.yml | (deprecated on:{}) | NVD feed legacy | NO | LOW |
| nvd-snapshot-bootstrap.yml | schedule/dispatch | NVD snapshot | NO | LOW |
| nvd-snapshot-integrity.yml | schedule/dispatch | NVD integrity | NO | LOW |
| nvd-snapshot-publisher.yml | schedule/dispatch | NVD publisher | NO | LOW |

## New Workflow Added in Stage 02

| Workflow | Trigger | Purpose | Deploys? | Required Check? |
|---|---|---|---|---|
| quality-gate.yml | pull_request/push(main)/dispatch | Central quality gate (11 jobs + aggregation) | NO | YES (quality-gate) |
