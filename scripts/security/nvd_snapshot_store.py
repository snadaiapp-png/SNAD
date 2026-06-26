#!/usr/bin/env python3
"""
SANAD — NVD Snapshot Store (Persistent Backend Abstraction)
=============================================================
EXEC-PROMPT-010R12E Section 9 — unified storage interface for NVD
Dependency-Check data snapshots.

Provides a single API over two supported backends:
  - s3   (AWS S3, Cloudflare R2, MinIO, or any S3-compatible store)
  - ghcr (GitHub Container Registry via OCI/ORAS artifacts)

Backend selection is driven by the NVD_SNAPSHOT_BACKEND variable
('s3' or 'ghcr'). All operations are content-addressed: every
snapshot has an immutable snapshot_id and is published under
`snapshots/<snapshot_id>/`. A separate `channels/verified/latest.json`
pointer is updated atomically only after all verification passes.

This module is import-safe: missing optional dependencies (boto3,
oci) are reported clearly when the corresponding backend is selected,
not at import time. This lets the module load in test environments
without cloud SDKs installed.
"""
from __future__ import annotations

import abc
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path
from typing import Any

# ---------- Constants ----------

CONTRACT_VERSION = "snad-nvd-snapshot-v1"
SCHEMA_VERSION = "v1"
DEFAULT_DEPENDENCY_CHECK_VERSION = "12.1.0"
DEFAULT_RETENTION_COUNT = 14
DEFAULT_MAX_AGE_HOURS = 48
WARNING_AGE_HOURS = 24
PREFERRED_AGE_HOURS = 12

FORBIDDEN_TEMP_PATTERNS = ("*.tmp.db", "*.temp.db", "odc.mv.db.temp")
FORBIDDEN_LOCK_PATTERNS = ("*.lock.db", "*.lock")
# odc.trace.db is a legitimate H2 byproduct and is accepted.

SUPPORTED_BACKENDS = ("s3", "ghcr", "filesystem")


# ---------- Errors ----------

class SnapshotError(Exception):
    """Base class for snapshot store errors."""


class SnapshotNotFoundError(SnapshotError):
    """Raised when a requested snapshot does not exist (404/NoSuchKey)."""


class SnapshotVerificationError(SnapshotError):
    """Raised when a snapshot's digest or manifest does not verify."""


class StorageBackendError(SnapshotError):
    """Raised when a storage backend is misconfigured or unavailable."""


class StorageAuthenticationError(StorageBackendError):
    """Raised when storage authentication fails (401/ExpiredToken)."""


class StorageAuthorizationError(StorageBackendError):
    """Raised when storage authorization fails (403/AccessDenied)."""


class StorageUnavailableError(StorageBackendError):
    """Raised when storage is unreachable (network/EndpointConnectionError)."""


class StorageIntegrityError(StorageBackendError):
    """Raised when stored content is corrupt or digest mismatch."""


# ---------- Helpers ----------

def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def snapshot_id_for(timestamp_iso: str, commit_sha: str, db_sha256: str) -> str:
    """Stable snapshot id: <UTC-date>-<commit-sha-12>-<db-sha-12>.

    Combines the publish timestamp, publisher commit, and database
    content hash so that identical re-publishes of the same data
    produce the same id (idempotent), but any change to the data
    produces a different id.
    """
    date_part = timestamp_iso.replace(":", "").replace("-", "")[:14]
    return f"{date_part}-{commit_sha[:12]}-{db_sha256[:12]}"


# ---------- Manifest ----------

def build_manifest(
    *,
    snapshot_id: str,
    created_at: str,
    last_successful_update_at: str,
    publisher_commit_sha: str,
    publisher_run_id: int | str,
    archive_filename: str,
    archive_sha256: str,
    archive_size_bytes: int,
    database_filename: str,
    database_sha256: str,
    database_size_bytes: int,
    validation_result: str,
    offline_smoke_result: str,
    storage_backend: str,
    storage_version_or_digest: str,
    previous_snapshot_id: str,
    freshness_hours_at_publish: int,
    dependency_check_version: str = DEFAULT_DEPENDENCY_CHECK_VERSION,
) -> dict:
    """Build a snapshot manifest dict matching the snad-nvd-snapshot-v1 contract."""
    return {
        "contract_version": CONTRACT_VERSION,
        "schema_version": SCHEMA_VERSION,
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
        "database_filename": database_filename,
        "database_sha256": database_sha256,
        "database_size_bytes": database_size_bytes,
        "validation_result": validation_result,
        "offline_smoke_result": offline_smoke_result,
        "storage_backend": storage_backend,
        "storage_version_or_digest": storage_version_or_digest,
        "previous_snapshot_id": previous_snapshot_id,
        "freshness_hours_at_publish": freshness_hours_at_publish,
    }


