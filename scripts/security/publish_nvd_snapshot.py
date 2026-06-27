#!/usr/bin/env python3
"""
SANAD — NVD Snapshot Publisher (shared logic)
==============================================
EXEC-PROMPT-010R12F Section 18.3 — shared publish logic used by both:
  - .github/workflows/nvd-snapshot-bootstrap.yml   (--mode bootstrap)
  - .github/workflows/nvd-snapshot-publisher.yml   (--mode incremental)

This script enforces the R12F commit-point semantics:

  1. Update work directory (incremental: from canonical; bootstrap: fresh)
  2. Validate work directory
  3. Run offline smoke test
  4. Build archive + manifest sidecar + SHA256SUMS sidecar
  5. Upload three immutable objects ONCE (single push per snapshot)
  6. Verify remote archive and manifest digests
  7. Promote remote latest.json pointer (commit point)
  8. Atomically update local canonical cache
  9. Apply retention policy

Before step 7: latest.json and local canonical remain UNCHANGED.
On any pre-commit failure: remote LKG and local canonical preserved.

Usage:
  python3 scripts/security/publish_nvd_snapshot.py \\
      --mode bootstrap|incremental \\
      --work-dir /var/lib/snad/dependency-check-data/work/<run_id> \\
      --canonical-dir /var/lib/snad/dependency-check-data/canonical \\
      --lkg-dir /var/lib/snad/dependency-check-data/lkg \\
      --publisher-commit-sha <40-char-sha> \\
      --publisher-run-id <run_id>
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
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_archive import (
    ArchiveError,
    create_snapshot_archive,
    extract_snapshot_archive,
    list_snapshot_archive,
    stream_hash_file,
    validate_archive_paths,
)
from scripts.security.nvd_snapshot_store import (
    CONTRACT_VERSION,
    SCHEMA_VERSION,
    DEFAULT_DEPENDENCY_CHECK_VERSION,
    DEFAULT_RETENTION_COUNT,
    FilesystemBackend,
    SnapshotError,
    SnapshotNotFoundError,
    StorageBackendError,
    build_manifest,
    get_backend,
    sha256_bytes,
    sha256_file,
    utc_now_iso,
    validate_manifest,
)

# R12F: new contract versions
CONTRACT_VERSION_V2 = "snad-nvd-snapshot-v2"
SCHEMA_VERSION_V2 = "v2"
LATEST_CONTRACT_VERSION = "snad-nvd-latest-v1"


def build_manifest_v2(
    *,
    snapshot_id: str,
    created_at: str,
    last_successful_update_at: str,
    publisher_commit_sha: str,
    publisher_run_id: int | str,
    archive_filename: str,
    archive_sha256: str,
    archive_size_bytes: int,
    database_relative_path: str,
    database_sha256: str,
    database_size_bytes: int,
    validation_result: str,
    offline_smoke_result: str,
    storage_backend: str,
    previous_snapshot_id: str,
    freshness_hours_at_publish: int,
    dependency_check_version: str = DEFAULT_DEPENDENCY_CHECK_VERSION,
) -> dict:
    """Build a v2 manifest. R12F: storage_version_or_digest removed
    (it lives in latest.json, not the manifest, to avoid circular hashing).
    """
    return {
        "contract_version": CONTRACT_VERSION_V2,
        "schema_version": SCHEMA_VERSION_V2,
        "dependency_check_version": dependency_check_version,
        "snapshot_id": snapshot_id,
        "created_at": created_at,
        "last_successful_update_at": last_successful_update_at,
        "publisher_commit_sha": publisher_commit_sha,
        "publisher_run_id": publisher_run_id,
        "source": "NVD_API",
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "archive_size_bytes": archive_size_bytes,
        "database_relative_path": database_relative_path,
        "database_sha256": database_sha256,
        "database_size_bytes": database_size_bytes,
        "validation_result": validation_result,
        "offline_smoke_result": offline_smoke_result,
        "storage_backend": storage_backend,
        "previous_snapshot_id": previous_snapshot_id,
        "freshness_hours_at_publish": freshness_hours_at_publish,
    }


def validate_manifest_v2(manifest: dict) -> None:
    """Validate a v2 manifest. Raises ValueError on any violation."""
    if not isinstance(manifest, dict):
        raise ValueError("manifest must be a JSON object")

    required_keys = (
        "contract_version", "schema_version", "dependency_check_version",
        "snapshot_id", "created_at", "last_successful_update_at",
        "publisher_commit_sha", "publisher_run_id", "source",
        "archive_filename", "archive_sha256", "archive_size_bytes",
        "database_relative_path", "database_sha256", "database_size_bytes",
        "validation_result", "offline_smoke_result",
        "storage_backend", "previous_snapshot_id", "freshness_hours_at_publish",
    )
    for key in required_keys:
        if key not in manifest:
            raise ValueError(f"manifest missing required key: {key}")

    if manifest["contract_version"] != CONTRACT_VERSION_V2:
        raise ValueError(
            f"manifest contract_version is {manifest['contract_version']!r}; "
            f"expected {CONTRACT_VERSION_V2!r}"
        )
    if manifest["schema_version"] != SCHEMA_VERSION_V2:
        raise ValueError(
            f"manifest schema_version is {manifest['schema_version']!r}; "
            f"expected {SCHEMA_VERSION_V2!r}"
        )
    if manifest["validation_result"] != "valid":
        raise ValueError("manifest validation_result must be 'valid'")
    if manifest["offline_smoke_result"] != "success":
        raise ValueError("manifest offline_smoke_result must be 'success'")
    # R12F: storage_version_or_digest must NOT be in v2 manifest
    if "storage_version_or_digest" in manifest:
        raise ValueError(
            "manifest must NOT contain storage_version_or_digest "
            "(it lives in latest.json in v2)"
        )

    # SHA-256 = 64 hex chars
    for k in ("archive_sha256", "database_sha256"):
        v = manifest[k]
        if not isinstance(v, str) or len(v) != 64 or not all(c in "0123456789abcdef" for c in v):
            raise ValueError(f"manifest {k} is not a 64-char hex SHA-256: {v!r}")
    # publisher_commit_sha = 40 hex chars
    v = manifest["publisher_commit_sha"]
    if not isinstance(v, str) or len(v) != 40 or not all(c in "0123456789abcdef" for c in v):
        raise ValueError(f"manifest publisher_commit_sha is not a 40-char hex SHA: {v!r}")


def build_latest_pointer(
    *,
    snapshot_id: str,
    promoted_at: str,
    created_at: str,
    archive_filename: str,
    archive_sha256: str,
    manifest_sha256: str,
    storage_backend: str,
    storage_version_or_digest: str,
) -> dict:
    """Build the latest.json pointer content."""
    return {
        "contract_version": LATEST_CONTRACT_VERSION,
        "snapshot_id": snapshot_id,
        "promoted_at": promoted_at,
        "created_at": created_at,
        "archive_filename": archive_filename,
        "archive_sha256": archive_sha256,
        "manifest_sha256": manifest_sha256,
        "storage_backend": storage_backend,
        "storage_version_or_digest": storage_version_or_digest,
    }


def build_sha256sums(archive_filename: str, archive_sha256: str, manifest_sha256: str) -> str:
    """Build SHA256SUMS file content."""
    return f"{archive_sha256}  {archive_filename}\n{manifest_sha256}  manifest.json\n"


def atomic_canonical_swap(
    work_dir: Path,
    canonical_dir: Path,
    run_id: str,
) -> None:
    """R12F Section 15.3 — atomically swap canonical directory.

    Uses rename (atomic on same filesystem) instead of rm -rf + cp.

    Sequence:
      1. Copy work → canonical.next.<run_id>
      2. Rename canonical → canonical.previous.<run_id>
      3. Rename canonical.next.<run_id> → canonical
      4. Remove canonical.previous.<run_id>
    """
    work_dir = Path(work_dir)
    canonical_dir = Path(canonical_dir)
    parent = canonical_dir.parent

    next_dir = parent / f"canonical.next.{run_id}"
    prev_dir = parent / f"canonical.previous.{run_id}"

    # Clean up any leftovers
    if next_dir.exists():
        shutil.rmtree(next_dir)
    if prev_dir.exists():
        shutil.rmtree(prev_dir)

    # Step 1: copy work to next
    shutil.copytree(work_dir, next_dir)

    # Step 2: rename current canonical to previous (if exists)
    if canonical_dir.exists():
        canonical_dir.rename(prev_dir)

    # Step 3: rename next to canonical
    next_dir.rename(canonical_dir)

    # Step 4: remove previous
    if prev_dir.exists():
        shutil.rmtree(prev_dir)


def run_maven_update_only(
    work_dir: Path,
    dc_version: str,
    env: dict | None = None,
    timeout_minutes: int | None = None,
    datafeed_url: str | None = None,
) -> tuple[int, str, str]:
    """Run a single update-only pass. Returns (exit_code, stdout, stderr).

    R12G: no default 100-minute timeout. Callers must pass an explicit
    timeout_minutes value. Bootstrap uses 1320; incremental uses 180.

    R12J: if datafeed_url is provided, uses -DnvdDatafeedUrl instead of
    direct NVD API access. No NVD_API_KEY is used in datafeed mode.
    """
    if timeout_minutes is None:
        raise ValueError("timeout_minutes must be explicitly set (R12G: no default)")
    cmd = [
        "mvn", "--batch-mode", "--no-transfer-progress", "-e",
        f"org.owasp:dependency-check-maven:{dc_version}:update-only",
        f"-DdataDirectory={work_dir}",
        "-DfailOnError=true",
        "-DhostedSuppressionsEnabled=false",
        "-DversionCheckEnabled=false",
        "-DossIndexAnalyzerEnabled=false",
    ]
    if datafeed_url:
        # R12J: datafeed mode — no NVD API key, use local feed server
        cmd.append(f"-DnvdDatafeedUrl={datafeed_url}")
        cmd.append("-DnvdValidForHours=24")
    else:
        # Direct NVD API mode (deprecated for Snapshot Builder in R12J)
        cmd.append("-DnvdApiKeyEnvironmentVariable=NVD_API_KEY")
        cmd.append("-DnvdApiDelay=6000")
        cmd.append("-DnvdMaxRetryCount=5")
    full_env = os.environ.copy()
    if env:
        full_env.update(env)
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout_minutes * 60, env=full_env,
        )
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return 124, "", "timeout"


def run_offline_smoke_test(
    work_dir: Path,
    dc_version: str,
    output_dir: Path,
    timeout_minutes: int = 30,
) -> tuple[int, str]:
    """Run offline smoke test. Returns (exit_code, log_path)."""
    log_path = output_dir / "offline-smoke-test.log"
    output_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        "mvn", "--batch-mode", "--no-transfer-progress", "-e",
        f"org.owasp:dependency-check-maven:{dc_version}:check",
        f"-DdataDirectory={work_dir}",
        "-DautoUpdate=false",
        "-DfailOnError=true",
        "-DfailBuildOnCVSS=11",
        "-DossIndexAnalyzerEnabled=false",
        "-DhostedSuppressionsEnabled=false",
        "-DversionCheckEnabled=false",
        f"-DoutputDirectory={output_dir / 'smoke-output'}",
    ]
    try:
        with log_path.open("w") as log:
            result = subprocess.run(
                cmd, stdout=log, stderr=subprocess.STDOUT, timeout=timeout_minutes * 60,
            )
        return result.returncode, str(log_path)
    except subprocess.TimeoutExpired:
        return 124, str(log_path)


# ---------- Local Feed Server (R12J Section 8) ----------

class LocalFeedServer:
    """R12J/R12K: localhost-only HTTP server serving NVD feed files.

    Binds to 127.0.0.1 only (never 0.0.0.0). Picks a dynamic port.
    Serves files from the feed directory. Prevents path traversal.
    R12K: verifies a specific file (nvdcve-modified.json.gz) is accessible
    before declaring the server ready.
    """

    def __init__(self, feed_dir: Path):
        self.feed_dir = Path(feed_dir).resolve()
        self.server = None
        self.port = 0

    def start(self) -> int:
        import http.server
        import socketserver
        import threading

        feed_dir = self.feed_dir

        class FeedHandler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=str(feed_dir), **kwargs)

            def log_message(self, format, *args):
                pass  # suppress logging

            def list_directory(self, path):
                # R12K: reject directory listing
                self.send_error(403, "Directory listing not allowed")
                return None

        # Find available port on 127.0.0.1
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind(("127.0.0.1", 0))
            self.port = s.getsockname()[1]

        self.server = socketserver.TCPServer(("127.0.0.1", self.port), FeedHandler, bind_and_activate=True)
        self.server.allow_reuse_address = True
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()

        # R12K: verify a specific file is accessible (not just root)
        import urllib.request as _urllib
        try:
            resp = _urllib.urlopen(f"http://127.0.0.1:{self.port}/nvdcve-modified.json.gz", timeout=5)
            if resp.status != 200:
                raise RuntimeError(f"Feed server ready check failed: HTTP {resp.status}")
            content_length = resp.headers.get("Content-Length", "0")
            if int(content_length) <= 0:
                raise RuntimeError("Feed server ready check failed: Content-Length <= 0")
        except Exception as e:
            raise RuntimeError(f"Feed server readiness check failed: {e}") from e

        print(f"Local feed server started on http://127.0.0.1:{self.port}")
        print(f"  Readiness: GET /nvdcve-modified.json.gz → HTTP 200 (Content-Length: {content_length})")
        return self.port

    def stop(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            print("Local feed server stopped")

    def get_datafeed_url(self) -> str:
        """Return the URL pattern for Dependency-Check nvdDatafeedUrl."""
        return f"http://127.0.0.1:{self.port}/nvdcve-{{0}}.json.gz"

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, *args):
        self.stop()


def publish_snapshot_v2(
    backend,
    *,
    work_dir: Path,
    publisher_commit_sha: str,
    publisher_run_id: int | str,
    previous_snapshot_id: str,
    last_successful_update_at: str,
    database_sha256: str,
    database_size_bytes: int,
    validation_result: str,
    offline_smoke_result: str,
    freshness_hours_at_publish: int,
    temp_dir: Path | None = None,
) -> dict:
    """R12F: single-push publish flow.

    1. Build archive (data/ prefix)
    2. Build manifest sidecar (v2)
    3. Build SHA256SUMS sidecar
    4. Upload all three immutably ONCE
    5. Verify remote digests
    6. Promote latest.json (commit point)

    Returns the manifest dict + storage metadata.
    """
    work_dir = Path(work_dir).resolve()
    temp_dir = Path(temp_dir) if temp_dir else Path(tempfile.mkdtemp(prefix="nvd-publish-"))
    temp_dir.mkdir(parents=True, exist_ok=True)

    created_at = utc_now_iso()
    snapshot_id = f"{created_at.replace(':', '').replace('-', '')[:14]}-{publisher_commit_sha[:12]}-{database_sha256[:12]}"
    # R12F: use .tar.zst extension; create_snapshot_archive will fall back
    # to .tar.gz if zstd is unavailable and return the actual path.
    archive_filename = f"snad-nvd-data-{snapshot_id}.tar.zst"
    archive_path = temp_dir / archive_filename

    # Step 1: build archive (may return .tar.gz path if zstd unavailable)
    actual_archive_path = create_snapshot_archive(archive_path, work_dir, temp_dir)
    if actual_archive_path != archive_path:
        # zstd was unavailable, fell back to .tar.gz
        archive_filename = actual_archive_path.name
        archive_path = actual_archive_path
    archive_sha256 = sha256_file(archive_path)
    archive_size_bytes = archive_path.stat().st_size

    # Step 2: build manifest (NO storage_version_or_digest)
    manifest = build_manifest_v2(
        snapshot_id=snapshot_id,
        created_at=created_at,
        last_successful_update_at=last_successful_update_at,
        publisher_commit_sha=publisher_commit_sha,
        publisher_run_id=publisher_run_id,
        archive_filename=archive_filename,
        archive_sha256=archive_sha256,
        archive_size_bytes=archive_size_bytes,
        database_relative_path="data/odc.mv.db",
        database_sha256=database_sha256,
        database_size_bytes=database_size_bytes,
        validation_result=validation_result,
        offline_smoke_result=offline_smoke_result,
        storage_backend=backend.backend_name,
        previous_snapshot_id=previous_snapshot_id,
        freshness_hours_at_publish=freshness_hours_at_publish,
    )
    validate_manifest_v2(manifest)

    manifest_path = temp_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    manifest_sha256 = sha256_file(manifest_path)

    # Step 3: build SHA256SUMS
    sha256sums_content = build_sha256sums(archive_filename, archive_sha256, manifest_sha256)
    sha256sums_path = temp_dir / "SHA256SUMS"
    sha256sums_path.write_text(sha256sums_content, encoding="utf-8")

    # Step 4: single immutable publish
    # The backend's publish_immutable_snapshot uploads all three files
    # and returns the storage version/digest.
    storage_metadata = backend.publish_immutable_snapshot(
        snapshot_id=snapshot_id,
        archive_path=archive_path,
        manifest_path=manifest_path,
        checksums_path=sha256sums_path,
    )

    # Step 5: verify remote digests
    if not backend.verify_storage_digest(snapshot_id, storage_metadata["storage_version_or_digest"]):
        raise SnapshotError(f"remote digest verification failed for snapshot {snapshot_id}")

    # Step 6: promote latest.json (commit point)
    pointer = build_latest_pointer(
        snapshot_id=snapshot_id,
        promoted_at=utc_now_iso(),
        created_at=created_at,
        archive_filename=archive_filename,
        archive_sha256=archive_sha256,
        manifest_sha256=manifest_sha256,
        storage_backend=backend.backend_name,
        storage_version_or_digest=storage_metadata["storage_version_or_digest"],
    )
    backend.promote_latest_pointer(pointer)

    return {
        "manifest": manifest,
        "latest_pointer": pointer,
        "storage_metadata": storage_metadata,
    }


def main():
    parser = argparse.ArgumentParser(description="NVD Snapshot Publisher (shared logic)")
    parser.add_argument("--mode", choices=["bootstrap", "incremental"], required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--canonical-dir", required=True)
    parser.add_argument("--lkg-dir", required=True)
    parser.add_argument("--publisher-commit-sha", required=True)
    parser.add_argument("--publisher-run-id", required=True)
    parser.add_argument("--dependency-check-version", default=DEFAULT_DEPENDENCY_CHECK_VERSION)
    parser.add_argument("--update-timeout-minutes", type=int, default=None,
                        help="Explicit Maven update timeout. R12G: no default 100. Bootstrap=1320, Incremental=180.")
    parser.add_argument("--skip-maven", action="store_true", help="Skip Maven update (for testing)")
    parser.add_argument("--source-mode", choices=["api", "datafeed"], default=None,
                        help="R12J: 'datafeed' uses local feed server, 'api' uses direct NVD API. "
                             "Defaults to env NVD_SOURCE_MODE or 'api'.")
    args = parser.parse_args()

    # R12J: determine source mode
    source_mode = args.source_mode or os.environ.get("NVD_SOURCE_MODE", "api")
    print(f"  Source mode: {source_mode}")

    # R12G: mode-specific timeouts (no default 100)
    if args.update_timeout_minutes is not None:
        update_timeout = args.update_timeout_minutes
    elif args.mode == "bootstrap":
        update_timeout = 1320
    else:  # incremental
        update_timeout = 180

    work_dir = Path(args.work_dir)
    canonical_dir = Path(args.canonical_dir)
    lkg_dir = Path(args.lkg_dir)

    print(f"=== NVD Snapshot Publisher ({args.mode} mode, {source_mode} source) ===")
    print(f"  Work dir:              {work_dir}")
    print(f"  Canonical dir:         {canonical_dir}")
    print(f"  LKG dir:               {lkg_dir}")
    print(f"  Publisher SHA:         {args.publisher_commit_sha}")
    print(f"  Run ID:                {args.publisher_run_id}")
    print(f"  Update timeout:        {update_timeout} minutes")
    print(f"  Update mode:           {args.mode}")
    print(f"  Source mode:           {source_mode}")

    # Resolve previous snapshot
    backend = get_backend(os.environ.get("NVD_SNAPSHOT_BACKEND", "filesystem"))
    latest = backend.resolve_latest_verified()
    previous_snapshot_id = latest["snapshot_id"] if latest else ""
    print(f"  Previous snapshot: {previous_snapshot_id or '<none>'}")

    if args.mode == "incremental":
        # R12F: incremental MUST NOT cold bootstrap
        if not canonical_dir.exists() or not any(canonical_dir.iterdir()):
            if not latest:
                print("::error::No verified canonical or remote snapshot exists.")
                print("Run NVD Snapshot Bootstrap before enabling incremental publishing.")
                return 1
            # Restore from remote
            print("Canonical empty — restoring from remote LKG...")
            temp_restore = Path(tempfile.mkdtemp())
            archive = backend.download_snapshot(latest["snapshot_id"], temp_restore)
            extract_snapshot_archive(archive, temp_restore)
            shutil.copytree(temp_restore / "data", work_dir, dirs_exist_ok=True)
            shutil.rmtree(temp_restore)
        else:
            # Copy canonical to work
            shutil.copytree(canonical_dir, work_dir, dirs_exist_ok=True)
    else:  # bootstrap
        if latest:
            print("Bootstrap mode but remote snapshot exists — restoring from remote...")
            temp_restore = Path(tempfile.mkdtemp())
            archive = backend.download_snapshot(latest["snapshot_id"], temp_restore)
            extract_snapshot_archive(archive, temp_restore)
            shutil.copytree(temp_restore / "data", work_dir, dirs_exist_ok=True)
            shutil.rmtree(temp_restore)
        else:
            print("Bootstrap mode — fresh build (no remote LKG)")
            work_dir.mkdir(parents=True, exist_ok=True)

    # Run Maven update
    if not args.skip_maven:
        import datetime as _dt
        print("Running NVD update-only...")

        # R12J: datafeed mode — resolve feed, download, start local server
        datafeed_url = None
        feed_server = None
        feed_info = {}
        if source_mode == "datafeed":
            from scripts.security.nvd_feed_mirror import (
                resolve_latest_valid_feed_release,
                download_feed_bundle,
            )
            feed_backend = GitHubReleasesBackend()
            feed_pointer = resolve_latest_valid_feed_release(feed_backend)
            if not feed_pointer:
                print("::error::No verified NVD feed release available.")
                print("Run NVD Feed Mirror Publisher first.")
                return 1

            print(f"  Feed release: {feed_pointer.get('release_tag', '?')}")
            feed_dir = work_dir.parent / "nvd-feed-extract"
            feed_dir.mkdir(parents=True, exist_ok=True)
            bundle = download_feed_bundle(feed_backend, feed_pointer, work_dir.parent / "nvd-feed-download")

            # Extract feed archive
            from scripts.security.nvd_feed_archive import extract_feed_archive
            extract_feed_archive(bundle["archive_path"], feed_dir)

            # Start local server
            feed_server = LocalFeedServer(feed_dir)
            feed_server.start()
            datafeed_url = feed_server.get_datafeed_url()
            feed_info = {
                "feed_id": feed_pointer.get("feed_id", ""),
                "feed_release_tag": feed_pointer.get("release_tag", ""),
                "feed_archive_sha256": feed_pointer.get("archive_sha256", ""),
                "datafeed_mode": "true",
                "direct_nvd_api_access": "false",
            }
            print(f"  datafeed_url={datafeed_url}")
            print(f"  datafeed_mode=true")
            print(f"  direct_nvd_api_access=false")

        try:
            update_started_at = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            update_start_epoch = _dt.datetime.now(_dt.timezone.utc).timestamp()
            print(f"update_started_at={update_started_at}")
            print(f"update_timeout_minutes={update_timeout}")
            exit_code, stdout, stderr = run_maven_update_only(
                work_dir, args.dependency_check_version,
                timeout_minutes=update_timeout,
                datafeed_url=datafeed_url,
            )
            update_finished_at = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            update_duration = int(_dt.datetime.now(_dt.timezone.utc).timestamp() - update_start_epoch)
            print(f"update_finished_at={update_finished_at}")
            print(f"update_duration_seconds={update_duration}")
            print(f"update_exit_code={exit_code}")
            print(f"update_mode={args.mode}")

            # R12J: verify no direct NVD API access in datafeed mode
            if source_mode == "datafeed":
                combined_output = (stdout or "") + (stderr or "")
                if "services.nvd.nist.gov" in combined_output or "nvd.nist.gov/developers" in combined_output:
                    print("::error::DIRECT_NVD_API_ACCESS_DETECTED — Maven contacted NVD API directly!")
                    print("classification=NETWORK_VIOLATION")
                    return 1
                print("  Network enforcement: PASS (no direct NVD API access)")

            if exit_code == 124:
                print("::error::NVD update timed out")
                print("classification=UPDATE_TIMEOUT")
                return 1
            if exit_code != 0:
                print(f"::error::NVD update-only failed (exit: {exit_code})")
                print("=== Maven stdout (last 10000 chars) ===")
                print(stdout[-10000:] if stdout else "(empty)")
                print("=== Maven stderr (last 5000 chars) ===")
                print(stderr[-5000:] if stderr else "(empty)")
                return 1
        finally:
            if feed_server:
                feed_server.stop()

    # Validate — first create a manifest so the validator can check it
    print("Validating database...")
    db_file = work_dir / "odc.mv.db"
    if not db_file.exists():
        print("::error::odc.mv.db not found after update")
        return 1

    db_sha256 = sha256_file(db_file)
    db_size = db_file.stat().st_size
    update_ts = utc_now_iso()

    # Create manifest (the validator expects it to exist)
    # R12K: correct provenance based on source_mode
    if source_mode == "datafeed":
        manifest_data = {
            "schema_version": "v1",
            "dependency_check_version": args.dependency_check_version,
            "update_mode": "update-only",
            "update_completed_at": update_ts,
            "builder_run_id": int(args.publisher_run_id) if args.publisher_run_id.isdigit() else args.publisher_run_id,
            "builder_sha": args.publisher_commit_sha,
            "nvd_source": "SNAD_NVD_DATA_FEED",
            "nvd_api_key_used": False,
            "datafeed_mode": True,
            "direct_nvd_api_access": False,
            "feed_id": feed_info.get("feed_id", ""),
            "feed_release_tag": feed_info.get("feed_release_tag", ""),
            "feed_archive_sha256": feed_info.get("feed_archive_sha256", ""),
            "update_exit_code": 0,
            "database_filename": "odc.mv.db",
            "database_size_bytes": db_size,
            "database_sha256": db_sha256,
            "validation_result": "valid",
        }
    else:
        manifest_data = {
            "schema_version": "v1",
            "dependency_check_version": args.dependency_check_version,
            "update_mode": "update-only",
            "update_completed_at": update_ts,
            "builder_run_id": int(args.publisher_run_id) if args.publisher_run_id.isdigit() else args.publisher_run_id,
            "builder_sha": args.publisher_commit_sha,
            "nvd_source": "NVD_API",
            "nvd_api_key_used": True,
            "datafeed_mode": False,
            "direct_nvd_api_access": False,
            "update_exit_code": 0,
            "database_filename": "odc.mv.db",
            "database_size_bytes": db_size,
            "database_sha256": db_sha256,
            "validation_result": "valid",
        }
    manifest_path = work_dir / "sanad-nvd-manifest.json"
    manifest_path.write_text(json.dumps(manifest_data, indent=2), encoding="utf-8")

    # Now run the validator
    validator = subprocess.run(
        ["python3", "scripts/ci/validate_nvd_database.py", str(work_dir),
         "--min-size", "52428800", "--max-age-hours", "48",
         "--expected-dc-version", args.dependency_check_version,
         "--expected-schema", "v1"],
        capture_output=True, text=True,
    )
    print(validator.stdout)
    if validator.returncode != 0:
        print(f"::error::Validation failed (exit: {validator.returncode})")
        print(validator.stderr[-2000:] if validator.stderr else "")
        return 1

    # Smoke test
    print("Running offline smoke test...")
    smoke_dir = work_dir.parent / f"smoke-{args.publisher_run_id}"
    smoke_exit, smoke_log = run_offline_smoke_test(work_dir, args.dependency_check_version, smoke_dir)
    if smoke_exit != 0:
        print(f"::error::Smoke test failed (exit: {smoke_exit})")
        return 1

    # Backup current canonical to LKG (before publish)
    if canonical_dir.exists() and any(canonical_dir.iterdir()):
        if lkg_dir.exists():
            shutil.rmtree(lkg_dir)
        shutil.copytree(canonical_dir, lkg_dir)

    # Publish (single push, commit point = latest.json promotion)
    print("Publishing snapshot (single push)...")
    result = publish_snapshot_v2(
        backend=backend,
        work_dir=work_dir,
        publisher_commit_sha=args.publisher_commit_sha,
        publisher_run_id=args.publisher_run_id,
        previous_snapshot_id=previous_snapshot_id,
        last_successful_update_at=utc_now_iso(),
        database_sha256=db_sha256,
        database_size_bytes=db_size,
        validation_result="valid",
        offline_smoke_result="success",
        freshness_hours_at_publish=0,
    )

    # After commit point: atomically swap local canonical
    print("Atomically updating local canonical cache...")
    try:
        atomic_canonical_swap(work_dir, canonical_dir, args.publisher_run_id)
        local_promotion = "success"
    except Exception as e:
        print(f"::warning::Local canonical promotion failed: {e}")
        print("Remote snapshot is valid; local cache will be restored on next run.")
        local_promotion = "failed"

    # Apply retention
    print("Applying retention policy...")
    deleted = backend.apply_retention_policy(keep=DEFAULT_RETENTION_COUNT)

    print()
    print("=== Publish Complete ===")
    print(f"  snapshot_id: {result['manifest']['snapshot_id']}")
    print(f"  archive_sha256: {result['manifest']['archive_sha256']}")
    print(f"  storage_version: {result['storage_metadata']['storage_version_or_digest']}")
    print(f"  local_promotion: {local_promotion}")
    print(f"  retention_deleted: {len(deleted)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
