from __future__ import annotations

import pathlib

from scripts.security import nvd_snapshot_store as store
from scripts.security.run_nvd_snapshot_publisher import promote_latest_pointer


class FakeBackend:
    LATEST_TAG = "nvd-snapshot-latest"

    def __init__(self, release_exists=True, deletion_missing=False):
        self.release_exists = release_exists
        self.deletion_missing = deletion_missing
        self.calls = []
        self.uploaded = False

    def _request(self, method, path, body=None):
        self.calls.append((method, path))
        if method == "GET" and path.startswith("releases/tags/"):
            if not self.release_exists:
                raise store.SnapshotNotFoundError("missing release")
            return {
                "id": 100,
                "upload_url": "upload-target",
                "assets": [{"id": 200, "name": "latest.json"}],
            }
        if method == "POST" and path == "releases":
            self.release_exists = True
            return {"id": 101, "upload_url": "upload-target", "assets": []}
        if method == "DELETE" and path == "releases/assets/200":
            if self.deletion_missing:
                raise store.SnapshotNotFoundError("missing asset")
            return {}
        if method == "GET" and path in ("releases/100", "releases/101"):
            return {"assets": [{"id": 201, "name": "latest.json"}]}
        raise AssertionError((method, path))

    def _upload_asset(self, upload_url, asset_name, asset_path):
        assert upload_url == "upload-target"
        assert asset_name == "latest.json"
        assert pathlib.Path(asset_path).is_file()
        self.uploaded = True


def sample_pointer():
    return {
        "contract_version": "snad-nvd-latest-v1",
        "snapshot_id": "snapshot-1",
        "created_at": "2026-07-01T17:00:00Z",
    }


def test_existing_release_deletes_asset_without_recreating_release():
    backend = FakeBackend()
    promote_latest_pointer(backend, sample_pointer())
    assert ("DELETE", "releases/assets/200") in backend.calls
    assert ("POST", "releases") not in backend.calls
    assert backend.uploaded


def test_missing_asset_does_not_trigger_duplicate_release_creation():
    backend = FakeBackend(deletion_missing=True)
    promote_latest_pointer(backend, sample_pointer())
    assert ("POST", "releases") not in backend.calls
    assert backend.uploaded


def test_missing_release_is_created_once():
    backend = FakeBackend(release_exists=False)
    promote_latest_pointer(backend, sample_pointer())
    assert backend.calls.count(("POST", "releases")) == 1
    assert backend.uploaded
