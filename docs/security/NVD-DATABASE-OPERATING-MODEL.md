# NVD Database Operating Model

**Status:** ACTIVE
**Date:** 2026-06-26
**Version:** EXEC-PROMPT-010R12A

---

## Architecture

```
NVD Database Maintenance Workflow (Single Writer)
    │
    ├── Restore last verified database
    │     → into CANONICAL path:
    │       ${{ github.workspace }}/.cache/dependency-check-data
    │
    ├── Copy-on-Write to clean temp work dir:
    │     ${{ runner.temp }}/dependency-check-work
    │
    ├── Run update-only (single deterministic retry loop)
    │     1 attempt + 60s backoff + 300s backoff
    │     Stops immediately on first success.
    │     -DossIndexAnalyzerEnabled=false (no OSS Index contact)
    │
    ├── Validate via scripts/ci/validate_nvd_database.py
    │     (manifest schema v3, DC version, SHA-256, size, freshness,
    │      lock files, temp files)
    │
    ├── Emit sanad-nvd-manifest.json with verified SHA-256
    │
    ├── Promote work dir → CANONICAL path (atomic replace)
    │
    ├── Save CANONICAL path under immutable unique cache key
    │     nvd-db-<os>-dc-<dc-ver>-v3-<UTC-date>-<run-id>-<run-attempt>
    │
    └── Independent verify-nvd-cache-restore job
          (fresh runner, exact-key restore, revalidate, offline smoke test)
              │
              ▼
OWASP Security Scan Workflow (Read-Only Reader)
    │
    ├── Restore verified NVD database → CANONICAL path
    ├── Require cache-hit OR matched-cache-key (else NVD_DATABASE_MISSING)
    ├── Run validate_nvd_database.py
    ├── Run Dependency-Check -Powasp-offline-gate (autoUpdate=false)
    ├── Preflight: verify both JSON + HTML reports exist and are valid
    ├── Offline-mode network audit (forbidden hosts check)
    ├── Parse JSON report
    ├── Upload evidence
    └── Final enforcement (R12A Section 19 — all 13 conditions)
```

## Single-Writer Principle

Only `nvd-database-maintenance.yml` may write to the NVD database.
The `security-scan.yml` workflow is a read-only consumer.

Concurrency group `sanad-nvd-database-writer` prevents parallel writes.

## Canonical Cache Path

R12A Section 6 mandates a single canonical path used across the entire
NVD database lifecycle:

```
NVD_CANONICAL_DIR = ${{ github.workspace }}/.cache/dependency-check-data
```

This path is used identically by:

| Phase                           | Path                                      |
|---------------------------------|-------------------------------------------|
| Maintenance cache restore       | `${NVD_CANONICAL_DIR}`                    |
| Maintenance work directory      | `${{ runner.temp }}/dependency-check-work`|
| Maintenance cache save          | `${NVD_CANONICAL_DIR}`                    |
| Independent restore verification| `${NVD_CANONICAL_DIR}`                    |
| Offline scanner cache restore   | `${NVD_CANONICAL_DIR}`                    |
| Dependency-Check dataDirectory  | `${NVD_CANONICAL_DIR}`                    |
| Database validator              | `${NVD_CANONICAL_DIR}`                    |
| Manifest creation / hash verify | `${NVD_CANONICAL_DIR}`                    |

**Forbidden** (R12 divergent paths now eliminated):
- `nvd-restored` (was the maintenance restore target)
- `nvd-work` (was the maintenance work dir AND save source)
- `nvd-data` (was the scanner restore target)

## Copy-on-Write Pattern (R12A Section 7)

The maintenance workflow never mutates the restored canonical directory
in place. Instead:

1. Restore last verified cache → canonical path.
2. Create clean temp work dir under `${{ runner.temp }}`.
3. Copy canonical → work dir (if a valid cache exists).
4. Run `update-only` against the work dir.
5. Validate the work dir's database.
6. Generate manifest inside the work dir.
7. **Only after validation passes**: replace canonical dir with work dir contents.
8. Save canonical dir under the new immutable cache key.

