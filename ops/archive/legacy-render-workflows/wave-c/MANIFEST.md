# Legacy Render Control-Plane Removal — Wave C

Date: 2026-08-16
Branch: `infra/backend-clean-room-v1`

This manifest records files removed from the executable GitHub Actions control plane during the Clean-Room decontamination. Git history remains the forensic source for their prior contents; the files are intentionally not copied here when doing so would preserve operationally dangerous or credential-bearing source in the current tree.

## Security removals

The following 15 workflows were removed after the sanitized auditor reported `plaintext_password_literal`. Secret values are intentionally not reproduced:

- `.github/workflows/final-bootstrap-api.yml`
- `.github/workflows/final-create-admin.yml`
- `.github/workflows/final-solution-v2.yml`
- `.github/workflows/final-solution.yml`
- `.github/workflows/final-spring-hash.yml`
- `.github/workflows/increase-pool.yml`
- `.github/workflows/insert-all-dbs.yml`
- `.github/workflows/insert-render-spring.yml`
- `.github/workflows/insert-spring-hash.yml`
- `.github/workflows/pooler-spring-hash.yml`
- `.github/workflows/restore-render-db.yml`
- `.github/workflows/root-fix-v2.yml`
- `.github/workflows/root-fix-v3.yml`
- `.github/workflows/true-final.yml`
- `.github/workflows/txn-pooler-spring.yml`

## Remaining legacy Render writers removed

The following 39 workflows were then removed because the read-only control-plane auditor classified them as capable of Render mutation/deployment. They are retired in favor of the future single canonical `render-deploy` writer:

- `.github/workflows/_set-enc-key.yml`
- `.github/workflows/bootstrap-admin.yml`
- `.github/workflows/bootstrap-poll.yml`
- `.github/workflows/check-deploy-history.yml`
- `.github/workflows/check-render-deploy.yml`
- `.github/workflows/check-users-update-password.yml`
- `.github/workflows/commercial-go-live.yml`
- `.github/workflows/control-plane-bootstrap-disable.yml`
- `.github/workflows/create-admin-role-v2.yml`
- `.github/workflows/create-admin-role.yml`
- `.github/workflows/create-external-postgres.yml`
- `.github/workflows/crm-contact-500-root-cause.yml`
- `.github/workflows/crm-g1-production-closure.yml`
- `.github/workflows/crm-idempotency-production-reconciliation.yml`
- `.github/workflows/debug-render-deploy-v3.yml`
- `.github/workflows/debug-render-env-keys.yml`
- `.github/workflows/deploy-256mb.yml`
- `.github/workflows/deploy-and-wait.yml`
- `.github/workflows/disable-db-validation.yml`
- `.github/workflows/extract-render-logs.yml`
- `.github/workflows/final-bootstrap.yml`
- `.github/workflows/final-fix.yml`
- `.github/workflows/fix-render-backend-v2.yml`
- `.github/workflows/force-resume-deploy.yml`
- `.github/workflows/insert-admin.yml`
- `.github/workflows/list-render-services.yml`
- `.github/workflows/production-release.yml`
- `.github/workflows/quick-restore-backend.yml`
- `.github/workflows/render-db-env-restore.yml`
- `.github/workflows/render-flyway-runtime-diagnostic.yml`
- `.github/workflows/render-production-preflight.yml`
- `.github/workflows/restore-render-env-vars-v2.yml`
- `.github/workflows/resume-backend.yml`
- `.github/workflows/rollback-bootstrap.yml`
- `.github/workflows/root-fix.yml`
- `.github/workflows/set-password.yml`
- `.github/workflows/switch-txn-deploy.yml`
- `.github/workflows/trigger-render-redeploy.yml`
- `.github/workflows/update-admin-password.yml`

## Governance

```text
LEGACY_GITHUB_RENDER_WRITERS_ALLOWED=0
FUTURE_CANONICAL_RENDER_WRITER=.github/workflows/render-deploy.yml
GREEN_RENDER_PROVISIONING=BLOCKED
PRODUCTION_DB_MUTATION=FORBIDDEN_DURING_CLEAN_ROOM
CREDENTIAL_ROTATION=REQUIRED
```

The next audit must prove the current executable tree satisfies these controls before the canonical deploy writer is introduced.
