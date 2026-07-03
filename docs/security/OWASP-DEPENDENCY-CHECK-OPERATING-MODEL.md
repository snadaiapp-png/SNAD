# OWASP Dependency-Check Operating Model

**Status:** ACTIVE
**Date:** 2026-06-26
**Version:** EXEC-PROMPT-010R12A

---

## Overview

This document describes the operating model for the OWASP Dependency-Check security scan workflow.

## Architecture (R12A)

The scanner is an **offline reader** of a verified NVD database built by `nvd-database-maintenance.yml`. The scanner never contacts NVD or Sonatype OSS Index.

```
Builder workflow (single writer)
   │
   ├── builds NVD database in canonical path
   ├── validates with scripts/ci/validate_nvd_database.py
   ├── saves immutable cache key
   └── independent verify-nvd-cache-restore job
                 │
                 ▼
Scanner workflow (read-only reader)
   │
   ├── restore verified cache → canonical path
   ├── require cache-hit OR matched-key (else NVD_DATABASE_MISSING)
   ├── validate_nvd_database.py
   ├── mvn -Powasp-offline-gate (HTML+JSON reports)
   ├── preflight: verify both report files exist and are valid
   ├── offline-mode network audit (forbidden hosts check)
   ├── parse JSON
   ├── upload evidence
   └── final enforcement (13 conditions)
```

## Canonical Cache Path

R12A Section 6 mandates a single canonical path used across the entire NVD database lifecycle:

```
NVD_CANONICAL_DIR = ${{ github.workspace }}/.cache/dependency-check-data
```

This eliminates R12's divergent paths (`nvd-restored`, `nvd-work`, `nvd-data`).

## Fail-Closed Behavior (R12A Section 19)

The scanner's Final Enforcement requires ALL of the following conditions:

| Condition                              | Required Value |
|----------------------------------------|----------------|
| Parser tests                           | passed         |
| Validator tests                        | passed         |
| Verified cache restored                | true           |
| Matched cache key recorded             | non-empty      |
| Database validation                    | valid          |
| Database freshness                     | valid          |
| Database hash                          | valid          |
| Offline mode                           | verified       |
| Scanner exit code                      | 0              |
| JSON report present                    | true           |
| HTML report present                    | true           |
| JSON valid                             | true           |
| HTML valid                             | true           |
| Total dependencies                     | > 0            |
| Parser result                          | pass           |
| HIGH                                   | 0              |
| CRITICAL                               | 0              |
| UNKNOWN                                | 0              |
| Analysis Exceptions                    | 0              |
| Artifact upload                        | success        |

Any other result: `exit 1`.

## Multi-Format Report Configuration (R12A Section 11)

R12A prefers a Maven profile over repeated `-Dformat=...` flags. The profile `owasp-offline-gate` is defined in `apps/sanad-platform/pom.xml`:

