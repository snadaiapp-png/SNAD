#!/usr/bin/env python3
"""Run the NVD snapshot publisher with a corrected GitHub Releases pointer update.

The legacy backend grouped release lookup and asset deletion in one try block and
used a non-existent deletion endpoint. A 404 while deleting latest.json was
therefore misclassified as a missing release, followed by a duplicate-tag 422.
This compatibility runner installs the corrected, regression-tested method
before delegating to the publisher CLI.
"""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security import nvd_snapshot_store as store  # noqa: E402


def promote_latest_pointer(self: store.GitHubReleasesBackend, pointer: dict) -> None:
    """Atomically replace latest.json without recreating an existing release."""
    try:
        release = self._request("GET", f"releases/tags/{self.LATEST_TAG}")
    except store.SnapshotNotFoundError:
        release = self._request(
            "POST",
            "releases",
            body={
                "tag_name": self.LATEST_TAG,
                "name": "NVD Snapshot Latest Pointer",
                "body": "Auto-updated pointer to the latest verified NVD snapshot",
                "draft": False,
                "prerelease": False,
            },
        )

    release_id = release.get("id")
    if not release_id:
        raise store.StorageBackendError("latest-pointer release has no id")

    for asset in release.get("assets", []):
        if asset.get("name") != "latest.json":
            continue
        asset_id = asset.get("id")
        if not asset_id:
            raise store.StorageBackendError("latest.json asset has no id")
        try:
            self._request("DELETE", f"releases/assets/{asset_id}")
        except store.SnapshotNotFoundError:
            pass

    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as handle:
        handle.write(json.dumps(pointer, indent=2, sort_keys=True).encode("utf-8"))
        pointer_path = Path(handle.name)
    try:
        upload_url = release.get("upload_url", "")
        if not upload_url:
            raise store.StorageBackendError("latest-pointer release has no upload_url")
        self._upload_asset(upload_url, "latest.json", pointer_path)
    finally:
        pointer_path.unlink(missing_ok=True)

    refreshed = self._request("GET", f"releases/{release_id}")
    latest_assets = [
        asset for asset in refreshed.get("assets", []) if asset.get("name") == "latest.json"
    ]
    if len(latest_assets) != 1:
        raise store.StorageBackendError(
            f"expected exactly one latest.json asset after promotion; found {len(latest_assets)}"
        )


store.GitHubReleasesBackend.promote_latest_pointer = promote_latest_pointer

from scripts.security.publish_nvd_snapshot import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
