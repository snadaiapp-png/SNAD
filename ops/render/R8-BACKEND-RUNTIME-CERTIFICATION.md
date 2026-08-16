# R8 — Backend Runtime Certification

## Verdict

`PASS`

## Certified source

```text
SOURCE_SHA=4397e343063ec82dffb3c7e4511349b4ebe38015
AUDIT_RUN=31920459286
RUNTIME_RUN=31920459290
RUNTIME_JOB=95099441380
```

The certified source contains the latest protected `main` ERP baseline available before certification, synchronized into `infra/backend-clean-room-v1` without force/reset.

## Clean-Room control-plane gate

```text
CONTRACT_TESTS=58/58 PASS
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
```

## Isolated PostgreSQL / Flyway evidence

```text
POSTGRESQL_VERSION=16.15
FLYWAY_VERSION=11.7.2
MIGRATIONS_VALIDATED=115
MIGRATIONS_APPLIED=115
SCHEMA_VERSION=v20260816.8
FLYWAY_HISTORY_BEFORE=115
FLYWAY_HISTORY_AFTER=115
RUNTIME_SCHEMA_MUTATION=NONE
MIGRATION_AUTHORITY=SEPARATE_FLYWAY
RUNTIME_FLYWAY_ENABLED=false
```

ERP migrations `20260816.7` and `20260816.8` are included in the certified migration set.

## Runtime evidence

```text
MEMORY_LIMIT=512MiB
STARTUP_SECONDS=12
LIVENESS_GATE=PASS
READINESS_GATE=PASS
PRODUCTION_GUARD_ENABLED=true
MEMORY_USAGE=311.8MiB / 512MiB
MEMORY_PERCENT=60.90%
CPU_SNAPSHOT=0.14%
PIDS=34
GRACEFUL_SHUTDOWN_GATE=PASS
```

The image started with the production profile and production integration guard enabled. Required integration secrets were generated ephemerally inside the isolated CI job and masked. No Production provider credential was used.

## Image evidence

The local certification build produced:

```text
LOCAL_CERT_IMAGE_ID=sha256:72e694a13e556a43e15dc37f3cbda620150fc394728531b047817339295152e8
```

This is local CI image evidence only, not the final GHCR release digest. R9 owns immutable GHCR publication/certification.

## Artifact evidence

```text
ARTIFACT_ID=9256222972
ARTIFACT_NAME=backend-runtime-certification-evidence-31920459290
ARTIFACT_ZIP_SHA256=3c2b27abbf1467f3e15abbdd07b4e655405cab0434574f0503d2f1f3f6dce834
```

## Safety boundary

```text
PRODUCTION_RENDER_MUTATION=0
PRODUCTION_DATABASE_MUTATION=0
PRODUCTION_FLYWAY_EXECUTION=0
VERCEL_CUTOVER=0
CREDENTIAL_ROTATION=0
```

Credential rotation remains deferred to the final project/cutover phase by project-owner decision.

## Next gate

`R9 — Immutable GHCR Image Certification`
