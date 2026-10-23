# Owner Risk Acceptance Register

## Purpose

Record residual risks that only the Project Owner account may accept.

## Risk acceptance rules

- Critical risks are not accepted for production release unless explicitly documented as emergency owner exception.
- High risks require remediation or owner acceptance with compensating controls.
- Every accepted risk must include expiry and review date.
- **Remediation supersedes risk acceptance:** a finding eliminated by
  engineering changes is recorded as SUPERSEDED and requires no owner
  signature.

## Register

| Risk ID | Source issue | Severity | Risk | Status | Owner decision | Expiry | Evidence |
|---|---|---|---|---|---|---|---|
| RISK-CRM-032-001 | HIGH-01 | HIGH | Test encryption key hardcoded as default in application-local.yml | ✅ SUPERSEDED — remediated by engineering (2026-07-31) | N/A — not accepted; remediated | N/A | `ProductionSecurityGuard.java`, `CrmEncryptionKeyValidator.java`, `application-local.yml`, `ProductionSecurityGuardTest` (8/8), `CrmEncryptionKeyValidatorTest` (8/8) |
| RISK-CRM-032-002 | HIGH-02 | HIGH | No startup guard for RLS, rate limiter, actuator endpoints | ✅ SUPERSEDED — remediated by engineering (2026-07-31) | N/A — not accepted; remediated | N/A | `ProductionSecurityGuard.java`, `META-INF/spring.factories`, `ProductionSecurityGuardTest` (8/8) |
| RISK-REM-P0-006-001 | REM-P0-006 / Issue #784 | HIGH | Unsigned branch commits in the assessed range; `required_signatures` disabled on `main` | 🟡 ACCEPTED with compensating controls (2026-10-23) | Owner decision `OWNER-PHASE-APPROVAL-REM-P0-006-001` §2.5 | 2026-11-30 (review 2026-11-15) | Live branch-protection verification 2026-10-23 (`bp-main-legacy-verified-20261023.json`): `enforce_admins=true`, 6 required checks, 1 required review, force-push/deletion denied; ruleset `min` (17903112); web-flow-signed merge commits |
| RISK-REM-P0-006-002 | REM-P0-006 / B9 | HIGH | Assessor role conflict — appointed assessor account operated release-operator roles; no independence attestation | 🟡 ACCEPTED for the owner-attested phase certification ONLY; NOT accepted for closure (2026-10-23) | Owner decision `B9-RESOLUTION-REM-P0-006-001` | 2026-11-30 (review 2026-11-15) | `B9-ROLE-CONFLICT-RESOLUTION-2026-10-23.md`; manifest `independence_status=PENDING_VERIFICATION`; dual-control compensating controls (§2.4) |
| RISK-REM-P0-006-003 | REM-P0-006 / PR #1433 | MEDIUM | Build provenance attestation control unmerged; PR carries a failing `Frontend Production Dependency Audit` check | 🟡 OPEN — tracked to merge or rework (2026-10-23) | Owner decision `OWNER-PHASE-APPROVAL-REM-P0-006-001` §2.1 (phase state accepted as-is) | 2026-11-15 | PR #1433 check-run state 2026-10-23 (22 checks; 1 failure) |
| RISK-REM-P0-006-004 | Credential verification 2026-10-23 | MEDIUM | Supabase and Resend access tokens presented by the owner failed live verification (expired/revoked) — credential lifecycle gap | 🟡 ACCEPTED — rotation action recorded (2026-10-23) | Owner decision `OWNER-PHASE-APPROVAL-REM-P0-006-001` §4.1 | 2026-11-15 | API responses: Supabase `Invalid access token`; Resend `API key is invalid`. Supporting services only; neither is part of the assessed release runtime |

## Risk Acceptance Details

### RISK-CRM-032-001: Test Encryption Key Hardcoded as Default

| Field | Value |
|---|---|
| **Risk ID** | RISK-CRM-032-001 |
| **Source Issue** | HIGH-01 |
| **Severity** | HIGH |
| **Finding Title** | Test Encryption Key Hardcoded as Default |
| **Technical Description** | The `custom-field-encryption-key` property had a hardcoded default value (`AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=`) in the local profile. If the `local` profile accidentally activates in a deployed environment, this test key would be used for encrypting sensitive custom field values. |
| **Resolution** | ✅ REMEDIATED (2026-07-31) — no risk acceptance required |
| **Remediation Evidence** | 1. `ProductionSecurityGuard` refuses prod startup on test/default key. 2. `CrmEncryptionKeyValidator` rejects the known test key and trivially weak keys unconditionally in both crypto paths. 3. Test key default removed from `application-local.yml`; non-prod unconfigured → ephemeral AES-256 key. 4. Prod requires `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`. |
| **Test Evidence** | `ProductionSecurityGuardTest` 8/8 PASS; `CrmEncryptionKeyValidatorTest` 8/8 PASS |
| **Owner** | N/A — remediated, no acceptance required |
| **External Approver** | N/A |
| **Decision** | SUPERSEDED (remediation) |
| **Approval Date** | N/A |
| **Review Date** | N/A |
| **Expiration Date** | N/A |
| **Signature Evidence** | Not required — finding eliminated by engineering change |