If update or validation fails: canonical is NOT replaced, no new cache is saved, and the build is marked failed.

## Cache Key

```
CACHE_PREFIX = nvd-db-${runner.os}-dc-${DEPENDENCY_CHECK_VERSION}-${NVD_CACHE_SCHEMA}
CACHE_KEY    = ${CACHE_PREFIX}-${UTC_DATE}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}
```

Example: `nvd-db-Linux-dc-12.1.0-v3-20260626-28210000000-1`

- **Immutable:** Every successful build creates a new unique key (includes run_id + run_attempt).
- **Restore primary key:** A deliberately non-existing exact key for the current run, so `cache-hit` and `cache-matched-key` outputs are populated.
- **Restore prefix:** Falls back to the most recent matching prefix.
- **No overwrite:** Failed builds never create cache entries.

Recorded outputs (R12A Section 8):
- `cache_hit`
- `cache_primary_key`
- `cache_matched_key`
- `cache_output_key`

## Deterministic Retry Loop (R12A Section 9)

R12 had three separate steps (`update_attempt1/2/3`) with `if:` conditionals. R12A replaces these with a single bash step that manages 1..3 attempts internally:

```bash
for ATTEMPT in 1 2 3; do
  case "$ATTEMPT" in
    1) DELAY=0 ;;
    2) DELAY=60 ;;
    3) DELAY=300 ;;
  esac
  [ "$DELAY" -gt 0 ] && sleep "$DELAY"
  mvn ... update-only ...
  EXIT_CODE=$?
  record_attempt_result "$ATTEMPT" "$EXIT_CODE"
  if [ "$EXIT_CODE" -eq 0 ]; then
    SUCCESS=true; SUCCESSFUL_ATTEMPT="$ATTEMPT"; break
  fi
  FINAL_EXIT_CODE="$EXIT_CODE"
done
```

**Properties:**
- Stops IMMEDIATELY on first success.
- Records per-attempt exit codes as separate outputs.
- After 3 failures: exits with the final non-zero exit code.

Published outputs:
- `update_result` (success | failure)
- `successful_attempt` (0 if all failed, else 1/2/3)
- `final_exit_code`
- `attempt_1_exit_code`
- `attempt_2_exit_code`
- `attempt_3_exit_code`

## NVD Builder Configuration

| Parameter                     | Value      | Purpose                                  |
|-------------------------------|------------|------------------------------------------|
| Goal                          | update-only| No dependency analysis                   |
| nvdApiDelay                   | 6000ms     | NVD API rate limit compliance            |
| nvdMaxRetryCount              | 5          | Bounded retries per attempt              |
| failOnError                   | true       | Fail-closed                              |
| hostedSuppressionsEnabled     | false      | Skip Sonatype OSS Index (401 errors)     |
| ossIndexAnalyzerEnabled       | false      | R12A Section 10 — fully disable OSS Index|
| versionCheckEnabled           | false      | Skip version check                       |
| API key                       | via env var name (not value)              |

## Database Validation (R12A Section 13)

The strengthened validator `scripts/ci/validate_nvd_database.py` enforces:

| Check                                                | Requirement                                  |
|------------------------------------------------------|----------------------------------------------|
| Canonical directory exists                           | YES                                          |
| `odc.mv.db` exists                                   | YES                                          |
| Database size exceeds configured realistic minimum  | YES (default 50 MiB)                         |
| Manifest exists                                      | YES                                          |
| Manifest is valid JSON                               | YES                                          |
| `schema_version` = `v3`                              | YES                                          |
| `dependency_check_version` = `12.1.0`                | YES                                          |
| `update_mode` = `update-only`                        | YES                                          |
| `update_exit_code` = `0`                             | YES                                          |
| `validation_result` = `valid`                        | YES                                          |
| `database_filename` matches actual                  | YES (`odc.mv.db`)                            |
| Recorded size matches actual size                   | YES                                          |
| Recorded SHA-256 matches actual SHA-256             | YES                                          |
| `builder_run_id` present and numeric                | YES                                          |
| `builder_sha` is 40-char hex                        | YES (git commit SHA)                         |
| `update_completed_at` parses as UTC                 | YES                                          |
| Database age within policy                          | YES (default ≤ 48 hours)                     |
| No H2 lock files (`*.lock.db`, `*.lock`)            | YES                                          |
| No temp files (`*.tmp.db`, `*.temp.db`, etc.)       | YES                                          |

