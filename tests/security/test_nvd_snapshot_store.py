"""
SANAD — NVD Snapshot Store Tests
=================================
EXEC-PROMPT-010R12E Section 19: tests for
scripts/security/nvd_snapshot_store.py.

Covers:
  - Manifest creation + validation
  - Manifest rejection (missing keys, bad SHA, bad version)
  - FilesystemBackend publish/download/verify/resolve/promote
  - Retention policy (keeps latest, prunes old)
  - snapshot_id generation (idempotent)
  - Backend factory (s3/ghcr/filesystem)
  - StorageBackendError on missing config
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_snapshot_store import (
    CONTRACT_VERSION,
    SCHEMA_VERSION,
    DEFAULT_RETENTION_COUNT,
    FilesystemBackend,
    SnapshotError,
    SnapshotNotFoundError,
    SnapshotVerificationError,
    StorageBackendError,
    build_manifest,
    get_backend,
    publish_snapshot,
    sha256_bytes,
    sha256_file,
    snapshot_id_for,
    validate_manifest,
    utc_now_iso,
)


# ---------- Helpers ----------

def make_valid_manifest(**overrides) -> dict:
    """Build a manifest with all required fields, valid by default."""
    base = {
        "contract_version": CONTRACT_VERSION,
        "schema_version": SCHEMA_VERSION,
        "dependency_check_version": "12.1.0",
        "snapshot_id": "20260626T120000Z-abc123def456-0123456789ab",
        "created_at": utc_now_iso(),
        "last_successful_update_at": utc_now_iso(),
        "publisher_commit_sha": "a" * 40,
        "publisher_run_id": 123456,
        "source": "NVD_API",
        "archive_filename": "snad-nvd-data-test.tar.zst",
        "archive_sha256": "a" * 64,
        "archive_size_bytes": 1000000,
        "database_filename": "odc.mv.db",
        "database_sha256": "b" * 64,
        "database_size_bytes": 50000000,
        "validation_result": "valid",
        "offline_smoke_result": "success",
        "storage_backend": "filesystem",
        "storage_version_or_digest": "filesystem:test",
        "previous_snapshot_id": "",
        "freshness_hours_at_publish": 0,
    }
    base.update(overrides)
    return base


# ---------- Manifest tests ----------

def test_build_manifest_produces_valid_dict():
    m = build_manifest(
        snapshot_id="test-id",
        created_at=utc_now_iso(),
        last_successful_update_at=utc_now_iso(),
        publisher_commit_sha="a" * 40,
        publisher_run_id=123,
        archive_filename="test.tar.zst",
        archive_sha256="a" * 64,
        archive_size_bytes=1000,
        database_filename="odc.mv.db",
        database_sha256="b" * 64,
        database_size_bytes=2000,
        validation_result="valid",
        offline_smoke_result="success",
        storage_backend="filesystem",
        storage_version_or_digest="filesystem:test",
        previous_snapshot_id="",
        freshness_hours_at_publish=0,
    )
    validate_manifest(m)  # should not raise


def test_validate_manifest_rejects_missing_key():
    m = make_valid_manifest()
    del m["snapshot_id"]
    with pytest.raises(ValueError, match="missing required key: snapshot_id"):
        validate_manifest(m)


def test_validate_manifest_rejects_wrong_contract_version():
    m = make_valid_manifest(contract_version="wrong")
    with pytest.raises(ValueError, match="contract_version"):
        validate_manifest(m)


def test_validate_manifest_rejects_invalid_validation_result():
    m = make_valid_manifest(validation_result="incomplete")
    with pytest.raises(ValueError, match="validation_result"):
        validate_manifest(m)


def test_validate_manifest_rejects_failed_smoke():
    m = make_valid_manifest(offline_smoke_result="failure")
    with pytest.raises(ValueError, match="offline_smoke_result"):
        validate_manifest(m)


def test_validate_manifest_rejects_bad_archive_sha():
    m = make_valid_manifest(archive_sha256="too-short")
    with pytest.raises(ValueError, match="archive_sha256"):
        validate_manifest(m)


def test_validate_manifest_rejects_bad_publisher_sha():
    m = make_valid_manifest(publisher_commit_sha="xyz")
    with pytest.raises(ValueError, match="publisher_commit_sha"):
        validate_manifest(m)


def test_validate_manifest_rejects_unsupported_backend():
    m = make_valid_manifest(storage_backend="azure")
    with pytest.raises(ValueError, match="storage_backend"):
        validate_manifest(m)


def test_validate_manifest_rejects_negative_freshness():
    m = make_valid_manifest(freshness_hours_at_publish=-1)
    with pytest.raises(ValueError, match="freshness_hours_at_publish"):
        validate_manifest(m)


# ---------- snapshot_id_for ----------

def test_snapshot_id_is_idempotent():
    ts = "2026-06-26T12:00:00Z"
    sha = "a" * 40
    db = "b" * 64
    sid1 = snapshot_id_for(ts, sha, db)
    sid2 = snapshot_id_for(ts, sha, db)
    assert sid1 == sid2


def test_snapshot_id_changes_with_db_content():
    ts = "2026-06-26T12:00:00Z"
    sha = "a" * 40
    sid1 = snapshot_id_for(ts, sha, "a" * 64)
    sid2 = snapshot_id_for(ts, sha, "b" * 64)
    assert sid1 != sid2


# ---------- FilesystemBackend tests ----------

@pytest.fixture
def fs_backend(tmp_path):
    return FilesystemBackend(tmp_path / "snapshot-store")


def test_filesystem_backend_publish_and_resolve(fs_backend, tmp_path):
    # Create a fake data dir with a database file
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    db_file = data_dir / "odc.mv.db"
    db_file.write_bytes(b"sanad-nvd-test" * (1024 * 1024 // 14 + 1))  # ~1 MiB for tests

    db_sha = sha256_file(db_file)
    db_size = db_file.stat().st_size

    manifest = publish_snapshot(
        backend=fs_backend,
        data_dir=data_dir,
        publisher_commit_sha="a" * 40,
        publisher_run_id=123,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=db_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
    )

    assert manifest["snapshot_id"]
    assert manifest["archive_sha256"]
    assert manifest["storage_version_or_digest"].startswith("filesystem:")

    # Resolve latest
    latest = fs_backend.resolve_latest_verified()
    assert latest is not None
    assert latest["snapshot_id"] == manifest["snapshot_id"]


def test_filesystem_backend_download_manifest(fs_backend, tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    db_file = data_dir / "odc.mv.db"
    db_file.write_bytes(b"x" * (1024 * 1024))
    db_sha = sha256_file(db_file)

    manifest = publish_snapshot(
        backend=fs_backend,
        data_dir=data_dir,
        publisher_commit_sha="b" * 40,
        publisher_run_id=456,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=db_file.stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
    )

    downloaded = fs_backend.download_manifest(manifest["snapshot_id"])
    assert downloaded["snapshot_id"] == manifest["snapshot_id"]
    assert downloaded["database_sha256"] == db_sha


def test_filesystem_backend_download_snapshot(fs_backend, tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    db_file = data_dir / "odc.mv.db"
    db_file.write_bytes(b"y" * (1024 * 1024))
    db_sha = sha256_file(db_file)

    manifest = publish_snapshot(
        backend=fs_backend,
        data_dir=data_dir,
        publisher_commit_sha="c" * 40,
        publisher_run_id=789,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=db_file.stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
    )

    dest = tmp_path / "download"
    archive = fs_backend.download_snapshot(manifest["snapshot_id"], dest)
    assert archive.exists()
    assert sha256_file(archive) == manifest["archive_sha256"]


def test_filesystem_backend_verify_storage_digest(fs_backend, tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    db_file = data_dir / "odc.mv.db"
    db_file.write_bytes(b"z" * (1024 * 1024))
    db_sha = sha256_file(db_file)

    manifest = publish_snapshot(
        backend=fs_backend,
        data_dir=data_dir,
        publisher_commit_sha="d" * 40,
        publisher_run_id=111,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=db_file.stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
    )

    assert fs_backend.verify_storage_digest(manifest["snapshot_id"], manifest["storage_version_or_digest"]) is True


def test_filesystem_backend_resolve_returns_none_when_empty(fs_backend):
    assert fs_backend.resolve_latest_verified() is None


def test_filesystem_backend_list_verified_snapshots(fs_backend, tmp_path):
    for i in range(3):
        data_dir = tmp_path / f"data-{i}"
        data_dir.mkdir()
        db_file = data_dir / "odc.mv.db"
        db_file.write_bytes(f"db-{i}".encode() * (256 * 1024))
        db_sha = sha256_file(db_file)
        publish_snapshot(
            backend=fs_backend,
            data_dir=data_dir,
            publisher_commit_sha="e" * 40,
            publisher_run_id=200 + i,
            previous_snapshot_id="",
            last_successful_update_at=utc_now_iso(),
            database_sha256=db_sha,
            database_size_bytes=db_file.stat().st_size,
            validation_result="valid",
            offline_smoke_result="success",
            freshness_hours_at_publish=0,
        )
    ids = fs_backend.list_verified_snapshots()
    assert len(ids) == 3


def test_filesystem_backend_retention_keeps_latest(fs_backend, tmp_path):
    published_ids = []
    for i in range(5):
        data_dir = tmp_path / f"data-r-{i}"
        data_dir.mkdir()
        db_file = data_dir / "odc.mv.db"
        db_file.write_bytes(f"ret-{i}".encode() * (256 * 1024))
        db_sha = sha256_file(db_file)
        m = publish_snapshot(
            backend=fs_backend,
            data_dir=data_dir,
            publisher_commit_sha="f" * 40,
            publisher_run_id=300 + i,
            previous_snapshot_id=published_ids[-1] if published_ids else "",
            last_successful_update_at=utc_now_iso(),
            database_sha256=db_sha,
            database_size_bytes=db_file.stat().st_size,
            validation_result="valid",
            offline_smoke_result="success",
            freshness_hours_at_publish=0,
        )
        published_ids.append(m["snapshot_id"])

    # Latest should be the last published
    latest = fs_backend.resolve_latest_verified()
    latest_id = latest["snapshot_id"]
    assert latest_id == published_ids[-1]

    # Apply retention keeping only 2
    deleted = fs_backend.apply_retention_policy(keep=2)
    # Should delete 5 - 2 = 3 (but must keep latest)
    assert len(deleted) == 3
    assert latest_id not in deleted

    # Latest must still resolve
    latest_after = fs_backend.resolve_latest_verified()
    assert latest_after["snapshot_id"] == latest_id


def test_filesystem_backend_download_missing_raises(fs_backend):
    with pytest.raises(SnapshotNotFoundError):
        fs_backend.download_manifest("nonexistent-snapshot-id")


# ---------- Backend factory ----------

def test_get_backend_filesystem():
    b = get_backend("filesystem", root_dir="/tmp/test-store")
    assert b.backend_name == "filesystem"


def test_get_backend_s3_requires_bucket(monkeypatch):
    monkeypatch.delenv("NVD_SNAPSHOT_BUCKET", raising=False)
    with pytest.raises(StorageBackendError, match="NVD_SNAPSHOT_BUCKET"):
        get_backend("s3")


def test_get_backend_ghcr_requires_owner(monkeypatch):
    monkeypatch.delenv("NVD_SNAPSHOT_GHCR_OWNER", raising=False)
    monkeypatch.delenv("GITHUB_REPOSITORY_OWNER", raising=False)
    with pytest.raises(StorageBackendError, match="NVD_SNAPSHOT_GHCR_OWNER"):
        get_backend("ghcr")


def test_get_backend_unknown_raises():
    with pytest.raises(StorageBackendError, match="Unknown"):
        get_backend("azure")


def test_get_backend_uses_env_var(monkeypatch, tmp_path):
    monkeypatch.setenv("NVD_SNAPSHOT_BACKEND", "filesystem")
    # filesystem backend also needs root_dir via kwarg
    b = get_backend(root_dir=str(tmp_path / "env-store"))
    assert b.backend_name == "filesystem"


# ---------- publish_snapshot error cases ----------

def test_publish_snapshot_rejects_missing_data_dir(fs_backend, tmp_path):
    with pytest.raises(SnapshotError, match="data_dir does not exist"):
        publish_snapshot(
            backend=fs_backend,
            data_dir=tmp_path / "nonexistent",
            publisher_commit_sha="a" * 40,
            publisher_run_id=1,
            previous_snapshot_id="",
            last_successful_update_at=utc_now_iso(),
            database_sha256="a" * 64,
            database_size_bytes=50000000,
            validation_result="valid",
            offline_smoke_result="success",
            freshness_hours_at_publish=0,
        )
