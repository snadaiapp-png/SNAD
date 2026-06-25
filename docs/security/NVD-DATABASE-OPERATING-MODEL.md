# NVD Database Operating Model

**Status:** ACTIVE
**Date:** 2026-06-25
**Version:** EXEC-PROMPT-010R12

---

## Architecture

```
NVD Database Maintenance Workflow (Single Writer)
    │
    ├── Restore last verified database
    ├── Run update-only (3 retries with backoff)
    ├── Validate database (size, locks, manifest)
    ├── Generate SHA-256 manifest
    └── Save immutable unique cache
              │
              ▼
OWASP Security Scan Workflow (Read-Only Reader)
    │
    ├── Restore verified NVD database
    ├── Validate freshness and integrity
    ├── Run Dependency-Check offline (autoUpdate=false)
    ├── Generate JSON and HTML reports
    ├── Parse results
    ├── Upload evidence
    └── Enforce terminal security decision
```

## Single-Writer Principle

Only `nvd-database-maintenance.yml` may write to the NVD database.
The `security-scan.yml` workflow is a read-only consumer.

Concurrency group `sanad-nvd-database-writer` prevents parallel writes.

## Cache Strategy

- **Key format:** `nvd-db-<os>-dc-<version>-<schema>-<UTC-date>-<run-id>`
- **Immutable:** Every successful build creates a new unique key
- **Restore prefix:** `nvd-db-<os>-dc-<version>-<schema>-` allows fallback to last valid
- **No overwrite:** Failed builds never create cache entries

## Database Validation

| Check | Requirement |
|-------|-------------|
| Directory exists | YES |
| odc.mv.db exists | YES |
| Size > 1KB | YES |
| No lock files | YES |
| Manifest exists | YES |
| SHA-256 matches | YES |
| Age < 48 hours | YES |

## Freshness Policy

- **Daily:** Maximum 26 hours (recommended)
- **Emergency:** Maximum 48 hours (documented exception only)
- **Stale:** > 48 hours → scan refuses to start

## NVD Update Configuration

| Parameter | Value |
|-----------|-------|
| Goal | update-only |
| nvdApiDelay | 6000ms |
| nvdMaxRetryCount | 5 |
| failOnError | true |
| hostedSuppressionsEnabled | false |
| API key | via environment variable name (not value) |

## Offline Scan Configuration

| Parameter | Value |
|-----------|-------|
| Goal | check |
| autoUpdate | false |
| failOnError | true |
| failBuildOnCVSS | 11 |
| ossIndexAnalyzerEnabled | false |
| hostedSuppressionsEnabled | false |
| NVD_API_KEY | NOT USED (offline) |

## R11 Root Cause (Corrected)

The R11 failure was caused by:
1. `-Dformat=HTML,JSON` — invalid syntax for DC 12.1.0 (treated as single template name)
2. Sonatype OSS Index 401 — `hostedSuppressionsEnabled=false` did not fully disable OSS Index

R12 fixes:
1. Use `-Dformat=HTML -Dformat=JSON` (repeated parameter)
2. Add `-DossIndexAnalyzerEnabled=false` to fully disable OSS Index
3. Separate NVD maintenance from scanning
