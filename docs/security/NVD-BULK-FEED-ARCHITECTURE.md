# NVD Bulk Feed Architecture

**Status:** PROPOSED (EXEC-PROMPT-010R12L)
**Date:** 2026-06-27
**Version:** R12L

## Overview

Replaces the non-resumable NVD API bootstrap with a resumable bulk feed download pipeline.

## Architecture

```
NVD JSON 2.0 Bulk Feeds (nvd.nist.gov/feeds/json/cve/2.0/)
   │
   ├── Per-year download (nvdcve-2.0-YYYY.json.gz + .meta)
   ├── META SHA-256 verification per file
   ├── Durable checkpoint (GitHub Draft Release)
   │     └── Survives workflow interruption
   ├── Resume: skip verified, download only pending
   │
   ▼
Verified Immutable Feed Release (GitHub Releases)
   │
   ├── archive.tar.zst (files at root, no data/ prefix)
   ├── manifest.json (schema v2, provenance)
   └── SHA256SUMS
   │
   ▼
Snapshot Builder (datafeed mode, offline)
   │
   ▼
OWASP Offline → Integrity → R12B Final
```

## Key Properties

1. **No NVD API calls** — uses bulk feed files directly
2. **No NVD_API_KEY** — bulk feeds are public
3. **No vulnz** — direct HTTP download
4. **Resumable** — progress persists between runs
5. **Per-file verification** — META SHA-256 per file
6. **Durable checkpoint** — GitHub Draft Release as storage
7. **Last-Known-Good** — previous verified release preserved on failure

## File Mapping

| NVD Source | Internal Normalized |
|------------|-------------------|
| nvdcve-2.0-2002.json.gz | nvdcve-2002.json.gz |
| nvdcve-2.0-2026.json.gz | nvdcve-2026.json.gz |
| nvdcve-2.0-modified.json.gz | nvdcve-modified.json.gz |
| nvdcve-2.0-recent.json.gz | nvdcve-recent.json.gz |

## Checkpoint Schema

See `scripts/security/nvd_bulk_feed_checkpoint.py` → `Checkpoint` class.

## Feed Manifest Schema (v2)

See `scripts/security/nvd_bulk_feed_release.py` → `build_feed_manifest()`.

## Freshness Policy

| State | Age | Action |
|-------|-----|--------|
| FRESH | ≤24h | Normal use |
| STALE_WARNING | >24h | Development only |
| STALE_BLOCKED | >7d | Fail closed |
| MISSING | N/A | Fail closed |
| INVALID | N/A | Fail closed |