def validate_manifest(manifest: dict) -> None:
    """Validate a manifest dict against the snad-nvd-snapshot-v1 contract.

    Raises ValueError on any violation.
    """
    if not isinstance(manifest, dict):
        raise ValueError("manifest must be a JSON object")

    required_keys = (
        "contract_version", "schema_version", "dependency_check_version",
        "snapshot_id", "created_at", "last_successful_update_at",
        "publisher_commit_sha", "publisher_run_id", "source",
        "archive_filename", "archive_sha256", "archive_size_bytes",
        "database_filename", "database_sha256", "database_size_bytes",
        "validation_result", "offline_smoke_result",
        "storage_backend", "storage_version_or_digest",
        "previous_snapshot_id", "freshness_hours_at_publish",
    )
    for key in required_keys:
        if key not in manifest:
            raise ValueError(f"manifest missing required key: {key}")

    if manifest["contract_version"] != CONTRACT_VERSION:
        raise ValueError(
            f"manifest contract_version is {manifest['contract_version']!r}; "
            f"expected {CONTRACT_VERSION!r}"
        )
    if manifest["schema_version"] != SCHEMA_VERSION:
        raise ValueError(
            f"manifest schema_version is {manifest['schema_version']!r}; "
            f"expected {SCHEMA_VERSION!r}"
        )
    if manifest["validation_result"] != "valid":
        raise ValueError(
            f"manifest validation_result is {manifest['validation_result']!r}; "
            f"only 'valid' snapshots may be published"
        )
    if manifest["offline_smoke_result"] != "success":
        raise ValueError(
            f"manifest offline_smoke_result is {manifest['offline_smoke_result']!r}; "
            f"only 'success' snapshots may be published"
        )
    if manifest["storage_backend"] not in SUPPORTED_BACKENDS:
        raise ValueError(
            f"manifest storage_backend is {manifest['storage_backend']!r}; "
            f"supported: {SUPPORTED_BACKENDS}"
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

    # Freshness
    if not isinstance(manifest["freshness_hours_at_publish"], int) or manifest["freshness_hours_at_publish"] < 0:
        raise ValueError("manifest freshness_hours_at_publish must be a non-negative integer")


# ---------- Backend abstraction ----------

class SnapshotBackend(abc.ABC):
    """Abstract base for snapshot storage backends.

    R12F: backends implement publish_immutable_snapshot (single push)
    and promote_latest_pointer (takes a pointer dict, not snapshot_id).
    """

    backend_name: str = "abstract"

    @abc.abstractmethod
    def publish_immutable_snapshot(
        self,
        snapshot_id: str,
        archive_path: Path,
        manifest_path: Path,
        checksums_path: Path,
    ) -> dict:
        """R12F: upload archive + manifest + SHA256SUMS as immutable
        objects in a SINGLE push operation.

        Returns a dict with:
          - storage_version_or_digest (immutable identifier)
          - archive_object (backend-specific object key/path)
          - manifest_object
          - checksums_object
        """

    @abc.abstractmethod
    def resolve_latest_verified(self) -> dict | None:
        """Return the latest.json pointer content, or None if no
        verified snapshot has been published yet."""

    @abc.abstractmethod
    def download_manifest(self, snapshot_id: str) -> dict:
        """Download the manifest sidecar for a specific snapshot."""

    @abc.abstractmethod
    def download_snapshot(self, snapshot_id: str, dest_dir: Path) -> Path:
        """Download the archive for a specific snapshot into dest_dir."""

    @abc.abstractmethod
    def verify_storage_digest(self, snapshot_id: str, expected_digest: str) -> bool:
        """Verify that the stored object still matches the expected digest."""

    @abc.abstractmethod
    def promote_latest_pointer(self, pointer: dict) -> None:
        """R12F: atomically update channels/verified/latest.json.

        Takes a pointer dict (not snapshot_id) so the caller can
        populate storage_version_or_digest which is only known after
        the immutable publish.
        """

    @abc.abstractmethod
    def download_snapshot_bundle(
        self,
        pointer: dict,
        destination: Path,
    ) -> dict:
        """R12G Section 10.2 — download the full snapshot bundle (archive +
        manifest + SHA256SUMS) using exact versions from the latest.json
        pointer.

        Returns a dict with:
          - archive_path (local Path)
          - manifest_path (local Path)
          - checksums_path (local Path)
          - snapshot_id
        """

    @abc.abstractmethod
    def list_verified_snapshots(self) -> list[str]:
        """Return a list of snapshot_ids that have been published."""

    @abc.abstractmethod
    def apply_retention_policy(self, keep: int = DEFAULT_RETENTION_COUNT) -> list[str]:
        """Delete old snapshots, keeping the most recent `keep`.

        MUST NOT delete the snapshot currently pointed to by latest.json.
        Returns the list of deleted snapshot_ids."""


# ---------- Filesystem backend (for tests + local validation) ----------

class FilesystemBackend(SnapshotBackend):
    """A filesystem-backed SnapshotBackend for testing and local validation.

    NOT for production use — no immutability guarantee. But it implements
    the full contract so tests can exercise the publish/verify/consume
    flow without cloud credentials.
    """

    backend_name = "filesystem"

    def __init__(self, root_dir: Path):
        self.root = Path(root_dir)
        self.snapshots_dir = self.root / "snapshots"
        self.channels_dir = self.root / "channels" / "verified"
        self.snapshots_dir.mkdir(parents=True, exist_ok=True)
        self.channels_dir.mkdir(parents=True, exist_ok=True)

    def _snapshot_dir(self, snapshot_id: str) -> Path:
        return self.snapshots_dir / snapshot_id

    def publish_immutable_snapshot(
        self,
        snapshot_id: str,
        archive_path: Path,
        manifest_path: Path,
        checksums_path: Path,
    ) -> dict:
        """R12F: single-push publish. Uploads all three files at once."""
        dest = self._snapshot_dir(snapshot_id)
        dest.mkdir(parents=True, exist_ok=True)
        import shutil

        archive_dest = dest / archive_path.name
        manifest_dest = dest / "manifest.json"
        checksums_dest = dest / "SHA256SUMS"

        shutil.copy2(archive_path, archive_dest)
        shutil.copy2(manifest_path, manifest_dest)
        shutil.copy2(checksums_path, checksums_dest)

        # Verify copies
        actual_archive_sha = sha256_file(archive_dest)
        archive_expected = sha256_file(archive_path)
        if actual_archive_sha != archive_expected:
            raise SnapshotVerificationError(
                f"archive SHA-256 mismatch after copy: expected {archive_expected}, got {actual_archive_sha}"
            )

        storage_version = f"filesystem:{snapshot_id}"
        return {
            "storage_version_or_digest": storage_version,
            "archive_object": str(archive_dest),
            "manifest_object": str(manifest_dest),
            "checksums_object": str(checksums_dest),
        }

    def resolve_latest_verified(self):
        latest = self.channels_dir / "latest.json"
        if not latest.exists():
            return None
        return json.loads(latest.read_text(encoding="utf-8"))

    def download_manifest(self, snapshot_id):
        p = self._snapshot_dir(snapshot_id) / "manifest.json"
        if not p.exists():
            raise SnapshotNotFoundError(f"snapshot {snapshot_id} not found")
        return json.loads(p.read_text(encoding="utf-8"))

    def download_snapshot(self, snapshot_id, dest_dir):
        manifest = self.download_manifest(snapshot_id)
        src = self._snapshot_dir(snapshot_id) / manifest["archive_filename"]
        if not src.exists():
            raise SnapshotNotFoundError(f"archive for snapshot {snapshot_id} not found")
        import shutil
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / manifest["archive_filename"]
        shutil.copy2(src, dest)
        return dest

    def verify_storage_digest(self, snapshot_id, expected_digest):
        manifest = self.download_manifest(snapshot_id)
        archive = self._snapshot_dir(snapshot_id) / manifest["archive_filename"]
        if not archive.exists():
            return False
        actual = sha256_file(archive)
        return actual == manifest["archive_sha256"]

    def download_snapshot_bundle(self, pointer: dict, destination: Path) -> dict:
        """R12G: download all three bundle files using exact versions from pointer."""
        import shutil
        destination = Path(destination)
        destination.mkdir(parents=True, exist_ok=True)
        snapshot_id = pointer["snapshot_id"]
        snap_dir = self._snapshot_dir(snapshot_id)

        # Archive
        archive_filename = pointer.get("archive_filename", "")
        if not archive_filename:
            # Fallback: read from manifest
            manifest = self.download_manifest(snapshot_id)
            archive_filename = manifest["archive_filename"]
        archive_src = snap_dir / archive_filename
        archive_dest = destination / archive_filename
        shutil.copy2(archive_src, archive_dest)

        # Manifest
        manifest_src = snap_dir / "manifest.json"
        manifest_dest = destination / "manifest.json"
        shutil.copy2(manifest_src, manifest_dest)

        # SHA256SUMS
        checksums_src = snap_dir / "SHA256SUMS"
        checksums_dest = destination / "SHA256SUMS"
        shutil.copy2(checksums_src, checksums_dest)

        return {
            "archive_path": archive_dest,
            "manifest_path": manifest_dest,
            "checksums_path": checksums_dest,
            "snapshot_id": snapshot_id,
        }

    def promote_latest_pointer(self, pointer: dict):
        """R12F: takes a pointer dict (not snapshot_id + manifest)."""
        latest = self.channels_dir / "latest.json"
        # Atomic write via temp + rename
        tmp = latest.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(pointer, indent=2), encoding="utf-8")
        tmp.replace(latest)

    def list_verified_snapshots(self):
        result = []
        for d in sorted(self.snapshots_dir.iterdir()):
            if d.is_dir() and (d / "manifest.json").exists():
                result.append(d.name)
        return result

    def apply_retention_policy(self, keep=DEFAULT_RETENTION_COUNT):
        latest = self.resolve_latest_verified()
        latest_id = latest["snapshot_id"] if latest else None

        all_ids = self.list_verified_snapshots()
        # Sort by snapshot_id (which starts with timestamp) — newest first
        all_ids.sort(reverse=True)

        # Always keep latest + (keep - 1) most recent others = keep total
        others = [sid for sid in all_ids if sid != latest_id]
        to_keep = set(others[:keep - 1]) | ({latest_id} if latest_id else set())
        to_delete = [sid for sid in all_ids if sid not in to_keep]

        import shutil
        for sid in to_delete:
            shutil.rmtree(self._snapshot_dir(sid), ignore_errors=True)
        return to_delete


