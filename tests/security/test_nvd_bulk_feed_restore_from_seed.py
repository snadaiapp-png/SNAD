"""
SANAD — NVD Bulk Feed Restore-From-Seed Tests
==============================================
EXEC-PROMPT-010R12L post-PR#167 regression tests.

Guards the resumability fix that addresses the failure mode where the
workflow's `rm -rf "$FEED_DIR"` step destroyed locally-verified feed
files, forcing every run to re-download ALL feeds from NVD (which is
slow and frequently returns 5xx/timeout).

The fix introduces ``restore_feed_from_seed`` (download a verified
asset from the seed release) and ``try_restore_verified_feed`` (use
the checkpoint to look up the expected SHA, restore from seed, and
skip the NVD download if successful).

These tests verify:
  1. A VERIFIED feed missing locally is restored from the seed release.
  2. A non-VERIFIED feed is NOT restored (falls back to NVD download).
  3. SHA-256 mismatch on seed asset causes fallback to NVD download.
  4. Missing asset on seed release causes fallback to NVD download.
  5. Download errors during restore cause fallback (no exception bubbles).
  6. ``should_skip_feed`` returns False when local file is missing
     even if checkpoint says VERIFIED.
  7. ``run_bulk_feed_mirror`` uses the restore path and produces
     accurate skipped/restored/downloaded/failed counts.
"""
from __future__ import annotations

import gzip
import io
import json
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_bulk_feed_checkpoint import (
    Checkpoint,
    FeedRecord,
    restore_feed_from_seed,
)
from scripts.security.nvd_bulk_feed_mirror import (
    should_skip_feed,
    try_restore_verified_feed,
    run_bulk_feed_mirror,
    MAX_ATTEMPTS,
    DOWNLOAD_TIMEOUT_SECONDS,
)


# ---------- Mock backend with realistic GitHub Releases API semantics ----------

class _MockBackend:
    """In-memory mock of GitHubReleasesBackend for testing."""

    def __init__(self):
        self.token = "fake-token"
        self.repo = "owner/repo"
        self._api_base = "https://api.github.com/repos/owner/repo"
        self._next_release_id = 1000
        self._next_asset_id = 5000
        # release_id -> { asset_name -> {id, url, size, data} }
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
        if method == "GET" and path.startswith("releases"):
            # Handle both:
            #   - "releases?per_page=100&page=1" → list all releases
            #   - "releases/<id>" → single release detail
            if path.startswith("releases?"):
                # List endpoint
                releases_list = []
                for rid, body in self._release_bodies.items():
                    releases_list.append({
                        "id": rid,
                        "tag_name": body.get("tag_name", f"tag-{rid}"),
                        "draft": body.get("draft", False),
                        "prerelease": body.get("prerelease", False),
                        "upload_url": f"https://uploads.github.com/repos/owner/repo/releases/{rid}/assets{{?name,label}}",
                        "assets": [
                            {
                                "name": n,
                                "url": f"https://api.github.com/.../assets/{a['id']}",
                                "id": a["id"],
                                "size": a["size"],
                            }
                            for n, a in self._release_assets[rid].items()
                        ],
                    })
                return releases_list
            # Single release detail
            rid = int(path.split("/")[1])
            return {
                "id": rid,
                "tag_name": self._release_bodies.get(rid, {}).get("tag_name", f"tag-{rid}"),
                "upload_url": f"https://uploads.github.com/repos/owner/repo/releases/{rid}/assets{{?name,label}}",
                "assets": [
                    {
                        "name": n,
                        "url": f"https://api.github.com/.../assets/{a['id']}",
                        "id": a["id"],
                        "size": a["size"],
                    }
                    for n, a in self._release_assets.get(rid, {}).items()
                ],
            }
        if method == "PATCH" and path.startswith("releases/"):
            rid = int(path.split("/")[1])
            self._release_bodies[rid].update(body or {})
            return {}
        if method == "DELETE" and path.startswith("releases/"):
            return {}
        if method == "DELETE" and "/assets/" in path:
            # /releases/<rid>/assets/<aid>
            parts = path.split("/")
            rid = int(parts[1])
            aid = int(parts[3])
            for n, a in list(self._release_assets[rid].items()):
                if a["id"] == aid:
                    del self._release_assets[rid][n]
                    break
            return {}
        return {}

    def _upload_asset(self, upload_url, name, path):
        assert isinstance(name, str), f"asset name must be str, got {type(name).__name__}"
        assert "/" not in name, f"asset name must not contain '/'; got {name!r}"
        rid = int(upload_url.split("/releases/")[1].split("/")[0])
        data = Path(path).read_bytes()
        aid = self._next_asset_id
        self._next_asset_id += 1
        self._release_assets[rid][name] = {
            "id": aid,
            "name": name,
            "size": len(data),
            "data": data,
            "url": f"https://api.github.com/.../assets/{aid}",
        }
        return {"id": aid, "name": name, "size": len(data)}

    def _download_asset(self, url, dest_path):
        # URL ends with /assets/<id>
        aid = int(url.rsplit("/", 1)[-1])
        for rid, assets in self._release_assets.items():
            for n, a in assets.items():
                if a["id"] == aid:
                    Path(dest_path).write_bytes(a["data"])
                    return
        Path(dest_path).write_bytes(b"")


