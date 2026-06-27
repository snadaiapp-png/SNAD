#!/usr/bin/env python3
"""
SANAD — NVD Feed Mirror Publisher
==================================
EXEC-PROMPT-010R12J Section 5.1 — controlled NVD data feed mirror
that downloads NVD CVE data using the open-vulnerability-cli (vulnz),
packages it into a verified immutable bundle, and publishes it to
GitHub Releases.

Architecture:
  NVD API → vulnz download → feed files (nvdcve-*.json.gz + .meta)
          → archive + manifest + SHA256SUMS → GitHub Release (draft→published)

The feed files follow the NVD JSON feed format that Dependency-Check
can consume via -DnvdDatafeedUrl.

NVD_API_KEY is used ONLY in this publisher.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_archive import (
    create_snapshot_archive,
    list_snapshot_archive,
    stream_hash_file,
    validate_archive_paths,
)
from scripts.security.nvd_snapshot_store import (
    GitHubReleasesBackend,
    SnapshotError,
    SnapshotNotFoundError,
    StorageBackendError,
    sha256_bytes,
    sha256_file,
    utc_now_iso,
)

# ---------- Constants ----------

FEED_SCHEMA = "snad-nvd-feed-v1"
FEED_RELEASE_TAG_PREFIX = "nvd-feed-"
FEED_LATEST_TAG = "nvd-feed-latest"
VULNZ_VERSION = "9.0.4"
VULNZ_JAR_URL = f"https://github.com/jeremylong/open-vulnerability-cli/releases/download/v{VULNZ_VERSION}/vulnz-{VULNZ_VERSION}.jar"

# NVD CVE data starts from 2002
NVD_YEAR_START = 2002


# ---------- Helpers ----------

def current_year() -> int:
    return dt.datetime.now(dt.timezone.utc).year


def expected_year_files() -> list[str]:
    """Return the list of expected nvdcve-YYYY.json.gz filenames."""
    years = list(range(NVD_YEAR_START, current_year() + 1))
    return [f"nvdcve-{y}.json.gz" for y in years]


def expected_meta_files() -> list[str]:
    years = list(range(NVD_YEAR_START, current_year() + 1))
    return [f"nvdcve-{y}.meta" for y in years]


def expected_feed_files() -> set[str]:
    """All expected files in the feed directory."""
    files = set()
    files.update(expected_year_files())
    files.update(expected_meta_files())
    files.add("nvdcve-modified.json.gz")
    files.add("nvdcve-modified.meta")
    files.add("cache.properties")
    return files


# ---------- Feed validation ----------

def validate_feed_file_set(feed_dir: Path) -> None:
    """Validate that all expected feed files exist."""
    feed_dir = Path(feed_dir)
    expected = expected_feed_files()
    actual = set()
    for f in feed_dir.iterdir():
        if f.is_file():
            actual.add(f.name)

    missing = expected - actual
    if missing:
        raise ValueError(f"Missing feed files: {sorted(missing)}")

    unexpected = actual - expected - {"manifest.json", "SHA256SUMS"}
    if unexpected:
        raise ValueError(f"Unexpected files in feed: {sorted(unexpected)}")


def validate_feed_metadata(feed_dir: Path) -> dict:
    """Validate cache.properties and .meta files."""
    feed_dir = Path(feed_dir)
    cache_props = feed_dir / "cache.properties"
    if not cache_props.exists():
        raise ValueError("cache.properties not found")

    # Check modified.meta has a valid timestamp
    modified_meta = feed_dir / "nvdcve-modified.meta"
    if not modified_meta.exists():
        raise ValueError("nvdcve-modified.meta not found")

    meta_content = modified_meta.read_text(encoding="utf-8").strip()
    if not meta_content:
        raise ValueError("nvdcve-modified.meta is empty")

    # Parse lastModifiedDate from meta
    last_modified = ""
    for line in meta_content.split("\n"):
        if line.startswith("lastModifiedDate:"):
            last_modified = line.split(":", 1)[1].strip()
            break

    if not last_modified:
        raise ValueError("lastModifiedDate not found in nvdcve-modified.meta")

    return {
        "last_modified_at": last_modified,
        "cache_properties_sha256": sha256_file(cache_props),
    }


def validate_feed_json_schema(feed_dir: Path) -> None:
    """Validate that JSON.gz files are valid gzip and contain valid JSON."""
    import gzip
    feed_dir = Path(feed_dir)
    for gz_file in feed_dir.glob("nvdcve-*.json.gz"):
        try:
            with gzip.open(gz_file, "rt", encoding="utf-8") as f:
                data = json.load(f)
                if not isinstance(data, dict):
                    raise ValueError(f"{gz_file.name}: root is not a JSON object")
                if "CVE_Items" not in data and "vulnerabilities" not in data:
                    raise ValueError(f"{gz_file.name}: missing CVE_Items or vulnerabilities")
        except gzip.BadGzipFile as e:
            raise ValueError(f"{gz_file.name}: invalid gzip: {e}") from e
        except json.JSONDecodeError as e:
            raise ValueError(f"{gz_file.name}: invalid JSON: {e}") from e


def verify_feed_digests(feed_dir: Path, checksums_path: Path) -> None:
    """Verify SHA256SUMS for all feed files."""
    feed_dir = Path(feed_dir)
    checksums = checksums_path.read_text(encoding="utf-8")
    for line in checksums.strip().split("\n"):
        if not line.strip():
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            raise ValueError(f"Invalid SHA256SUMS line: {line}")
        expected_sha, filename = parts
        filepath = feed_dir / filename
        if not filepath.exists():
            raise ValueError(f"File listed in SHA256SUMS not found: {filename}")
        actual_sha = sha256_file(filepath)
        if actual_sha != expected_sha:
            raise ValueError(f"Digest mismatch for {filename}: expected={expected_sha} actual={actual_sha}")


# ---------- Feed manifest ----------

def build_feed_manifest(
    *,
    feed_id: str,
    release_tag: str,
    created_at: str,
    last_modified_at: str,
    publisher_sha: str,
    publisher_run_id: str,
    archive_filename: str,
    archive_sha256: str,
    archive_size_bytes: int,
    feed_dir: Path,
    modified_feed_sha256: str,
    cache_properties_sha256: str,
    freshness_hours: int,
) -> dict:
    """Build the feed manifest."""
    year_end = current_year()
    yearly_count = year_end - NVD_YEAR_START + 1
    return {
        "schema": FEED_SCHEMA,
        "feed_id": feed_id,
        "release_tag": release_tag,
        "created_at": created_at,
        "last_modified_at": last_modified_at,
        "source": "NVD API via controlled mirror publisher",
        "publisher_sha": publisher_sha,
        "publisher_run_id": publisher_run_id,
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "archive_size_bytes": archive_size_bytes,
        "year_start": NVD_YEAR_START,
        "year_end": year_end,
        "yearly_feed_count": yearly_count,
        "modified_feed_filename": "nvdcve-modified.json.gz",
        "modified_feed_sha256": modified_feed_sha256,
        "cache_properties_sha256": cache_properties_sha256,
        "validation_result": "valid",
        "freshness_hours": freshness_hours,
    }


def validate_feed_manifest(manifest: dict) -> None:
    """Validate the feed manifest."""
    if not isinstance(manifest, dict):
        raise ValueError("manifest must be a JSON object")
    if manifest.get("schema") != FEED_SCHEMA:
        raise ValueError(f"manifest schema is {manifest.get('schema')!r}; expected {FEED_SCHEMA!r}")
    if manifest.get("validation_result") != "valid":
        raise ValueError("manifest validation_result must be 'valid'")
    if manifest.get("year_end") != current_year():
        raise ValueError(f"manifest year_end is {manifest.get('year_end')}; expected {current_year()}")
    expected_count = current_year() - NVD_YEAR_START + 1
    if manifest.get("yearly_feed_count") != expected_count:
        raise ValueError(f"manifest yearly_feed_count is {manifest.get('yearly_feed_count')}; expected {expected_count}")
    for key in ("feed_id", "release_tag", "created_at", "archive_sha256", "modified_feed_sha256"):
        if not manifest.get(key):
            raise ValueError(f"manifest missing required field: {key}")


# ---------- Feed archive ----------

def build_feed_archive(feed_dir: Path, archive_path: Path, work_dir: Path) -> str:
    """Archive the feed directory into a tar.zst (or .tar.gz fallback)."""
    actual = create_snapshot_archive(archive_path, feed_dir, work_dir)
    return actual.name


def build_feed_checksums(feed_dir: Path, manifest_sha256: str, archive_filename: str, archive_sha256: str) -> str:
    """Build SHA256SUMS content for the feed."""
    lines = [f"{archive_sha256}  {archive_filename}"]
    for f in sorted(feed_dir.iterdir()):
        if f.is_file():
            lines.append(f"{sha256_file(f)}  {f.name}")
    lines.append(f"{manifest_sha256}  manifest.json")
    return "\n".join(lines) + "\n"


# ---------- Feed release publish ----------

def publish_feed_release(
    backend: GitHubReleasesBackend,
    *,
    feed_id: str,
    feed_dir: Path,
    publisher_sha: str,
    publisher_run_id: str,
    last_modified_at: str,
    freshness_hours: int,
    work_dir: Path,
) -> dict:
    """Full publish sequence for a feed release."""
    created_at = utc_now_iso()
    release_tag = f"{FEED_RELEASE_TAG_PREFIX}{feed_id}"

    # Validate feed files
    validate_feed_file_set(feed_dir)
    validate_feed_json_schema(feed_dir)
    meta_info = validate_feed_metadata(feed_dir)

    # Build archive
    archive_filename = f"snad-nvd-feed-{feed_id}.tar.zst"
    archive_path = work_dir / archive_filename
    actual_archive_name = build_feed_archive(feed_dir, archive_path, work_dir)
    if actual_archive_name != archive_filename:
        archive_filename = actual_archive_name
        archive_path = work_dir / archive_filename

    archive_sha256 = sha256_file(archive_path)
    archive_size = archive_path.stat().st_size

    # Validate archive paths
    validate_archive_paths(archive_path)

    # Build manifest
    modified_feed_sha256 = sha256_file(feed_dir / "nvdcve-modified.json.gz")
    manifest = build_feed_manifest(
        feed_id=feed_id,
        release_tag=release_tag,
        created_at=created_at,
        last_modified_at=last_modified_at,
        publisher_sha=publisher_sha,
        publisher_run_id=publisher_run_id,
        archive_filename=archive_filename,
        archive_sha256=archive_sha256,
        archive_size_bytes=archive_size,
        feed_dir=feed_dir,
        modified_feed_sha256=modified_feed_sha256,
        cache_properties_sha256=meta_info["cache_properties_sha256"],
        freshness_hours=freshness_hours,
    )
    validate_feed_manifest(manifest)

    manifest_path = work_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    manifest_sha256 = sha256_file(manifest_path)

    # Build SHA256SUMS
    checksums_content = build_feed_checksums(feed_dir, manifest_sha256, archive_filename, archive_sha256)
    checksums_path = work_dir / "SHA256SUMS"
    checksums_path.write_text(checksums_content, encoding="utf-8")

    # Create draft release
    import urllib.request as _urllib
    url = f"https://api.github.com/repos/{backend.repo}/releases"
    headers = backend._headers()
    body = json.dumps({
        "tag_name": release_tag,
        "name": f"NVD Feed {feed_id}",
        "body": f"NVD CVE data feed mirror {feed_id}",
        "draft": True,
        "prerelease": True,
        "make_latest": False,
    }).encode()
    req = _urllib.request.Request(url, method="POST", data=body, headers=headers)
    with _urllib.request.urlopen(req, timeout=30) as resp:
        release = json.loads(resp.read().decode())

    upload_url = release.get("upload_url", "")
    release_id = release.get("id")

    # Upload assets
    backend._upload_asset(upload_url, archive_filename, archive_path)
    backend._upload_asset(upload_url, "manifest.json", manifest_path)
    backend._upload_asset(upload_url, "SHA256SUMS", checksums_path)

    # Verify assets exist
    release = backend._request("GET", f"releases/{release_id}")
    assets = {a["name"]: a for a in release.get("assets", [])}
    for name in (archive_filename, "manifest.json", "SHA256SUMS"):
        if name not in assets:
            raise SnapshotError(f"Asset {name} not found after upload")

    # Verify SHA-256 by downloading and checking
    for name in (archive_filename, "manifest.json", "SHA256SUMS"):
        asset = assets[name]
        tmp = Path(tempfile.mktemp())
        backend._download_asset(asset["url"], tmp)
        if name == archive_filename:
            actual = sha256_file(tmp)
            if actual != archive_sha256:
                raise SnapshotError(f"Archive SHA mismatch after download: expected={archive_sha256} actual={actual}")
        tmp.unlink(missing_ok=True)

    # Publish release (remove draft)
    backend._request("PATCH", f"releases/{release_id}", body={
        "draft": False,
        "prerelease": False,
        "make_latest": False,
    })

    # Build latest pointer
    pointer = {
        "contract_version": "snad-nvd-feed-latest-v1",
        "feed_id": feed_id,
        "release_tag": release_tag,
        "release_id": release_id,
        "promoted_at": utc_now_iso(),
        "created_at": created_at,
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "manifest_sha256": manifest_sha256,
        "last_modified_at": last_modified_at,
        "year_start": NVD_YEAR_START,
        "year_end": current_year(),
    }

    # Promote latest pointer
    promote_feed_latest_pointer(backend, pointer)

    return {
        "manifest": manifest,
        "latest_pointer": pointer,
        "release_id": release_id,
        "release_tag": release_tag,
    }


def promote_feed_latest_pointer(backend: GitHubReleasesBackend, pointer: dict) -> None:
    """Update the nvd-feed-latest release with latest.json."""
    import tempfile
    try:
        release = backend._request("GET", f"releases/tags/{FEED_LATEST_TAG}")
        release_id = release.get("id")
        for asset in release.get("assets", []):
            if asset["name"] == "latest.json":
                backend._request("DELETE", f"releases/{release_id}/assets/{asset['id']}")
    except SnapshotNotFoundError:
        release = backend._request("POST", "releases", body={
            "tag_name": FEED_LATEST_TAG,
            "name": "NVD Feed Latest Pointer",
            "body": "Auto-updated pointer to the latest verified NVD feed",
            "draft": False,
            "prerelease": False,
            "make_latest": False,
        })
        release_id = release.get("id")

    tmp = Path(tempfile.mktemp(suffix=".json"))
    tmp.write_text(json.dumps(pointer, indent=2), encoding="utf-8")
    upload_url = release.get("upload_url", "").replace("{?name,label}", "?name=latest.json")
    backend._upload_asset(upload_url, "latest.json", tmp)
    tmp.unlink(missing_ok=True)

    # Re-read to verify
    re_release = backend._request("GET", f"releases/{release_id}")
    found = any(a["name"] == "latest.json" for a in re_release.get("assets", []))
    if not found:
        raise StorageBackendError("latest.json not found after upload — feed pointer verification failed")


# ---------- Feed resolve / download ----------

def resolve_latest_valid_feed_release(backend: GitHubReleasesBackend) -> dict | None:
    """Resolve the latest valid (non-draft, published) feed release."""
    try:
        release = backend._request("GET", f"releases/tags/{FEED_LATEST_TAG}")
    except SnapshotNotFoundError:
        return None

    for asset in release.get("assets", []):
        if asset["name"] == "latest.json":
            tmp = Path(tempfile.mktemp(suffix=".json"))
            backend._download_asset(asset["url"], tmp)
            pointer = json.loads(tmp.read_text(encoding="utf-8"))
            tmp.unlink(missing_ok=True)
            return pointer
    return None


def download_feed_bundle(backend: GitHubReleasesBackend, pointer: dict, destination: Path) -> dict:
    """Download feed archive + manifest + SHA256SUMS."""
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    release_tag = pointer["release_tag"]
    release = backend._request("GET", f"releases/tags/{release_tag}")
    assets = {a["name"]: a for a in release.get("assets", [])}

    archive_filename = pointer["archive_filename"]
    archive_dest = destination / archive_filename
    if archive_filename in assets:
        backend._download_asset(assets[archive_filename]["url"], archive_dest)

    manifest_dest = destination / "manifest.json"
    if "manifest.json" in assets:
        backend._download_asset(assets["manifest.json"]["url"], manifest_dest)

    checksums_dest = destination / "SHA256SUMS"
    if "SHA256SUMS" in assets:
        backend._download_asset(assets["SHA256SUMS"]["url"], checksums_dest)

    return {
        "archive_path": archive_dest,
        "manifest_path": manifest_dest,
        "checksums_path": checksums_dest,
        "feed_id": pointer["feed_id"],
    }


# ---------- Feed retention ----------

def apply_feed_retention_policy(backend: GitHubReleasesBackend, keep: int = 14) -> list[str]:
    """Delete old feed releases, keeping the most recent `keep`."""
    latest = resolve_latest_valid_feed_release(backend)
    latest_id = latest["feed_id"] if latest else None

    all_ids = list_feed_releases(backend)
    all_ids.sort(reverse=True)

    others = [sid for sid in all_ids if sid != latest_id]
    to_keep = set(others[:keep - 1]) | ({latest_id} if latest_id else set())
    to_delete = [sid for sid in all_ids if sid not in to_keep]

    for sid in to_delete:
        tag = f"{FEED_RELEASE_TAG_PREFIX}{sid}"
        try:
            backend._request("DELETE", f"releases/tags/{tag}")
            backend._request("DELETE", f"git/refs/tags/{tag}")
        except Exception:
            pass
    return to_delete


def list_feed_releases(backend: GitHubReleasesBackend) -> list[str]:
    """List all feed release tags (non-draft, published)."""
    result = []
    page = 1
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
            if tag.startswith(FEED_RELEASE_TAG_PREFIX) and tag != FEED_LATEST_TAG:
                result.append(tag[len(FEED_RELEASE_TAG_PREFIX):])
        page += 1
        if len(releases) < 100:
            break
    return sorted(result)


# ---------- Vulnz download ----------

def download_nvd_feed(
    feed_dir: Path,
    nvd_api_key: str,
    vulnz_jar_path: Path | None = None,
    timeout_minutes: int = 120,
) -> None:
    """Download NVD CVE data using vulnz (open-vulnerability-cli)."""
    feed_dir = Path(feed_dir)
    feed_dir.mkdir(parents=True, exist_ok=True)

    # Download vulnz jar if not provided
    if vulnz_jar_path is None:
        vulnz_jar_path = feed_dir.parent / "vulnz.jar"

    if not vulnz_jar_path.exists():
        print(f"Downloading vulnz {VULNZ_VERSION}...")
        urllib.request.urlretrieve(VULNZ_JAR_URL, vulnz_jar_path)

    # Run vulnz to download NVD data
    # vulnz 9.x CLI: java -jar vulnz.jar cve --prefix <dir>/nvdcve-
    # NVD_API_KEY is passed via environment variable (recommended by vulnz)
    cmd = [
        "java", "-jar", str(vulnz_jar_path),
        "cve",
        "--prefix", str(feed_dir / "nvdcve-"),
        "--requestCount", "5",
        "--maxRetry", "5",
    ]
    # Set NVD_API_KEY in the subprocess environment
    env = os.environ.copy()
    env["NVD_API_KEY"] = nvd_api_key
    print(f"Running vulnz: {' '.join(cmd[:6])}...")
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout_minutes * 60, env=env,
        )
        if result.returncode != 0:
            print(f"vulnz failed (exit: {result.returncode})")
            print(result.stdout[-3000:] if result.stdout else "")
            print(result.stderr[-3000:] if result.stderr else "")
            raise RuntimeError(f"vulnz download failed (exit: {result.returncode})")
        print("vulnz download completed successfully")
    except subprocess.TimeoutExpired:
        raise RuntimeError(f"vulnz timed out after {timeout_minutes} minutes")


# ---------- Main ----------

def main():
    parser = argparse.ArgumentParser(description="NVD Feed Mirror Publisher")
    parser.add_argument("--feed-dir", required=True, help="Directory to store feed files")
    parser.add_argument("--publisher-sha", required=True)
    parser.add_argument("--publisher-run-id", required=True)
    parser.add_argument("--nvd-api-key-env", default="NVD_API_KEY", help="Env var name for NVD API key")
    parser.add_argument("--vulnz-jar", default=None, help="Path to vulnz jar (auto-downloaded if not provided)")
    parser.add_argument("--download-timeout", type=int, default=120, help="Download timeout in minutes")
    parser.add_argument("--skip-download", action="store_true", help="Skip NVD download (for testing)")
    args = parser.parse_args()

    nvd_api_key = os.environ.get(args.nvd_api_key_env)
    if not nvd_api_key and not args.skip_download:
        print("::error::NVD_API_KEY is required")
        return 1

    feed_dir = Path(args.feed_dir)
    work_dir = feed_dir.parent / "feed-work"
    work_dir.mkdir(parents=True, exist_ok=True)

    # Download NVD data
    if not args.skip_download:
        download_nvd_feed(feed_dir, nvd_api_key, args.vulnz_jar, args.download_timeout)

    # Validate
    validate_feed_file_set(feed_dir)
    validate_feed_json_schema(feed_dir)
    meta_info = validate_feed_metadata(feed_dir)

    # Compute feed ID
    modified_sha = sha256_file(feed_dir / "nvdcve-modified.json.gz")
    feed_id = f"{utc_now_iso().replace(':', '').replace('-', '')[:14]}-{modified_sha[:12]}"

    # Compute freshness
    last_modified_str = meta_info["last_modified_at"]
    try:
        # Try parsing the NVD meta date format
        last_modified_dt = dt.datetime.strptime(last_modified_str, "%Y-%m-%dT%H:%M:%S.%f")
    except ValueError:
        try:
            last_modified_dt = dt.datetime.strptime(last_modified_str, "%Y-%m-%dT%H:%M:%SZ")
        except ValueError:
            last_modified_dt = dt.datetime.now(dt.timezone.utc)
    if last_modified_dt.tzinfo is None:
        last_modified_dt = last_modified_dt.replace(tzinfo=dt.timezone.utc)
    freshness_hours = int((dt.datetime.now(dt.timezone.utc) - last_modified_dt).total_seconds() // 3600)

    # Publish
    backend = GitHubReleasesBackend()

    result = publish_feed_release(
        backend=backend,
        feed_id=feed_id,
        feed_dir=feed_dir,
        publisher_sha=args.publisher_sha,
        publisher_run_id=args.publisher_run_id,
        last_modified_at=last_modified_str,
        freshness_hours=freshness_hours,
        work_dir=work_dir,
    )

    # Apply retention
    deleted = apply_feed_retention_policy(backend, keep=14)

    print()
    print("=== Feed Publish Complete ===")
    print(f"  feed_id: {result['manifest']['feed_id']}")
    print(f"  release_tag: {result['release_tag']}")
    print(f"  archive_sha256: {result['manifest']['archive_sha256']}")
    print(f"  retention_deleted: {len(deleted)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