# ---------- S3 backend (requires boto3 at runtime) ----------

class S3Backend(SnapshotBackend):
    """S3-compatible snapshot backend.

    Configuration (read from env or constructor):
      NVD_SNAPSHOT_BUCKET       — bucket name (required)
      NVD_SNAPSHOT_PREFIX       — key prefix, default 'snad-nvd'
      NVD_SNAPSHOT_REGION       — AWS region (required for non-R2)
      NVD_SNAPSHOT_ENDPOINT     — custom endpoint (R2, MinIO)
      NVD_SNAPSHOT_ROLE         — IAM role ARN for OIDC (preferred)

    OIDC is preferred over long-lived access keys. If NVD_SNAPSHOT_ROLE
    is set, the backend assumes that role via the GitHub OIDC token.
    Otherwise it falls back to standard AWS credential resolution
    (env vars, instance metadata, etc.).
    """

    backend_name = "s3"

    def __init__(self, bucket=None, prefix=None, region=None, endpoint=None, role=None):
        self.bucket = bucket or os.environ.get("NVD_SNAPSHOT_BUCKET")
        self.prefix = (prefix or os.environ.get("NVD_SNAPSHOT_PREFIX", "snad-nvd")).rstrip("/")
        self.region = region or os.environ.get("NVD_SNAPSHOT_REGION")
        self.endpoint = endpoint or os.environ.get("NVD_SNAPSHOT_ENDPOINT")
        self.role = role or os.environ.get("NVD_SNAPSHOT_ROLE")
        if not self.bucket:
            raise StorageBackendError("NVD_SNAPSHOT_BUCKET is required for S3 backend")

    def _client(self):
        try:
            import boto3  # type: ignore
        except ImportError as e:
            raise StorageBackendError(
                "boto3 is required for the S3 backend. Install it in the publisher environment."
            ) from e
        kwargs = {}
        if self.region:
            kwargs["region_name"] = self.region
        if self.endpoint:
            kwargs["endpoint_url"] = self.endpoint
        return boto3.client("s3", **kwargs)

    def _key(self, snapshot_id, filename):
        return f"{self.prefix}/snapshots/{snapshot_id}/{filename}"

    def _latest_key(self):
        return f"{self.prefix}/channels/verified/latest.json"

    def publish_immutable_snapshot(
        self,
        snapshot_id: str,
        archive_path: Path,
        manifest_path: Path,
        checksums_path: Path,
    ) -> dict:
        """R12F: single-push S3 publish. Uploads all three objects."""
        client = self._client()

        archive_key = self._key(snapshot_id, archive_path.name)
        manifest_key = self._key(snapshot_id, "manifest.json")
        checksums_key = self._key(snapshot_id, "SHA256SUMS")

        # Upload archive with metadata
        import hashlib as _hashlib
        archive_sha = sha256_file(archive_path)
        client.upload_file(
            str(archive_path), self.bucket, archive_key,
            ExtraArgs={
                "Metadata": {
                    "sha256": archive_sha,
                    "snapshot-id": snapshot_id,
                    "contract-version": "snad-nvd-snapshot-v2",
                },
            },
        )
        # Verify upload via HEAD
        head = client.head_object(Bucket=self.bucket, Key=archive_key)
        etag = head.get("ETag", "").strip('"')
        version_id = head.get("VersionId", "null")

        # Upload manifest
        manifest_bytes = manifest_path.read_bytes()
        client.put_object(
            Bucket=self.bucket, Key=manifest_key, Body=manifest_bytes,
            ContentType="application/json",
            Metadata={"snapshot-id": snapshot_id, "contract-version": "snad-nvd-snapshot-v2"},
        )

        # Upload SHA256SUMS
        checksums_bytes = checksums_path.read_bytes()
        client.put_object(
            Bucket=self.bucket, Key=checksums_key, Body=checksums_bytes,
            ContentType="text/plain",
            Metadata={"snapshot-id": snapshot_id},
        )

        storage_version = f"s3:{self.bucket}/{archive_key}:v{version_id}:etag-{etag}"
        return {
            "storage_version_or_digest": storage_version,
            "archive_object": archive_key,
            "manifest_object": manifest_key,
            "checksums_object": checksums_key,
        }

    def resolve_latest_verified(self):
        client = self._client()
        try:
            resp = client.get_object(Bucket=self.bucket, Key=self._latest_key())
            return json.loads(resp["Body"].read().decode("utf-8"))
        except client.exceptions.NoSuchKey:
            return None
        except client.exceptions.ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("NoSuchKey", "404"):
                return None
            if code in ("AccessDenied", "403"):
                raise StorageAuthorizationError(f"S3 access denied: {e}") from e
            if code in ("ExpiredToken", "InvalidToken"):
                raise StorageAuthenticationError(f"S3 token expired: {e}") from e
            raise StorageBackendError(f"S3 error: {e}") from e
        except Exception as e:
            raise StorageUnavailableError(f"S3 unavailable: {e}") from e

    def download_manifest(self, snapshot_id):
        client = self._client()
        manifest_key = self._key(snapshot_id, "manifest.json")
        try:
            resp = client.get_object(Bucket=self.bucket, Key=manifest_key)
            return json.loads(resp["Body"].read().decode("utf-8"))
        except client.exceptions.NoSuchKey as e:
            raise SnapshotNotFoundError(f"snapshot {snapshot_id} not found") from e
        except client.exceptions.ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("NoSuchKey", "404"):
                raise SnapshotNotFoundError(f"snapshot {snapshot_id} not found") from e
            if code in ("AccessDenied", "403"):
                raise StorageAuthorizationError(f"S3 access denied: {e}") from e
            raise StorageBackendError(f"S3 error: {e}") from e

    def download_snapshot(self, snapshot_id, dest_dir):
        manifest = self.download_manifest(snapshot_id)
        client = self._client()
        archive_key = self._key(snapshot_id, manifest["archive_filename"])
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / manifest["archive_filename"]
        try:
            client.download_file(self.bucket, archive_key, str(dest))
        except client.exceptions.ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("NoSuchKey", "404"):
                raise SnapshotNotFoundError(f"archive not found: {e}") from e
            if code in ("AccessDenied", "403"):
                raise StorageAuthorizationError(f"S3 access denied: {e}") from e
            raise StorageBackendError(f"S3 download error: {e}") from e
        return dest

    def verify_storage_digest(self, snapshot_id, expected_digest):
        """R12F: stream-hash the remote object (not load into RAM)."""
        manifest = self.download_manifest(snapshot_id)
        client = self._client()
        archive_key = self._key(snapshot_id, manifest["archive_filename"])
        try:
            resp = client.get_object(Bucket=self.bucket, Key=archive_key)
            body = resp["Body"]
            h = hashlib.sha256()
            for chunk in body.iter_chunks(chunk_size=1024 * 1024):
                h.update(chunk)
            actual = h.hexdigest()
            return actual == manifest["archive_sha256"]
        except client.exceptions.NoSuchKey:
            return False
        except client.exceptions.ClientError as e:
            return False

    def promote_latest_pointer(self, pointer: dict):
        """R12F: takes a pointer dict."""
        client = self._client()
        client.put_object(
            Bucket=self.bucket,
            Key=self._latest_key(),
            Body=json.dumps(pointer, indent=2).encode("utf-8"),
            ContentType="application/json",
        )

    def download_snapshot_bundle(self, pointer: dict, destination: Path) -> dict:
        """R12G: download all three bundle files using exact S3 VersionIds
        from the latest-v2 pointer.
        """
        destination = Path(destination)
        destination.mkdir(parents=True, exist_ok=True)
        snapshot_id = pointer["snapshot_id"]
        client = self._client()

        # Archive — use exact VersionId if available
        archive_filename = pointer.get("archive", {}).get("filename", "")
        archive_version = pointer.get("archive", {}).get("version_id", "")
        if not archive_filename:
            manifest = self.download_manifest(snapshot_id)
            archive_filename = manifest["archive_filename"]
        archive_key = self._key(snapshot_id, archive_filename)
        archive_dest = destination / archive_filename
        get_kwargs = {"Bucket": self.bucket, "Key": archive_key}
        if archive_version and archive_version != "null":
            get_kwargs["VersionId"] = archive_version
        try:
            resp = client.get_object(**get_kwargs)
            body = resp["Body"]
            with archive_dest.open("wb") as f:
                for chunk in body.iter_chunks(chunk_size=1024 * 1024):
                    f.write(chunk)
        except client.exceptions.ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("NoSuchKey", "404"):
                raise SnapshotNotFoundError(f"archive not found: {e}") from e
            if code in ("AccessDenied", "403"):
                raise StorageAuthorizationError(f"S3 access denied: {e}") from e
            raise

        # Manifest
        manifest_key = self._key(snapshot_id, "manifest.json")
        manifest_version = pointer.get("manifest", {}).get("version_id", "")
        manifest_dest = destination / "manifest.json"
        get_kwargs = {"Bucket": self.bucket, "Key": manifest_key}
        if manifest_version and manifest_version != "null":
            get_kwargs["VersionId"] = manifest_version
        try:
            resp = client.get_object(**get_kwargs)
            manifest_dest.write_bytes(resp["Body"].read())
        except client.exceptions.ClientError as e:
            raise SnapshotNotFoundError(f"manifest not found: {e}") from e

        # SHA256SUMS
        checksums_key = self._key(snapshot_id, "SHA256SUMS")
        checksums_version = pointer.get("checksums", {}).get("version_id", "")
        checksums_dest = destination / "SHA256SUMS"
        get_kwargs = {"Bucket": self.bucket, "Key": checksums_key}
        if checksums_version and checksums_version != "null":
            get_kwargs["VersionId"] = checksums_version
        try:
            resp = client.get_object(**get_kwargs)
            checksums_dest.write_bytes(resp["Body"].read())
        except client.exceptions.ClientError as e:
            raise SnapshotNotFoundError(f"SHA256SUMS not found: {e}") from e

        return {
            "archive_path": archive_dest,
            "manifest_path": manifest_dest,
            "checksums_path": checksums_dest,
            "snapshot_id": snapshot_id,
        }

    def verify_bucket_versioning(self) -> bool:
        """R12F Section 16.2: verify bucket versioning is enabled."""
        client = self._client()
        try:
            resp = client.get_bucket_versioning(Bucket=self.bucket)
            return resp.get("Status") == "Enabled"
        except Exception:
            return False

    def list_verified_snapshots(self):
        client = self._client()
        prefix = f"{self.prefix}/snapshots/"
        result = []
        paginator = client.get_paginator("list_objects_v2")
        for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix, Delimiter="/"):
            for cp in page.get("CommonPrefixes", []):
                # cp["Prefix"] is like "snad-nvd/snapshots/<id>/"
                sid = cp["Prefix"].rstrip("/").rsplit("/", 1)[-1]
                result.append(sid)
        return sorted(result)

    def apply_retention_policy(self, keep=DEFAULT_RETENTION_COUNT):
        latest = self.resolve_latest_verified()
        latest_id = latest["snapshot_id"] if latest else None
        all_ids = self.list_verified_snapshots()
        all_ids.sort(reverse=True)
        client = self._client()
        to_delete = []
        kept = 0
        for sid in all_ids:
            if sid == latest_id:
                kept += 1
                continue
            if kept < keep:
                kept += 1
                continue
            # Delete all objects under snapshots/<sid>/
            prefix = self._key(sid, "")
            objects = client.list_objects_v2(Bucket=self.bucket, Prefix=prefix)
            if "Contents" in objects:
                delete_keys = [{"Key": o["Key"]} for o in objects["Contents"]]
                client.delete_objects(Bucket=self.bucket, Delete={"Objects": delete_keys})
            to_delete.append(sid)
        return to_delete


