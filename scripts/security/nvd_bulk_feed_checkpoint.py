#!/usr/bin/env python3
"""
SANAD — NVD Bulk Feed Checkpoint Manager
=========================================
EXEC-PROMPT-010R12L Section 12/13 — durable checkpoint for resumable
NVD bulk feed downloads using GitHub Draft Releases as persistent storage.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_snapshot_store import (
    GitHubReleasesBackend,
    SnapshotError,
    SnapshotNotFoundError,
    sha256_file,
    utc_now_iso,
)

CHECKPOINT_SCHEMA_VERSION = 1
SEED_TAG_PREFIX = "nvd-feed-seed-"


@dataclass
class FeedRecord:
    """Record of a single feed file in the checkpoint."""
    name: str = ""
    meta_name: str = ""
    source_url: str = ""
    meta_url: str = ""
    last_modified: str = ""
    compressed_size: int = 0
    uncompressed_size: int = 0
    expected_sha256: str = ""
    actual_sha256: str = ""
    asset_id: int = 0
    verified_at: str = ""
    status: str = "PENDING"  # PENDING, VERIFIED, FAILED

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "meta_name": self.meta_name,
            "source_url": self.source_url,
            "meta_url": self.meta_url,
            "last_modified": self.last_modified,
            "compressed_size": self.compressed_size,
            "uncompressed_size": self.uncompressed_size,
            "expected_sha256": self.expected_sha256,
            "actual_sha256": self.actual_sha256,
            "asset_id": self.asset_id,
            "verified_at": self.verified_at,
            "status": self.status,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "FeedRecord":
        return cls(
            name=d.get("name", ""),
            meta_name=d.get("meta_name", ""),
            source_url=d.get("source_url", ""),
            meta_url=d.get("meta_url", ""),
            last_modified=d.get("last_modified", ""),
            compressed_size=d.get("compressed_size", 0),
            uncompressed_size=d.get("uncompressed_size", 0),
            expected_sha256=d.get("expected_sha256", ""),
            actual_sha256=d.get("actual_sha256", ""),
            asset_id=d.get("asset_id", 0),
            verified_at=d.get("verified_at", ""),
            status=d.get("status", "PENDING"),
        )


@dataclass
class Checkpoint:
    """Durable checkpoint for resumable NVD bulk feed downloads."""
    schema_version: int = CHECKPOINT_SCHEMA_VERSION
    generation_id: str = ""
    created_at: str = ""
    updated_at: str = ""
    publisher_sha: str = ""
    publisher_run_ids: list[str] = field(default_factory=list)
    required_feeds: list[str] = field(default_factory=list)
    completed_feeds: list[str] = field(default_factory=list)
    pending_feeds: list[str] = field(default_factory=list)
    failed_feeds: list[str] = field(default_factory=list)
    feed_records: dict[str, FeedRecord] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "schema_version": self.schema_version,
            "generation_id": self.generation_id,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "publisher_sha": self.publisher_sha,
            "publisher_run_ids": self.publisher_run_ids,
            "required_feeds": self.required_feeds,
            "completed_feeds": self.completed_feeds,
            "pending_feeds": self.pending_feeds,
            "failed_feeds": self.failed_feeds,
            "feed_records": {k: v.to_dict() for k, v in self.feed_records.items()},
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2)

    @classmethod
    def from_dict(cls, d: dict) -> "Checkpoint":
        records = {}
        for k, v in d.get("feed_records", {}).items():
            records[k] = FeedRecord.from_dict(v)
        return cls(
            schema_version=d.get("schema_version", CHECKPOINT_SCHEMA_VERSION),
            generation_id=d.get("generation_id", ""),
            created_at=d.get("created_at", ""),
            updated_at=d.get("updated_at", ""),
            publisher_sha=d.get("publisher_sha", ""),
            publisher_run_ids=d.get("publisher_run_ids", []),
            required_feeds=d.get("required_feeds", []),
            completed_feeds=d.get("completed_feeds", []),
            pending_feeds=d.get("pending_feeds", []),
            failed_feeds=d.get("failed_feeds", []),
            feed_records=records,
        )

    def mark_verified(self, feed_name: str, record: FeedRecord) -> None:
        record.status = "VERIFIED"
        record.verified_at = utc_now_iso()
        self.feed_records[feed_name] = record
        if feed_name not in self.completed_feeds:
            self.completed_feeds.append(feed_name)
        if feed_name in self.pending_feeds:
            self.pending_feeds.remove(feed_name)
        if feed_name in self.failed_feeds:
            self.failed_feeds.remove(feed_name)
        self.updated_at = utc_now_iso()

    def mark_failed(self, feed_name: str, record: FeedRecord) -> None:
        record.status = "FAILED"
        self.feed_records[feed_name] = record
        if feed_name not in self.failed_feeds:
            self.failed_feeds.append(feed_name)
        if feed_name in self.pending_feeds:
            self.pending_feeds.remove(feed_name)
        self.updated_at = utc_now_iso()

    def mark_pending(self, feed_name: str) -> None:
        if feed_name not in self.pending_feeds:
            self.pending_feeds.append(feed_name)
        if feed_name in self.completed_feeds:
            self.completed_feeds.remove(feed_name)
        self.updated_at = utc_now_iso()

    def is_complete(self) -> bool:
        return len(self.completed_feeds) == len(self.required_feeds) and not self.pending_feeds


class CheckpointError(ValueError):
    """Raised when checkpoint operations fail."""


def validate_checkpoint(d: dict) -> None:
    """Validate checkpoint dict schema."""
    if not isinstance(d, dict):
        raise CheckpointError("checkpoint must be a JSON object")
    if d.get("schema_version") != CHECKPOINT_SCHEMA_VERSION:
        raise CheckpointError(f"schema_version must be {CHECKPOINT_SCHEMA_VERSION}")
    if not d.get("generation_id"):
        raise CheckpointError("generation_id is required")
    if not isinstance(d.get("required_feeds"), list):
        raise CheckpointError("required_feeds must be a list")
    if not isinstance(d.get("completed_feeds"), list):
        raise CheckpointError("completed_feeds must be a list")


# ---------- Seed Release operations ----------

def find_active_seed(backend: GitHubReleasesBackend) -> dict | None:
    """Find an active draft seed release (if any)."""
    try:
        releases = backend._request("GET", "releases?per_page=100")
    except Exception:
        return None

    for r in releases:
        if not r.get("draft"):
            continue
        tag = r.get("tag_name", "")
        if tag.startswith(SEED_TAG_PREFIX):
            return r
    return None


def create_seed_release(backend: GitHubReleasesBackend, generation_id: str) -> dict:
    """Create a new draft seed release for checkpoint persistence."""
    tag = f"{SEED_TAG_PREFIX}{generation_id}"
    release = backend._request("POST", "releases", body={
        "tag_name": tag,
        "name": f"NVD Feed Seed {generation_id}",
        "body": f"Resumable checkpoint for NVD bulk feed generation {generation_id}",
        "draft": True,
        "prerelease": True,
        "make_latest": "false",
    })
    return release


def upload_checkpoint(backend: GitHubReleasesBackend, seed_release_id: int, checkpoint: Checkpoint) -> None:
    """Upload or update checkpoint.json on the seed release.

    R12L fix: non-fatal error handling for asset deletion (404 is OK —
    asset may have already been deleted or never existed).
    """
    # Delete existing checkpoint.json asset if present (non-fatal if 404)
    release = backend._request("GET", f"releases/{seed_release_id}")
    for asset in release.get("assets", []):
        if asset["name"] == "checkpoint.json":
            try:
                backend._request("DELETE", f"releases/{seed_release_id}/assets/{asset['id']}")
            except Exception as e:
                # Non-fatal: asset may already be deleted or in an inconsistent state
                print(f"  ⚠️ Non-fatal: could not delete old checkpoint.json asset: {e}")
            break

    # Upload new checkpoint
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False, mode="w") as tf:
        tf.write(checkpoint.to_json())
        tmp_path = Path(tf.name)
    try:
        upload_url = release.get("upload_url", "").replace("{?name,label}", "?name=checkpoint.json")
        backend._upload_asset(upload_url, "checkpoint.json", tmp_path)
    finally:
        tmp_path.unlink(missing_ok=True)


def download_checkpoint(backend: GitHubReleasesBackend, seed_release: dict) -> Checkpoint | None:
    """Download and parse checkpoint.json from a seed release."""
    for asset in seed_release.get("assets", []):
        if asset["name"] == "checkpoint.json":
            with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
                tmp_path = Path(tf.name)
            try:
                backend._download_asset(asset["url"], tmp_path)
                data = json.loads(tmp_path.read_text(encoding="utf-8"))
                validate_checkpoint(data)
                return Checkpoint.from_dict(data)
            finally:
                tmp_path.unlink(missing_ok=True)
    return None


def _delete_existing_asset(backend: GitHubReleasesBackend, seed_release_id: int, feed_name: str) -> None:
    """Delete any existing asset with the given name on the seed release.

    Handles the GitHub Releases API's eventual consistency:
      - The asset list may show an asset that DELETE returns 404 for
        (orphaned/stale entry from a prior failed run).
      - We retry the list+delete cycle up to 3 times with a delay to
        give GitHub's API time to converge.
    """
    import time as _time

    for round_n in range(1, 4):
        release = backend._request("GET", f"releases/{seed_release_id}")
        asset_ids = [a["id"] for a in release.get("assets", []) if a["name"] == feed_name]
        if not asset_ids:
            return  # nothing to delete
        for aid in asset_ids:
            try:
                backend._request("DELETE", f"releases/{seed_release_id}/assets/{aid}")
            except Exception as e:
                # 404 is OK — asset already gone (orphaned list entry)
                if "404" not in str(e) and "not found" not in str(e).lower():
                    print(f"  ⚠️ Non-fatal: could not delete old asset {feed_name} (id={aid}): {e}")
        _time.sleep(2 * round_n)  # 2s, 4s, 6s — let API converge


def upload_feed_asset(backend: GitHubReleasesBackend, seed_release_id: int, feed_name: str, feed_path: Path) -> int:
    """Upload a verified feed file to the seed release. Returns asset ID.

    R12L post-167 hardening:
      - Pre-deletes any existing asset with the same name (handles stale
        orphaned list entries from prior failed runs).
      - On 422 `already_exists`, retries up to 3 times with re-delete +
        longer backoff (5s, 10s, 20s) before giving up.
      - On transient SSL/network errors, _upload_asset already retries
        internally (MAX_UPLOAD_ATTEMPTS=8).
    """
    import time as _time

    # Pre-clean: delete any existing asset with the same name
    _delete_existing_asset(backend, seed_release_id, feed_name)

    upload_url_template = None
    max_422_retries = 3

    for attempt_422 in range(1, max_422_retries + 1):
        # Fetch fresh upload_url each iteration (release state may have changed)
        release = backend._request("GET", f"releases/{seed_release_id}")
        upload_url_template = release.get("upload_url", "")
        upload_url = upload_url_template.replace("{?name,label}", f"?name={feed_name}")

        try:
            backend._upload_asset(upload_url, feed_name, feed_path)
            break  # success
        except Exception as e:
            err_msg = str(e)
            if "422" in err_msg and "already_exists" in err_msg:
                if attempt_422 < max_422_retries:
                    wait = 5 * (2 ** (attempt_422 - 1))  # 5s, 10s, 20s
                    print(f"  ⚠️ Upload 422 already_exists (attempt {attempt_422}/{max_422_retries}) — "
                          f"re-deleting asset and retrying in {wait}s...")
                    _time.sleep(wait)
                    _delete_existing_asset(backend, seed_release_id, feed_name)
                    continue
                else:
                    raise
            else:
                raise

    # Re-read to get asset ID
    release = backend._request("GET", f"releases/{seed_release_id}")
    for asset in release.get("assets", []):
        if asset["name"] == feed_name:
            return asset["id"]
    raise CheckpointError(f"Asset {feed_name} not found after upload")


def verify_seed_asset(backend: GitHubReleasesBackend, seed_release_id: int, asset_name: str, expected_sha256: str) -> bool:
    """Verify a seed asset by downloading and checking SHA-256."""
    release = backend._request("GET", f"releases/{seed_release_id}")
    for asset in release.get("assets", []):
        if asset["name"] == asset_name:
            with tempfile.NamedTemporaryFile(delete=False) as tf:
                tmp_path = Path(tf.name)
            try:
                backend._download_asset(asset["url"], tmp_path)
                actual_sha = sha256_file(tmp_path)
                return actual_sha == expected_sha256
            finally:
                tmp_path.unlink(missing_ok=True)
    return False


def restore_feed_from_seed(
    backend: GitHubReleasesBackend,
    seed_release_id: int,
    feed_name: str,
    expected_sha256: str,
    dest_path: Path,
) -> bool:
    """Restore a verified feed file from the seed release (GitHub CDN).

    Returns True if the file was successfully restored and SHA-256 matches
    (or expected_sha256 is empty, in which case SHA check is skipped).
    Returns False if the asset is not present on the seed release or if
    the SHA-256 does not match.

    This is the key to true resumability: when the workflow's `rm -rf`
    destroys the local feed_dir, we can restore already-verified files
    from the seed release instead of re-downloading them from NVD (which
    is slow and frequently returns 5xx/timeout).

    When expected_sha256 is empty (orphan recovery case), the SHA check
    is skipped — the caller is responsible for gzip+JSON validation.
    """
    release = backend._request("GET", f"releases/{seed_release_id}")
    for asset in release.get("assets", []):
        if asset["name"] != feed_name:
            continue
        # Download to .part first, verify SHA, then atomic rename
        part_path = dest_path.with_suffix(dest_path.suffix + ".restored")
        try:
            backend._download_asset(asset["url"], part_path)
            actual_sha = sha256_file(part_path)
            if expected_sha256 and actual_sha != expected_sha256:
                print(f"  ⚠️ Seed asset {feed_name} SHA mismatch "
                      f"(expected={expected_sha256[:12]}..., actual={actual_sha[:12]}...); "
                      f"will re-download from NVD")
                part_path.unlink(missing_ok=True)
                return False
            # Atomic rename
            part_path.rename(dest_path)
            return True
        except Exception as e:
            print(f"  ⚠️ Seed restore for {feed_name} failed: {e}; will re-download from NVD")
            part_path.unlink(missing_ok=True)
            return False
    # Asset not present on seed
    return False


def close_seed_release(backend: GitHubReleasesBackend, seed_release_id: int) -> None:
    """Delete a completed seed release after final feed is published."""
    try:
        backend._request("DELETE", f"releases/{seed_release_id}")
    except Exception as e:
        # Log but don't fail — seed cleanup is best-effort
        print(f"Warning: failed to delete seed release {seed_release_id}: {e}")