### RISK-CRM-032-002: No Startup Guard for Production-Critical Security Features

| Field | Value |
|---|---|
| **Risk ID** | RISK-CRM-032-002 |
| **Source Issue** | HIGH-02 |
| **Severity** | HIGH |
| **Finding Title** | No Startup Guard for Production-Critical Security Features |
| **Technical Description** | No startup validation ensured critical security features are enabled in production: (1) Row-Level Security could be disabled without detection, (2) Rate limiter could remain in-memory without distributed adapter, (3) Actuator endpoints could be over-exposed. |
| **Resolution** | ✅ REMEDIATED (2026-07-31) — no risk acceptance required |
| **Remediation Evidence** | 1. `ProductionSecurityGuard` (EnvironmentPostProcessor, prod profile) validates encryption key, `snad.rls.enabled`, and actuator exposure before any bean is created. 2. Registered in `META-INF/spring.factories`. 3. `SKIP_SECURITY_GUARD=true` escape hatch is audited via WARN log. |
| **Test Evidence** | `ProductionSecurityGuardTest` 8/8 PASS (incl. `blocksOnRlsDisabled`, `blocksOnSensitiveActuatorEndpoint`) |
| **Owner** | N/A — remediated, no acceptance required |
| **External Approver** | N/A |
| **Decision** | SUPERSEDED (remediation) |
| **Approval Date** | N/A |
| **Review Date** | N/A |
| **Expiration Date** | N/A |
| **Signature Evidence** | Not required — finding eliminated by engineering change |

### RISK-REM-P0-006-001: Unsigned Commits and Disabled `required_signatures` on `main`