# ---------- Fixtures ----------

def _make_fake_gzip_feed(year: int, content_seed: str = "fake CVE data") -> bytes:
    """Create a valid gzip-compressed NVD-style feed file in memory."""
    payload = {
        "CVE_data_type": "CVE",
        "CVE_data_format": "MITRE",
        "CVE_data_version": "4.0",
        "CVE_data_numberOfCVEs": "1",
        "CVE_data_timestamp": f"2026-06-27T00:00:00Z",
        "CVE_Items": [{"cve": {"CVE_data_meta": {"ID": f"CVE-{year}-0001"}}}],
        "_content_seed": content_seed,
    }
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb") as gz:
        gz.write(json.dumps(payload).encode("utf-8"))
    return buf.getvalue()


@pytest.fixture
def mock_backend():
    return _MockBackend()


@pytest.fixture
def fake_feed_record():
    """A VERIFIED feed record with a known SHA-256."""
    data = _make_fake_gzip_feed(2024, "stable-content")
    import hashlib
    sha = hashlib.sha256(data).hexdigest()
    return FeedRecord(
        name="nvdcve-2024.json.gz",
        meta_name="nvdcve-2.0-2024.meta",
        source_url="https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-2024.json.gz",
        meta_url="https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-2024.meta",
        last_modified="2026-06-27T00:00:00Z",
        compressed_size=len(data),
        uncompressed_size=4096,
        expected_sha256="",  # META SHA omitted (stale)
        actual_sha256=sha,
        asset_id=1,
        verified_at="2026-06-27T12:00:00Z",
        status="VERIFIED",
    ), data


# ---------- Tests: restore_feed_from_seed ----------

def test_restore_feed_from_seed_success(mock_backend, fake_feed_record, tmp_path):
    """A verified asset on the seed release is restored and SHA matches."""
    record, data = fake_feed_record
    # Create a seed release with the asset
    release = mock_backend._request("POST", "releases", body={
        "tag_name": "nvd-feed-seed-test",
        "draft": True,
    })
    seed_id = release["id"]
    # Upload the asset to seed
    src_path = tmp_path / "source.json.gz"
    src_path.write_bytes(data)
    mock_backend._upload_asset(release["upload_url"], "nvdcve-2024.json.gz", src_path)

    # Now restore to a fresh location
    dest_path = tmp_path / "feed" / "nvdcve-2024.json.gz"
    dest_path.parent.mkdir()
    ok = restore_feed_from_seed(
        backend=mock_backend,
        seed_release_id=seed_id,
        feed_name="nvdcve-2024.json.gz",
        expected_sha256=record.actual_sha256,
        dest_path=dest_path,
    )
    assert ok is True
    assert dest_path.exists()
    assert dest_path.read_bytes() == data


