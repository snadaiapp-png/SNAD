"""
SANAD — NVD Bulk Feed Release Tests
====================================
EXEC-PROMPT-010R12L post-PR#166 regression tests.

Specifically guards against the Path-vs-basename bug that caused the
final archive upload to silently produce an asset whose name did not
match the verification lookup key, leading to:

    SnapshotError: Asset /tmp/nvd-feed-work/snad-nvd-feed-<id>.tar.zst
    not found after upload

The root cause was that ``create_feed_archive`` returns a ``Path``
object (the full archive path, possibly with a .tar.gz suffix if zstd
was unavailable), but ``publish_final_feed_release`` assigned that
return value directly to ``archive_filename`` and then passed it as
the ``asset_name`` argument to ``_upload_asset``. The upload endpoint
received a name containing '/' separators, which GitHub normalizes;
the subsequent verification check (``if name not in assets``) compared
a ``Path`` against string keys and always failed.
"""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_bulk_feed_checkpoint import Checkpoint, FeedRecord
from scripts.security.nvd_bulk_feed_release import (
    build_feed_manifest,
    publish_final_feed_release,
)
from scripts.security.nvd_snapshot_store import SnapshotError


# ---------- Mock backend ----------

class _MockBackend:
    """In-memory mock of GitHubReleasesBackend for testing."""

    def __init__(self):
        self.token = "fake-token"
        self.repo = "owner/repo"
        self._api_base = "https://api.github.com/repos/owner/repo"
        self.uploads: list[dict] = []          # list of {name, size, sha}
        self._next_release_id = 1000
        self._release_assets: dict[int, dict[str, dict]] = {}
        self._release_bodies: dict[int, dict] = {}

    def _request(self, method, path, body=None, expected=(200, 201, 204)):
        if method == "POST" and path == "releases":
            rid = self._next_release_id
            self._next_release_id += 1
            self._release_assets[rid] = {}
            self._release_bodies[rid] = body or {}
            return {
                "id": rid,
                "tag_name": (body or {}).get("tag_name", f"tag-{rid}"),
                "upload_url": f"https://uploads.github.com/repos/owner/repo/releases/{rid}/assets{{?name,label}}",
                "assets": [],
            }
        if method == "GET" and path.startswith("releases/"):
            rid = int(path.split("/")[1])
            return {
                "id": rid,
                "assets": [
                    {"name": n, "url": f"https://api.github.com/.../assets/{i+1}", "id": i+1}
                    for i, (n, _) in enumerate(self._release_assets[rid].items())
                ],
            }
        if method == "PATCH" and path.startswith("releases/"):
            rid = int(path.split("/")[1])
            self._release_bodies[rid].update(body or {})
            return {}
        if method == "DELETE" and path.startswith("releases/"):
            return {}
        return {}

    def _upload_asset(self, upload_url, name, path):
        # CRITICAL INVARIANT: name must be a string basename, never a Path.
        assert isinstance(name, str), (
            f"asset name must be str, got {type(name).__name__}: {name!r}"
        )
        assert "/" not in name, (
            f"asset name must not contain '/'; got {name!r} "
            "(this is the Path-vs-basename regression)"
        )
        # Parse release_id from upload_url
        rid = int(upload_url.split("/releases/")[1].split("/")[0])
        data = Path(path).read_bytes()
        self._release_assets[rid][name] = {
            "name": name,
            "size": len(data),
            "data": data,
        }
        self.uploads.append({"name": name, "size": len(data)})
        return {"id": len(self.uploads), "name": name, "size": len(data)}

    def _download_asset(self, url, dest_path):
        # Find the asset by URL (we encode the asset index in the URL)
        # For simplicity, just copy from our in-memory store.
        # The URL looks like: https://api.github.com/.../assets/<i+1>
        idx = int(url.rsplit("/", 1)[-1]) - 1
        # Find the right release by matching index across all releases
        for rid, assets in self._release_assets.items():
            names = list(assets.keys())
            if idx < len(names):
                Path(dest_path).write_bytes(assets[names[idx]]["data"])
                return
        Path(dest_path).write_bytes(b"")


# ---------- Fixtures ----------

@pytest.fixture
def fake_checkpoint():
    return Checkpoint(
        schema_version=1,
        generation_id="UNITTEST123",
        created_at="2026-06-27T12:00:00Z",
        updated_at="2026-06-27T12:00:00Z",
        publisher_sha="abc123",
        publisher_run_ids=["42"],
        required_feeds=["nvdcve-2024.json.gz", "nvdcve-2025.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz", "nvdcve-2025.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={
            "nvdcve-2024.json.gz": FeedRecord(
                name="nvdcve-2024.json.gz",
                meta_name="nvdcve-2.0-2024.meta",
                source_url="https://nvd.nist.gov/.../nvdcve-2.0-2024.json.gz",
                meta_url="https://nvd.nist.gov/.../nvdcve-2.0-2024.meta",
                last_modified="2026-06-27T00:00:00Z",
                compressed_size=1024,
                uncompressed_size=4096,
                expected_sha256="a" * 64,
                actual_sha256="a" * 64,
                asset_id=1,
                verified_at="2026-06-27T12:00:00Z",
                status="VERIFIED",
            ),
            "nvdcve-2025.json.gz": FeedRecord(
                name="nvdcve-2025.json.gz",
                status="VERIFIED",
                actual_sha256="b" * 64,
                compressed_size=2048,
            ),
        },
    )


