# OWASP Dependency-Check Operating Model

**Status:** ACTIVE
**Date:** 2026-06-25
**Version:** EXEC-PROMPT-010R11

---

## Overview

This document describes the operating model for the OWASP Dependency-Check security scan workflow.

## NVD Data Strategy

**Model:** A — NVD API with persistent complete cache

### How It Works

1. **Initial run (cold cache):** Downloads complete NVD database (360,959+ records) via NVD API with 6-second delay between requests and 3 retries. Takes 60-120 minutes.

2. **Subsequent runs (warm cache):** Restores cached database from GitHub Actions cache, performs incremental update only. Takes 5-15 minutes.

3. **Cache key:** `nvd-cache-${runner.os}-dc12.1.0-v2-${week}` — keyed by OS, Dependency-Check version, and week.

4. **Cache save:** Only saves when scan completes successfully (scanner exit code = 0).

5. **Cache restore:** Uses `actions/cache/restore` (read-only) with restore-keys prefix for fallback.

## Fail-Closed Behavior

| Condition | Result |
|-----------|--------|
| Scanner exit code ≠ 0 | `execution_error` → FAIL |
| JSON report missing | `execution_error` → FAIL |
| HTML report missing | `execution_error` → FAIL |
| Total dependencies = 0 | `execution_error` → FAIL |
| HIGH > 0 | `failed` → FAIL |
| CRITICAL > 0 | `failed` → FAIL |
| UNKNOWN > 0 | `incomplete` → FAIL |
| Analysis exceptions > 0 | `incomplete` → FAIL |
| All checks pass | `pass` → PASS |

## Configuration

| Parameter | Value | Source |
|-----------|-------|--------|
| failBuildOnCVSS | 11 | Allows report generation before parser gate |
| nvdApiDelay | 6000ms | NVD API rate limit compliance |
| nvdMaxRetryCount | 3 | Bounded retries |
| hostedSuppressionsEnabled | false | Skip Sonatype OSS Index (401 errors) |
| hostedSuppressionsForceupdate | false | Don't force-update suppression rules |
| timeout-minutes | 180 | Sufficient for initial NVD download |

## Properties Removed (R11 Correction)

| Parameter | Reason |
|-----------|--------|
| nvdApiDataFeedUrl (modified feed) | Insufficient coverage — only 8 days of data |
| failOnError=false | Fail-open — suppresses scanner execution errors |

## Artifact

- **Name:** `owasp-dependency-check-report-<run_id>`
- **Contents:** `dependency-check-report.json` + `dependency-check-report.html`
- **Retention:** 30 days
- **if-no-files-found:** error (not warn)

## Parser

- **Script:** `scripts/ci/parse_owasp_report.py`
- **Tests:** `tests/ci/test_parse_owasp.py` (28+ scenarios)
- **Result types:** `pass`, `failed`, `incomplete`, `execution_error`
- **Only `pass` is accepted by Final Enforcement**

## R12 Update

The OWASP scan is now an **offline reader**:
- No NVD API calls during scanning
- No OSS Index calls
- Requires verified NVD database from maintenance workflow
- Uses `autoUpdate=false`
- Uses `ossIndexAnalyzerEnabled=false`
- Uses `-Dformat=HTML -Dformat=JSON` (correct syntax for DC 12.1.0)