| Field | Value |
|---|---|
| **Risk ID** | RISK-REM-P0-006-001 |
| **Source Issue** | REM-P0-006 / Issue #784 (trust-policy control `GIT-SIGNATURES`) |
| **Severity** | HIGH |
| **Finding Title** | Branch commits in the assessed range are unsigned; legacy `required_signatures` is disabled on `main` |
| **Technical Description** | Commits of the assessed branches (PRs #1431/#1432, verified 2026-10-22) carry no GPG/SSH/S/MIME signatures; the branch-protection verification on 2026-10-23 confirmed `required_signatures.enabled=false` on `main`. Merge commits are signed by GitHub web-flow, and the active ruleset enforces non-fast-forward and review gates, but the per-commit provenance of the assessed range is weaker than the trust policy's critical control. |
| **Resolution** | 🟡 ACCEPTED with compensating controls — remediation planned |
| **Compensating Controls** | 1. Merge commits into `main` are web-flow-signed. 2. Ruleset `min` (17903112) enforces 1 approving review, stale-review dismissal, extra approval for unattributed changes, non-fast-forward, deletion denial. 3. Legacy protection: `enforce_admins=true`, 6 required status checks. 4. Cryptographic content binding (tree/diff digests) substitutes commit-signature strength for the already-merged mainline content. |
| **Remediation Path** | Both project accounts enable commit signing (GPG or SSH), re-push governance branches signed, then enable `required_signatures` on `main` via the protection API; verified by re-running the branch-protection check. |
| **Owner** | snadaiapp-png |
| **Decision** | ACCEPTED (2026-10-23) — `OWNER-PHASE-APPROVAL-REM-P0-006-001` |
| **Approval Date** | 2026-10-23 |
| **Review Date** | 2026-11-15 |
| **Expiration Date** | 2026-11-30 |
| **Signature Evidence** | Owner-attributed governance commit + Issue #784 comment (2026-10-23) |

### RISK-REM-P0-006-002: Assessor Role Conflict (B9)

| Field | Value |
|---|---|
| **Risk ID** | RISK-REM-P0-006-002 |
| **Source Issue** | REM-P0-006 / finding B9 (trust-policy control `ASSESSOR-TRUST`) |
| **Severity** | HIGH |
| **Finding Title** | Appointed assessor account simultaneously operated release-operator roles; independence unattested |
| **Technical Description** | `abdulrhmansenan1985-creator` merged PR #1431, triggered the release push run, and created a production deployment record inside the assessed window, while being the appointed independent assessor; both accounts belong to the same natural person per the owner's statement. The organizational-separation requirement of `ASSESSOR-TRUST` is not met in substance. |
| **Resolution** | 🟡 ACCEPTED for the owner-attested phase certification ONLY — explicitly NOT accepted for closure |
| **Compensating Controls** | Dual-control (both accounts co-record assessor-significant evidence); `rem-p0-006-closure` environment keeps both accounts as required reviewers with `can_admins_bypass=false`; no document may be labeled independent under this conflict; preferred path is engagement of a genuinely external assessor. |
| **Remediation Path** | `B9-ROLE-CONFLICT-RESOLUTION-2026-10-23.md` §2.3(a)/(b). |
| **Owner** | snadaiapp-png |
| **Decision** | ACCEPTED (2026-10-23) — `B9-RESOLUTION-REM-P0-006-001` |
| **Approval Date** | 2026-10-23 |
| **Review Date** | 2026-11-15 |
| **Expiration Date** | 2026-11-30 |
| **Signature Evidence** | Owner-attributed governance commit + Issue #784 comment (2026-10-23) |

### RISK-REM-P0-006-003: Build Provenance Attestation Control Unmerged (PR #1433)

| Field | Value |
|---|---|
| **Risk ID** | RISK-REM-P0-006-003 |
| **Source Issue** | REM-P0-006 / supply-chain condition 6 of Phase G |
| **Severity** | MEDIUM |
| **Finding Title** | BuildKit provenance/SBOM and build attestation workflow unmerged; PR carries a failing required-ish check |
| **Technical Description** | PR #1433 (`security/ghcr-build-provenance-attestation`, head `806ffef2`) adds verifiable build attestations for future images. On 2026-10-23 its check-run state showed 22 checks with `Frontend Production Dependency Audit` failing; the PR is therefore not mergeable under a green-CI discipline. The currently live image retains its SLSA v1 provenance from the July workflow, so this is a forward-control gap, not a live-chain gap. |
| **Resolution** | 🟡 OPEN — tracked to merge (after CI fix) or rework |
| **Compensating Controls** | The live production image digest is pinned and triple-source verified; the attestation gap affects future builds only. |
| **Remediation Path** | Fix the dependency-audit failure on the PR branch, then merge through the protected review path. |
| **Owner** | snadaiapp-png |
| **Decision** | ACCEPTED as open item (2026-10-23) |
| **Approval Date** | 2026-10-23 |
| **Review Date** | 2026-11-15 |
| **Expiration Date** | 2026-11-15 |
| **Signature Evidence** | Owner-attributed governance commit + Issue #784 comment (2026-10-23) |

### RISK-REM-P0-006-004: Invalid Supabase and Resend Access Tokens

| Field | Value |
|---|---|
| **Risk ID** | RISK-REM-P0-006-004 |
| **Source Issue** | Credential verification exercise, 2026-10-23 |
| **Severity** | MEDIUM |
| **Finding Title** | Two of five owner-presented access tokens failed live verification |
| **Technical Description** | During the 2026-10-23 verification exercise, the Supabase access token was rejected (`Invalid access token`) and the Resend API key was rejected (`API key is invalid`). GitHub, Vercel and Render tokens verified successfully. Expired/revoked credentials still held by the owner indicate a credential-lifecycle gap (no rotation/deletion discipline), which is the same class of weakness as incident 109. |
| **Resolution** | 🟡 ACCEPTED — rotation action recorded |
| **Compensating Controls** | Neither service is part of the assessed release runtime; no secret material is stored in repository evidence files; tokens exercised only against official vendor APIs. |
| **Remediation Path** | Owner revokes/rotates the dead credentials at the vendors, adopts a verification-before-storage step, and re-runs the identity check; tokens shared in chat channels are rotated after use. |
| **Owner** | snadaiapp-png |
| **Decision** | ACCEPTED (2026-10-23) |
| **Approval Date** | 2026-10-23 |
| **Review Date** | 2026-11-15 |
| **Expiration Date** | 2026-11-15 |
| **Signature Evidence** | Owner-attributed governance commit + Issue #784 comment (2026-10-23) |

## Owner decision template

| Field | Value |
|---|---|
| Owner account | snadaiapp-png |
| Decision | TBD |
| Release SHA | TBD |
| UTC timestamp | TBD |
| Scope | TBD |
| Review date | TBD |

## Closure

This register is mandatory for any non-remediated High risk or exceptional Critical-risk decision.

**Status:** ✅ CLOSED — Both HIGH findings (HIGH-01, HIGH-02) were remediated
by engineering changes on 2026-07-31 (CRM-032 Engineering Remediation,
Security-by-Design). No owner risk acceptance was required or recorded.
Per SANAD governance, remediation supersedes risk acceptance.
