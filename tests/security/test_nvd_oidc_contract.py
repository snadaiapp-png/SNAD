from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]


def test_reader_role_uses_restricted_github_subjects():
    template = (ROOT / "infra" / "aws" / "nvd-snapshot-reader-role.yml").read_text(
        encoding="utf-8"
    )
    assert "token.actions.githubusercontent.com:aud: sts.amazonaws.com" in template
    assert "ref:refs/heads/main" in template
    assert "environment:${AcceptanceEnvironment}" in template
    assert "s3:GetObject" in template
    assert "s3:GetObjectVersion" in template
    assert "s3:ListBucket" in template
    assert "s3:PutObject" not in template
    assert "s3:DeleteObject" not in template
    assert "s3:PutBucketPolicy" not in template
    assert "kms:Encrypt" not in template


def test_security_workflow_requests_oidc_before_snapshot_download():
    workflow = (ROOT / ".github" / "workflows" / "security-scan.yml").read_text(
        encoding="utf-8"
    )
    assert "id-token: write" in workflow
    oidc_position = workflow.index("Configure AWS credentials via OIDC")
    download_position = workflow.index("Download and verify NVD snapshot")
    assert oidc_position < download_position
    assert "download_nvd_snapshot.py" in workflow
    assert "dependency-check-report.html" in workflow
    assert "dependency-check-report.json" in workflow
    assert "Final enforcement" in workflow