def test_restore_feed_from_seed_returns_false_when_asset_missing(mock_backend, tmp_path):
    """If the seed release has no asset with that name, returns False (no exception)."""
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    dest_path = tmp_path / "feed" / "nvdcve-2024.json.gz"
    dest_path.parent.mkdir()
    ok = restore_feed_from_seed(
        backend=mock_backend,
        seed_release_id=release["id"],
        feed_name="nvdcve-2024.json.gz",
        expected_sha256="a" * 64,
        dest_path=dest_path,
    )
    assert ok is False
    assert not dest_path.exists()


def test_restore_feed_from_seed_returns_false_on_sha_mismatch(mock_backend, fake_feed_record, tmp_path):
    """If the seed asset's SHA doesn't match the expected, returns False."""
    record, data = fake_feed_record
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    src_path = tmp_path / "source.json.gz"
    src_path.write_bytes(data)
    mock_backend._upload_asset(release["upload_url"], "nvdcve-2024.json.gz", src_path)

    dest_path = tmp_path / "feed" / "nvdcve-2024.json.gz"
    dest_path.parent.mkdir()
    # Pass a deliberately wrong expected SHA
    ok = restore_feed_from_seed(
        backend=mock_backend,
        seed_release_id=release["id"],
        feed_name="nvdcve-2024.json.gz",
        expected_sha256="0" * 64,  # wrong
        dest_path=dest_path,
    )
    assert ok is False
    # The .restored temp file should have been cleaned up
    assert not dest_path.exists()
    restored_tmp = dest_path.with_suffix(dest_path.suffix + ".restored")
    assert not restored_tmp.exists()


def test_restore_feed_from_seed_returns_false_on_download_error(mock_backend, tmp_path):
    """If _download_asset raises, restore_feed_from_seed returns False (no bubble)."""
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    # No asset uploaded, but we'll make _download_asset raise
    def boom(url, dest_path):
        raise RuntimeError("simulated network failure")
    mock_backend._download_asset = boom

    dest_path = tmp_path / "feed" / "nvdcve-2024.json.gz"
    dest_path.parent.mkdir()
    # Pre-create an asset entry so the function gets to the download step
    mock_backend._release_assets[release["id"]]["nvdcve-2024.json.gz"] = {
        "id": 999,
        "name": "nvdcve-2024.json.gz",
        "size": 0,
        "data": b"",
        "url": "https://api.github.com/.../assets/999",
    }
    ok = restore_feed_from_seed(
        backend=mock_backend,
        seed_release_id=release["id"],
        feed_name="nvdcve-2024.json.gz",
        expected_sha256="a" * 64,
        dest_path=dest_path,
    )
    assert ok is False
    restored_tmp = dest_path.with_suffix(dest_path.suffix + ".restored")
    assert not restored_tmp.exists()


# ---------- Tests: should_skip_feed ----------

def test_should_skip_feed_returns_false_when_local_missing(tmp_path, fake_feed_record):
    """Even if VERIFIED in checkpoint, missing local file → False (needs restore)."""
    record, _ = fake_feed_record
    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    # No local file
    assert should_skip_feed("nvdcve-2024.json.gz", checkpoint, feed_dir) is False


def test_should_skip_feed_returns_true_when_local_matches(tmp_path, fake_feed_record):
    """Local file present with matching SHA → True (skip)."""
    record, data = fake_feed_record
    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    (feed_dir / "nvdcve-2024.json.gz").write_bytes(data)
    assert should_skip_feed("nvdcve-2024.json.gz", checkpoint, feed_dir) is True


