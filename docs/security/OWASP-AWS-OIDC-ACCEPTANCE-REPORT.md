# SNAD OWASP and AWS/OIDC Security Acceptance Report

## 1. Document control

| Field | Value |
|---|---|
| Project | SNAD Global AI ERP SaaS / CRM Runtime |
| Pull request | #196 |
| Security tracking issue | #197 |
| Development gate | #101 |
| Report date | 2026-07-01 |
| OWASP accepted branch SHA | `ecdd96edb7f4d09f3668727cc15d7a124022ddb6` |
| OWASP workflow run | `28536553168` |
| OWASP artifact | `owasp-dependency-check-evidence-28536553168` |
| Artifact digest | `sha256:cab77d913066d8529ac1c1fc0cf83fba1c84ca8d0a0c83e96f779e6403915212` |

## 2. Executive decision

```text
NVD SNAPSHOT CONTRACT: PASS
NVD SNAPSHOT CHECKSUM: PASS
NVD SNAPSHOT FRESHNESS: PASS
DEPENDENCY-CHECK EXECUTION: PASS
HTML REPORT: PASS
JSON REPORT: PASS
CRITICAL FINDINGS: 0
HIGH FINDINGS: 0
MEDIUM FINDINGS: 0
LOW FINDINGS: 0
UNKNOWN FINDINGS: 0
ANALYSIS EXCEPTIONS: 0
TERMINAL ENFORCEMENT: PASS
AWS/OIDC SOURCE IMPLEMENTATION: PASS
AWS/OIDC LIVE ACCOUNT VALIDATION: BLOCKED — EXTERNAL VARIABLES NOT CONFIGURED
PRODUCTION / COMMERCIAL RELEASE: NO-GO
```

The repository-wide OWASP gate is operational and produced a terminal PASS using a verified offline NVD database. The AWS reader role, trust policy, and live-validation workflow are implemented in source, but the live STS/S3 test could not execute because the required AWS/GitHub environment values are not configured. This report does not claim that an AWS role was provisioned or assumed.

## 3. Implemented security architecture

### 3.1 Snapshot consumer

The security gate uses `scripts/security/download_nvd_snapshot.py` and supports:

- GitHub Releases as the pull-request evidence backend.
- S3 as the acceptance backend after GitHub OIDC role assumption.
- strict latest-pointer contract validation;
- archive, manifest, and checksums asset validation;
- SHA-256 verification;
- freshness enforcement;
- machine-readable download evidence.

The consumer fails closed when the pointer is malformed, stale, incomplete, or has a digest mismatch.

### 3.2 AWS OIDC reader role

`infra/aws/nvd-snapshot-reader-role.yml` defines a read-only IAM role with:

- `sts:AssumeRoleWithWebIdentity` only;
- audience restricted to `sts.amazonaws.com`;
- subjects restricted to the SNAD repository main branch and the `nvd-reader` GitHub environment;
- read-only access to the verified pointer and immutable snapshot prefix;
- prefix-restricted `s3:ListBucket`;
- optional KMS decrypt through S3;
- no S3 write, delete, bucket-policy, or KMS-encrypt permissions;
- maximum role session duration of one hour.

### 3.3 Live AWS validation workflow

`.github/workflows/aws-nvd-reader-validation.yml` validates:

1. required repository/environment variables;
2. GitHub OIDC role assumption;
3. STS caller identity and optional expected account;
4. S3 bucket versioning;
5. S3 bucket encryption;
6. read access to `channels/verified/latest.json`;
7. evidence artifact upload;
8. terminal enforcement.

The workflow is manual and fail-closed. It does not use long-lived AWS access keys.

### 3.4 OWASP offline gate

`.github/workflows/security-scan.yml` and `scripts/security/run_owasp_gate.sh` provide:

- security contract tests;
- backend preflight;
- OIDC before S3 access;
- verified snapshot download;
- database digest, size, and age verification;
- OWASP Dependency-Check 12.1.0;
- HTML and JSON reports;
- report parsing;
- severity and analysis-exception enforcement;
- evidence artifact upload before the final enforcement step;
- terminal decision JSON.

## 4. Snapshot evidence

| Control | Result |
|---|---|
| Snapshot ID | `20260701T17232-dd1c87899755-d1a98c428c3c` |
| Created at | `2026-07-01T17:23:21Z` |
| Age at accepted scan | `0` hours |
| Freshness tier | `preferred` |
| Archive size | `126281892` bytes |
| Archive SHA-256 | `28263ad1b95c417e667142196f7ecaf188861e581aad1095ed756bc89f18f057` |
| Database size | `253771776` bytes |
| Database SHA-256 | `d1a98c428c3c3f6631b810dd71ed2b0dbdf48b58ac4ce58d34978d8100f53998` |
| Verification result | `valid` |

