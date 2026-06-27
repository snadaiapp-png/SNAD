# NVD Bulk Feed Operating Model

**Status:** ACTIVE
**Date:** 2026-06-27
**Version:** R12L

---

## Overview

This document describes the operating model for the NVD Bulk Feed Mirror
Publisher, which downloads NVD JSON 2.0 bulk feed files per year,
verifies each against its META, and publishes verified immutable releases.

## Architecture

```
NVD JSON 2.0 Bulk Feeds (https://nvd.nist.gov/feeds/json/cve/2.0/)
   │
   ├── Per-year download (2002 → current year)
   │   ├── Download .json.gz to .part
   │   ├── Download .meta
   │   ├── Parse META (sha256, size, lastModifiedDate)
   │   ├── Verify gzip integrity
   │   ├── Compute SHA-256 (streaming)
   │   ├── Compare with META sha256
   │   └── Atomically rename .part → final
   │
   ├── Durable Checkpoint (GitHub Draft Release)
   │   ├── checkpoint.json
   │   ├── verified feed files
   │   └── META files
   │
   ├── Resume Logic
   │   ├── Skip verified unchanged files
   │   ├── Re-download changed files (META mismatch)
   │   └── Download missing files
   │
   └── Final Release
       ├── Create draft release
       ├── Upload archive + manifest + SHA256SUMS
       ├── Verify all assets
       └── Publish (draft → false)
```

## Key Properties

- **No NVD API**: Uses bulk feed files, not REST API
- **No NVD_API_KEY**: Not required for bulk downloads
- **No vulnz**: Direct HTTP download of .json.gz files
- **Resumable**: Progress persists between workflow runs
- **Per-file verification**: Each file verified against its META
- **Durable checkpoint**: GitHub Draft Release as persistent storage
- **Last-Known-Good**: Previous verified release preserved on failure

## Freshness Policy

| Tier | Age | Action |
|------|-----|--------|
| FRESH | ≤ 24 hours | Normal operation |
| STALE_WARNING | > 24 hours, ≤ 7 days | Development only |
| STALE_BLOCKED | > 7 days | Fail closed (production) |
| MISSING | No release | Fail closed |

## Retry Policy

| Parameter | Value |
|-----------|-------|
| Max attempts per file | 4 |
| Initial delay | 30 seconds |
| Backoff | Exponential |
| Max delay | 10 minutes |
| Jitter | Required |
| Retry-After | Honored |

## Checkpoint Schema

See `scripts/security/nvd_bulk_feed_checkpoint.py` → `Checkpoint` class.

## Feed Release Contract

- Tag: `nvd-feed-<UTC_TIMESTAMP>-<SHORT_SHA>`
- Assets: `snad-nvd-feed-<id>.tar.zst`, `manifest.json`, `SHA256SUMS`
- Draft → verify → publish (never expose incomplete releases)
