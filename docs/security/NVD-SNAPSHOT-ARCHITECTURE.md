# NVD Snapshot Architecture

**Status:** PROPOSED (EXEC-PROMPT-010R12E)
**Date:** 2026-06-26
**Version:** R12E

---

## Overview

This document describes the persistent NVD snapshot architecture that
decouples NVD database publishing from R12B acceptance testing.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    NVD Snapshot Publisher                        │
│                   (Self-Hosted Runner)                           │
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  Canonical   │───▶│  Work Dir    │───▶│  Validate    │      │
│  │  (persistent)│    │ (copy-on-write)│   │  + Smoke     │      │
│  └──────────────┘    └──────────────┘    └──────┬───────┘      │
│           ▲                                      │              │
│           │           ┌──────────────┐           │              │
│           └───────────│  Promote     │◀──────────┘              │
│                       │  (atomic)    │                          │
│                       └──────┬───────┘                          │
│                              │                                  │
│                       ┌──────▼───────┐                          │
│                       │  Publish     │                          │
│                       │  Snapshot    │                          │
│                       └──────┬───────┘                          │
│                              │                                  │
│  NVD_API_KEY ◀───────────────┘                                  │
│  (publisher only)                                               │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Persistent Storage  │
                    │  (S3 / GHCR)        │
                    │                     │
                    │  snapshots/<id>/    │
                    │    archive.tar.zst  │
                    │    manifest.json    │
                    │    SHA256SUMS       │
                    │                     │
                    │  channels/verified/ │
                    │    latest.json      │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
    ┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
    │ NVD Snapshot    │ │ OWASP       │ │ R12B            │
    │ Integrity       │ │ Consumer    │ │ Orchestrator    │
    │ (GH-hosted)     │ │ (GH-hosted) │ │ (GH-hosted)     │
    │                 │ │             │ │                 │
    │ Read-only       │ │ Read-only   │ │ Read-only       │
    │ No NVD_API_KEY  │ │ No API key  │ │ No NVD_API_KEY  │
    └─────────────────┘ └─────────────┘ └─────────────────┘
```

## Key Principles

### 1. Single Writer
Only the NVD Snapshot Publisher writes to the canonical directory and
publishes snapshots. The concurrency group
`snad-nvd-snapshot-single-writer` prevents parallel writers.

### 2. Copy-on-Write
The publisher never mutates the canonical directory in place. It copies
canonical → work dir, runs update-only against the work dir, validates,
then atomically promotes work → canonical only after validation passes.

### 3. Immutable Snapshots
Each published snapshot has a unique `snapshot_id` derived from the
publish timestamp, publisher commit SHA, and database SHA-256. The
archive is content-addressed and never overwritten.

### 4. Last-Known-Good Preservation
On ANY failure (NVD API outage, validation failure, smoke test failure):
- The canonical directory remains unchanged
- The `latest.json` pointer remains unchanged
- The previous verified snapshot remains available to consumers
- The publisher run fails and retries on the next schedule

### 5. Read-Only Consumers
OWASP, R12B Orchestrator, and NVD Snapshot Integrity are all read-only
consumers. They:
- Do NOT possess `NVD_API_KEY`
- Do NOT contact the NVD API
- Do NOT build the NVD database
- Resolve the latest verified snapshot from storage
- Verify the snapshot's digest, manifest, and freshness before use
- Fail closed if no fresh verified snapshot exists

## Storage Contract

### Archive Format
```
snad-nvd-data-<snapshot_id>.tar.zst
├── data/
│   ├── odc.mv.db
│   ├── odc.trace.db (accepted)
│   ├── cache/
│   └── (other Dependency-Check files)
├── manifest.json
└── SHA256SUMS
```

### Manifest Contract (snad-nvd-snapshot-v1)
See `scripts/security/nvd_snapshot_store.py` → `build_manifest()` and
`validate_manifest()` for the full schema.

### Freshness Policy
| Tier | Age | Action |
|------|-----|--------|
| Preferred | ≤ 12 hours | Accept |
| Warning | 12-24 hours | Accept with warning |
| Maximum | 24-48 hours | Accept (last resort) |
| Blocked | > 48 hours | Fail closed |

## Infrastructure Requirements

### Self-Hosted Runner
The publisher requires a self-hosted runner with labels:
```
self-hosted, linux, x64, snad-nvd-publisher
```

The runner must have:
- Persistent directory: `/var/lib/snad/dependency-check-data/`
- Java 21, Python 3.12, Maven, tar, zstd
- NVD API connectivity
- Storage backend access (S3 or GHCR)
- ~1 GB free disk space

### Storage Backend
Choose one:

**S3-compatible (preferred for OIDC):**
- `NVD_SNAPSHOT_BACKEND=s3`
- `NVD_SNAPSHOT_BUCKET=<bucket-name>`
- `NVD_SNAPSHOT_PREFIX=snad-nvd`
- `NVD_SNAPSHOT_REGION=<region>`
- `NVD_SNAPSHOT_ROLE=<iam-role-arn>` (for OIDC)

**GHCR OCI artifacts:**
- `NVD_SNAPSHOT_BACKEND=ghcr`
- `NVD_SNAPSHOT_GHCR_OWNER=snadaiapp-png`
- `NVD_SNAPSHOT_GHCR_REPO=snad-nvd-data`
- `packages: write` permission

### GitHub Environment
Create a protected environment `nvd-publisher` with:
- `NVD_API_KEY` secret
- Required reviewer (optional but recommended)
- Deployment branch restriction to `main`

## Workflow Reference

| Workflow | File | Runner | Role |
|----------|------|--------|------|
| NVD Snapshot Publisher | `nvd-snapshot-publisher.yml` | Self-hosted | Writer |
| NVD Snapshot Integrity | `nvd-snapshot-integrity.yml` | GH-hosted | Reader |
| Security Scan (OWASP) | `security-scan.yml` | GH-hosted | Reader |
| R12B Orchestrator | `r12b-acceptance-orchestrator.yml` | GH-hosted | Reader |
| NVD Database Maintenance | `nvd-database-maintenance.yml` | N/A | DEPRECATED |

## Bootstrap Sequence

1. Owner provisions self-hosted runner + storage backend
2. Owner configures repository variables and environment
3. Dispatch NVD Snapshot Publisher (initial bootstrap)
4. Publisher downloads full NVD database (~360K records, 30-90 min)
5. Publisher validates database + runs offline smoke test
6. Publisher promotes canonical + publishes immutable snapshot
7. Publisher updates `latest.json` pointer
8. Dispatch NVD Snapshot Integrity to verify download path
9. Dispatch OWASP Offline Scan to verify consumer path
10. Dispatch R12B Orchestrator for final acceptance
