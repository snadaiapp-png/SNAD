#!/usr/bin/env python3
"""
SANAD — NVD Bulk Feed Release Publisher
========================================
EXEC-PROMPT-010R12L Section 16 — publishes a verified immutable NVD
bulk feed release to GitHub Releases after all feed files are verified.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_bulk_feed_mirror import (
    NVD_YEAR_START,
    current_year,
    expected_feed_names,
)
from scripts.security.nvd_bulk_feed_checkpoint import (
    Checkpoint,
    close_seed_release,
)
from scripts.security.nvd_feed_archive import create_feed_archive, validate_feed_archive
from scripts.security.nvd_snapshot_store import (
    GitHubReleasesBackend,
    SnapshotError,
    sha256_file,
    utc_now_iso,
)

FEED_RELEASE_PREFIX = "nvd-feed-"
FEED_SCHEMA = "snad-nvd-bulk-feed-v2"


def build_feed_manifest(
    *,
    feed_id: str,
    release_tag: str,
    checkpoint: Checkpoint,
    archive_filename: str,
    archive_sha256: str,
    archive_size_bytes: int,
) -> dict:
    """Build the final feed manifest."""
    year_end = current_year()
    feed_records = [r.to_dict() for r in checkpoint.feed_records.values()]
    return {
        "schema_version": 2,
        "feed_id": feed_id,
        "release_tag": release_tag,
        "publisher_sha": checkpoint.publisher_sha,
        "publisher_run_ids": checkpoint.publisher_run_ids,
        "generation_id": checkpoint.generation_id,
        "source_type": "NVD_JSON_2_BULK_FEEDS",
        "direct_nvd_api_access": False,
        "nvd_api_key_used": False,
        "resumable_download": True,
        "checkpoint_backend": "GITHUB_DRAFT_RELEASE",
        "start_year": NVD_YEAR_START,
        "end_year": year_end,
        "required_feed_count": len(checkpoint.required_feeds),
        "verified_feed_count": len(checkpoint.completed_feeds),
        "last_modified_at": max(
            (r.last_modified for r in checkpoint.feed_records.values() if r.last_modified),
            default="",
        ),
        "completed_at": utc_now_iso(),
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "archive_size_bytes": archive_size_bytes,
        "feed_records": feed_records,
    }


def publish_final_feed_release(
    backend: GitHubReleasesBackend,
    *,
    checkpoint: Checkpoint,
    feed_dir: Path,
    work_dir: Path,
    seed_release_id: int,
) -> dict:
    """Publish the final verified feed release.

    Transaction:
      1. Build archive from verified feed files
      2. Build manifest + SHA256SUMS
      3. Create draft final release
      4. Upload 3 assets
      5. Verify all 3 assets (download + hash)
      6. Publish (draft=false)
      7. Close seed release
    """
    feed_id = checkpoint.generation_id
    release_tag = f"{FEED_RELEASE_PREFIX}{feed_id}"

    # 1. Build archive
    archive_filename = f"snad-nvd-feed-{feed_id}.tar.zst"
    archive_path = work_dir / archive_filename
    actual_archive_name = create_feed_archive(archive_path, feed_dir, work_dir)
    if actual_archive_name != archive_filename:
        archive_filename = actual_archive_name
        archive_path = work_dir / archive_filename

    archive_sha256 = sha256_file(archive_path)
    archive_size = archive_path.stat().st_size

    # Validate archive
    validate_feed_archive(archive_path)

    # 2. Build manifest
    manifest = build_feed_manifest(
        feed_id=feed_id,
        release_tag=release_tag,
        checkpoint=checkpoint,
        archive_filename=archive_filename,
        archive_sha256=archive_sha256,
        archive_size_bytes=archive_size,
    )

    manifest_path = work_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    manifest_sha256 = sha256_file(manifest_path)

    # Build SHA256SUMS
    checksums_lines = [f"{archive_sha256}  {archive_filename}"]
    for f in sorted(feed_dir.iterdir()):
        if f.is_file():
            checksums_lines.append(f"{sha256_file(f)}  {f.name}")
    checksums_lines.append(f"{manifest_sha256}  manifest.json")
    checksums_content = "\n".join(checksums_lines) + "\n"
    checksums_path = work_dir / "SHA256SUMS"
    checksums_path.write_text(checksums_content, encoding="utf-8")

    # 3. Create draft final release
    release = backend._request("POST", "releases", body={
        "tag_name": release_tag,
        "name": f"NVD Bulk Feed {feed_id}",
        "body": f"Verified NVD JSON 2.0 bulk feed release {feed_id}",
        "draft": True,
        "prerelease": True,
        "make_latest": False,
    })
    release_id = release["id"]
    upload_url = release.get("upload_url", "")

    # 4. Upload 3 assets
    backend._upload_asset(upload_url, archive_filename, archive_path)
    backend._upload_asset(upload_url, "manifest.json", manifest_path)
    backend._upload_asset(upload_url, "SHA256SUMS", checksums_path)

    # 5. Verify all 3 assets
    release = backend._request("GET", f"releases/{release_id}")
    assets = {a["name"]: a for a in release.get("assets", [])}
    for name in (archive_filename, "manifest.json", "SHA256SUMS"):
        if name not in assets:
            raise SnapshotError(f"Asset {name} not found after upload")

    # Download and verify each asset
    for name, expected_sha in [
        (archive_filename, archive_sha256),
        ("manifest.json", manifest_sha256),
    ]:
        with tempfile.NamedTemporaryFile(delete=False) as tf:
            tmp = Path(tf.name)
        try:
            backend._download_asset(assets[name]["url"], tmp)
            actual = sha256_file(tmp)
            if actual != expected_sha:
                raise SnapshotError(f"Asset {name} SHA mismatch: expected={expected_sha} actual={actual}")
        finally:
            tmp.unlink(missing_ok=True)

    # 6. Publish
    backend._request("PATCH", f"releases/{release_id}", body={
        "draft": False,
        "prerelease": False,
        "make_latest": False,
    })

    # 7. Close seed
    close_seed_release(backend, seed_release_id)

    return {
        "release_tag": release_tag,
        "release_id": release_id,
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "manifest_sha256": manifest_sha256,
        "feed_id": feed_id,
    }


def resolve_latest_verified_feed(backend: GitHubReleasesBackend) -> dict | None:
    """R12K: resolve latest verified feed release by paginating releases.

    Does NOT use mutable nvd-feed-latest pointer.
    """
    page = 1
    candidates = []
    while True:
        try:
            releases = backend._request("GET", f"releases?per_page=100&page={page}")
        except Exception:
            break
        if not releases:
            break
        for r in releases:
            if r.get("draft"):
                continue
            tag = r.get("tag_name", "")
            if not tag.startswith(FEED_RELEASE_PREFIX):
                continue
            # Check for required assets
            assets = {a["name"] for a in r.get("assets", [])}
            # Look for archive + manifest.json + SHA256SUMS
            has_manifest = "manifest.json" in assets
            has_checksums = "SHA256SUMS" in assets
            has_archive = any(a.startswith("snad-nvd-feed-") for a in assets)
            if has_manifest and has_checksums and has_archive:
                candidates.append(r)
        page += 1
        if len(releases) < 100:
            break

    if not candidates:
        return None

    # Sort by published_at (newest first)
    candidates.sort(key=lambda r: r.get("published_at", ""), reverse=True)
    latest = candidates[0]

    # Download manifest to get feed_id
    for asset in latest.get("assets", []):
        if asset["name"] == "manifest.json":
            with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
                tmp = Path(tf.name)
            try:
                backend._download_asset(asset["url"], tmp)
                manifest = json.loads(tmp.read_text(encoding="utf-8"))
                return {
                    "feed_id": manifest.get("feed_id", ""),
                    "release_tag": latest["tag_name"],
                    "release_id": latest["id"],
                    "archive_filename": manifest.get("archive_filename", ""),
                    "archive_sha256": manifest.get("archive_sha256", ""),
                    "manifest_sha256": sha256_file(tmp),
                    "created_at": manifest.get("completed_at", ""),
                    "last_modified_at": manifest.get("last_modified_at", ""),
                    "source_type": manifest.get("source_type", ""),
                    "direct_nvd_api_access": manifest.get("direct_nvd_api_access", False),
                }
            finally:
                tmp.unlink(missing_ok=True)
    return None
