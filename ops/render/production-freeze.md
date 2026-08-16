# SNAD Production Freeze Boundary — Clean-Room R1

## Purpose

During Clean-Room R1/R2, feature development may continue on `main`, but ordinary source pushes must not mutate the legacy Render service or production PostgreSQL.

## Allowed From Normal Pushes

```text
checkout source
compile/test
build backend image
publish immutable SHA image
publish build digest/provenance
frontend preview/CI
read-only validation
```

## Forbidden From Normal Pushes

```text
Render deploy
Render suspend/resume
Render environment PUT/PATCH/DELETE
Flyway migrate against production
Flyway baseline against production
production DB DDL/DML
pg_terminate_backend
bootstrap/admin mutation
Vercel production backend cutover
```

## Release Mutations

Production mutations are permitted only through the future canonical protected release control plane after its gates are implemented. Until then, existing legacy production writers are considered untrusted/conflicting and are being inventoried for replacement/archive.

## Current Incident Evidence

GitHub Actions run `31914972536`, triggered by a normal push to `main` at `95dc60c35b6b2c44aafc32610d40fde753905472`, proved that `publish-render-image.yml` had effective auto-deploy enabled. It built and published digest:

```text
sha256:fa3fd0e622a011e7ee44b2a88f9f6bc6bfc1880f3ad8c96aed995622efeb1868
```

and successfully reconciled Render Flyway environment variables before the Render deploy terminated with `update_failed`.

## R1 Freeze Change

On `infra/backend-clean-room-v1`, `.github/workflows/publish-render-image.yml` is converted to build-only. It has no Production environment binding, no Render credentials, no Render API calls, no Flyway mutation, no production health polling, and no deploy step.

The freeze becomes effective for future `main` pushes only after the reviewed change is merged. Until then, the legacy workflow on `main` remains capable of attempting automatic production mutation.