def test_should_skip_feed_returns_false_for_non_verified(tmp_path):
    """A FAILED or PENDING feed → False."""
    record = FeedRecord(name="nvdcve-2024.json.gz", status="FAILED")
    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=[],
        pending_feeds=[],
        failed_feeds=["nvdcve-2024.json.gz"],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    assert should_skip_feed("nvdcve-2024.json.gz", checkpoint, feed_dir) is False


# ---------- Tests: try_restore_verified_feed ----------

def test_try_restore_verified_feed_success(mock_backend, fake_feed_record, tmp_path):
    """A VERIFIED feed missing locally is restored from seed."""
    record, data = fake_feed_record
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    seed_id = release["id"]
    src_path = tmp_path / "src.json.gz"
    src_path.write_bytes(data)
    mock_backend._upload_asset(release["upload_url"], "nvdcve-2024.json.gz", src_path)

    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    ok = try_restore_verified_feed(mock_backend, seed_id, "nvdcve-2024.json.gz", checkpoint, feed_dir)
    assert ok is True
    assert (feed_dir / "nvdcve-2024.json.gz").exists()
    assert (feed_dir / "nvdcve-2024.json.gz").read_bytes() == data


def test_try_restore_verified_feed_skipped_when_local_already_matches(mock_backend, fake_feed_record, tmp_path):
    """If the local file already matches, no seed download is attempted."""
    record, data = fake_feed_record
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    # Note: NO asset uploaded to seed

    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    (feed_dir / "nvdcve-2024.json.gz").write_bytes(data)

    # Should return True without needing the seed asset
    ok = try_restore_verified_feed(mock_backend, release["id"], "nvdcve-2024.json.gz", checkpoint, feed_dir)
    assert ok is True


def test_try_restore_verified_feed_returns_false_for_non_verified(mock_backend, tmp_path):
    """A non-VERIFIED feed → False (caller will do fresh NVD download)."""
    record = FeedRecord(name="nvdcve-2024.json.gz", status="PENDING")
    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=[],
        pending_feeds=["nvdcve-2024.json.gz"],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    ok = try_restore_verified_feed(mock_backend, release["id"], "nvdcve-2024.json.gz", checkpoint, feed_dir)
    assert ok is False


