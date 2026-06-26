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
    """Raised when a requested snapshot does not exist."""


class SnapshotVerificationError(SnapshotError):
    """Raised when a snapshot's digest or manifest does not verify."""


class StorageBackendError(SnapshotError):
    """Raised when a storage backend is misconfigured or unavailable."""


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
    """Abstract base for snapshot storage backends."""

    backend_name: str = "abstract"

    @abc.abstractmethod
    def publish_snapshot(
        self,
        snapshot_id: str,
        archive_path: Path,
        manifest: dict,
    ) -> str:
        """Upload the archive + manifest under snapshots/<snapshot_id>/.

        Returns the immutable storage version or digest (e.g. S3 ETag,
        OCI digest) that consumers can use to verify they got the
        exact bytes that were published.
        """

    @abc.abstractmethod
    def resolve_latest_verified(self) -> dict | None:
        """Return the latest.json pointer content, or None if no
        verified snapshot has been published yet."""

    @abc.abstractmethod
    def download_manifest(self, snapshot_id: str) -> dict:
        """Download the manifest for a specific snapshot."""

    @abc.abstractmethod
    def download_snapshot(self, snapshot_id: str, dest_dir: Path) -> Path:
        """Download the archive for a specific snapshot into dest_dir.

        Returns the local path of the downloaded archive."""

    @abc.abstractmethod
    def verify_storage_digest(self, snapshot_id: str, expected_digest: str) -> bool:
        """Verify that the stored object still matches the expected digest."""

    @abc.abstractmethod
    def promote_latest_pointer(self, snapshot_id: str, manifest: dict) -> None:
        """Atomically update channels/verified/latest.json to point at snapshot_id."""

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

    def publish_snapshot(self, snapshot_id, archive_path, manifest):
        validate_manifest(manifest)
        dest = self._snapshot_dir(snapshot_id)
        dest.mkdir(parents=True, exist_ok=True)
        archive_dest = dest / manifest["archive_filename"]
        manifest_dest = dest / "manifest.json"
        sha256sums_dest = dest / "SHA256SUMS"

        # Copy archive
        import shutil
        shutil.copy2(archive_path, archive_dest)

        # Verify copy
        actual_sha = sha256_file(archive_dest)
        if actual_sha != manifest["archive_sha256"]:
            raise SnapshotVerificationError(
                f"archive SHA-256 mismatch after copy: expected {manifest['archive_sha256']}, got {actual_sha}"
            )

        # Write manifest
        manifest_dest.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

        # Write SHA256SUMS
        lines = [
            f"{manifest['archive_sha256']}  {manifest['archive_filename']}",
            f"{sha256_bytes(json.dumps(manifest, sort_keys=True).encode())}  manifest.json",
        ]
        sha256sums_dest.write_text("\n".join(lines) + "\n", encoding="utf-8")

        return f"filesystem:{snapshot_id}"

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

    def promote_latest_pointer(self, snapshot_id, manifest):
        latest = self.channels_dir / "latest.json"
        pointer = {
            "snapshot_id": snapshot_id,
            "promoted_at": utc_now_iso(),
            "archive_sha256": manifest["archive_sha256"],
            "database_sha256": manifest["database_sha256"],
            "created_at": manifest["created_at"],
            "storage_backend": manifest["storage_backend"],
            "storage_version_or_digest": manifest["storage_version_or_digest"],
        }
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

    def publish_snapshot(self, snapshot_id, archive_path, manifest):
        validate_manifest(manifest)
        client = self._client()

        # Upload archive
        archive_key = self._key(snapshot_id, manifest["archive_filename"])
        client.upload_file(str(archive_path), self.bucket, archive_key)

        # Verify upload via HEAD
        head = client.head_object(Bucket=self.bucket, Key=archive_key)
        etag = head.get("ETag", "").strip('"')

        # Upload manifest
        manifest_key = self._key(snapshot_id, "manifest.json")
        client.put_object(
            Bucket=self.bucket,
            Key=manifest_key,
            Body=json.dumps(manifest, indent=2).encode("utf-8"),
            ContentType="application/json",
        )

        return f"s3:{self.bucket}/{archive_key}:{etag}"

    def resolve_latest_verified(self):
        client = self._client()
        try:
            resp = client.get_object(Bucket=self.bucket, Key=self._latest_key())
            return json.loads(resp["Body"].read().decode("utf-8"))
        except Exception:
            return None

    def download_manifest(self, snapshot_id):
        client = self._client()
        manifest_key = self._key(snapshot_id, "manifest.json")
        try:
            resp = client.get_object(Bucket=self.bucket, Key=manifest_key)
            return json.loads(resp["Body"].read().decode("utf-8"))
        except Exception as e:
            raise SnapshotNotFoundError(f"snapshot {snapshot_id} not found: {e}") from e

    def download_snapshot(self, snapshot_id, dest_dir):
        manifest = self.download_manifest(snapshot_id)
        client = self._client()
        archive_key = self._key(snapshot_id, manifest["archive_filename"])
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / manifest["archive_filename"]
        client.download_file(self.bucket, archive_key, str(dest))
        return dest

    def verify_storage_digest(self, snapshot_id, expected_digest):
        # For S3, we verify by re-downloading and hashing, or by checking
        # that the stored object's ETag/version matches. SHA-256 isn't
        # directly exposed via S3 HEAD, so we re-download and verify.
        manifest = self.download_manifest(snapshot_id)
        client = self._client()
        archive_key = self._key(snapshot_id, manifest["archive_filename"])
        try:
            resp = client.get_object(Bucket=self.bucket, Key=archive_key)
            data = resp["Body"].read()
            actual = sha256_bytes(data)
            return actual == manifest["archive_sha256"]
        except Exception:
            return False

    def promote_latest_pointer(self, snapshot_id, manifest):
        client = self._client()
        pointer = {
            "snapshot_id": snapshot_id,
            "promoted_at": utc_now_iso(),
            "archive_sha256": manifest["archive_sha256"],
            "database_sha256": manifest["database_sha256"],
            "created_at": manifest["created_at"],
            "storage_backend": manifest["storage_backend"],
            "storage_version_or_digest": manifest["storage_version_or_digest"],
        }
        client.put_object(
            Bucket=self.bucket,
            Key=self._latest_key(),
            Body=json.dumps(pointer, indent=2).encode("utf-8"),
            ContentType="application/json",
        )

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

    Each snapshot is published as an OCI artifact with two layers:
      - the archive (tar.zst)
      - the manifest.json (application/vnd.oci.image.config.v1+json)

    The OCI digest (sha256:...) is the immutable storage version.

    Configuration:
      NVD_SNAPSHOT_GHCR_OWNER  — e.g. 'snadaiapp-png'
      NVD_SNAPSHOT_GHCR_REPO   — e.g. 'snad-nvd-data'
    """

    backend_name = "ghcr"

    def __init__(self, owner=None, repo=None):
        self.owner = owner or os.environ.get("NVD_SNAPSHOT_GHCR_OWNER") or os.environ.get("GITHUB_REPOSITORY_OWNER")
        self.repo = repo or os.environ.get("NVD_SNAPSHOT_GHCR_REPO", "snad-nvd-data")
        if not self.owner:
            raise StorageBackendError("NVD_SNAPSHOT_GHCR_OWNER (or GITHUB_REPOSITORY_OWNER) is required for GHCR backend")
        self.fqdn = f"ghcr.io/{self.owner}/{self.repo}"

    def _oras(self, *args, check=True):
        """Run oras CLI. Returns CompletedProcess."""
        cmd = ["oras"] + list(args)
        return subprocess.run(cmd, capture_output=True, text=True, check=check)

    def _snapshot_tag(self, snapshot_id):
        return f"{self.fqdn}:{snapshot_id}"

    def _latest_tag(self):
        return f"{self.fqdn}:latest-verified"

    def publish_snapshot(self, snapshot_id, archive_path, manifest):
        validate_manifest(manifest)
        manifest_tmp = Path(tempfile.mkdtemp()) / "manifest.json"
        manifest_tmp.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

        tag = self._snapshot_tag(snapshot_id)
        # Push archive as a layer, manifest as config
        result = self._oras(
            "push", tag,
            f"{archive_path}:application/vnd.oci.image.layer.v1.tar+zstd",
            f"--config", f"{manifest_tmp}:application/vnd.oci.image.config.v1+json",
            "--annotation", f"org.opencontainers.image.title=sanad-nvd-snapshot-{snapshot_id}",
        )
        if result.returncode != 0:
            raise StorageBackendError(f"oras push failed: {result.stderr}")
        # Resolve digest
        digest_result = self._oras("resolve", "--digest", tag)
        if digest_result.returncode != 0:
            raise StorageBackendError(f"oras resolve failed: {digest_result.stderr}")
        digest = digest_result.stdout.strip()
        return f"ghcr:{digest}"

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
        # Fetch config blob
        cfg_result = self._oras("blob", "fetch", f"{tag}@{config_blob}")
        return json.loads(cfg_result.stdout)

    def download_snapshot(self, snapshot_id, dest_dir):
        manifest = self.download_manifest(snapshot_id)
        tag = self._snapshot_tag(snapshot_id)
        dest_dir = Path(dest_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / manifest["archive_filename"]
        result = self._oras(
            "blob", "fetch",
            f"{tag}",
            "--output", str(dest),
        )
        if result.returncode != 0:
            raise SnapshotNotFoundError(f"archive for snapshot {snapshot_id} not found: {result.stderr}")
        return dest

    def verify_storage_digest(self, snapshot_id, expected_digest):
        tag = self._snapshot_tag(snapshot_id)
        result = self._oras("resolve", "--digest", tag, check=False)
        if result.returncode != 0:
            return False
        actual = result.stdout.strip()
        expected = expected_digest.split("ghcr:")[-1] if expected_digest.startswith("ghcr:") else expected_digest
        return actual == expected

    def promote_latest_pointer(self, snapshot_id, manifest):
        # Tag the snapshot as latest-verified
        tag = self._snapshot_tag(snapshot_id)
        latest = self._latest_tag()
        result = self._oras("tag", tag, latest, check=False)
        if result.returncode != 0:
            raise StorageBackendError(f"oras tag failed: {result.stderr}")

    def list_verified_snapshots(self):
        result = self._oras("repo", "tags", self.fqdn, check=False)
        if result.returncode != 0:
            return []
        tags = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        # Exclude the latest-verified tag
        return sorted(t for t in tags if t != "latest-verified")

    def apply_retention_policy(self, keep=DEFAULT_RETENTION_COUNT):
        # GHCR doesn't support bulk deletion via oras easily; this is a no-op
        # placeholder. In production, use GHCR UI or API to prune old tags.
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

def publish_snapshot(
    backend: SnapshotBackend,
    *,
    data_dir: Path,
    publisher_commit_sha: str,
    publisher_run_id: int | str,
    previous_snapshot_id: str,
    last_successful_update_at: str,
    database_sha256: str,
    database_size_bytes: int,
    validation_result: str,
    offline_smoke_result: str,
    freshness_hours_at_publish: int,
    work_dir: Path | None = None,
) -> dict:
    """Full publish sequence:

    1. Archive the data_dir into a tar.zst.
    2. Compute archive SHA-256.
    3. Build manifest.
    4. Upload archive + manifest via backend.
    5. Verify storage digest.
    6. Atomically promote latest.json pointer.

    On ANY failure, latest.json is NOT changed — the previous
    Last-Known-Good snapshot remains active for consumers.
    """
    import tarfile
    import shutil

    data_dir = Path(data_dir).resolve()
    if not data_dir.is_dir():
        raise SnapshotError(f"data_dir does not exist: {data_dir}")

    work_dir = Path(work_dir) if work_dir else Path(tempfile.mkdtemp(prefix="nvd-snapshot-"))
    work_dir.mkdir(parents=True, exist_ok=True)

    created_at = utc_now_iso()
    snapshot_id = snapshot_id_for(created_at, publisher_commit_sha, database_sha256)
    archive_filename = f"snad-nvd-data-{snapshot_id}.tar.zst"
    archive_path = work_dir / archive_filename

    # Create tar.zst preserving mtimes, perms, layout.
    # Filter out forbidden files (lock/temp).
    # Uses `tar --zstd` via subprocess for broad compatibility
    # (Python 3.12's tarfile module does not support w:zst natively).
    import subprocess as _sp
    import fnmatch as _fnmatch

    # Build the tar file list, excluding forbidden patterns
    file_list_path = work_dir / "file-list.txt"
    with file_list_path.open("w") as fl:
        for root, dirs, files in os.walk(data_dir):
            for fname in files:
                fpath = Path(root) / fname
                rel = fpath.relative_to(data_dir)
                skip = False
                for pat in FORBIDDEN_LOCK_PATTERNS + FORBIDDEN_TEMP_PATTERNS:
                    if _fnmatch.fnmatch(fname, pat):
                        skip = True
                        break
                if not skip:
                    fl.write(str(rel) + "\n")

    # Try zstd first, fall back to gz
    try:
        _sp.run(
            ["tar", "--zstd", "-cf", str(archive_path),
             "-C", str(data_dir), "-T", str(file_list_path)],
            check=True, capture_output=True,
        )
        actual_archive_filename = archive_filename
    except (_sp.CalledProcessError, FileNotFoundError):
        # Fall back to gzip if zstd unavailable
        gz_path = work_dir / f"snad-nvd-data-{snapshot_id}.tar.gz"
        _sp.run(
            ["tar", "-czf", str(gz_path),
             "-C", str(data_dir), "-T", str(file_list_path)],
            check=True, capture_output=True,
        )
        archive_path = gz_path
        actual_archive_filename = f"snad-nvd-data-{snapshot_id}.tar.gz"

    # Clean up temp file list
    file_list_path.unlink(missing_ok=True)

    archive_sha256 = sha256_file(archive_path)
    archive_size_bytes = archive_path.stat().st_size

    manifest = build_manifest(
        snapshot_id=snapshot_id,
        created_at=created_at,
        last_successful_update_at=last_successful_update_at,
        publisher_commit_sha=publisher_commit_sha,
        publisher_run_id=publisher_run_id,
        archive_filename=actual_archive_filename,
        archive_sha256=archive_sha256,
        archive_size_bytes=archive_size_bytes,
        database_filename="odc.mv.db",
        database_sha256=database_sha256,
        database_size_bytes=database_size_bytes,
        validation_result=validation_result,
        offline_smoke_result=offline_smoke_result,
        storage_backend=backend.backend_name,
        storage_version_or_digest="",  # filled after upload
        previous_snapshot_id=previous_snapshot_id,
        freshness_hours_at_publish=freshness_hours_at_publish,
    )

    # Upload
    storage_version = backend.publish_snapshot(snapshot_id, archive_path, manifest)
    manifest["storage_version_or_digest"] = storage_version

    # Re-publish manifest with the storage version filled in
    backend.publish_snapshot(snapshot_id, archive_path, manifest)

    # Verify
    if not backend.verify_storage_digest(snapshot_id, storage_version):
        raise SnapshotVerificationError(
            f"storage digest verification failed for snapshot {snapshot_id}"
        )

    # Promote pointer
    backend.promote_latest_pointer(snapshot_id, manifest)

    return manifest


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