The publisher defect that prevented `latest.json` promotion was corrected. The legacy code treated an asset-delete 404 as a missing release and attempted to recreate an existing tag. The corrected publisher uses the GitHub release-asset endpoint, separates asset absence from release absence, and verifies that exactly one `latest.json` asset exists after promotion. Regression tests cover existing release, already-absent asset, and missing release scenarios.

## 5. Dependency remediation

The first complete scan produced:

```text
Dependencies: 84
Total findings: 25
Critical: 0
High: 1
Medium: 18
Low: 6
Analysis exceptions: 0
```

Remediation applied:

- Spring Boot upgraded from 3.5.6 to 3.5.16.
- Springdoc upgraded from 2.6.0 to 2.8.17.
- Swagger UI explicitly upgraded to 5.32.8.
- Swagger UI 5.32.8 embeds DOMPurify 3.4.11, replacing the vulnerable 3.3.2 bundle.
- the OSS Index configuration key was corrected to `ossindexAnalyzerEnabled`;
- Central, RetireJS, known-exploited, and hosted-suppression network paths are disabled for the offline gate;
- the Prometheus-server CVE falsely associated with Micrometer was handled by a package- and CVE-specific suppression with an automatic expiry on 2026-10-01.

The correction is not a broad severity suppression. It is limited to `io.micrometer:micrometer-registry-prometheus` and one CVE whose affected product is the Prometheus server remote-read implementation, not the Micrometer Java registry.

## 6. Accepted OWASP result

| Metric | Result |
|---|---:|
| Dependencies analyzed | 78 |
| Dependencies with findings | 0 |
| Total vulnerabilities | 0 |
| Low | 0 |
| Medium | 0 |
| High | 0 |
| Critical | 0 |
| Unknown | 0 |
| Analysis exceptions | 0 |
| Suppressed unique findings | 11 |
| HTML report size | 750359 bytes |
| JSON report size | 464696 bytes |
| Terminal decision | PASS |

The accepted workflow completed every stage, including evidence upload and Final Enforcement.

## 7. Evidence package

The GitHub Actions artifact contains:

```text
apps/sanad-platform/target/dependency-check-report/dependency-check-report.html
apps/sanad-platform/target/dependency-check-report/dependency-check-report.json
artifacts/security/owasp/download-evidence.json
artifacts/security/owasp/snapshot-download.log
artifacts/security/owasp/snapshot-verification.log
artifacts/security/owasp/canonical-database-files.txt
artifacts/security/owasp/owasp-scan.log
artifacts/security/owasp/parsed-owasp-result.txt
artifacts/security/owasp/scan-execution.env
artifacts/security/owasp/terminal-enforcement.json
```

The artifact retention period is 30 days.

## 8. AWS/OIDC live-validation result

The live workflow run reached `Validate configured inputs` and failed before OIDC token exchange. The following values were not available as a complete configured set:

```text
NVD_SNAPSHOT_READER_ROLE
NVD_SNAPSHOT_REGION
NVD_SNAPSHOT_BUCKET
NVD_SNAPSHOT_PREFIX
NVD_SNAPSHOT_AWS_ACCOUNT_ID  # recommended
```

Consequently:

- no AWS STS role was assumed;
- no caller identity was produced;
- no S3 versioning/encryption evidence was produced;
- no AWS latest-pointer object was read;
- no claim of live AWS acceptance is made.

### Required external activation

1. Deploy `infra/aws/nvd-snapshot-reader-role.yml` in the approved AWS account.
2. Configure the GitHub `nvd-reader` environment.
3. Add the role ARN, region, bucket, prefix, and expected account as environment/repository variables.
4. Run `AWS NVD Reader Validation` manually.
5. Require a PASS artifact containing caller identity, versioning, encryption, object metadata, and `latest.json`.
6. Change the acceptance backend to `s3` only after the live validation passes.

## 9. Gate impact

```text
OWASP TERMINAL EVIDENCE: PASSED
CRM SOURCE-CONTROLLED SECURITY BASELINE: PASSED
AWS/OIDC CODE AND IAC: READY
AWS/OIDC LIVE ACCOUNT EVIDENCE: NOT ACHIEVED
CREDENTIAL ROTATION ISSUE #173: OPEN
FULL INSTALLATION: CONDITIONAL GO
PRODUCTION / COMMERCIAL RELEASE: NO-GO
```

## 10. Mandatory follow-up

- obtain and record the live AWS/OIDC PASS artifact;
- close or formally disposition Issue #197 only after that evidence exists;
- complete credential rotation and old-value rejection evidence in Issue #173;
- preserve the OWASP artifact and terminal JSON with the accepted commit;
- review the expiring Micrometer CPE correction before 2026-10-01;
- keep scheduled feed and snapshot publishers enabled and monitored;
- do not authorize production until managed infrastructure, secrets, DR, and release gates are separately passed.