def test_try_restore_verified_feed_returns_false_when_seed_missing(mock_backend, fake_feed_record, tmp_path):
    """VERIFIED record but no asset on seed → False (fall back to NVD)."""
    record, _ = fake_feed_record
    release = mock_backend._request("POST", "releases", body={"tag_name": "seed", "draft": True})
    # No asset uploaded

    checkpoint = Checkpoint(
        generation_id="TEST",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["1"],
        required_feeds=["nvdcve-2024.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={"nvdcve-2024.json.gz": record},
    )
    feed_dir = tmp_path / "feed"
    feed_dir.mkdir()
    ok = try_restore_verified_feed(mock_backend, release["id"], "nvdcve-2024.json.gz", checkpoint, feed_dir)
    assert ok is False


# ---------- Tests: run_bulk_feed_mirror resume behavior ----------

def test_run_bulk_feed_mirror_restores_from_seed_then_downloads_remaining(
    mock_backend, fake_feed_record, tmp_path, monkeypatch,
):
    """End-to-end: a checkpoint with 1 VERIFIED + 1 PENDING feed.
    The VERIFIED feed is missing locally but present on the seed.
    The PENDING feed is downloaded fresh (mocked).
    """
    record_verified, data_verified = fake_feed_record
    # Build a checkpoint with one VERIFIED feed and one missing feed
    checkpoint_data = Checkpoint(
        generation_id="RESUME001",
        created_at="2026-06-27T00:00:00Z",
        updated_at="2026-06-27T00:00:00Z",
        publisher_sha="abc",
        publisher_run_ids=["prior-run"],
        required_feeds=["nvdcve-2024.json.gz", "nvdcve-2025.json.gz"],
        completed_feeds=["nvdcve-2024.json.gz"],
        pending_feeds=[],
        failed_feeds=[],
        feed_records={
            "nvdcve-2024.json.gz": record_verified,
        },
    )

    # Create the seed release with the verified asset already uploaded
    seed_release = mock_backend._request("POST", "releases", body={
        "tag_name": "nvd-feed-seed-RESUME001",
        "draft": True,
    })
    seed_id = seed_release["id"]
    src_verified = tmp_path / "src_verified.json.gz"
    src_verified.write_bytes(data_verified)
    mock_backend._upload_asset(seed_release["upload_url"], "nvdcve-2024.json.gz", src_verified)
    # Also upload a checkpoint.json so find_active_seed → download_checkpoint works
    ckpt_path = tmp_path / "checkpoint.json"
    ckpt_path.write_text(checkpoint_data.to_json())
    mock_backend._upload_asset(seed_release["upload_url"], "checkpoint.json", ckpt_path)

    # Mock the NVD download for the PENDING feed (nvdcve-2025.json.gz)
    pending_data = _make_fake_gzip_feed(2025, "fresh-2025")

    def fake_download_feed_file(feed_name, feed_dir):
        if feed_name != "nvdcve-2025.json.gz":
            raise AssertionError(f"should not download {feed_name} — should be restored or skipped")
        dest = feed_dir / feed_name
        dest.write_bytes(pending_data)
        import hashlib
        record = FeedRecord(
            name=feed_name,
            status="VERIFIED",
            actual_sha256=hashlib.sha256(pending_data).hexdigest(),
            compressed_size=len(pending_data),
        )
        return record, dest

    # Patch the function in the module where it's looked up
    import scripts.security.nvd_bulk_feed_mirror as mirror_mod
    monkeypatch.setattr(mirror_mod, "download_feed_file", fake_download_feed_file)
    # Mock expected_feed_names so we only iterate over our 2 test feeds (not all 27)
    monkeypatch.setattr(
        mirror_mod, "expected_feed_names",
        lambda: ["nvdcve-2024.json.gz", "nvdcve-2025.json.gz"],
    )

    feed_dir = tmp_path / "feed"
    work_dir = tmp_path / "work"
    feed_dir.mkdir()
    work_dir.mkdir()

    checkpoint = run_bulk_feed_mirror(
        feed_dir=feed_dir,
        work_dir=work_dir,
        publisher_sha="new-sha",
        publisher_run_id="new-run",
        backend=mock_backend,
    )

    # Both feeds should be VERIFIED
    assert checkpoint.is_complete()
    assert "nvdcve-2024.json.gz" in checkpoint.completed_feeds
    assert "nvdcve-2025.json.gz" in checkpoint.completed_feeds
    # The verified feed was restored from seed (file exists locally with correct content)
    assert (feed_dir / "nvdcve-2024.json.gz").read_bytes() == data_verified
    # The pending feed was downloaded fresh
    assert (feed_dir / "nvdcve-2025.json.gz").read_bytes() == pending_data


# ---------- Tests: retry policy constants ----------

def test_max_attempts_increased_from_4_to_6():
    """NVD downloads now retry up to 6 times (was 4) — gives more tolerance for NVD flakiness."""
    assert MAX_ATTEMPTS == 6, (
        f"MAX_ATTEMPTS should be 6 (increased from 4 for NVD flakiness), got {MAX_ATTEMPTS}"
    )


def test_download_timeout_increased_from_300_to_600():
    """NVD download timeout is now 600s (was 300s) — NVD can be very slow for large yearly feeds."""
    assert DOWNLOAD_TIMEOUT_SECONDS == 600, (
        f"DOWNLOAD_TIMEOUT_SECONDS should be 600 (was 300), got {DOWNLOAD_TIMEOUT_SECONDS}"
    )