### Realistic Minimum Size

A real NVD `odc.mv.db` after initial download is hundreds of MB. The conservative default of 50 MiB is well below the smallest known-good snapshot and catches "fresh empty H2 file" failure modes. The threshold is configurable via `--min-size` or `NVD_MIN_SIZE_BYTES` env var.

## Independent Restore Verification (R12A Section 14)

A separate `verify-nvd-cache-restore` job runs after the builder succeeds:

1. Runs on a **fresh runner** (separate machine from the builder).
2. Restores the cache using the **exact key** saved by the builder.
3. Restores into the canonical path.
4. Executes `validate_nvd_database.py`.
5. Verifies database SHA-256 matches the builder's reported SHA-256.
6. Verifies the manifest matches.
7. Runs an offline smoke test (`autoUpdate=false`).

### Smoke Test Acceptance

The smoke test must prove:
- Database can be opened by Dependency-Check
- No NVD network update is attempted
- No OSS Index request is attempted
- No H2 corruption or lock error occurs

A cache is NOT considered accepted before this job passes.

## Freshness Policy

- **Daily:** Maximum 26 hours (recommended)
- **Emergency:** Maximum 48 hours (documented exception only)
- **Stale:** > 48 hours → scan refuses to start

## Offline Scan Configuration

| Parameter                     | Value      | Purpose                                  |
|-------------------------------|------------|------------------------------------------|
| Goal                          | check      | Run dependency analysis                  |
| Maven profile                 | owasp-offline-gate | Multi-format reports (HTML+JSON)  |
| autoUpdate                    | false      | No NVD API calls                         |
| failOnError                   | true       | Fail-closed                              |
| failBuildOnCVSS               | 11         | HIGH/CRITICAL gate                       |
| ossIndexAnalyzerEnabled       | false      | No Sonatype OSS Index                    |
| hostedSuppressionsEnabled     | false      | Skip suppression download                |
| versionCheckEnabled           | false      | Skip version check                       |
| NVD_API_KEY                   | NOT USED   | Offline                                  |

## R12 Defects Corrected by R12A

1. **Divergent cache paths** (nvd-restored / nvd-work / nvd-data) → unified canonical path.
2. **Three retry steps with `if:` conditionals** → single deterministic bash loop.
3. **`-Dformat=HTML -Dformat=JSON` repeated flag** (last wins, produces only JSON) → Maven profile with `<formats>` element.
4. **Inline shell validator with only `> 1024 byte` check** → `scripts/ci/validate_nvd_database.py` with 17+ checks.
5. **No independent restore verification** → `verify-nvd-cache-restore` job on fresh runner.
6. **No offline-mode network audit** → scanner log grepped for forbidden hosts.
7. **Cache key lacked `GITHUB_RUN_ATTEMPT`** → key now includes it.
8. **OSS Index not disabled in update-only** → `-DossIndexAnalyzerEnabled=false` added.

## R11 Root Cause (Historical)

The R11 failure was caused by:
1. `-Dformat=HTML,JSON` — invalid syntax for DC 12.1.0 (treated as single template name)
2. Sonatype OSS Index 401 — `hostedSuppressionsEnabled=false` did not fully disable OSS Index

R12 separated maintenance from scanning. R12A hardened that separation with canonical paths, deterministic retry, manifest-verified validation, independent restore testing, and multi-format preflight.