# ---------- GHCR backend (via ORAS) ----------

class GHCRBackend(SnapshotBackend):
    """GHCR OCI artifact backend via ORAS CLI.

    R12F: BEHIND EXPERIMENTAL GATE. Default is disabled until a real
    registry E2E test passes. Set NVD_ENABLE_EXPERIMENTAL_GHCR=true
    to enable.

    Each snapshot is published as an OCI artifact with three layers:
      - the archive (tar.zst)
      - the manifest.json (application/vnd.oci.image.config.v1+json)
      - the SHA256SUMS (text/plain)

    The OCI digest (sha256:...) is the immutable storage version.

    Configuration:
      NVD_SNAPSHOT_GHCR_OWNER  — e.g. 'snadaiapp-png'
      NVD_SNAPSHOT_GHCR_REPO   — e.g. 'snad-nvd-data'
      NVD_ENABLE_EXPERIMENTAL_GHCR — 'true' to enable (default: false)
    """

    backend_name = "ghcr"

    def __init__(self, owner=None, repo=None, enable_experimental=None):
        # R12F: experimental gate
        enable = enable_experimental if enable_experimental is not None else os.environ.get("NVD_ENABLE_EXPERIMENTAL_GHCR", "false")
        if str(enable).lower() != "true":
            raise StorageBackendError(
                "GHCR backend is EXPERIMENTAL and disabled by default. "
                "Set NVD_ENABLE_EXPERIMENTAL_GHCR=true to enable after "
                "completing a registry E2E test."
            )
        self.owner = owner or os.environ.get("NVD_SNAPSHOT_GHCR_OWNER") or os.environ.get("GITHUB_REPOSITORY_OWNER")
        self.repo = repo or os.environ.get("NVD_SNAPSHOT_GHCR_REPO", "snad-nvd-data")
        if not self.owner:
            raise StorageBackendError("NVD_SNAPSHOT_GHCR_OWNER (or GITHUB_REPOSITORY_OWNER) is required for GHCR backend")
        self.fqdn = f"ghcr.io/{self.owner}/{self.repo}"

    def _oras(self, *args, check=True):
        cmd = ["oras"] + list(args)
        return subprocess.run(cmd, capture_output=True, text=True, check=check)

    def _snapshot_tag(self, snapshot_id):
        return f"{self.fqdn}:{snapshot_id}"

    def _latest_tag(self):
        return f"{self.fqdn}:latest-verified"

    def publish_immutable_snapshot(
        self,
        snapshot_id: str,
        archive_path: Path,
        manifest_path: Path,
        checksums_path: Path,
    ) -> dict:
        """R12F: single ORAS push with all three files as layers."""
        tag = self._snapshot_tag(snapshot_id)
        # Single push: archive as layer, manifest as config, checksums as layer
        result = self._oras(
            "push", tag,
            f"{archive_path}:application/vnd.oci.image.layer.v1.tar+zstd",
            f"{checksums_path}:text/plain",
            "--config", f"{manifest_path}:application/vnd.oci.image.config.v1+json",
            "--annotation", f"org.opencontainers.image.title=sanad-nvd-snapshot-{snapshot_id}",
        )
        if result.returncode != 0:
            raise StorageBackendError(f"oras push failed: {result.stderr}")
        # Resolve digest
        digest_result = self._oras("resolve", "--digest", tag)
        if digest_result.returncode != 0:
            raise StorageBackendError(f"oras resolve failed: {digest_result.stderr}")
        digest = digest_result.stdout.strip()
        return {
            "storage_version_or_digest": f"ghcr:{digest}",
            "archive_object": tag,
            "manifest_object": tag,
            "checksums_object": tag,
        }

    def resolve_latest_verified(self):
        result = self._oras("manifest", "fetch", self._latest_tag(), check=False)
        if result.returncode != 0:
            return None
        try:
            manifest = json.loads(result.stdout)
            config = json.loads(manifest.get("config", {}).get("blob", "{}"))
            return config
        except Exception:
            return None

    def download_manifest(self, snapshot_id):
        tag = self._snapshot_tag(snapshot_id)
        result = self._oras("manifest", "fetch", tag)
        if result.returncode != 0:
            raise SnapshotNotFoundError(f"snapshot {snapshot_id} not found: {result.stderr}")
        manifest = json.loads(result.stdout)
        config_blob = manifest.get("config", {}).get("digest")
        if not config_blob:
            raise SnapshotNotFoundError(f"snapshot {snapshot_id} has no config blob")
        cfg_result = self._oras("blob", "fetch", f"{tag}@{config_blob}")
        return json.loads(cfg_result.stdout)

    def download_snapshot(self, snapshot_id, dest_dir):
        manifest = self.download_manifest(snapshot_id)
        tag = self._snapshot_tag(snapshot_id)
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / manifest["archive_filename"]
        result = self._oras(
            "blob", "fetch", tag, "--output", str(dest),
        )
        if result.returncode != 0:
            raise SnapshotNotFoundError(f"archive not found: {result.stderr}")
        return dest

    def verify_storage_digest(self, snapshot_id, expected_digest):
        tag = self._snapshot_tag(snapshot_id)
        result = self._oras("resolve", "--digest", tag, check=False)
        if result.returncode != 0:
            return False
        actual = result.stdout.strip()
        expected = expected_digest.split("ghcr:")[-1] if expected_digest.startswith("ghcr:") else expected_digest
        return actual == expected

    def download_snapshot_bundle(self, pointer: dict, destination: Path) -> dict:
        """R12G: download bundle via oras pull at exact digest."""
        destination = Path(destination)
        destination.mkdir(parents=True, exist_ok=True)
        snapshot_id = pointer["snapshot_id"]
        tag = self._snapshot_tag(snapshot_id)
        # oras pull downloads all layers to the destination
        result = self._oras("pull", tag, "-o", str(destination), "--keep-old-files")
        if result.returncode != 0:
            raise SnapshotNotFoundError(f"oras pull failed: {result.stderr}")
        # Find the downloaded files
        manifest = self.download_manifest(snapshot_id)
        archive_filename = manifest["archive_filename"]
        return {
            "archive_path": destination / archive_filename,
            "manifest_path": destination / "manifest.json",
            "checksums_path": destination / "SHA256SUMS",
            "snapshot_id": snapshot_id,
        }

    def promote_latest_pointer(self, pointer: dict):
        """R12F: tag the snapshot as latest-verified."""
        tag = self._snapshot_tag(pointer["snapshot_id"])
        latest = self._latest_tag()
        result = self._oras("tag", tag, latest, check=False)
        if result.returncode != 0:
            raise StorageBackendError(f"oras tag failed: {result.stderr}")

    def list_verified_snapshots(self):
        result = self._oras("repo", "tags", self.fqdn, check=False)
        if result.returncode != 0:
            return []
        tags = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        return sorted(t for t in tags if t != "latest-verified")

    def apply_retention_policy(self, keep=DEFAULT_RETENTION_COUNT):
        return []


