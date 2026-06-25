"""
SANAD — NVD Database Validator Tests
=====================================
EXEC-PROMPT-010R12A Section 13: Comprehensive test coverage for
scripts/ci/validate_nvd_database.py.

Covers:
  - happy path: valid directory, valid manifest, fresh timestamp
  - missing directory
  - missing odc.mv.db
  - missing manifest
  - invalid manifest JSON
  - manifest schema_version mismatch
  - manifest dependency_check_version mismatch
  - manifest update_mode != update-only
  - manifest update_exit_code != 0
  - manifest validation_result != valid
  - manifest database_filename != odc.mv.db
  - manifest database_size_bytes mismatch
  - manifest database_sha256 not hex / wrong length
  - SHA-256 mismatch (file tampered after manifest written)
  - manifest builder_run_id missing / non-numeric
  - manifest builder_sha not 40-char hex
  - manifest update_completed_at invalid format
  - manifest update_completed_at in the future
  - manifest update_completed_at older than max_age_hours
  - lock file present
  - temp file present
  - size below minimum
  - env-var override of min-size / max-age
  - CLI flag override of expected-dc-version / expected-schema
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

# ---------- Paths ----------

REPO_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = REPO_ROOT / "scripts" / "ci" / "validate_nvd_database.py"
FIXTURES = REPO_ROOT / "tests" / "ci" / "fixtures" / "nvd"


# ---------- Helpers ----------

def utc_now_str() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_db_file(db_dir: Path, size_bytes: int = 60 * 1024 * 1024) -> str:
    """Write a fake odc.mv.db of the given size filled with deterministic bytes.
    Returns the SHA-256 of the file."""
    db_dir.mkdir(parents=True, exist_ok=True)
    db_file = db_dir / "odc.mv.db"
    # Write a deterministic 1 MiB block, repeated to reach the desired size
    block = b"sanad-nvd-test-block-" * ( (1024 * 1024) // 20 )
    remaining = size_bytes
    sha = hashlib.sha256()
    with db_file.open("wb") as f:
        while remaining >= len(block):
            f.write(block)
            sha.update(block)
            remaining -= len(block)
        if remaining > 0:
            tail = block[:remaining]
            f.write(tail)
            sha.update(tail)
    return sha.hexdigest()


def write_manifest(
    db_dir: Path,
    *,
    schema_version: str = "v3",
    dc_version: str = "12.1.0",
    update_mode: str = "update-only",
    update_exit_code: int = 0,
    validation_result: str = "valid",
    database_filename: str = "odc.mv.db",
    database_size_bytes: int | None = None,
    database_sha256: str | None = None,
    update_completed_at: str | None = None,
    builder_run_id: int | str = 28210000000,
    builder_sha: str = "a" * 40,
    extra_fields: dict | None = None,
) -> dict:
    """Write a manifest. Defaults produce a valid manifest."""
    db_file = db_dir / "odc.mv.db"
    if database_size_bytes is None:
        database_size_bytes = db_file.stat().st_size if db_file.exists() else 0
    if database_sha256 is None:
        if db_file.exists():
            database_sha256 = hashlib.sha256(db_file.read_bytes()).hexdigest()
        else:
            database_sha256 = "0" * 40
    if update_completed_at is None:
        update_completed_at = utc_now_str()

    manifest = {
        "schema_version": schema_version,
        "dependency_check_version": dc_version,
        "update_mode": update_mode,
        "update_completed_at": update_completed_at,
        "builder_run_id": builder_run_id,
        "builder_sha": builder_sha,
        "nvd_source": "NVD API",
        "nvd_api_key_used": True,
        "update_exit_code": update_exit_code,
        "database_filename": database_filename,
        "database_size_bytes": database_size_bytes,
        "database_sha256": database_sha256,
        "validation_result": validation_result,
    }
    if extra_fields:
        manifest.update(extra_fields)

    (db_dir / "sanad-nvd-manifest.json").write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )
    return manifest


def run_validator(db_dir: Path, *extra_args: str, env: dict | None = None) -> tuple[int, str, str]:
    """Run the validator as a subprocess. Returns (exit_code, stdout, stderr)."""
    full_env = os.environ.copy()
    # Strip env vars that could affect defaults
    full_env.pop("NVD_MIN_SIZE_BYTES", None)
    full_env.pop("NVD_MAX_AGE_HOURS", None)
    full_env.pop("DEPENDENCY_CHECK_VERSION", None)
    full_env.pop("NVD_CACHE_SCHEMA", None)
    if env:
        full_env.update(env)
    cmd = [sys.executable, str(VALIDATOR), str(db_dir)] + list(extra_args)
    proc = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        env=full_env,
        timeout=30,
    )
    return proc.returncode, proc.stdout, proc.stderr


# ---------- Happy path ----------

def test_happy_path_valid_database(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)

    rc, stdout, _ = run_validator(db_dir)
    assert rc == 0, f"expected rc=0, got {rc}\nstdout:\n{stdout}"
    assert "RESULT=valid" in stdout
    assert "validation_result=valid" in stdout


# ---------- Missing directory / file ----------

def test_missing_directory(tmp_path: Path):
    missing = tmp_path / "does-not-exist"
    rc, stdout, _ = run_validator(missing)
    assert rc == 1
    assert "validation_result=missing_directory" in stdout


def test_directory_exists_but_no_db_file(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    # No odc.mv.db, no manifest
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 1
    assert "validation_result=missing_db_file" in stdout


def test_db_file_too_small(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir, size_bytes=1024)  # 1 KiB — below 50 MiB default
    write_manifest(db_dir, database_size_bytes=1024)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 5
    assert "validation_result=size_below_minimum" in stdout


# ---------- Manifest issues ----------

def test_missing_manifest(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    # No manifest written
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 2
    assert "validation_result=missing_manifest" in stdout


def test_invalid_manifest_json(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    (db_dir / "sanad-nvd-manifest.json").write_text(
        "{ this is not valid json", encoding="utf-8"
    )
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 2
    assert "validation_result=invalid_manifest_json" in stdout


def test_manifest_schema_mismatch(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, schema_version="v2")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_field_violation" in stdout
    assert "manifest_violation_field=schema_version" in stdout


def test_manifest_dc_version_mismatch(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, dc_version="11.1.1")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "manifest_violation_field=dependency_check_version" in stdout


def test_manifest_update_mode_not_update_only(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, update_mode="check")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "manifest_violation_field=update_mode" in stdout


def test_manifest_update_exit_code_nonzero(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, update_exit_code=1)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "manifest_violation_field=update_exit_code" in stdout


def test_manifest_validation_result_not_valid(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, validation_result="incomplete")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "manifest_violation_field=validation_result" in stdout


def test_manifest_filename_mismatch(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, database_filename="wrong.mv.db")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_filename_mismatch" in stdout


def test_manifest_size_mismatch(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    # Lie about size
    write_manifest(db_dir, database_size_bytes=999)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_size_mismatch" in stdout


def test_manifest_sha_not_hex(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, database_sha256="not-a-hex-string")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_sha_invalid" in stdout


def test_manifest_sha_wrong_length(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, database_sha256="abc123")  # too short
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_sha_invalid" in stdout


def test_sha256_mismatch_after_tamper(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)
    # Tamper: overwrite the first 4 bytes of the db file so size is
    # unchanged but content (and therefore SHA-256) differs.
    db_file = db_dir / "odc.mv.db"
    with db_file.open("r+b") as f:
        f.seek(0)
        f.write(b"TAMP")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 4
    assert "validation_result=sha256_mismatch" in stdout


# ---------- Provenance ----------

def test_manifest_builder_run_id_missing(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    manifest = write_manifest(db_dir)
    del manifest["builder_run_id"]
    (db_dir / "sanad-nvd-manifest.json").write_text(
        json.dumps(manifest), encoding="utf-8"
    )
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_provenance_invalid" in stdout


def test_manifest_builder_run_id_non_numeric(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, builder_run_id="not-a-number")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_provenance_invalid" in stdout


def test_manifest_builder_sha_not_hex(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, builder_sha="xyz")  # not 40-char hex
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 3
    assert "validation_result=manifest_provenance_invalid" in stdout


# ---------- Freshness ----------

def test_manifest_timestamp_invalid_format(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, update_completed_at="2026/06/26 02:15:00")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 6
    assert "validation_result=manifest_timestamp_invalid" in stdout


def test_manifest_timestamp_in_future(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    future = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )
    write_manifest(db_dir, update_completed_at=future)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 6
    assert "validation_result=future_timestamp" in stdout


def test_database_stale(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    old = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=72)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )
    write_manifest(db_dir, update_completed_at=old)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 6
    assert "validation_result=stale" in stdout


# ---------- Lock / temp files ----------

def test_lock_file_present(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)
    (db_dir / "odc.mv.db.lock.db").write_text("lock", encoding="utf-8")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 7
    assert "validation_result=lock_files_present" in stdout


def test_temp_file_present(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)
    (db_dir / "odc.mv.db.temp").write_text("temp", encoding="utf-8")
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 7
    assert "validation_result=temp_files_present" in stdout


# ---------- CLI / env overrides ----------

def test_min_size_override_via_cli(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir, size_bytes=1024)
    write_manifest(db_dir, database_size_bytes=1024)
    # Override min-size to 512 bytes — should pass
    rc, stdout, _ = run_validator(db_dir, "--min-size", "512")
    assert rc == 0
    assert "RESULT=valid" in stdout


def test_max_age_override_via_env(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    old = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=72)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )
    write_manifest(db_dir, update_completed_at=old)
    # Allow up to 100 hours via env var
    rc, stdout, _ = run_validator(db_dir, env={"NVD_MAX_AGE_HOURS": "100"})
    assert rc == 0
    assert "RESULT=valid" in stdout


def test_expected_dc_version_override(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, dc_version="13.0.0")
    rc, stdout, _ = run_validator(db_dir, "--expected-dc-version", "13.0.0")
    assert rc == 0
    assert "RESULT=valid" in stdout


def test_expected_schema_override(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir, schema_version="v4")
    rc, stdout, _ = run_validator(db_dir, "--expected-schema", "v4")
    assert rc == 0
    assert "RESULT=valid" in stdout


# ---------- Output contract ----------

def test_outputs_recorded_on_success(tmp_path: Path):
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 0
    # Must publish these output keys
    for key in (
        "canonical_dir=",
        "validation_result=valid",
        "database_sha256=",
        "database_size_bytes=",
        "update_completed_at=",
        "age_hours=",
        "builder_run_id=",
        "builder_sha=",
        "dependency_check_version=",
        "schema_version=",
    ):
        assert key in stdout, f"missing output: {key}\nstdout:\n{stdout}"


def test_no_lock_or_temp_files_when_clean(tmp_path: Path):
    """Ensure a clean directory with no extra files does not falsely flag."""
    db_dir = tmp_path / "canonical"
    db_dir.mkdir()
    write_db_file(db_dir)
    write_manifest(db_dir)
    rc, stdout, _ = run_validator(db_dir)
    assert rc == 0
    assert "lock_files_present" not in stdout
    assert "temp_files_present" not in stdout