```xml
<profile>
  <id>owasp-offline-gate</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.owasp</groupId>
        <artifactId>dependency-check-maven</artifactId>
        <version>${dependency-check.version}</version>
        <configuration>
          <formats>
            <format>HTML</format>
            <format>JSON</format>
          </formats>
          <autoUpdate>false</autoUpdate>
          <failOnError>true</failOnError>
          <failBuildOnCVSS>11</failBuildOnCVSS>
          <ossIndexAnalyzerEnabled>false</ossIndexAnalyzerEnabled>
          <hostedSuppressionsEnabled>false</hostedSuppressionsEnabled>
          <versionCheckEnabled>false</versionCheckEnabled>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

The scanner invokes Maven with `-Powasp-offline-gate` to activate this profile.

### Why Not `-Dformat=HTML -Dformat=JSON`?

Maven's command-line parser does NOT accumulate repeated `-D` flags — the last value wins. So `-Dformat=HTML -Dformat=JSON` produces only JSON, breaking the HTML report gate. The Maven profile's `<formats>` element is the supported multi-format mechanism.

### Why Not `-Dformats=HTML,JSON`?

This alternative is not officially documented for the Dependency-Check Maven plugin. R12A allows it only after a documented preflight proof. The Maven profile is the preferred model and does not require such proof.

## Preflight Report Verification (R12A Section 12)

Before the parser gate runs, a preflight step verifies:

- `dependency-check-report.json` exists
- `dependency-check-report.html` exists
- Both files are non-empty
- JSON is valid (parses as JSON)
- HTML contains the `Dependency-Check` marker
- No `FileNotFoundException` mentioning `vsl` in the scan log
- No `HTML,JSON.vsl` reference in the scan log

If preflight fails:

```
REPORT_FORMAT_CONFIGURATION_FAILURE
OWASP scan acceptance: BLOCKED
```

## Offline-Mode Network Audit (R12A Section 16)

After the scan completes, the scan log is grepped for forbidden hosts:

```
services.nvd.nist.gov
nvd.nist.gov/developers
ossindex.sonatype.org
Sonatype OSS Index API
```

Any hit produces:

```
OFFLINE_MODE_VIOLATION
Final Enforcement: FAILURE
```

**Note:** Maven dependency resolution (downloading plugin JARs from Maven Central) is NOT a violation — it is unavoidable and distinct from vulnerability-data network access.

## Configuration

| Parameter                     | Value | Source                                       |
|-------------------------------|-------|----------------------------------------------|
| Maven profile                 | owasp-offline-gate | pom.xml                             |
| failBuildOnCVSS               | 11    | Allows report generation before parser gate  |
| nvdApiDelay                   | 6000ms | NVD API rate limit compliance (builder only)|
| nvdMaxRetryCount              | 5     | Bounded retries (builder only)               |
| hostedSuppressionsEnabled     | false | Skip Sonatype OSS Index (401 errors)         |
| ossIndexAnalyzerEnabled       | false | Fully disable OSS Index                      |
| autoUpdate                    | false | Offline mode                                 |
| timeout-minutes               | 60    | Sufficient for offline scan                  |

## Properties Removed (R11/R12A Correction)

| Parameter                                  | Reason                                                        |
|--------------------------------------------|---------------------------------------------------------------|
| nvdApiDataFeedUrl (modified feed)          | Insufficient coverage — only 8 days of data                   |
| failOnError=false                          | Fail-open — suppresses scanner execution errors               |
| `-Dformat=HTML -Dformat=JSON` (repeated)   | Maven does not accumulate repeated -D flags; last wins        |
| `-Dformats=HTML,JSON` (alternative)        | Not officially documented; only accepted after preflight proof|

## Artifacts (R12A Section 17)

### NVD Builder Artifact

- **Name:** `nvd-database-build-evidence-<run-id>`
- **Contents:**
  - `sanad-nvd-manifest.json`
  - `nvd-build-attempts.json`
  - `nvd-cache-evidence.json`
  - `database-sha256.txt`
- **Retention:** 30 days
- **if-no-files-found:** error

### Restore Verification Artifact

- **Name:** `nvd-cache-restore-verification-<run-id>`
- **Contents:**
  - `restore-result.json`
  - `validated-manifest.json`
  - `offline-smoke-test.log`
- **Retention:** 30 days
- **if-no-files-found:** error

### OWASP Scanner Artifact

- **Name:** `owasp-dependency-check-evidence-<run-id>`
- **Contents:**
  - `dependency-check-report.json`
  - `dependency-check-report.html`
  - `sanad-nvd-manifest.json`
  - `scan-evidence.json`
- **Retention:** 30 days
- **if-no-files-found:** error

For each artifact, the evidence register records:
- Artifact ID
- Artifact name
- Artifact digest
- Artifact size
- Retention date
- Run ID
- Job ID
- Tested SHA

## Parser

- **Script:** `scripts/ci/parse_owasp_report.py`
- **Tests:** `tests/ci/test_parse_owasp.py` (28+ scenarios)
- **Result types:** `pass`, `failed`, `incomplete`, `execution_error`
- **Only `pass` is accepted by Final Enforcement**

## Validator

- **Script:** `scripts/ci/validate_nvd_database.py`
- **Tests:** `tests/ci/test_validate_nvd_database.py` (30 scenarios)
- **Result types:** `valid`, `missing_directory`, `missing_db_file`, `missing_manifest`, `invalid_manifest_json`, `manifest_field_violation`, `manifest_filename_mismatch`, `manifest_size_mismatch`, `manifest_sha_invalid`, `manifest_provenance_invalid`, `manifest_timestamp_invalid`, `future_timestamp`, `stale`, `sha256_mismatch`, `size_below_minimum`, `lock_files_present`, `temp_files_present`
- **Only `valid` is accepted by Final Enforcement**

## R12A Corrections Over R12

1. Canonical cache path unified across all phases.
2. Multi-format reports via Maven profile (not repeated `-Dformat=`).
3. Preflight report verification step added.
4. Offline-mode network audit added.
5. Validator script extracted and strengthened.
6. Independent restore verification job added.
7. OSS Index fully disabled in builder.
8. Deterministic retry loop replaces three-step retry.
