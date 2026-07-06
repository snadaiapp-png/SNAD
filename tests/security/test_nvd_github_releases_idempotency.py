import json
from pathlib import Path

import pytest

from scripts.security.nvd_github_releases_idempotency import (
    promote_latest_pointer_idempotent,
)
from scripts.security.nvd_snapshot_store import (
    GitHubReleasesBackend,
    SnapshotNotFoundError,
    StorageBackendError,
)


class FakeGitHubReleasesBackend(GitHubReleasesBackend):
    def __init__(
        self,
        *,
        release_exists=True,
        asset_exists=True,
        delete_404_once=False,
        create_422_once=False,
        upload_422_once=False,
    ):
        self.release_exists = release_exists
        self.asset_exists = asset_exists
        self.delete_404_once = delete_404_once
        self.create_422_once = create_422_once
        self.upload_422_once = upload_422_once
        self.upload_attempts = 0
        self.deleted_assets = 0
        self.stored_bytes = b""

    def _release(self):
        assets = []
        if self.asset_exists:
            assets.append(
                {
                    "id": 99,
                    "name": "latest.json",
                    "url": "https://api.example/assets/99",
                }
            )
        return {
            "id": 7,
            "tag_name": self.LATEST_TAG,
            "upload_url": "https://uploads.example/releases/7/assets{?name,label}",
            "assets": assets,
        }

    def _request(self, method, path, body=None, expected=(200, 201, 204)):
        if method == "GET" and path == f"releases/tags/{self.LATEST_TAG}":
            if not self.release_exists:
                raise SnapshotNotFoundError("missing")
            return self._release()

        if method == "POST" and path == "releases":
            if self.create_422_once:
                self.create_422_once = False
                self.release_exists = True
                raise StorageBackendError("HTTP 422 on POST releases: already_exists")
            self.release_exists = True
            return self._release()

        if method == "GET" and path == "releases/7":
            return self._release()

        if method == "DELETE" and path == "releases/7/assets/99":
            self.deleted_assets += 1
            self.asset_exists = False
            if self.delete_404_once:
                self.delete_404_once = False
                raise SnapshotNotFoundError("already deleted")
            return {}

        raise AssertionError(f"unexpected request: {method} {path}")

    def _upload_asset(self, upload_url_template, asset_name, asset_path, max_attempts=None):
        self.upload_attempts += 1
        if self.upload_422_once:
            self.upload_422_once = False
            self.asset_exists = True
            raise StorageBackendError("asset upload failed (422): already_exists")
        self.asset_exists = True
        self.stored_bytes = Path(asset_path).read_bytes()
        return {"id": 99, "name": asset_name}

    def _download_asset(self, asset_url, dest_path):
        Path(dest_path).write_bytes(self.stored_bytes)


def pointer():
    return {
        "contract_version": "snad-nvd-latest-v1",
        "snapshot_id": "snapshot-123",
        "archive_sha256": "a" * 64,
        "manifest_sha256": "b" * 64,
    }


def test_delete_404_is_treated_as_already_successful():
    backend = FakeGitHubReleasesBackend(delete_404_once=True)

    promote_latest_pointer_idempotent(backend, pointer())

    assert backend.deleted_assets == 1
    assert backend.upload_attempts == 1
    assert json.loads(backend.stored_bytes) == pointer()


def test_release_create_422_reuses_concurrently_created_release():
    backend = FakeGitHubReleasesBackend(
        release_exists=False,
        asset_exists=False,
        create_422_once=True,
    )

    promote_latest_pointer_idempotent(backend, pointer())

    assert backend.release_exists is True
    assert backend.upload_attempts == 1
    assert json.loads(backend.stored_bytes) == pointer()


def test_duplicate_asset_422_is_cleaned_and_retried_once():
    backend = FakeGitHubReleasesBackend(
        asset_exists=False,
        upload_422_once=True,
    )

    promote_latest_pointer_idempotent(backend, pointer())

    assert backend.upload_attempts == 2
    assert backend.deleted_assets == 1
    assert json.loads(backend.stored_bytes) == pointer()


def test_stored_pointer_mismatch_fails_closed():
    backend = FakeGitHubReleasesBackend(asset_exists=False)

    def corrupt_download(asset_url, dest_path):
        Path(dest_path).write_text('{"snapshot_id":"wrong"}', encoding="utf-8")

    backend._download_asset = corrupt_download

    with pytest.raises(StorageBackendError, match="payload verification failed"):
        promote_latest_pointer_idempotent(backend, pointer())
