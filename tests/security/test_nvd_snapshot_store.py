"""
SANAD — NVD Snapshot Store Tests (v2)
======================================
EXEC-PROMPT-010R12F Section 21: tests for the v2 snapshot contract.
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

from scripts.security.nvd_archive import (
    create_snapshot_archive,
    extract_snapshot_archive,
    list_snapshot_archive,
    stream_hash_file,
)
from scripts.security.nvd_snapshot_store import (
    CONTRACT_VERSION,
    SCHEMA_VERSION,
    DEFAULT_RETENTION_COUNT,
    FilesystemBackend,
    SnapshotError,
    SnapshotNotFoundError,
    SnapshotVerificationError,
    StorageAuthenticationError,
    StorageAuthorizationError,
    StorageBackendError,
    StorageIntegrityError,
    StorageUnavailableError,
    build_manifest,
    get_backend,
    sha256_bytes,
    sha256_file,
    snapshot_id_for,
    utc_now_iso,
    validate_manifest,
)
from scripts.security.publish_nvd_snapshot import (
    CONTRACT_VERSION_V2,
    SCHEMA_VERSION_V2,
    LATEST_CONTRACT_VERSION,
    atomic_canonical_swap,
    build_latest_pointer,
    build_manifest_v2,
    build_sha256sums,
    publish_snapshot_v2,
    validate_manifest_v2,
)


# ---------- Helpers ----------

def make_fake_data_dir(parent_dir: Path, size_bytes: int = 1024 * 1024) -> Path:
    """Create a fake data dir with an odc.mv.db file.

    Creates parent_dir/data/odc.mv.db. Returns parent_dir/data.
    """
    data_dir = parent_dir / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    db_file = data_dir / "odc.mv.db"
    db_file.write_bytes(b"sanad-nvd-test" * (size_bytes // 14 + 1))
    return data_dir


def make_valid_v2_manifest(**overrides) -> dict:
    """Build a v2 manifest with all required fields."""
    base = {
        "contract_version": CONTRACT_VERSION_V2,
        "schema_version": SCHEMA_VERSION_V2,
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
        "database_relative_path": "data/odc.mv.db",
        "database_sha256": "b" * 64,
        "database_size_bytes": 50000000,
        "validation_result": "valid",
        "offline_smoke_result": "success",
        "storage_backend": "filesystem",
        "previous_snapshot_id": "",
        "freshness_hours_at_publish": 0,
    }
    base.update(overrides)
    return base


# ---------- Manifest v2 tests ----------

def test_validate_manifest_v2_accepts_valid():
    m = make_valid_v2_manifest()
    validate_manifest_v2(m)


def test_validate_manifest_v2_rejects_v1_contract():
    m = make_valid_v2_manifest(contract_version="snad-nvd-snapshot-v1")
    with pytest.raises(ValueError, match="contract_version"):
        validate_manifest_v2(m)


def test_validate_manifest_v2_rejects_storage_digest_field():
    """R12F: storage_version_or_digest must NOT be in v2 manifest."""
    m = make_valid_v2_manifest()
    m["storage_version_or_digest"] = "should-not-be-here"
    with pytest.raises(ValueError, match="must NOT contain storage_version_or_digest"):
        validate_manifest_v2(m)


def test_validate_manifest_v2_rejects_missing_database_relative_path():
    m = make_valid_v2_manifest()
    del m["database_relative_path"]
    with pytest.raises(ValueError, match="missing required key: database_relative_path"):
        validate_manifest_v2(m)


def test_validate_manifest_v2_rejects_bad_archive_sha():
    m = make_valid_v2_manifest(archive_sha256="too-short")
    with pytest.raises(ValueError, match="archive_sha256"):
        validate_manifest_v2(m)


def test_validate_manifest_v2_rejects_invalid_validation_result():
    m = make_valid_v2_manifest(validation_result="incomplete")
    with pytest.raises(ValueError, match="validation_result"):
        validate_manifest_v2(m)


# ---------- latest.json pointer tests ----------

def test_build_latest_pointer_includes_all_fields():
    pointer = build_latest_pointer(
        snapshot_id="test-id",
        promoted_at=utc_now_iso(),
        created_at=utc_now_iso(),
        archive_filename="test.tar.zst",
        archive_sha256="a" * 64,
        manifest_sha256="b" * 64,
        storage_backend="filesystem",
        storage_version_or_digest="filesystem:test-id",
    )
    assert pointer["contract_version"] == LATEST_CONTRACT_VERSION
    assert pointer["snapshot_id"] == "test-id"
    assert pointer["storage_version_or_digest"] == "filesystem:test-id"
    assert "manifest_sha256" in pointer


def test_build_sha256sums_format():
    content = build_sha256sums("test.tar.zst", "a" * 64, "b" * 64)
    assert "test.tar.zst" in content
    assert "manifest.json" in content
    assert "a" * 64 in content


# ---------- FilesystemBackend v2 tests ----------

@pytest.fixture
def fs_backend(tmp_path):
    return FilesystemBackend(tmp_path / "snapshot-store")


def test_filesystem_backend_publish_v2_and_resolve(fs_backend, tmp_path):
    """R12F: single-push publish + resolve."""
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024 * 1024)
    work_dir = tmp_path / "work"
    work_dir.mkdir()

    db_sha = sha256_file(data_dir / "odc.mv.db")
    db_size = (data_dir / "odc.mv.db").stat().st_size

    result = publish_snapshot_v2(
        backend=fs_backend,
        work_dir=data_dir,
        publisher_commit_sha="a" * 40,
        publisher_run_id=123,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=db_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
        temp_dir=work_dir,
    )

    assert result["manifest"]["snapshot_id"]
    assert result["manifest"]["contract_version"] == CONTRACT_VERSION_V2
    assert "storage_version_or_digest" not in result["manifest"]
    assert result["storage_metadata"]["storage_version_or_digest"].startswith("filesystem:")

    latest = fs_backend.resolve_latest_verified()
    assert latest is not None
    assert latest["snapshot_id"] == result["manifest"]["snapshot_id"]
    assert latest["contract_version"] == LATEST_CONTRACT_VERSION


def test_filesystem_backend_download_manifest_v2(fs_backend, tmp_path):
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024 * 1024)
    work_dir = tmp_path / "work"
    work_dir.mkdir()

    db_sha = sha256_file(data_dir / "odc.mv.db")
    result = publish_snapshot_v2(
        backend=fs_backend,
        work_dir=data_dir,
        publisher_commit_sha="b" * 40,
        publisher_run_id=456,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
        temp_dir=work_dir,
    )

    downloaded = fs_backend.download_manifest(result["manifest"]["snapshot_id"])
    assert downloaded["contract_version"] == CONTRACT_VERSION_V2
    assert downloaded["snapshot_id"] == result["manifest"]["snapshot_id"]


def test_filesystem_backend_download_snapshot_v2(fs_backend, tmp_path):
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024 * 1024)
    work_dir = tmp_path / "work"
    work_dir.mkdir()

    db_sha = sha256_file(data_dir / "odc.mv.db")
    result = publish_snapshot_v2(
        backend=fs_backend,
        work_dir=data_dir,
        publisher_commit_sha="c" * 40,
        publisher_run_id=789,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
        temp_dir=work_dir,
    )

    dest = tmp_path / "download"
    archive = fs_backend.download_snapshot(result["manifest"]["snapshot_id"], dest)
    assert archive.exists()
    assert sha256_file(archive) == result["manifest"]["archive_sha256"]


def test_filesystem_backend_verify_storage_digest_v2(fs_backend, tmp_path):
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024 * 1024)
    work_dir = tmp_path / "work"
    work_dir.mkdir()

    db_sha = sha256_file(data_dir / "odc.mv.db")
    result = publish_snapshot_v2(
        backend=fs_backend,
        work_dir=data_dir,
        publisher_commit_sha="d" * 40,
        publisher_run_id=111,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
        temp_dir=work_dir,
    )

    assert fs_backend.verify_storage_digest(
        result["manifest"]["snapshot_id"],
        result["storage_metadata"]["storage_version_or_digest"],
    )


def test_filesystem_backend_resolve_returns_none_when_empty(fs_backend):
    assert fs_backend.resolve_latest_verified() is None


def test_filesystem_backend_list_verified_snapshots_v2(fs_backend, tmp_path):
    for i in range(3):
        # Use unique content per iteration so snapshot_id differs
        data_dir = make_fake_data_dir(tmp_path / f"data-{i}", size_bytes=256 * 1024)
        # Make content unique
        (data_dir / "odc.mv.db").write_bytes(f"snapshot-{i}-".encode() * (256 * 1024 // 10 + 1))
        work_dir = tmp_path / f"work-{i}"
        work_dir.mkdir()
        db_sha = sha256_file(data_dir / "odc.mv.db")
        publish_snapshot_v2(
            backend=fs_backend,
            work_dir=data_dir,
            publisher_commit_sha="e" * 40,
            publisher_run_id=200 + i,
            previous_snapshot_id="",
            last_successful_update_at=utc_now_iso(),
            database_sha256=db_sha,
            database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
            validation_result="valid",
            offline_smoke_result="success",
            freshness_hours_at_publish=0,
            temp_dir=work_dir,
        )
    ids = fs_backend.list_verified_snapshots()
    assert len(ids) == 3


def test_filesystem_backend_retention_keeps_latest_v2(fs_backend, tmp_path):
    published_ids = []
    for i in range(5):
        data_dir = make_fake_data_dir(tmp_path / f"ret-{i}", size_bytes=256 * 1024)
        # Make content unique
        (data_dir / "odc.mv.db").write_bytes(f"retention-{i}-".encode() * (256 * 1024 // 12 + 1))
        work_dir = tmp_path / f"retwork-{i}"
        work_dir.mkdir()
        db_sha = sha256_file(data_dir / "odc.mv.db")
        m = publish_snapshot_v2(
            backend=fs_backend,
            work_dir=data_dir,
            publisher_commit_sha="f" * 40,
            publisher_run_id=300 + i,
            previous_snapshot_id=published_ids[-1] if published_ids else "",
            last_successful_update_at=utc_now_iso(),
            database_sha256=db_sha,
            database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
            validation_result="valid",
            offline_smoke_result="success",
            freshness_hours_at_publish=0,
            temp_dir=work_dir,
        )
        published_ids.append(m["manifest"]["snapshot_id"])

    latest = fs_backend.resolve_latest_verified()
    latest_id = latest["snapshot_id"]
    assert latest_id == published_ids[-1]

    deleted = fs_backend.apply_retention_policy(keep=2)
    assert len(deleted) == 3
    assert latest_id not in deleted

    latest_after = fs_backend.resolve_latest_verified()
    assert latest_after["snapshot_id"] == latest_id


def test_filesystem_backend_download_missing_raises(fs_backend):
    with pytest.raises(SnapshotNotFoundError):
        fs_backend.download_manifest("nonexistent-snapshot-id")


# ---------- Single-push test ----------

def test_publish_v2_calls_publish_immutable_once(tmp_path, monkeypatch):
    """R12F Section 10.1: publish_immutable_snapshot must be called exactly once."""
    backend = FilesystemBackend(tmp_path / "store")
    call_count = {"count": 0}
    original = backend.publish_immutable_snapshot

    def counting_publish(*args, **kwargs):
        call_count["count"] += 1
        return original(*args, **kwargs)

    backend.publish_immutable_snapshot = counting_publish

    data_dir = make_fake_data_dir(tmp_path, size_bytes=256 * 1024)
    work_dir = tmp_path / "work"
    work_dir.mkdir()
    db_sha = sha256_file(data_dir / "odc.mv.db")

    publish_snapshot_v2(
        backend=backend,
        work_dir=data_dir,
        publisher_commit_sha="a" * 40,
        publisher_run_id=999,
        previous_snapshot_id="",
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha,
        database_size_bytes=(data_dir / "odc.mv.db").stat().st_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
        temp_dir=work_dir,
    )

    assert call_count["count"] == 1, "publish_immutable_snapshot must be called exactly once"


# ---------- Backend factory ----------

def test_get_backend_filesystem():
    b = get_backend("filesystem", root_dir="/tmp/test-store")
    assert b.backend_name == "filesystem"


def test_get_backend_s3_requires_bucket(monkeypatch):
    monkeypatch.delenv("NVD_SNAPSHOT_BUCKET", raising=False)
    with pytest.raises(StorageBackendError, match="NVD_SNAPSHOT_BUCKET"):
        get_backend("s3")


def test_get_backend_ghcr_requires_experimental_gate(monkeypatch):
    """R12F: GHCR is behind experimental gate, disabled by default."""
    monkeypatch.delenv("NVD_ENABLE_EXPERIMENTAL_GHCR", raising=False)
    monkeypatch.setenv("NVD_SNAPSHOT_GHCR_OWNER", "snadaiapp-png")
    with pytest.raises(StorageBackendError, match="EXPERIMENTAL"):
        get_backend("ghcr")


def test_get_backend_ghcr_enabled_with_flag(monkeypatch):
    monkeypatch.setenv("NVD_ENABLE_EXPERIMENTAL_GHCR", "true")
    monkeypatch.setenv("NVD_SNAPSHOT_GHCR_OWNER", "snadaiapp-png")
    b = get_backend("ghcr")
    assert b.backend_name == "ghcr"


def test_get_backend_unknown_raises():
    with pytest.raises(StorageBackendError, match="Unknown"):
        get_backend("azure")


# ---------- Atomic canonical swap ----------

def test_atomic_canonical_swap(tmp_path):
    """R12F Section 15.3: atomic swap via rename."""
    canonical = tmp_path / "canonical"
    canonical.mkdir()
    (canonical / "old.txt").write_text("old")

    work = tmp_path / "work"
    work.mkdir()
    (work / "new.txt").write_text("new")

    atomic_canonical_swap(work, canonical, "run-123")

    assert canonical.exists()
    assert (canonical / "new.txt").read_text() == "new"
    assert not (canonical / "old.txt").exists()
    # No leftover next/previous dirs
    assert not (tmp_path / "canonical.next.run-123").exists()
    assert not (tmp_path / "canonical.previous.run-123").exists()


def test_atomic_canonical_swap_first_time(tmp_path):
    """Atomic swap when canonical doesn't exist yet."""
    canonical = tmp_path / "canonical"
    work = tmp_path / "work"
    work.mkdir()
    (work / "init.txt").write_text("init")

    atomic_canonical_swap(work, canonical, "run-1")

    assert canonical.exists()
    assert (canonical / "init.txt").read_text() == "init"


