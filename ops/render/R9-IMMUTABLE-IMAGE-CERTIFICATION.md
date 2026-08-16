# R9 — Immutable GHCR Image Certification

## Verdict

`PASS`

## Certified source

```text
SOURCE_SHA=8c6ba8aa70e115bc6557847549ea497b046ac171
AUDIT_RUN=31920774005
IMAGE_RUN=31920774035
IMAGE_JOB=95100223132
```

## Clean-Room control-plane gate

```text
CONTRACT_TESTS=64/64 PASS
CANONICAL_PRODUCTION_WRITERS=3
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
```

## GHCR publication evidence

```text
IMAGE_TAG=ghcr.io/snadaiapp-png/snad-backend:8c6ba8aa70e115bc6557847549ea497b046ac171
IMAGE_DIGEST=sha256:97f1de95d5217c88eb142369580cd6edd9d30b9d2750ee74f2bc33f471c9eb54
IMMUTABLE_REF=ghcr.io/snadaiapp-png/snad-backend@sha256:97f1de95d5217c88eb142369580cd6edd9d30b9d2750ee74f2bc33f471c9eb54
LINUX_AMD64_MANIFEST=sha256:6d16ed705095b9b36929b883eea86915af682f571411e6adef217640898f21ac
IMAGE_CONFIG=sha256:7e545d8dca3fcdbae96aeba544f9a6560f3a4b4def9af3d3976a6c40e8d2b95d
ATTESTATION_MANIFEST=sha256:962a416afe4ff52c234f819dd6e68de4a9f4a7e04d3cf18e5e211fb85053add5
```

The workflow successfully pulled the exact immutable GHCR reference back from the registry and verified:

```text
IMMUTABLE_DIGEST_GATE=PASS
REVISION_LABEL_GATE=PASS
OCI_REVISION=8c6ba8aa70e115bc6557847549ea497b046ac171
SBOM=ENABLED
PROVENANCE=mode=max
PLATFORM=linux/amd64
```

Provenance builder:

```text
https://github.com/snadaiapp-png/SNAD/actions/runs/31920774035/attempts/1
```

## Artifact evidence

```text
EVIDENCE_ARTIFACT_ID=9256319817
EVIDENCE_ARTIFACT_NAME=backend-image-evidence-31920774035
EVIDENCE_ARTIFACT_ZIP_SHA256=3a00050632dc8697c5bd67ebee8737dfd24d53f0510af501d5e9e7830d06b7f9
BUILD_RECORD_ARTIFACT_ID=9256320040
BUILD_RECORD_SHA256=7559ab70a6d395b682a577aa173df102c8f482d1ef7512ebe07774f1b88c0aa5
```

## Publication concurrency governance

Clean-Room image builds now cancel superseded Clean-Room builds while protected `main` publication remains non-cancellable:

```text
CLEAN_ROOM_SUPERSEDED_BUILD_CANCEL=ENABLED
MAIN_RELEASE_BUILD_CANCEL=DISABLED
```

This behavior was verified by an intermediate Clean-Room image run being cancelled after a newer source SHA arrived.

## Safety boundary

```text
PRODUCTION_RENDER_DEPLOYMENT=0
PRODUCTION_RENDER_ENV_MUTATION=0
PRODUCTION_DATABASE_MUTATION=0
PRODUCTION_FLYWAY_EXECUTION=0
VERCEL_CUTOVER=0
CREDENTIAL_ROTATION=0
```

Credential rotation remains deferred to the final project/cutover phase by project-owner decision.

## Next gate

`R10 — PostgreSQL Direct Read-Only Certification`

R10 must prove the governing Direct route and schema/Flyway state without executing `flyway:migrate`, destructive SQL, pooler fallback, Render deployment, or Vercel cutover.
