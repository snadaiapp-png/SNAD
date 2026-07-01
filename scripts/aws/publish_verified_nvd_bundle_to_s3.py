#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
import sys
from typing import Any

import boto3
from botocore.exceptions import ClientError


class PublishError(RuntimeError):
    pass


def digest(path: pathlib.Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def head(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    try:
        return client.head_object(Bucket=bucket, Key=key)
    except ClientError as exc:
        code = exc.response.get("Error", {}).get("Code", "")
        if code in {"404", "NoSuchKey", "NotFound"}:
            return None
        raise


def upload(
    client: Any,
    bucket: str,
    key: str,
    source: pathlib.Path,
    content_type: str,
    kms_key: str,
    snapshot_id: str,
) -> dict[str, Any]:
    expected = digest(source)
    current = head(client, bucket, key)
    if current is not None:
        if current.get("Metadata", {}).get("sha256") != expected:
            raise PublishError(f"immutable object conflict: {key}")
        return {
            "key": key,
            "version_id": current.get("VersionId", ""),
            "etag": str(current.get("ETag", "")).strip('"'),
            "sha256": expected,
            "size_bytes": source.stat().st_size,
            "uploaded": False,
        }
    client.upload_file(
        str(source),
        bucket,
        key,
        ExtraArgs={
            "ContentType": content_type,
            "ServerSideEncryption": "aws:kms",
            "SSEKMSKeyId": kms_key,
            "Metadata": {"snapshot-id": snapshot_id, "sha256": expected},
        },
    )
    current = client.head_object(Bucket=bucket, Key=key)
    if current.get("Metadata", {}).get("sha256") != expected:
        raise PublishError(f"metadata verification failed: {key}")
    return {
        "key": key,
        "version_id": current.get("VersionId", ""),
        "etag": str(current.get("ETag", "")).strip('"'),
        "sha256": expected,
        "size_bytes": source.stat().st_size,
        "uploaded": True,
    }


def remote_digest(client: Any, bucket: str, key: str, version_id: str) -> str:
    request: dict[str, str] = {"Bucket": bucket, "Key": key}
    if version_id:
        request["VersionId"] = version_id
    response = client.get_object(**request)
    value = hashlib.sha256()
    for chunk in response["Body"].iter_chunks(chunk_size=1024 * 1024):
        value.update(chunk)
    return value.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pointer", type=pathlib.Path, required=True)
    parser.add_argument("--archive", type=pathlib.Path, required=True)
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--checksums", type=pathlib.Path, required=True)
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--region", required=True)
    parser.add_argument("--kms-key-arn", required=True)
    parser.add_argument("--evidence", type=pathlib.Path, required=True)
    args = parser.parse_args()

    pointer = json.loads(args.pointer.read_text(encoding="utf-8"))
    snapshot_id = pointer["snapshot_id"]
    if digest(args.archive) != pointer["archive_sha256"]:
        raise PublishError("archive SHA-256 does not match the verified pointer")

    client = boto3.client("s3", region_name=args.region)
    if client.get_bucket_versioning(Bucket=args.bucket).get("Status") != "Enabled":
        raise PublishError("bucket versioning is not enabled")
    encryption = client.get_bucket_encryption(Bucket=args.bucket)
    if not encryption.get("ServerSideEncryptionConfiguration", {}).get("Rules"):
        raise PublishError("bucket encryption is not configured")

    prefix = args.prefix.strip("/")
    base = f"{prefix}/snapshots/{snapshot_id}"
    archive_result = upload(
        client, args.bucket, f"{base}/{args.archive.name}", args.archive,
        "application/octet-stream", args.kms_key_arn, snapshot_id,
    )
    manifest_result = upload(
        client, args.bucket, f"{base}/manifest.json", args.manifest,
        "application/json", args.kms_key_arn, snapshot_id,
    )
    checksums_result = upload(
        client, args.bucket, f"{base}/SHA256SUMS", args.checksums,
        "text/plain", args.kms_key_arn, snapshot_id,
    )

    pointer.update({
        "storage_backend": "s3",
        "storage_version_or_digest": (
            f"s3:{args.bucket}/{archive_result['key']}:"
            f"v{archive_result['version_id']}:etag-{archive_result['etag']}"
        ),
        "archive": {"filename": args.archive.name, "version_id": archive_result["version_id"]},
        "manifest": {"filename": "manifest.json", "version_id": manifest_result["version_id"]},
        "checksums": {"filename": "SHA256SUMS", "version_id": checksums_result["version_id"]},
        "mirrored_at": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    })
    pointer_bytes = json.dumps(pointer, indent=2, sort_keys=True).encode("utf-8")
    latest_key = f"{prefix}/channels/verified/latest.json"
    response = client.put_object(
        Bucket=args.bucket,
        Key=latest_key,
        Body=pointer_bytes,
        ContentType="application/json",
        ServerSideEncryption="aws:kms",
        SSEKMSKeyId=args.kms_key_arn,
        Metadata={"snapshot-id": snapshot_id, "sha256": hashlib.sha256(pointer_bytes).hexdigest()},
    )

    resolved = json.loads(client.get_object(Bucket=args.bucket, Key=latest_key)["Body"].read())
    if resolved.get("snapshot_id") != snapshot_id:
        raise PublishError("latest pointer read-back failed")
    remote_sha = remote_digest(
        client, args.bucket, archive_result["key"], archive_result["version_id"]
    )
    if remote_sha != pointer["archive_sha256"]:
        raise PublishError("remote archive SHA-256 mismatch")

    evidence = {
        "result": "pass",
        "account_id": boto3.client("sts").get_caller_identity()["Account"],
        "region": args.region,
        "bucket": args.bucket,
        "prefix": prefix,
        "snapshot_id": snapshot_id,
        "archive": archive_result,
        "manifest": manifest_result,
        "checksums": checksums_result,
        "latest_pointer_key": latest_key,
        "latest_pointer_version_id": response.get("VersionId", ""),
        "remote_archive_sha256": remote_sha,
        "kms_key_arn": args.kms_key_arn,
    }
    args.evidence.parent.mkdir(parents=True, exist_ok=True)
    args.evidence.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(evidence, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PublishError as exc:
        print(f"PUBLISH_ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
