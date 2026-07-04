#!/usr/bin/env python3
"""Idempotency hardening for the GitHub Releases NVD snapshot backend.

The NVD database and immutable snapshot may be valid while promotion of the
``nvd-snapshot-latest`` pointer races with a retry or another publisher. This
module makes the mutable pointer update retry-safe without weakening immutable
snapshot semantics.
"""
from __future__ import annotations

import json
import tempfile
import time
from pathlib import Path
from typing import Any

from scripts.security.nvd_snapshot_store import (
    GitHubReleasesBackend,
    SnapshotNotFoundError,
    StorageBackendError,
)


def _is_already_exists(error: BaseException) -> bool:
    message = str(error).lower()
    return "already_exists" in message or ("http 422" in message and "release" in message)


def _get_or_create_latest_release(backend: GitHubReleasesBackend) -> dict[str, Any]:
    """Return the singleton latest-pointer release, tolerating create races."""
    try:
        return backend._request("GET", f"releases/tags/{backend.LATEST_TAG}")
    except SnapshotNotFoundError:
        try:
            return backend._request(
                "POST",
                "releases",
                body={
                    "tag_name": backend.LATEST_TAG,
                    "name": "NVD Snapshot Latest Pointer",
                    "body": "Auto-updated pointer to the latest verified NVD snapshot",
                    "draft": False,
                    "prerelease": False,
                },
            )
        except StorageBackendError as error:
            if not _is_already_exists(error):
                raise

    # Another publisher created the release between GET and POST. GitHub is
    # normally read-after-write consistent, but retry briefly to avoid turning
    # a harmless create race into a failed publisher run.
    for attempt in range(5):
        try:
            return backend._request("GET", f"releases/tags/{backend.LATEST_TAG}")
        except SnapshotNotFoundError:
            if attempt == 4:
                raise
            time.sleep(1)
    raise AssertionError("unreachable")


def _remove_named_assets(
    backend: GitHubReleasesBackend,
    release: dict[str, Any],
    asset_name: str,
) -> dict[str, Any]:
    """Delete every matching asset; a concurrent 404 is already-success."""
    release_id = release.get("id")
    if not release_id:
        raise StorageBackendError("latest-pointer release response has no id")

    for asset in release.get("assets", []):
        if asset.get("name") != asset_name:
            continue
        asset_id = asset.get("id")
        if not asset_id:
            continue
        try:
            backend._request("DELETE", f"releases/{release_id}/assets/{asset_id}")
        except SnapshotNotFoundError:
            # A retry/concurrent publisher already removed the asset. The
            # desired postcondition is therefore satisfied.
            pass

    return backend._request("GET", f"releases/{release_id}")


def promote_latest_pointer_idempotent(
    backend: GitHubReleasesBackend,
    pointer: dict[str, Any],
) -> None:
    """Atomically replace ``latest.json`` and verify the stored payload.

    Handles both observed retry races:
    * DELETE returns 404 because another run already removed the asset.
    * POST release/upload returns 422 because the singleton resource exists.
    """
    release = _get_or_create_latest_release(backend)
    release = _remove_named_assets(backend, release, "latest.json")
    release_id = release.get("id")
    if not release_id:
        raise StorageBackendError("latest-pointer release response has no id")

    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as handle:
        handle.write(json.dumps(pointer, indent=2, sort_keys=True).encode("utf-8"))
        pointer_path = Path(handle.name)

    try:
        for attempt in range(2):
            try:
                upload_url = release.get("upload_url", "")
                if not upload_url:
                    raise StorageBackendError("latest-pointer release has no upload_url")
                backend._upload_asset(upload_url, "latest.json", pointer_path)
                break
            except StorageBackendError as error:
                if attempt != 0 or not _is_already_exists(error):
                    raise
                # GitHub still sees a duplicate asset. Re-read, remove the
                # duplicate idempotently, and retry exactly once.
                release = backend._request("GET", f"releases/{release_id}")
                release = _remove_named_assets(backend, release, "latest.json")
        else:
            raise StorageBackendError("latest.json upload retry exhausted")

        verified_release = backend._request("GET", f"releases/{release_id}")
        matching_assets = [
            asset for asset in verified_release.get("assets", [])
            if asset.get("name") == "latest.json"
        ]
        if len(matching_assets) != 1:
            raise StorageBackendError(
                f"expected exactly one latest.json asset, found {len(matching_assets)}"
            )

        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as handle:
            verify_path = Path(handle.name)
        try:
            backend._download_asset(matching_assets[0]["url"], verify_path)
            stored_pointer = json.loads(verify_path.read_text(encoding="utf-8"))
            if stored_pointer != pointer:
                raise StorageBackendError("latest.json payload verification failed")
        finally:
            verify_path.unlink(missing_ok=True)
    finally:
        pointer_path.unlink(missing_ok=True)


def install_github_releases_idempotency_patch() -> None:
    """Install the patch once for publisher and feed-backend instances."""
    if getattr(GitHubReleasesBackend, "_sanad_idempotency_patch", False):
        return
    GitHubReleasesBackend.promote_latest_pointer = promote_latest_pointer_idempotent
    GitHubReleasesBackend._sanad_idempotency_patch = True
