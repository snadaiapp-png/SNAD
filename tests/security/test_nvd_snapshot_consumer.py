from __future__ import annotations

import datetime as dt
import importlib.util
import pathlib

import pytest

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "security" / "download_nvd_snapshot.py"
SPEC = importlib.util.spec_from_file_location("download_nvd_snapshot", MODULE_PATH)
assert SPEC and SPEC.loader
consumer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(consumer)


def valid_pointer(**overrides):
    current = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    pointer = {
        "contract_version": "snad-nvd-latest-v1",
        "snapshot_id": "20260701T120000Z-0123456789ab-abcdef012345",
        "created_at": current,
        "archive_filename": "snad-nvd-data.tar.zst",
        "archive_sha256": "a" * 64,
        "manifest_sha256": "b" * 64,
        "storage_backend": "github-releases",
        "storage_version_or_digest": "github-releases:tag=test",
    }
    pointer.update(overrides)
    return pointer


def test_pointer_contract_and_freshness_pass():
    age = consumer.validate_pointer(valid_pointer(), max_age_hours=48)
    assert 0 <= age < 1


def test_pointer_stale_fails_closed():
    old = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=49)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )
    with pytest.raises(consumer.SnapshotConsumerError, match="stale"):
        consumer.validate_pointer(valid_pointer(created_at=old), max_age_hours=48)


def test_pointer_missing_digest_fails_closed():
    pointer = valid_pointer()
    del pointer["archive_sha256"]
    with pytest.raises(consumer.SnapshotConsumerError, match="missing fields"):
        consumer.validate_pointer(pointer, max_age_hours=48)


def test_pointer_rejects_non_basename_archive():
    with pytest.raises(consumer.SnapshotConsumerError, match="unsafe archive"):
        consumer.validate_pointer(
            valid_pointer(archive_filename="folder/snapshot.tar.zst"), max_age_hours=48
        )