@pytest.fixture
def fake_feed_dir(tmp_path):
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    (feed_dir / "nvdcve-2024.json.gz").write_bytes(b"fake 2024 feed data")
    (feed_dir / "nvdcve-2025.json.gz").write_bytes(b"fake 2025 feed data")
    return feed_dir


# ---------- Tests ----------

def test_publish_final_feed_release_uses_basename_not_path(
    fake_checkpoint, fake_feed_dir, tmp_path, capsys
):
    """REGRESSION: archive_filename must be a string basename, not a Path.

    Before the fix, ``create_feed_archive`` returned a Path object which
    was assigned to ``archive_filename`` and passed to ``_upload_asset``.
    The upload URL became ``?name=/tmp/.../snad-nvd-feed-X.tar.zst`` (with
    slashes), which GitHub normalizes — and the verification step
    ``if name not in assets`` compared Path to str, always failing.
    """
    work_dir = tmp_path / "work"
    work_dir.mkdir()
    backend = _MockBackend()

    result = publish_final_feed_release(
        backend=backend,
        checkpoint=fake_checkpoint,
        feed_dir=fake_feed_dir,
        work_dir=work_dir,
        seed_release_id=999,
    )

    # The archive asset name should be a basename, not a path
    archive_uploads = [u for u in backend.uploads
                       if u["name"].startswith("snad-nvd-feed-")]
    assert len(archive_uploads) == 1, f"expected 1 archive upload, got {archive_uploads}"
    archive_name = archive_uploads[0]["name"]
    assert "/" not in archive_name, (
        f"archive name must not contain '/'; got {archive_name!r}"
    )
    assert archive_name.startswith("snad-nvd-feed-UNITTEST123"), (
        f"unexpected archive name: {archive_name!r}"
    )
    # Accept either .tar.zst (zstd available) or .tar.gz (fallback)
    assert archive_name.endswith((".tar.zst", ".tar.gz"))

    # manifest.json and SHA256SUMS should also be uploaded
    names = [u["name"] for u in backend.uploads]
    assert "manifest.json" in names
    assert "SHA256SUMS" in names

    # Result should have the correct (string) filename
    assert isinstance(result["archive_filename"], str)
    assert result["archive_filename"] == archive_name


def test_publish_final_feed_release_manifest_is_json_serializable(
    fake_checkpoint, fake_feed_dir, tmp_path
):
    """REGRESSION for PR #166: manifest must be JSON-serializable.

    Feed records may contain Path objects or other non-serializable
    types; build_feed_manifest must produce a dict that json.dumps can
    serialize. The default=str fallback must be present.
    """
    work_dir = tmp_path / "work"
    work_dir.mkdir()
    backend = _MockBackend()

    publish_final_feed_release(
        backend=backend,
        checkpoint=fake_checkpoint,
        feed_dir=fake_feed_dir,
        work_dir=work_dir,
        seed_release_id=999,
    )

    # Find the manifest upload and verify it's valid JSON
    manifest_uploads = [u for u in backend.uploads if u["name"] == "manifest.json"]
    assert len(manifest_uploads) == 1
    # Re-read from the in-memory store
    rid = backend._next_release_id - 1  # last release created
    manifest_data = backend._release_assets[rid]["manifest.json"]["data"]
    manifest = json.loads(manifest_data.decode("utf-8"))

    # Check critical fields
    assert manifest["schema_version"] == 2
    assert manifest["feed_id"] == "UNITTEST123"
    assert manifest["source_type"] == "NVD_JSON_2_BULK_FEEDS"
    assert manifest["direct_nvd_api_access"] is False
    assert manifest["nvd_api_key_used"] is False
    assert manifest["resumable_download"] is True
    assert manifest["verified_feed_count"] == 2
    assert "feed_records" in manifest
    assert len(manifest["feed_records"]) == 2


def test_build_feed_manifest_serializes_feed_records(fake_checkpoint):
    """build_feed_manifest output must be JSON-serializable without raising."""
    manifest = build_feed_manifest(
        feed_id="TEST",
        release_tag="nvd-feed-TEST",
        checkpoint=fake_checkpoint,
        archive_filename="archive.tar.zst",
        archive_sha256="a" * 64,
        archive_size_bytes=1024,
    )
    # This must not raise
    serialized = json.dumps(manifest, indent=2, default=str)
    assert isinstance(serialized, str)
    # Round-trip
    parsed = json.loads(serialized)
    assert parsed["feed_id"] == "TEST"
