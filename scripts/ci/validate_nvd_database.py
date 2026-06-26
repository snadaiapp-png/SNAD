#!/usr/bin/env python3
"""
SANAD — NVD Database Validator (Fail-Closed)
=============================================
EXEC-PROMPT-010R12A Section 13: Strengthened validator that proves a
NVD database cache is structurally complete, hash-verified, fresh, and
internally consistent before it is accepted as scan input.

The validator is invoked from BOTH:
  - nvd-database-maintenance.yml  (after update-only, before cache save)
  - security-scan.yml             (after cache restore, before scan)
  - verify-nvd-cache-restore job  (after fresh-runner restore, before smoke test)

Required validations (all must pass):
  1. Canonical directory exists
  2. odc.mv.db exists
  3. Database size exceeds configured realistic minimum
  4. Manifest exists and is valid JSON
  5. Manifest schema_version = "v3"
  6. Manifest dependency_check_version = expected (default 12.1.0)
  7. Manifest update_mode = "update-only"
  8. Manifest update_exit_code = 0
  9. Manifest validation_result = "valid"
 10. Manifest database_filename matches actual file
 11. Manifest database_size_bytes matches actual size
 12. Manifest database_sha256 matches actual SHA-256
 13. Manifest builder_run_id is present and numeric
 14. Manifest builder_sha is a 40-character hex string
 15. Manifest update_completed_at parses as UTC and is within freshness policy
 16. No H2 lock files (*.lock.db, *.lock) remain
 17. No temporary database files indicate incomplete shutdown (*.tmp.db, *.temp.db, odc.mv.db.temp)

Exit codes:
  0 = database is valid
  1 = directory or file missing
  2 = manifest missing or invalid JSON
  3 = manifest field validation failed
  4 = SHA-256 mismatch
  5 = size below minimum
  6 = freshness violation
  7 = lock or temp files present
  8 = bad CLI arguments

Output:
  Prints snake_case key=value lines for $GITHUB_OUTPUT, then a final
  RESULT line. All findings go to stdout; fatal errors to stderr.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

# ---------- Configuration constants ----------

SCHEMA_VERSION = "v3"
EXPECTED_DC_VERSION = "12.1.0"
DEFAULT_MIN_SIZE_BYTES = 50 * 1024 * 1024  # 50 MiB — see "Realistic minimum" below
DEFAULT_MAX_AGE_HOURS = 48  # R12A Section 13 — freshness policy
LOCK_FILE_PATTERNS = ("*.lock.db", "*.lock")
# Temporary file patterns that indicate an INCOMPLETE shutdown (H2 did not
# close cleanly). Note: `odc.trace.db` is a legitimate H2 trace file that
# Dependency-Check leaves behind after a successful update — it is NOT a
# corruption indicator and is intentionally excluded from this list.
TEMP_FILE_PATTERNS = ("*.tmp.db", "*.temp.db", "odc.mv.db.temp")

SHA256_HEX_RE = re.compile(r"^[a-f0-9]{64}$")  # SHA-256 = 64 hex chars
GIT_SHA_RE = re.compile(r"^[a-f0-9]{40}$")      # git commit SHA = 40 hex chars
UTC_TS_RE = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"
)

# ---------- Helpers ----------

def eprint(*args: Any, **kwargs: Any) -> None:
    print(*args, file=sys.stderr, **kwargs)


def out(key: str, value: Any) -> None:
    print(f"{key}={value}")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_utc_ts(ts: str) -> dt.datetime | None:
    """Parse a strict UTC ISO-8601 timestamp like 2026-06-26T02:15:00Z."""
    if not isinstance(ts, str) or not UTC_TS_RE.match(ts):
        return None
    try:
        return dt.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=dt.timezone.utc
        )
    except ValueError:
        return None


# ---------- Validators ----------

def validate_directory(db_dir: Path) -> int:
    if not db_dir.exists():
        eprint(f"ERROR: canonical directory does not exist: {db_dir}")
        out("validation_result", "missing_directory")
        return 1
    if not db_dir.is_dir():
        eprint(f"ERROR: canonical path is not a directory: {db_dir}")
        out("validation_result", "missing_directory")
        return 1
    return 0


def validate_db_file(db_dir: Path) -> tuple[int, Path | None, int]:
    db_file = db_dir / "odc.mv.db"
    if not db_file.exists() or not db_file.is_file():
        eprint(f"ERROR: odc.mv.db not found at {db_file}")
        out("validation_result", "missing_db_file")
        return 1, None, 0
    actual_size = db_file.stat().st_size
    return 0, db_file, actual_size


def validate_size(actual_size: int, min_size: int) -> int:
    if actual_size < min_size:
        eprint(
            f"ERROR: database size {actual_size} bytes is below minimum {min_size} bytes"
        )
        out("validation_result", "size_below_minimum")
        out("db_size_bytes", actual_size)
        out("min_size_bytes", min_size)
        return 5
    out("db_size_bytes", actual_size)
    out("min_size_bytes", min_size)
    return 0


def validate_manifest(manifest_path: Path) -> tuple[int, dict | None]:
    if not manifest_path.exists() or not manifest_path.is_file():
        eprint(f"ERROR: manifest not found at {manifest_path}")
        out("validation_result", "missing_manifest")
        return 2, None
    try:
        with manifest_path.open("r", encoding="utf-8") as f:
            manifest = json.load(f)
    except json.JSONDecodeError as e:
        eprint(f"ERROR: manifest is not valid JSON: {e}")
        out("validation_result", "invalid_manifest_json")
        return 2, None
    if not isinstance(manifest, dict):
        eprint("ERROR: manifest root is not an object")
        out("validation_result", "invalid_manifest_json")
        return 2, None
    return 0, manifest


def validate_manifest_fields(
    manifest: dict,
    expected_dc_version: str,
    expected_schema: str,
) -> int:
    """Returns non-zero exit code on any field violation."""

    def require(condition: bool, label: str, actual: Any, expected: Any) -> int:
        if not condition:
            eprint(
                f"ERROR: manifest field '{label}' is '{actual}' — expected '{expected}'"
            )
            out("validation_result", "manifest_field_violation")
            out("manifest_violation_field", label)
            return 3
        return 0

    schema = manifest.get("schema_version")
    rc = require(schema == expected_schema, "schema_version", schema, expected_schema)
    if rc:
        return rc

    dc_version = manifest.get("dependency_check_version")
    rc = require(
        dc_version == expected_dc_version,
        "dependency_check_version",
        dc_version,
        expected_dc_version,
    )
    if rc:
        return rc

    update_mode = manifest.get("update_mode")
    rc = require(update_mode == "update-only", "update_mode", update_mode, "update-only")
    if rc:
        return rc

    exit_code = manifest.get("update_exit_code")
    rc = require(exit_code == 0, "update_exit_code", exit_code, 0)
    if rc:
        return rc

    val_res = manifest.get("validation_result")
    rc = require(val_res == "valid", "validation_result", val_res, "valid")
    if rc:
        return rc

    return 0


def validate_manifest_consistency(
    manifest: dict, db_dir: Path, actual_size: int
) -> tuple[int, str | None, str | None]:
    """Cross-check manifest fields against actual file state."""

    filename = manifest.get("database_filename")
    if filename != "odc.mv.db":
        eprint(
            f"ERROR: manifest database_filename is '{filename}' — expected 'odc.mv.db'"
        )
        out("validation_result", "manifest_filename_mismatch")
        return 3, None, None

    recorded_size = manifest.get("database_size_bytes")
    if not isinstance(recorded_size, int) or recorded_size != actual_size:
        eprint(
            f"ERROR: manifest database_size_bytes is '{recorded_size}' — actual is '{actual_size}'"
        )
        out("validation_result", "manifest_size_mismatch")
        return 3, None, None

    recorded_sha = manifest.get("database_sha256")
    if not isinstance(recorded_sha, str) or not SHA256_HEX_RE.match(recorded_sha):
        eprint(
            f"ERROR: manifest database_sha256 is not a 64-character hexadecimal SHA-256 value: '{recorded_sha}'"
        )
        out("validation_result", "manifest_sha_invalid")
        return 3, None, None

    actual_sha = sha256_file(db_dir / "odc.mv.db")
    if actual_sha != recorded_sha:
        eprint(
            f"ERROR: SHA-256 mismatch — manifest={recorded_sha} actual={actual_sha}"
        )
        out("validation_result", "sha256_mismatch")
        out("manifest_sha256", recorded_sha)
        out("actual_sha256", actual_sha)
        return 4, recorded_sha, actual_sha

    return 0, recorded_sha, actual_sha


def validate_manifest_provenance(manifest: dict) -> int:
    """Validate builder_run_id and builder_sha fields."""

    run_id = manifest.get("builder_run_id")
    # Accept int or numeric string
    if isinstance(run_id, int):
        run_id_ok = run_id > 0
    elif isinstance(run_id, str) and run_id.isdigit():
        run_id_ok = int(run_id) > 0
    else:
        run_id_ok = False
    if not run_id_ok:
        eprint(f"ERROR: manifest builder_run_id is missing or non-numeric: '{run_id}'")
        out("validation_result", "manifest_provenance_invalid")
        return 3

    builder_sha = manifest.get("builder_sha")
    if not isinstance(builder_sha, str) or not GIT_SHA_RE.match(builder_sha):
        eprint(
            f"ERROR: manifest builder_sha is not a 40-char hex string: '{builder_sha}'"
        )
        out("validation_result", "manifest_provenance_invalid")
        return 3

    return 0


def validate_freshness(manifest: dict, max_age_hours: int) -> tuple[int, str | None, int | None]:
    update_ts_str = manifest.get("update_completed_at")
    update_ts = parse_utc_ts(update_ts_str) if isinstance(update_ts_str, str) else None
    if update_ts is None:
        eprint(
            f"ERROR: manifest update_completed_at is not a valid UTC timestamp: '{update_ts_str}'"
        )
        out("validation_result", "manifest_timestamp_invalid")
        return 6, None, None

    now = dt.datetime.now(dt.timezone.utc)
    age_delta = now - update_ts
    age_hours = int(age_delta.total_seconds() // 3600)

    if age_hours > max_age_hours:
        eprint(
            f"ERROR: database is stale — age {age_hours}h exceeds policy max {max_age_hours}h"
        )
        out("validation_result", "stale")
        out("age_hours", age_hours)
        out("max_age_hours", max_age_hours)
        return 6, update_ts_str, age_hours

    if age_hours < 0:
        eprint(
            f"ERROR: manifest update_completed_at is in the future: {update_ts_str}"
        )
        out("validation_result", "future_timestamp")
        return 6, update_ts_str, age_hours

    return 0, update_ts_str, age_hours


def validate_no_lock_or_temp_files(db_dir: Path) -> int:
    """Ensure no H2 lock files or temp files remain (clean shutdown)."""

    found_locks: list[str] = []
    for pattern in LOCK_FILE_PATTERNS:
        for p in db_dir.glob(pattern):
            found_locks.append(str(p))

    found_temps: list[str] = []
    for pattern in TEMP_FILE_PATTERNS:
        for p in db_dir.glob(pattern):
            found_temps.append(str(p))

    if found_locks:
        eprint(
            f"ERROR: H2 lock files present — database was not shut down cleanly: {found_locks}"
        )
        out("validation_result", "lock_files_present")
        out("lock_files", ",".join(found_locks))
        return 7

    if found_temps:
        eprint(
            f"ERROR: temporary database files present — possible incomplete shutdown: {found_temps}"
        )
        out("validation_result", "temp_files_present")
        out("temp_files", ",".join(found_temps))
        return 7

    return 0


# ---------- Main ----------

def validate(
    db_dir: Path,
    min_size: int,
    max_age_hours: int,
    expected_dc_version: str,
    expected_schema: str,
) -> int:
    """Run all validations. Returns 0 on success, non-zero on failure."""

    out("canonical_dir", str(db_dir))

    rc = validate_directory(db_dir)
    if rc:
        return rc

    rc, db_file, actual_size = validate_db_file(db_dir)
    if rc:
        return rc

    rc = validate_size(actual_size, min_size)
    if rc:
        return rc

    manifest_path = db_dir / "sanad-nvd-manifest.json"
    rc, manifest = validate_manifest(manifest_path)
    if rc:
        return rc

    rc = validate_manifest_fields(manifest, expected_dc_version, expected_schema)
    if rc:
        return rc

    rc, recorded_sha, actual_sha = validate_manifest_consistency(
        manifest, db_dir, actual_size
    )
    if rc:
        return rc

    rc = validate_manifest_provenance(manifest)
    if rc:
        return rc

    rc, update_ts, age_hours = validate_freshness(manifest, max_age_hours)
    if rc:
        return rc

    rc = validate_no_lock_or_temp_files(db_dir)
    if rc:
        return rc

    # All checks passed
    out("validation_result", "valid")
    out("database_sha256", actual_sha)
    out("database_size_bytes", actual_size)
    out("update_completed_at", update_ts)
    out("age_hours", age_hours)
    out("builder_run_id", manifest.get("builder_run_id"))
    out("builder_sha", manifest.get("builder_sha"))
    out("dependency_check_version", manifest.get("dependency_check_version"))
    out("schema_version", manifest.get("schema_version"))

    print("RESULT=valid")
    print(
        f"OK: NVD database validated — size={actual_size}B sha256={actual_sha} "
        f"age={age_hours}h builder_sha={manifest.get('builder_sha')}"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate a SANAD NVD database cache directory."
    )
    parser.add_argument(
        "db_dir",
        help="Canonical NVD database directory (must contain odc.mv.db and sanad-nvd-manifest.json)",
    )
    parser.add_argument(
        "--min-size",
        type=int,
        default=int(os.environ.get("NVD_MIN_SIZE_BYTES", DEFAULT_MIN_SIZE_BYTES)),
        help=f"Minimum odc.mv.db size in bytes (default {DEFAULT_MIN_SIZE_BYTES})",
    )
    parser.add_argument(
        "--max-age-hours",
        type=int,
        default=int(os.environ.get("NVD_MAX_AGE_HOURS", DEFAULT_MAX_AGE_HOURS)),
        help=f"Maximum database age in hours (default {DEFAULT_MAX_AGE_HOURS})",
    )
    parser.add_argument(
        "--expected-dc-version",
        default=os.environ.get("DEPENDENCY_CHECK_VERSION", EXPECTED_DC_VERSION),
        help=f"Expected Dependency-Check version (default {EXPECTED_DC_VERSION})",
    )
    parser.add_argument(
        "--expected-schema",
        default=os.environ.get("NVD_CACHE_SCHEMA", SCHEMA_VERSION),
        help=f"Expected cache schema version (default {SCHEMA_VERSION})",
    )

    args = parser.parse_args()

    db_dir = Path(args.db_dir).resolve()
    return validate(
        db_dir=db_dir,
        min_size=args.min_size,
        max_age_hours=args.max_age_hours,
        expected_dc_version=args.expected_dc_version,
        expected_schema=args.expected_schema,
    )


if __name__ == "__main__":
    sys.exit(main())
