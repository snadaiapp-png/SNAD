#!/usr/bin/env python3
"""
SANAD — NVD Snapshot Verifier (v2)
====================================
EXEC-PROMPT-010R12F Section 14 — verifies a downloaded NVD snapshot
archive + sidecar manifest + SHA256SUMS before any consumer is
allowed to use it.

R12F changes:
  - Accepts --archive, --manifest, --checksums as separate files
    (manifest is NO LONGER inside the archive)
  - Uses nvd_archive.py for safe extraction (no tarfile.open on .zst)
  - Streams database SHA-256 (no loading H2 into memory)
  - Validates archive paths (path traversal protection)

Checks:
  1. Archive, manifest, checksums files exist
  2. Manifest is valid v2 (no storage_version_or_digest)
  3. Archive SHA-256 matches manifest
  4. SHA256SUMS matches archive + manifest
  5. Archive paths are safe (no traversal, symlinks)
  6. Database file exists at data/odc.mv.db inside archive
  7. Database SHA-256 matches manifest (streamed)
  8. Database size >= minimum
  9. DC version matches
 10. Snapshot ID matches
 11. No forbidden lock/temp files
 12. Freshness within policy
 13. Storage digest verification (optional, via backend)
 14. Offline smoke test (optional)
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
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_archive import (
    ArchiveError,
    ArchivePathTraversalError,
    extract_snapshot_archive,
    list_snapshot_archive,
    stream_hash_file,
    validate_archive_paths,
)
from scripts.security.nvd_snapshot_store import (
    DEFAULT_DEPENDENCY_CHECK_VERSION,
    DEFAULT_MAX_AGE_HOURS,
    FORBIDDEN_LOCK_PATTERNS,
    FORBIDDEN_TEMP_PATTERNS,
    PREFERRED_AGE_HOURS,
    WARNING_AGE_HOURS,
    sha256_bytes,
    sha256_file,
)
from scripts.security.publish_nvd_snapshot import (
    CONTRACT_VERSION_V2,
    SCHEMA_VERSION_V2,
    validate_manifest_v2,
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


def verify(
    archive_path: Path,
    manifest_path: Path,
    checksums_path: Path,
    *,
    expected_dc_version: str = DEFAULT_DEPENDENCY_CHECK_VERSION,
    expected_snapshot_id: str | None = None,
    min_size_bytes: int = 50 * 1024 * 1024,
    max_age_hours: int = DEFAULT_MAX_AGE_HOURS,
    expected_storage_digest: str | None = None,
    run_smoke: bool = False,
    smoke_extract_dir: Path | None = None,
    maven_path: str = "mvn",
) -> int:
    """Run all v2 verification checks. Returns 0 on success."""

    out("archive_path", archive_path)
    out("manifest_path", manifest_path)
    out("checksums_path", checksums_path)

    # 1. Files exist
    for p, label in [(archive_path, "archive"), (manifest_path, "manifest"), (checksums_path, "checksums")]:
        if not p.exists() or not p.is_file():
            eprint(f"ERROR: {label} not found: {p}")
            out("verification_result", f"{label}_missing")
            return 1

    # 2. Manifest valid v2
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        validate_manifest_v2(manifest)
    except (json.JSONDecodeError, ValueError) as e:
        eprint(f"ERROR: manifest validation failed: {e}")
        out("verification_result", "manifest_invalid")
        return 2

    # 3. Archive SHA-256
    actual_archive_sha = sha256_file(archive_path)
    if actual_archive_sha != manifest["archive_sha256"]:
        eprint(f"ERROR: archive SHA-256 mismatch: expected={manifest['archive_sha256']} actual={actual_archive_sha}")
        out("verification_result", "archive_sha_mismatch")
        return 3
    out("archive_sha256", actual_archive_sha)
    out("archive_size_bytes", archive_path.stat().st_size)

    # 4. SHA256SUMS
    checksums_content = checksums_path.read_text(encoding="utf-8")
    manifest_sha = sha256_file(manifest_path)
    expected_archive_line = f"{manifest['archive_sha256']}  {manifest['archive_filename']}"
    expected_manifest_line = f"{manifest_sha}  manifest.json"
    if expected_archive_line not in checksums_content:
        eprint(f"ERROR: archive SHA not found in SHA256SUMS")
        out("verification_result", "checksums_archive_mismatch")
        return 3
    if expected_manifest_line not in checksums_content:
        eprint(f"ERROR: manifest SHA not found in SHA256SUMS")
        out("verification_result", "checksums_manifest_mismatch")
        return 3

    # 5. Archive paths safe
    try:
        validate_archive_paths(archive_path)
    except ArchivePathTraversalError as e:
        eprint(f"ERROR: archive path traversal: {e}")
        out("verification_result", "path_traversal")
        return 6

    # 6. Archive contains data/odc.mv.db
    members = list_snapshot_archive(archive_path)
    db_relative = manifest.get("database_relative_path", "data/odc.mv.db")
    db_member = None
    import fnmatch as _fnmatch
    forbidden_found = []
    for m in members:
        name = m.split("/")[-1]
        for pat in FORBIDDEN_LOCK_PATTERNS + FORBIDDEN_TEMP_PATTERNS:
            if _fnmatch.fnmatch(name, pat):
                forbidden_found.append(m)
        if m == db_relative:
            db_member = m
    if forbidden_found:
        eprint(f"ERROR: forbidden lock/temp files in archive: {forbidden_found}")
        out("verification_result", "forbidden_files")
        return 6
    if db_member is None:
        eprint(f"ERROR: {db_relative} not found in archive")
        out("verification_result", "db_not_found")
        return 1

    # 7. Database SHA-256 (streamed — extract to disk, hash in chunks)
    extract_dir = smoke_extract_dir or Path(archive_path.parent / "verify-extract")
    extract_dir.mkdir(parents=True, exist_ok=True)
    try:
        extract_snapshot_archive(archive_path, extract_dir, validate=False)
    except ArchiveError as e:
        eprint(f"ERROR: extraction failed: {e}")
        out("verification_result", "extraction_failed")
        return 1

    db_path = extract_dir / db_relative
    if not db_path.exists():
        eprint(f"ERROR: extracted database not found at {db_path}")
        out("verification_result", "db_not_extracted")
        return 1

    actual_db_sha = stream_hash_file(db_path)
    actual_db_size = db_path.stat().st_size

    # 8. Database size
    if actual_db_size < min_size_bytes:
        eprint(f"ERROR: database size {actual_db_size} below minimum {min_size_bytes}")
        out("verification_result", "size_below_minimum")
        return 4

    # 9. Database SHA-256
    if actual_db_sha != manifest["database_sha256"]:
        eprint(f"ERROR: database SHA-256 mismatch: expected={manifest['database_sha256']} actual={actual_db_sha}")
        out("verification_result", "db_sha_mismatch")
        return 3
    if actual_db_size != manifest["database_size_bytes"]:
        eprint(f"ERROR: database size mismatch: manifest={manifest['database_size_bytes']} actual={actual_db_size}")
        out("verification_result", "db_size_mismatch")
        return 3

    # 10. DC version + snapshot ID
    if manifest["dependency_check_version"] != expected_dc_version:
        eprint(f"ERROR: DC version mismatch: {manifest['dependency_check_version']} != {expected_dc_version}")
        out("verification_result", "dc_version_mismatch")
        return 7
    if expected_snapshot_id and manifest["snapshot_id"] != expected_snapshot_id:
        eprint(f"ERROR: snapshot ID mismatch: {manifest['snapshot_id']} != {expected_snapshot_id}")
        out("verification_result", "snapshot_id_mismatch")
        return 7

    # 11. Freshness
    created_at = manifest.get("created_at")
    created = parse_utc_ts(created_at) if isinstance(created_at, str) else None
    if created is None:
        eprint(f"ERROR: manifest created_at invalid: {created_at}")
        out("verification_result", "timestamp_invalid")
        return 5
    now = dt.datetime.now(dt.timezone.utc)
    age_hours = int((now - created).total_seconds() // 3600)
    if age_hours < 0:
        eprint(f"ERROR: snapshot created_at in future: {created_at}")
        out("verification_result", "future_timestamp")
        return 5
    if age_hours > max_age_hours:
        eprint(f"ERROR: snapshot stale — age {age_hours}h > max {max_age_hours}h")
        out("verification_result", "stale")
        return 5
    out("age_hours", age_hours)
    if age_hours <= PREFERRED_AGE_HOURS:
        out("freshness_tier", "preferred")
    elif age_hours <= WARNING_AGE_HOURS:
        out("freshness_tier", "warning")
    else:
        out("freshness_tier", "maximum")

    # 12. Storage digest (optional)
    if expected_storage_digest:
        out("expected_storage_digest", expected_storage_digest)
        # The caller verifies this via backend.verify_storage_digest()

    # 13. Offline smoke test (optional)
    if run_smoke:
        rc, smoke_result = _run_smoke_test(extract_dir, manifest, maven_path)
        if rc:
            out("verification_result", "smoke_failed")
            out("smoke_result", smoke_result)
            return 9
        out("smoke_result", "success")

    # All checks passed
    out("verification_result", "valid")
    out("snapshot_id", manifest["snapshot_id"])
    out("created_at", manifest["created_at"])
    out("database_sha256", actual_db_sha)
    out("database_size_bytes", actual_db_size)
    print("RESULT=valid")
    return 0


def _run_smoke_test(extract_dir: Path, manifest: dict, maven_path: str) -> tuple[int, str]:
    """Run offline smoke test against extracted data dir."""
    data_dir = extract_dir / "data"
    if not data_dir.is_dir():
        return 9, "no_data_dir"

    dc_version = manifest["dependency_check_version"]
    smoke_log = extract_dir.parent / "offline-smoke-test.log"
    output_dir = extract_dir / "smoke-output"

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
        f"-DoutputDirectory={output_dir}",
    ]
    try:
        with smoke_log.open("w") as log:
            result = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, timeout=1800)
    except subprocess.TimeoutExpired:
        return 9, "timeout"
    except FileNotFoundError:
        return 9, "maven_not_found"

    if result.returncode != 0:
        return 9, "execution_failure"

    log_text = smoke_log.read_text(encoding="utf-8", errors="replace") if smoke_log.exists() else ""
    for host in ("services.nvd.nist.gov", "nvd.nist.gov/developers", "ossindex.sonatype.org", "Sonatype OSS Index API"):
        if host in log_text:
            return 9, "offline_violation"
    for pat in ("H2.*corrupt", "H2.*lock", "H2.*error", "DatabaseException"):
        if re.search(pat, log_text):
            return 9, "h2_error"

    return 0, "success"


def main():
    parser = argparse.ArgumentParser(description="Verify a SANAD NVD snapshot (v2 contract).")
    parser.add_argument("--archive", required=True, help="Path to the .tar.zst archive")
    parser.add_argument("--manifest", required=True, help="Path to the manifest.json sidecar")
    parser.add_argument("--checksums", required=True, help="Path to the SHA256SUMS sidecar")
    parser.add_argument("--expected-dc-version", default=os.environ.get("DEPENDENCY_CHECK_VERSION", DEFAULT_DEPENDENCY_CHECK_VERSION))
    parser.add_argument("--expected-snapshot-id", default=None)
    parser.add_argument("--expected-storage-digest", default=None)
    parser.add_argument("--min-size", type=int, default=int(os.environ.get("NVD_MIN_SIZE_BYTES", 50 * 1024 * 1024)))
    parser.add_argument("--max-age-hours", type=int, default=int(os.environ.get("NVD_MAX_AGE_HOURS", DEFAULT_MAX_AGE_HOURS)))
    parser.add_argument("--run-smoke", action="store_true")
    parser.add_argument("--smoke-extract-dir", default=None)
    parser.add_argument("--maven-path", default="mvn")
    args = parser.parse_args()

    archive = Path(args.archive).resolve()
    manifest = Path(args.manifest).resolve()
    checksums = Path(args.checksums).resolve()
    smoke_dir = Path(args.smoke_extract_dir) if args.smoke_extract_dir else None

    return verify(
        archive, manifest, checksums,
        expected_dc_version=args.expected_dc_version,
        expected_snapshot_id=args.expected_snapshot_id,
        min_size_bytes=args.min_size,
        max_age_hours=args.max_age_hours,
        expected_storage_digest=args.expected_storage_digest,
        run_smoke=args.run_smoke,
        smoke_extract_dir=smoke_dir,
        maven_path=args.maven_path,
    )


if __name__ == "__main__":
    sys.exit(main())
