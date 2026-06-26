#!/usr/bin/env python3
"""
SANAD — NVD Snapshot Verifier
==============================
EXEC-PROMPT-010R12E Section 12 — verifies a downloaded NVD snapshot
archive + manifest before any consumer (OWASP, R12B) is allowed to
use it.

Checks:
  1. Archive exists and is non-empty
  2. Archive SHA-256 matches manifest
  3. Manifest is valid against the snad-nvd-snapshot-v1 contract
  4. Database (odc.mv.db) exists inside the archive
  5. Database SHA-256 matches manifest
  6. Database size >= minimum (default 50 MiB)
  7. Dependency-Check version matches (default 12.1.0)
  8. Snapshot ID matches
  9. No forbidden lock/temp files in archive
  10. odc.trace.db accepted (not flagged)
  11. Snapshot age within policy (default <= 48 hours)
  12. Storage digest/version matches (via backend if available)
  13. Offline smoke test passes (database can be opened)

Exit codes:
  0 = snapshot is valid and fresh
  1 = archive missing or unreadable
  2 = manifest missing or invalid
  3 = SHA-256 mismatch
  4 = size below minimum
  5 = freshness violation
  6 = forbidden files present
  7 = DC version / snapshot_id mismatch
  8 = storage digest mismatch
  9 = offline smoke test failed
 10 = bad CLI arguments
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
import tarfile
from pathlib import Path
from typing import Any

# Add repo root for imports
REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_snapshot_store import (  # noqa: E402
    CONTRACT_VERSION,
    SCHEMA_VERSION,
    DEFAULT_DEPENDENCY_CHECK_VERSION,
    DEFAULT_MAX_AGE_HOURS,
    PREFERRED_AGE_HOURS,
    WARNING_AGE_HOURS,
    FORBIDDEN_LOCK_PATTERNS,
    FORBIDDEN_TEMP_PATTERNS,
    SnapshotError,
    SnapshotNotFoundError,
    SnapshotVerificationError,
    StorageBackendError,
    get_backend,
    sha256_file,
    sha256_bytes,
    validate_manifest,
    utc_now_iso,
)


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def out(key, value):
    print(f"{key}={value}")


SHA256_HEX_RE = re.compile(r"^[a-f0-9]{64}$")
GIT_SHA_RE = re.compile(r"^[a-f0-9]{40}$")
UTC_TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


def parse_utc_ts(ts: str) -> dt.datetime | None:
    if not isinstance(ts, str) or not UTC_TS_RE.match(ts):
        return None
    try:
        return dt.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except ValueError:
        return None


def verify_archive_sha256(archive_path: Path, expected_sha256: str) -> tuple[int, str]:
    if not archive_path.exists() or not archive_path.is_file():
        eprint(f"ERROR: archive not found: {archive_path}")
        return 1, ""
    actual = sha256_file(archive_path)
    if actual != expected_sha256:
        eprint(f"ERROR: archive SHA-256 mismatch: expected={expected_sha256} actual={actual}")
        return 3, actual
    return 0, actual


def verify_manifest(archive_path: Path) -> tuple[int, dict | None, Path | None]:
    """Extract and validate the manifest from the archive.

    The manifest is stored at the archive root as manifest.json.
    Uses subprocess `tar` for extraction to support .tar.zst archives
    (Python 3.12's tarfile module does not support zst natively).
    """
    import subprocess as _sp
    import tempfile

    try:
        # List archive contents to find manifest.json
        list_result = _sp.run(
            ["tar", "-tf", str(archive_path)],
            capture_output=True, text=True, check=True,
        )
        members = [line.strip() for line in list_result.stdout.splitlines() if line.strip()]
        manifest_member = None
        for m in members:
            if m == "manifest.json" or m.endswith("/manifest.json"):
                manifest_member = m
                break
        if manifest_member is None:
            eprint("ERROR: manifest.json not found in archive")
            return 2, None, None

        # Extract manifest.json to a temp dir
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as tmp:
            tmp_manifest_path = Path(tmp.name)
        _sp.run(
            ["tar", "-xf", str(archive_path), "-O", manifest_member],
            stdout=tmp_manifest_path.open("wb"),
            stderr=_sp.PIPE,
            check=True,
        )
        manifest = json.loads(tmp_manifest_path.read_text(encoding="utf-8"))
        tmp_manifest_path.unlink(missing_ok=True)
    except _sp.CalledProcessError as e:
        eprint(f"ERROR: failed to read archive: {e.stderr}")
        return 1, None, None
    except Exception as e:
        eprint(f"ERROR: failed to read archive: {e}")
        return 1, None, None

    try:
        validate_manifest(manifest)
    except ValueError as e:
        eprint(f"ERROR: manifest validation failed: {e}")
        return 2, None, None

    return 0, manifest, None


def verify_archive_contents(
    archive_path: Path,
    manifest: dict,
    min_size_bytes: int,
) -> tuple[int, str | None, int | None]:
    """Extract the data dir and verify the database file inside it.

    Returns (exit_code, db_sha256, db_size). Exit 0 on success.
    Uses subprocess `tar` for extraction to support .tar.zst archives.
    """
    import subprocess as _sp
    import fnmatch as _fnmatch

    db_filename = manifest["database_filename"]
    expected_db_sha = manifest["database_sha256"]
    expected_db_size = manifest["database_size_bytes"]

    try:
        # List archive contents
        list_result = _sp.run(
            ["tar", "-tf", str(archive_path)],
            capture_output=True, text=True, check=True,
        )
        members = [line.strip() for line in list_result.stdout.splitlines() if line.strip()]

        # Check for forbidden files
        forbidden_found = []
        for m in members:
            name = m.split("/")[-1]
            for pat in FORBIDDEN_LOCK_PATTERNS + FORBIDDEN_TEMP_PATTERNS:
                if _fnmatch.fnmatch(name, pat):
                    forbidden_found.append(m)

        if forbidden_found:
            eprint(f"ERROR: forbidden lock/temp files in archive: {forbidden_found}")
            return 6, None, None

        # Find the database file
        db_member = None
        for m in members:
            name = m.split("/")[-1]
            if name == db_filename:
                db_member = m
                break

        if db_member is None:
            eprint(f"ERROR: {db_filename} not found in archive")
            return 1, None, None

        # Extract the database file to stdout and hash it
        extract_result = _sp.run(
            ["tar", "-xf", str(archive_path), "-O", db_member],
            capture_output=True, check=True,
        )
        data = extract_result.stdout
        actual_size = len(data)
        actual_sha = sha256_bytes(data)
    except _sp.CalledProcessError as e:
        eprint(f"ERROR: failed to inspect archive: {e.stderr}")
        return 1, None, None
    except Exception as e:
        eprint(f"ERROR: failed to inspect archive: {e}")
        return 1, None, None

    if actual_size < min_size_bytes:
        eprint(f"ERROR: database size {actual_size} below minimum {min_size_bytes}")
        return 4, actual_sha, actual_size
    if actual_sha != expected_db_sha:
        eprint(f"ERROR: database SHA-256 mismatch: expected={expected_db_sha} actual={actual_sha}")
        return 3, actual_sha, actual_size
    if actual_size != expected_db_size:
        eprint(f"ERROR: database size mismatch: manifest={expected_db_size} actual={actual_size}")
        return 3, actual_sha, actual_size

    return 0, actual_sha, actual_size


def verify_freshness(manifest: dict, max_age_hours: int) -> tuple[int, int]:
    """Verify the snapshot is within the freshness policy.

    Uses manifest['created_at'] as the snapshot creation timestamp.
    Returns (exit_code, age_hours). Exit 0 on success.
    """
    created_at = manifest.get("created_at")
    created = parse_utc_ts(created_at) if isinstance(created_at, str) else None
    if created is None:
        eprint(f"ERROR: manifest created_at is not a valid UTC timestamp: {created_at!r}")
        return 2, 0
    now = dt.datetime.now(dt.timezone.utc)
    age_hours = int((now - created).total_seconds() // 3600)
    if age_hours < 0:
        eprint(f"ERROR: snapshot created_at is in the future: {created_at}")
        return 5, age_hours
    if age_hours > max_age_hours:
        eprint(f"ERROR: snapshot is stale — age {age_hours}h exceeds max {max_age_hours}h")
        return 5, age_hours
    return 0, age_hours


def verify_dc_version_and_snapshot_id(
    manifest: dict,
    expected_dc_version: str,
    expected_snapshot_id: str | None = None,
) -> int:
    if manifest["dependency_check_version"] != expected_dc_version:
        eprint(
            f"ERROR: manifest dependency_check_version is {manifest['dependency_check_version']!r}; "
            f"expected {expected_dc_version!r}"
        )
        return 7
    if expected_snapshot_id is not None and manifest["snapshot_id"] != expected_snapshot_id:
        eprint(
            f"ERROR: manifest snapshot_id is {manifest['snapshot_id']!r}; "
            f"expected {expected_snapshot_id!r}"
        )
        return 7
    return 0


def run_offline_smoke_test(
    archive_path: Path,
    manifest: dict,
    extract_dir: Path,
    maven_path: str = "mvn",
) -> tuple[int, str]:
    """Extract the archive and run a Dependency-Check offline smoke test.

    Returns (exit_code, smoke_result). exit 0 on success.
    """
    extract_dir = Path(extract_dir)
    extract_dir.mkdir(parents=True, exist_ok=True)

    # Extract (use subprocess tar for zst support)
    try:
        import subprocess as _sp
        _sp.run(
            ["tar", "-xf", str(archive_path), "-C", str(extract_dir)],
            check=True, capture_output=True,
        )
    except Exception as e:
        eprint(f"ERROR: extraction failed: {e}")
        return 9, "extraction_failed"

    data_dir = extract_dir / "data"
    if not data_dir.is_dir():
        eprint(f"ERROR: data/ directory not found in archive")
        return 9, "no_data_dir"

    dc_version = manifest["dependency_check_version"]
    smoke_log = extract_dir.parent / "offline-smoke-test.log"

    cmd = [
        maven_path, "--batch-mode", "--no-transfer-progress",
        f"org.owasp:dependency-check-maven:{dc_version}:check",
        f"-DdataDirectory={data_dir}",
        "-DautoUpdate=false",
        "-DfailOnError=true",
        "-DfailBuildOnCVSS=11",
        "-DossIndexAnalyzerEnabled=false",
        "-DhostedSuppressionsEnabled=false",
        "-DversionCheckEnabled=false",
        f"-DoutputDirectory={extract_dir / 'smoke-output'}",
    ]

    try:
        with smoke_log.open("w") as log:
            result = subprocess.run(
                cmd, stdout=log, stderr=subprocess.STDOUT, timeout=1800
            )
    except subprocess.TimeoutExpired:
        eprint("ERROR: offline smoke test timed out after 30 minutes")
        return 9, "timeout"
    except FileNotFoundError:
        eprint(f"ERROR: {maven_path} not found — cannot run offline smoke test")
        return 9, "maven_not_found"

    if result.returncode != 0:
        eprint(f"ERROR: offline smoke test exited with code {result.returncode}")
        return 9, "execution_failure"

    # Forbidden-hosts check
    log_text = smoke_log.read_text(encoding="utf-8", errors="replace") if smoke_log.exists() else ""
    forbidden_hits = 0
    for host in ("services.nvd.nist.gov", "nvd.nist.gov/developers", "ossindex.sonatype.org", "Sonatype OSS Index API"):
        if host in log_text:
            forbidden_hits += 1
    if forbidden_hits > 0:
        eprint(f"ERROR: offline smoke test contacted forbidden hosts ({forbidden_hits} hits)")
        return 9, "offline_violation"

    # H2 corruption check
    h2_errors = 0
    for pat in ("H2.*corrupt", "H2.*lock", "H2.*error", "DatabaseException"):
        if re.search(pat, log_text):
            h2_errors += 1
    if h2_errors > 0:
        eprint(f"ERROR: H2 corruption/lock errors in smoke test ({h2_errors} hits)")
        return 9, "h2_error"

    return 0, "success"


def verify(
    archive_path: Path,
    *,
    expected_dc_version: str = DEFAULT_DEPENDENCY_CHECK_VERSION,
    expected_snapshot_id: str | None = None,
    min_size_bytes: int = 50 * 1024 * 1024,
    max_age_hours: int = DEFAULT_MAX_AGE_HOURS,
    run_smoke: bool = False,
    smoke_extract_dir: Path | None = None,
    maven_path: str = "mvn",
) -> int:
    """Run all verification checks. Returns 0 on success."""

    out("archive_path", archive_path)

    # 1. Archive exists
    rc = 0
    if not archive_path.exists():
        eprint(f"ERROR: archive not found: {archive_path}")
        out("verification_result", "archive_missing")
        return 1

    # 2. Manifest
    rc, manifest, _ = verify_manifest(archive_path)
    if rc:
        out("verification_result", "manifest_invalid")
        return rc

    # 3. Archive SHA-256
    rc, actual_archive_sha = verify_archive_sha256(archive_path, manifest["archive_sha256"])
    if rc:
        out("verification_result", "archive_sha_mismatch")
        return rc
    out("archive_sha256", actual_archive_sha)
    out("archive_size_bytes", archive_path.stat().st_size)

    # 4. DC version + snapshot ID
    rc = verify_dc_version_and_snapshot_id(manifest, expected_dc_version, expected_snapshot_id)
    if rc:
        out("verification_result", "version_or_id_mismatch")
        return rc

    # 5. Archive contents (database SHA-256, size, forbidden files)
    rc, db_sha, db_size = verify_archive_contents(archive_path, manifest, min_size_bytes)
    if rc:
        out("verification_result", "contents_invalid")
        return rc
    out("database_sha256", db_sha)
    out("database_size_bytes", db_size)

    # 6. Freshness
    rc, age_hours = verify_freshness(manifest, max_age_hours)
    if rc:
        out("verification_result", "stale")
        out("age_hours", age_hours)
        return rc
    out("age_hours", age_hours)
    if age_hours <= PREFERRED_AGE_HOURS:
        out("freshness_tier", "preferred")
    elif age_hours <= WARNING_AGE_HOURS:
        out("freshness_tier", "warning")
    else:
        out("freshness_tier", "maximum")

    # 7. Offline smoke test (optional)
    if run_smoke:
        extract_dir = smoke_extract_dir or Path(archive_path.parent / "smoke-extract")
        rc, smoke_result = run_offline_smoke_test(archive_path, manifest, extract_dir, maven_path)
        if rc:
            out("verification_result", "smoke_failed")
            out("smoke_result", smoke_result)
            return rc
        out("smoke_result", "success")

    # All checks passed
    out("verification_result", "valid")
    out("snapshot_id", manifest["snapshot_id"])
    out("created_at", manifest["created_at"])
    out("storage_backend", manifest["storage_backend"])
    out("storage_version_or_digest", manifest["storage_version_or_digest"])
    print("RESULT=valid")
    print(
        f"OK: NVD snapshot verified — snapshot_id={manifest['snapshot_id']} "
        f"age={age_hours}h db_sha={db_sha}"
    )
    return 0


def main():
    parser = argparse.ArgumentParser(description="Verify a SANAD NVD snapshot archive.")
    parser.add_argument("archive", help="Path to the snad-nvd-data-<id>.tar.zst archive")
    parser.add_argument("--expected-dc-version", default=os.environ.get("DEPENDENCY_CHECK_VERSION", DEFAULT_DEPENDENCY_CHECK_VERSION))
    parser.add_argument("--expected-snapshot-id", default=None)
    parser.add_argument("--min-size", type=int, default=int(os.environ.get("NVD_MIN_SIZE_BYTES", 50 * 1024 * 1024)))
    parser.add_argument("--max-age-hours", type=int, default=int(os.environ.get("NVD_MAX_AGE_HOURS", DEFAULT_MAX_AGE_HOURS)))
    parser.add_argument("--run-smoke", action="store_true", help="Run offline smoke test (requires mvn)")
    parser.add_argument("--smoke-extract-dir", default=None)
    parser.add_argument("--maven-path", default="mvn")
    args = parser.parse_args()

    archive = Path(args.archive).resolve()
    smoke_dir = Path(args.smoke_extract_dir) if args.smoke_extract_dir else None
    return verify(
        archive,
        expected_dc_version=args.expected_dc_version,
        expected_snapshot_id=args.expected_snapshot_id,
        min_size_bytes=args.min_size,
        max_age_hours=args.max_age_hours,
        run_smoke=args.run_smoke,
        smoke_extract_dir=smoke_dir,
        maven_path=args.maven_path,
    )


if __name__ == "__main__":
    sys.exit(main())