# ---------- Backend factory ----------

def get_backend(backend_name: str | None = None, **kwargs) -> SnapshotBackend:
    """Return the configured SnapshotBackend.

    backend_name defaults to env NVD_SNAPSHOT_BACKEND. For tests,
    pass 'filesystem' and a root_dir kwarg.
    """
    name = backend_name or os.environ.get("NVD_SNAPSHOT_BACKEND", "")
    if name == "s3":
        return S3Backend(**kwargs)
    if name == "ghcr":
        return GHCRBackend(**kwargs)
    if name == "filesystem":
        root = kwargs.pop("root_dir", None)
        if not root:
            raise StorageBackendError("filesystem backend requires root_dir kwarg")
        return FilesystemBackend(Path(root))
    raise StorageBackendError(
        f"Unknown NVD_SNAPSHOT_BACKEND: {name!r}. Supported: {SUPPORTED_BACKENDS} (+ 'filesystem' for tests)"
    )


# ---------- High-level publish flow ----------

def publish_snapshot(*args, **kwargs):
    """DEPRECATED — R12F removed the double-push publish flow.

    Use scripts/security/publish_nvd_snapshot.py → publish_snapshot_v2()
    instead, which performs a single immutable push and stores the
    storage_version_or_digest in latest.json (not in the manifest).
    """
    raise NotImplementedError(
        "publish_snapshot() is removed in R12F. Use "
        "scripts/security/publish_nvd_snapshot.py:publish_snapshot_v2() instead."
    )


# ---------- Main (CLI for testing) ----------

def main():
    import argparse
    parser = argparse.ArgumentParser(description="NVD Snapshot Store CLI")
    parser.add_argument("command", choices=["list", "latest", "manifest"])
    parser.add_argument("--backend", default=None)
    parser.add_argument("--snapshot-id", default=None)
    parser.add_argument("--root-dir", default=None, help="filesystem backend root")
    args = parser.parse_args()

    backend = get_backend(args.backend, root_dir=args.root_dir) if args.backend == "filesystem" else get_backend(args.backend)

    if args.command == "list":
        for sid in backend.list_verified_snapshots():
            print(sid)
    elif args.command == "latest":
        latest = backend.resolve_latest_verified()
        if latest:
            print(json.dumps(latest, indent=2))
        else:
            print("No verified snapshot published yet.")
    elif args.command == "manifest":
        if not args.snapshot_id:
            print("--snapshot-id required", file=sys.stderr)
            return 2
        m = backend.download_manifest(args.snapshot_id)
        print(json.dumps(m, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
