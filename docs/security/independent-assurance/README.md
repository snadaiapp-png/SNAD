# REM-P0-006 — Internal Technical Security Verification

**Current purpose:** Optional-to-certificate, risk-based internal technical verification and evidence tracking.  
**Certificate requirement:** `REVOKED_AS_PROJECT_COMPLETION_REQUIREMENT`  
**Independent reviewer/assessor appointment:** Not required by internal project governance.  
**Technical assessment state:** `NOT_READY` — the recorded 19-case matrix remains at its truthful state of 0/19 `COMPLETED`.  
**P0 security hold:** `ACTIVE`  
**Broad commercial go-live:** `NOT_APPROVED`

## Controlling governance decisions

- `DEC-SEC-CERT-REVOCATION-REM-P0-006-001` (2026-10-24) revoked the independent certificate as a project-completion requirement.
- `DEC-SEC-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25-001` (2026-10-25) confirms the rule applies project-wide: neither a named independent reviewer/assessor nor an independent certificate is an internal completion prerequisite.
- Formal decision: `docs/governance/SANAD-SECURITY-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25.md`.
- Decision record: GitHub Issue #1438, comment 6389802911.
- The earlier sole-reviewer designation, assessor appointment, three certificate-closure approvals, and post-completion certificate plan are historical records only. They do not impose current completion obligations.

## What this directory is for

Use this directory to track technical security tests, evidence integrity, findings, remediation and retest status. It is not a certification workflow and its status must not be represented as an independent assessment or certificate.

Relevant evidence files include:

- `TEST-COVERAGE-MATRIX.json` — current recorded 19-case matrix; do not infer completion.
- `assessment-manifest.json` — evidence and assessed-release references.
- `findings-register.json` — tracked findings and their real status.
- `evidence-index.json` — evidence references and integrity metadata.

A missing or incomplete item must remain missing or incomplete in the evidence. Do not create synthetic approvals, mark tests passed without execution, claim that a certificate exists, or treat a readiness-validation pass as security acceptance.

## Technical controls remain in force

The governance revocation does not waive or disable:

1. PostgreSQL RLS and tenant-isolation enforcement, including direct-database tests using the actual restricted application role.
2. Authentication, authorization, BOLA/IDOR and privilege-boundary testing.
3. Dependency, build, artifact and software-supply-chain controls.
4. Evidence integrity, exact-SHA traceability and truthful test reporting.
5. Protected branches, required reviews where configured, protected environments, OIDC/signature checks and audit logging.
6. Any applicable incident-response or release-specific security criteria.

This directory's readiness workflow may validate package structure and evidence integrity. Such validation is not a project-completion gate for obtaining an independent certificate, does not close `REM-P0-006`, and does not lift any standing hold.

## External obligations

This internal decision does not waive a requirement imposed by law, regulation, contract, customer commitment, insurer, regulator, or certification scheme. If one applies to a particular release or claim, identify the competent authority and satisfy the obligation, or obtain a valid written waiver from the authority empowered to grant it, before proceeding with the affected release or claim.

## Standing holds — unchanged

- `G1 = HOLD`
- `P0 SECURITY HOLD = ACTIVE`
- `NO DEPLOY`
- `PAYMENTS = OFF`
- `commercial_go_live = NOT_APPROVED`

No reviewer appointment, certificate, workflow result, or documentation change alone changes these statuses. Each gate requires its own authorized decision and evidence.
