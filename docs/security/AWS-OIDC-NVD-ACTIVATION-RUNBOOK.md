# AWS/OIDC NVD Platform Activation Runbook

## Purpose

This runbook activates the AWS trust anchor required by GitHub Actions and completes the live STS/S3 evidence chain for SNAD. It uses short-lived GitHub OIDC sessions and does not require permanent AWS access keys in GitHub.

## Delivered components

| Component | Path |
|---|---|
| Source CloudFormation template | `infra/aws/nvd-platform-bootstrap.yml` |
| Deterministic template renderer | `scripts/aws/render_nvd_platform_template.py` |
| AWS CloudShell bootstrap | `scripts/aws/bootstrap_nvd_platform.sh` |
| GitHub environment configuration | `scripts/aws/configure_github_nvd_platform.sh` |
| Verified bundle S3 publisher | `scripts/aws/publish_verified_nvd_bundle_to_s3.py` |
| S3 mirror workflow | `.github/workflows/aws-nvd-snapshot-mirror.yml` |
| Reader validation workflow | `.github/workflows/aws-nvd-reader-validation.yml` |
| Static engineering gate | `.github/workflows/aws-nvd-bootstrap-ci.yml` |

## Security model

The stack creates or reuses the GitHub Actions OIDC provider and provisions:

- a private S3 bucket;
- bucket versioning;
- SSE-KMS default encryption and S3 Bucket Keys;
- full S3 public-access blocking;
- TLS-only bucket policy;
- retained KMS key and retained bucket;
- a read-only role trusted only by the `nvd-reader` GitHub environment;
- a publisher role trusted only by the `nvd-publisher` GitHub environment;
- prefix-scoped object access;
- no permanent AWS credentials in GitHub.

## Required operator authorization

The one-time bootstrap operator must use an approved AWS identity that can create or update:

- CloudFormation stacks;
- IAM OIDC providers and roles;
- IAM inline policies;
- KMS keys and aliases;
- S3 buckets and bucket policies.

This identity is used only in AWS CloudShell or another approved administrative workstation. It is not stored in the repository or GitHub.

## Phase 1 — Deploy the AWS trust anchor

From a checked-out copy of the PR branch in AWS CloudShell:

```bash
export AWS_REGION='<APPROVED_AWS_REGION>'
export STACK_NAME='snad-nvd-platform'
export REPOSITORY_OWNER='snadaiapp-png'
export REPOSITORY_NAME='SNAD'
export READER_ENVIRONMENT='nvd-reader'
export PUBLISHER_ENVIRONMENT='nvd-publisher'
export NVD_SNAPSHOT_PREFIX='snad-nvd'

bash scripts/aws/bootstrap_nvd_platform.sh
```

The script performs:

1. `sts:GetCallerIdentity` and records the caller account and ARN.
2. Discovery of an existing GitHub Actions OIDC provider.
3. Rendering of the lint-correct CloudFormation template.
4. `cloudformation validate-template`.
5. Idempotent `cloudformation deploy` with named-IAM acknowledgement.
6. Retrieval of stack outputs.
7. Verification of S3 versioning.
8. Verification of bucket encryption.
9. Verification of public-access blocking.
10. Creation of `artifacts/security/aws-bootstrap/bootstrap-controls.json`.

Required terminal result:

```json
{
  "result": "pass",
  "checks": {
    "versioning_enabled": true,
    "encryption_configured": true,
    "public_access_blocked": true
  }
}
```

## Phase 2 — Configure GitHub without AWS secrets

Authenticate GitHub CLI with repository-administration permission, then run:

```bash
export GITHUB_REPOSITORY='snadaiapp-png/SNAD'
bash scripts/aws/configure_github_nvd_platform.sh \
  artifacts/security/aws-bootstrap/bootstrap-controls.json
```

The script creates or updates the following GitHub environments:

```text
nvd-reader
nvd-publisher
```

It configures non-secret repository and environment variables:

```text
NVD_SNAPSHOT_BACKEND=s3
NVD_SNAPSHOT_AWS_ACCOUNT_ID
NVD_SNAPSHOT_REGION
NVD_SNAPSHOT_BUCKET
NVD_SNAPSHOT_PREFIX
NVD_SNAPSHOT_READER_ROLE
NVD_SNAPSHOT_PUBLISHER_ROLE
NVD_SNAPSHOT_KMS_KEY_ARN
```

No `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, or AWS session token is stored in GitHub.

## Phase 3 — Publish the first verified S3 snapshot

Run the GitHub workflow:

```text
AWS NVD Snapshot Mirror
```

Mandatory checks:

- publisher role OIDC assumption;
- expected AWS account assertion;
- download of the current verified GitHub Releases snapshot;
- pointer contract and freshness validation;
- archive, manifest, checksums, and database verification;
- SSE-KMS object upload;
- S3 Version IDs captured;
- `latest.json` published only after immutable objects pass;
- remote S3 archive streamed back and SHA-256 verified;
- evidence artifact uploaded;
- Final Enforcement PASS.

Expected artifact:

```text
aws-nvd-snapshot-mirror-<run-id>
```

## Phase 4 — Validate the read-only role

Run the GitHub workflow:

```text
AWS NVD Reader Validation
```

Mandatory checks:

- reader role OIDC assumption;
- STS caller identity;
- expected AWS account;
- S3 versioning;
- S3 default encryption;
- complete bundle download through the read-only role;
- Snapshot ID, age, SHA-256, database digest, and minimum size;
- offline Dependency-Check database smoke test;
- evidence upload;
- Final Enforcement PASS.

Expected artifact:

```text
aws-nvd-reader-validation-<run-id>
```

## Phase 5 — Run acceptance OWASP through S3

After the repository variable `NVD_SNAPSHOT_BACKEND` is set to `s3`, run the acceptance Security Scan on `main` or through the approved manual acceptance path.

Required result:

```text
Snapshot backend: s3
OIDC assumption: PASS
Snapshot freshness: PASS
Archive checksum: PASS
Database digest: PASS
HTML report: PASS
JSON report: PASS
Analysis exceptions: 0
Terminal decision: PASS
```

## Rollback and retention

- The bucket and KMS key use `Retain` policies.
- Removing the CloudFormation stack does not delete evidence objects or the encryption key.
- Repository variable `NVD_SNAPSHOT_BACKEND` can be changed back to `github-releases` without destroying AWS resources.
- OIDC roles can be disabled by removing their trust statements or deleting the roles after evidence preservation.
- No bucket deletion is permitted until all versions and audit evidence are retained according to project policy.

## Acceptance record

AWS/OIDC live acceptance is achieved only when all three artifacts exist for the same approved configuration:

1. `bootstrap-controls.json` with `result=pass`;
2. S3 mirror artifact with `result=pass` and matching remote archive SHA-256;
3. reader-validation artifact with valid STS identity, versioning, encryption, freshness, checksums, and smoke-test evidence.

Until those artifacts exist, the correct status remains:

```text
AWS/OIDC SOURCE + STATIC ENGINEERING: PASS
AWS/OIDC LIVE ACCOUNT ACTIVATION: NOT ACHIEVED
PRODUCTION / COMMERCIAL RELEASE: NO-GO
```