# ---------- Archive utility tests ----------

def test_create_archive_has_data_prefix(tmp_path):
    """R12F: archive root must be data/."""
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024)
    archive = tmp_path / "test.tar.zst"
    actual_archive = create_snapshot_archive(archive, data_dir, tmp_path / "work")

    members = list_snapshot_archive(actual_archive)
    assert any(m.startswith("data/") for m in members), f"archive must have data/ prefix, got: {members}"


def test_archive_excludes_forbidden_files(tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    (data_dir / "odc.mv.db").write_bytes(b"db")
    (data_dir / "odc.trace.db").write_bytes(b"trace")  # accepted
    (data_dir / "odc.mv.db.lock.db").write_bytes(b"lock")  # forbidden
    (data_dir / "odc.mv.db.tmp.db").write_bytes(b"tmp")  # forbidden

    archive = tmp_path / "test.tar.zst"
    actual_archive = create_snapshot_archive(archive, data_dir, tmp_path / "work")

    members = list_snapshot_archive(actual_archive)
    assert any("odc.mv.db" in m and not m.endswith(".lock.db") and not m.endswith(".tmp.db") for m in members)
    assert not any(".lock.db" in m for m in members), "lock files must be excluded"
    assert not any(".tmp.db" in m for m in members), "temp files must be excluded"
    assert any("odc.trace.db" in m for m in members), "trace.db must be included"


def test_archive_path_traversal_rejected(tmp_path):
    """R12F Section 11.3: path traversal must be rejected."""
    # Create an archive with a malicious path manually
    import subprocess
    archive = tmp_path / "malicious.tar.gz"
    # Create a file with ../ in its name
    malicious_dir = tmp_path / "mal"
    malicious_dir.mkdir()
    (malicious_dir / "normal.txt").write_text("ok")
    # Use tar with a crafted name
    subprocess.run(
        ["tar", "-czf", str(archive), "-C", str(malicious_dir),
         "--transform=s,^,../../../,", "normal.txt"],
        check=True, capture_output=True,
    )

    from scripts.security.nvd_archive import validate_archive_paths, ArchivePathTraversalError
    with pytest.raises(ArchivePathTraversalError):
        validate_archive_paths(archive)


def test_stream_hash_file(tmp_path):
    """R12F: streaming hash does not load entire file into memory."""
    f = tmp_path / "large.bin"
    data = b"x" * (10 * 1024 * 1024)  # 10 MiB
    f.write_bytes(data)

    expected = hashlib.sha256(data).hexdigest()
    actual = stream_hash_file(f)
    assert actual == expected


def test_extract_archive_preserves_data_prefix(tmp_path):
    data_dir = make_fake_data_dir(tmp_path, size_bytes=1024)
    archive = tmp_path / "test.tar.zst"
    actual_archive = create_snapshot_archive(archive, data_dir, tmp_path / "work")

    extract_dir = tmp_path / "extract"
    extract_snapshot_archive(actual_archive, extract_dir)

    assert (extract_dir / "data" / "odc.mv.db").exists()


# ---------- Error classification tests ----------

def test_storage_error_hierarchy():
    """R12F: storage errors are properly classified."""
    assert issubclass(StorageAuthenticationError, StorageBackendError)
    assert issubclass(StorageAuthorizationError, StorageBackendError)
    assert issubclass(StorageUnavailableError, StorageBackendError)
    assert issubclass(StorageIntegrityError, StorageBackendError)
    assert issubclass(StorageBackendError, SnapshotError)
    assert issubclass(SnapshotNotFoundError, SnapshotError)


# ---------- Snapshot ID generation ----------

def test_snapshot_id_for_is_deterministic():
    ts = "2026-06-26T12:00:00Z"
    sha = "a" * 40
    db = "b" * 64
    sid1 = snapshot_id_for(ts, sha, db)
    sid2 = snapshot_id_for(ts, sha, db)
    assert sid1 == sid2


def test_snapshot_id_for_changes_with_db():
    ts = "2026-06-26T12:00:00Z"
    sha = "a" * 40
    sid1 = snapshot_id_for(ts, sha, "a" * 64)
    sid2 = snapshot_id_for(ts, sha, "b" * 64)
    assert sid1 != sid2
