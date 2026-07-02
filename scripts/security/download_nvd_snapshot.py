#!/usr/bin/env python3
"""Download the latest verified NVD Dependency-Check snapshot.

Supported backends:
- github-releases: uses the GitHub CLI and GITHUB_TOKEN.
- s3: uses boto3 after GitHub Actions has exchanged OIDC for AWS credentials.

The script never contacts the NVD API. It validates the latest-pointer contract,
requires the three immutable bundle files, records sizes and SHA-256 digests, and
writes machine-readable download evidence before OWASP consumes the snapshot.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import subprocess
import sys
from typing import Any

LATEST_CONTRACT = "snad-nvd-latest-v1"
REQUIRED_POINTER_FIELDS = {
    "contract_version",
    "snapshot_id",
    "created_at",
    "archive_filename",
    "archive_sha256",
    "manifest_sha256",
    "storage_backend",
    "storage_version_or_digest",
}


class SnapshotConsumerError(RuntimeError):
    pass


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_timestamp(value: str) -> dt.datetime:
    try:
        parsed = dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except (TypeError, ValueError) as exc:
        raise SnapshotConsumerError(f"invalid UTC timestamp: {value!r}") from exc
    return parsed.replace(tzinfo=dt.timezone.utc)


def validate_pointer(pointer: dict[str, Any], *, max_age_hours: int) -> float:
    missing = sorted(REQUIRED_POINTER_FIELDS.difference(pointer))
    if missing:
        raise SnapshotConsumerError(f"latest pointer missing fields: {', '.join(missing)}")
    if pointer["contract_version"] != LATEST_CONTRACT:
        raise SnapshotConsumerError(
            f"latest pointer contract {pointer['contract_version']!r} != {LATEST_CONTRACT!r}"
        )
    snapshot_id = pointer["snapshot_id"]
    if not isinstance(snapshot_id, str) or not snapshot_id or "/" in snapshot_id or ".." in snapshot_id:
        raise SnapshotConsumerError("unsafe or empty snapshot_id")
    archive_filename = pointer["archive_filename"]
    if (
        not isinstance(archive_filename, str)
        or not archive_filename.endswith((".tar.zst", ".tar.gz"))
        or pathlib.PurePath(archive_filename).name != archive_filename
    ):
        raise SnapshotConsumerError(f"unsafe archive filename: {archive_filename!r}")
    expected_sha = pointer["archive_sha256"]
    if not isinstance(expected_sha, str) or len(expected_sha) != 64:
        raise SnapshotConsumerError("archive_sha256 must be a 64-character digest")
    created = parse_timestamp(pointer["created_at"])
    age_hours = (dt.datetime.now(dt.timezone.utc) - created).total_seconds() / 3600
    if age_hours < -0.25:
        raise SnapshotConsumerError("snapshot created_at is in the future")
    if age_hours > max_age_hours:
        raise SnapshotConsumerError(
            f"snapshot is stale: age={age_hours:.2f}h maximum={max_age_hours}h"
        )
    return age_hours


def run_gh(args: list[str], *, token: str) -> str:
    env = os.environ.copy()
    env["GH_TOKEN"] = token
    result = subprocess.run(
        ["gh", *args],
        text=True,
        capture_output=True,
        env=env,
        timeout=1800,
        check=False,
    )
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise SnapshotConsumerError(f"gh {' '.join(args[:3])} failed: {detail[:1000]}")
    return result.stdout


def download_github_releases(destination: pathlib.Path, repository: str, token: str) -> tuple[dict, dict]:
    pointer_dir = destination / "pointer"
    pointer_dir.mkdir(parents=True, exist_ok=True)
    run_gh(
        [
            "release",
            "download",
            "nvd-snapshot-latest",
            "--repo",
            repository,
            "--pattern",
            "latest.json",
            "--dir",
            str(pointer_dir),
            "--clobber",
        ],
        token=token,
    )
    pointer_path = pointer_dir / "latest.json"
    if not pointer_path.is_file():
        raise SnapshotConsumerError("latest.json was not downloaded from nvd-snapshot-latest")
    pointer = json.loads(pointer_path.read_text(encoding="utf-8"))
    snapshot_id = pointer.get("snapshot_id", "")
    tag = f"nvd-snapshot-{snapshot_id}"
    release_data = json.loads(
        run_gh(
            ["release", "view", tag, "--repo", repository, "--json", "tagName,assets"],
            token=token,
        )
    )
    assets = {asset["name"]: asset for asset in release_data.get("assets", [])}
    required_names = [pointer.get("archive_filename", ""), "manifest.json", "SHA256SUMS"]
    missing = [name for name in required_names if not name or name not in assets]
    if missing:
        raise SnapshotConsumerError(
            f"snapshot release {tag} is missing assets: {', '.join(missing)}; "
            f"available={sorted(assets)}"
        )
    bundle_dir = destination / "bundle"
    bundle_dir.mkdir(parents=True, exist_ok=True)
    for name in required_names:
        run_gh(
            [
                "release",
                "download",
                tag,
                "--repo",
                repository,
                "--pattern",
                name,
                "--dir",
                str(bundle_dir),
                "--clobber",
            ],
            token=token,
        )
    return pointer, {"tag": tag, "assets": assets, "bundle_dir": str(bundle_dir)}


def download_s3(
    destination: pathlib.Path,
    bucket: str,
    prefix: str,
    region: str,
    endpoint: str | None,
) -> tuple[dict, dict]:
    try:
        import boto3  # type: ignore
    except ImportError as exc:
        raise SnapshotConsumerError("boto3 is required for the s3 backend") from exc
    client_args: dict[str, str] = {"region_name": region}
    if endpoint:
        client_args["endpoint_url"] = endpoint
    client = boto3.client("s3", **client_args)
    prefix = prefix.strip("/")
    latest_key = f"{prefix}/channels/verified/latest.json"
    pointer_response = client.get_object(Bucket=bucket, Key=latest_key)
    pointer = json.loads(pointer_response["Body"].read().decode("utf-8"))
    snapshot_id = pointer.get("snapshot_id", "")
    archive_name = pointer.get("archive_filename", "")
    bundle_dir = destination / "bundle"
    bundle_dir.mkdir(parents=True, exist_ok=True)
    objects: dict[str, dict] = {}
    for name in (archive_name, "manifest.json", "SHA256SUMS"):
        key = f"{prefix}/snapshots/{snapshot_id}/{name}"
        target = bundle_dir / name
        client.download_file(bucket, key, str(target))
        head = client.head_object(Bucket=bucket, Key=key)
        objects[name] = {
            "key": key,
            "version_id": head.get("VersionId", ""),
            "etag": str(head.get("ETag", "")).strip('"'),
            "content_length": int(head.get("ContentLength", 0)),
        }
    return pointer, {
        "bucket": bucket,
        "prefix": prefix,
        "latest_key": latest_key,
        "objects": objects,
        "bundle_dir": str(bundle_dir),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", choices=("github-releases", "s3"), required=True)
    parser.add_argument("--destination", type=pathlib.Path, required=True)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--bucket", default=os.environ.get("NVD_SNAPSHOT_BUCKET", ""))
    parser.add_argument("--prefix", default=os.environ.get("NVD_SNAPSHOT_PREFIX", "snad-nvd"))
    parser.add_argument("--region", default=os.environ.get("NVD_SNAPSHOT_REGION", ""))
    parser.add_argument("--endpoint", default=os.environ.get("NVD_SNAPSHOT_ENDPOINT", ""))
    parser.add_argument("--max-age-hours", type=int, default=48)
    parser.add_argument("--github-output", type=pathlib.Path)
    args = parser.parse_args()

    destination = args.destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    if args.backend == "github-releases":
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
        if not token or not args.repository:
            raise SnapshotConsumerError("GITHUB_TOKEN and repository are required")
        pointer, source = download_github_releases(destination, args.repository, token)
    else:
        if not args.bucket or not args.region:
            raise SnapshotConsumerError("bucket and region are required for s3")
        pointer, source = download_s3(
            destination, args.bucket, args.prefix, args.region, args.endpoint or None
        )

    age_hours = validate_pointer(pointer, max_age_hours=args.max_age_hours)
    bundle_dir = destination / "bundle"
    archive_path = bundle_dir / pointer["archive_filename"]
    manifest_path = bundle_dir / "manifest.json"
    checksums_path = bundle_dir / "SHA256SUMS"
    for path in (archive_path, manifest_path, checksums_path):
        if not path.is_file() or path.stat().st_size == 0:
            raise SnapshotConsumerError(f"required bundle file missing or empty: {path}")
    actual_archive_sha = sha256_file(archive_path)
    if actual_archive_sha != pointer["archive_sha256"]:
        raise SnapshotConsumerError(
            f"downloaded archive SHA-256 mismatch: expected={pointer['archive_sha256']} "
            f"actual={actual_archive_sha}"
        )
    evidence = {
        "backend": args.backend,
        "snapshot_id": pointer["snapshot_id"],
        "snapshot_created_at": pointer["created_at"],
        "snapshot_age_hours": round(age_hours, 3),
        "pointer_contract": pointer["contract_version"],
        "storage_version_or_digest": pointer["storage_version_or_digest"],
        "archive_path": str(archive_path),
        "archive_size_bytes": archive_path.stat().st_size,
        "archive_sha256": actual_archive_sha,
        "manifest_path": str(manifest_path),
        "manifest_size_bytes": manifest_path.stat().st_size,
        "manifest_sha256": sha256_file(manifest_path),
        "checksums_path": str(checksums_path),
        "checksums_size_bytes": checksums_path.stat().st_size,
        "checksums_sha256": sha256_file(checksums_path),
        "source": source,
    }
    evidence_path = destination / "download-evidence.json"
    evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
    outputs = {
        "snapshot_id": pointer["snapshot_id"],
        "snapshot_created_at": pointer["created_at"],
        "snapshot_age_hours": f"{age_hours:.3f}",
        "archive_path": str(archive_path),
        "manifest_path": str(manifest_path),
        "checksums_path": str(checksums_path),
        "archive_sha256": actual_archive_sha,
        "download_evidence_path": str(evidence_path),
    }
    for key, value in outputs.items():
        print(f"{key}={value}")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            for key, value in outputs.items():
                handle.write(f"{key}={value}\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SnapshotConsumerError as exc:
        print(f"SNAPSHOT_CONSUMER_ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
