#!/usr/bin/env python3
"""
SANAD — NVD Storage Readiness Verifier
=======================================
EXEC-PROMPT-010R12G Section 13 — verifies that S3 storage is ready
for NVD snapshot publishing before the Bootstrap or Publisher runs.

Checks (S3 backend):
  1. STS GetCallerIdentity succeeds (OIDC authenticated)
  2. Bucket exists
  3. Bucket region matches expected
  4. Bucket versioning is Enabled
  5. Bucket has default encryption (AES256 or aws:kms)
  6. Put permission (write test object)
  7. Get permission (read test object)
  8. Head permission (metadata check)
  9. List permission
 10. Delete permission (cleanup test object)

Fail-closed on any check failure with explicit classification.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_snapshot_store import (
    StorageAuthenticationError,
    StorageAuthorizationError,
    StorageBackendError,
    StorageUnavailableError,
)


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def out(key, value):
    print(f"{key}={value}")


def verify_s3_readiness() -> int:
    """Verify S3 storage readiness. Returns 0 on success, non-zero on failure."""
    bucket = os.environ.get("NVD_SNAPSHOT_BUCKET")
    region = os.environ.get("NVD_SNAPSHOT_REGION")
    prefix = os.environ.get("NVD_SNAPSHOT_PREFIX", "snad-nvd")
    endpoint = os.environ.get("NVD_SNAPSHOT_ENDPOINT")

    if not bucket:
        eprint("ERROR: NVD_SNAPSHOT_BUCKET is not set")
        out("classification", "BUCKET_NOT_CONFIGURED")
        return 1
    if not region:
        eprint("ERROR: NVD_SNAPSHOT_REGION is not set")
        out("classification", "REGION_NOT_CONFIGURED")
        return 1

    try:
        import boto3  # type: ignore
        from botocore.exceptions import ClientError, NoCredentialsError, TokenRetrievalError  # type: ignore
    except ImportError:
        eprint("ERROR: boto3 is not installed")
        out("classification", "BOTO3_NOT_INSTALLED")
        return 1

    # Build client
    kwargs = {"region_name": region}
    if endpoint:
        kwargs["endpoint_url"] = endpoint

    try:
        sts = boto3.client("sts", **kwargs)
        identity = sts.get_caller_identity()
        out("caller_arn", identity.get("Arn", ""))
        out("caller_account", identity.get("Account", ""))
        print("STS GetCallerIdentity: SUCCESS")
    except NoCredentialsError:
        eprint("ERROR: No AWS credentials found — OIDC not configured")
        out("classification", "OIDC_AUTHENTICATION_FAILED")
        return 1
    except TokenRetrievalError:
        eprint("ERROR: OIDC token retrieval failed")
        out("classification", "OIDC_AUTHENTICATION_FAILED")
        return 1
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in ("AccessDenied", "403"):
            eprint(f"ERROR: STS access denied: {e}")
            out("classification", "OIDC_AUTHORIZATION_FAILED")
        else:
            eprint(f"ERROR: STS failed: {e}")
            out("classification", "STS_FAILED")
        return 1

    s3 = boto3.client("s3", **kwargs)

    # Check bucket exists
    try:
        s3.head_bucket(Bucket=bucket)
        print(f"Bucket exists: SUCCESS ({bucket})")
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code == "404":
            eprint(f"ERROR: Bucket does not exist: {bucket}")
            out("classification", "BUCKET_NOT_FOUND")
        elif code in ("403", "AccessDenied"):
            eprint(f"ERROR: Bucket access denied: {bucket}")
            out("classification", "BUCKET_ACCESS_DENIED")
        else:
            eprint(f"ERROR: Bucket check failed: {e}")
            out("classification", "BUCKET_CHECK_FAILED")
        return 1

    # Check bucket region
    try:
        loc = s3.get_bucket_location(Bucket=bucket)
        bucket_region = loc.get("LocationConstraint", "us-east-1")
        if bucket_region is None:
            bucket_region = "us-east-1"
        if bucket_region != region:
            eprint(f"ERROR: Bucket region mismatch: expected={region} actual={bucket_region}")
            out("classification", "REGION_MISMATCH")
            return 1
        print(f"Bucket region: SUCCESS ({bucket_region})")
    except ClientError as e:
        eprint(f"ERROR: Get bucket location failed: {e}")
        out("classification", "BUCKET_LOCATION_FAILED")
        return 1

    # Check versioning
    try:
        ver = s3.get_bucket_versioning(Bucket=bucket)
        status = ver.get("Status", "")
        if status != "Enabled":
            eprint(f"ERROR: Bucket versioning is not Enabled (status={status})")
            out("classification", "BUCKET_VERSIONING_DISABLED")
            return 1
        print(f"Bucket versioning: SUCCESS ({status})")
    except ClientError as e:
        eprint(f"ERROR: Get bucket versioning failed: {e}")
        out("classification", "VERSIONING_CHECK_FAILED")
        return 1

    # Check encryption
    try:
        enc = s3.get_bucket_encryption(Bucket=bucket)
        rules = enc.get("ServerSideEncryptionConfiguration", {}).get("Rules", [])
        if not rules:
            eprint("ERROR: Bucket has no default encryption")
            out("classification", "BUCKET_ENCRYPTION_MISSING")
            return 1
        algo = rules[0].get("ApplyServerSideEncryptionByDefault", {}).get("SSEAlgorithm", "")
        if algo not in ("AES256", "aws:kms"):
            eprint(f"ERROR: Unsupported encryption algorithm: {algo}")
            out("classification", "BUCKET_ENCRYPTION_UNSUPPORTED")
            return 1
        print(f"Bucket encryption: SUCCESS ({algo})")
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code == "ServerSideEncryptionConfigurationNotFoundError":
            eprint("ERROR: Bucket has no default encryption")
            out("classification", "BUCKET_ENCRYPTION_MISSING")
        else:
            eprint(f"ERROR: Get bucket encryption failed: {e}")
            out("classification", "ENCRYPTION_CHECK_FAILED")
        return 1

    # Read/write probe
    test_key = f"{prefix}/readiness/readiness-{os.environ.get('GITHUB_RUN_ID', 'local')}.txt"
    test_content = b"sanad-nvd-readiness-probe"
    try:
        s3.put_object(Bucket=bucket, Key=test_key, Body=test_content, ContentType="text/plain")
        print("Put permission: SUCCESS")
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in ("AccessDenied", "403"):
            eprint(f"ERROR: Put permission denied")
            out("classification", "PUT_PERMISSION_DENIED")
        else:
            eprint(f"ERROR: Put failed: {e}")
            out("classification", "PUT_FAILED")
        return 1

    try:
        resp = s3.get_object(Bucket=bucket, Key=test_key)
        data = resp["Body"].read()
        if data != test_content:
            eprint("ERROR: Get returned wrong content")
            out("classification", "GET_CONTENT_MISMATCH")
            return 1
        print("Get permission: SUCCESS")
    except ClientError as e:
        eprint(f"ERROR: Get failed: {e}")
        out("classification", "GET_FAILED")
        return 1

    try:
        s3.head_object(Bucket=bucket, Key=test_key)
        print("Head permission: SUCCESS")
    except ClientError as e:
        eprint(f"ERROR: Head failed: {e}")
        out("classification", "HEAD_FAILED")
        return 1

    try:
        s3.list_objects_v2(Bucket=bucket, Prefix=f"{prefix}/readiness/", MaxKeys=1)
        print("List permission: SUCCESS")
    except ClientError as e:
        eprint(f"ERROR: List failed: {e}")
        out("classification", "LIST_FAILED")
        return 1

    try:
        s3.delete_object(Bucket=bucket, Key=test_key)
        print("Delete permission: SUCCESS")
    except ClientError as e:
        eprint(f"ERROR: Delete failed: {e}")
        out("classification", "DELETE_FAILED")
        return 1

    # All checks passed
    out("readiness_result", "ready")
    print("RESULT=ready")
    print("OK: S3 storage is ready for NVD snapshot publishing")
    return 0


def main():
    backend = os.environ.get("NVD_SNAPSHOT_BACKEND", "")
    if backend == "s3":
        return verify_s3_readiness()
    elif backend == "ghcr":
        eprint("ERROR: GHCR backend is experimental and not supported for readiness check")
        out("classification", "GHCR_EXPERIMENTAL")
        return 1
    elif backend == "filesystem":
        eprint("ERROR: Filesystem backend is for tests only, not production readiness")
        out("classification", "FILESYSTEM_NOT_PRODUCTION")
        return 1
    else:
        eprint(f"ERROR: Unknown or unconfigured NVD_SNAPSHOT_BACKEND: {backend!r}")
        out("classification", "BACKEND_NOT_CONFIGURED")
        return 1


if __name__ == "__main__":
    sys.exit(main())
