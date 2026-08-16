# SNAD Render Clean-Room Release Authority

This directory documents the canonical backend release boundary.

Production release concerns are separated deliberately:

```text
Git commit
  -> protected CI
  -> immutable GHCR image + digest
  -> PostgreSQL Direct Flyway migration gate
  -> exact-digest Render deploy
  -> readiness + non-destructive smoke
  -> cutover
```

## Authorities

- Image build: `.github/workflows/publish-render-image.yml`
- Database migration: `.github/workflows/database-migrate.yml`
- Render deploy: `.github/workflows/render-deploy.yml`
- Render rollback: `.github/workflows/render-rollback.yml` — currently BLOCKED until legitimately created and certified
- Control-plane audit: `.github/workflows/clean-room-control-plane-audit.yml`

## Hard rules

- Production migration is PostgreSQL Direct only, port 5432.
- Pooler fallback is forbidden for Flyway migration.
- Spring Boot production runtime uses `FLYWAY_ENABLED=false` by default.
- Image build must not mutate Render or Production database state.
- Render deployment consumes an exact immutable GHCR digest.
- Render auto-deploy remains disabled.
- No exposed credential may be reused for Green.
- No destructive database reset, Flyway clean, blind baseline, or production recreation.
- No Vercel cutover until Green readiness and smoke gates pass.

Current detailed status: `ops/render/CLEAN-ROOM-GATE-STATUS.md`.
Rollback blocker: `ops/render/ROLLBACK-BLOCKER.md`.
