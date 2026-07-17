# REM-P0-006 — Independent Security Assurance

**Current gate:** `NOT_READY`

**Commercial go-live:** `NOT_APPROVED`

This package is the execution and evidence boundary for `REM-P0-006`. It does not claim that SANAD has completed an independent assessment. Internal developers, automated scanners and repository maintainers may prepare the scope and remediate findings, but they cannot certify their own work as independent assurance.

## Required independence

The assessor must be organizationally independent from the people who designed, implemented, operate or approve the assessed controls. Before testing, Security Governance must verify the assessor's legal identity, competence, scope, conflict declaration and rules of engagement. The assessor must receive no production secrets through this repository.

## Required assessment scope

1. Authenticated and unauthenticated penetration testing of the approved target.
2. Tenant-boundary, BOLA/IDOR and object-level authorization testing using at least two isolated tenants and multiple privilege levels.
3. Production configuration, identity, access, logging, encryption, secret storage and exposed-service review.
4. Source, dependency, container, CI/CD and software-supply-chain review.
5. Privacy/data-flow review and challenge of the threat model in `THREAT-MODEL.md`.
6. Remediation verification and independent retest on the exact candidate release.

Testing must cover the BFF, backend APIs, administrative/control-plane functions, business-process APIs, file/import surfaces, integrations and failure paths. Destructive testing requires a separately approved isolated target. No denial-of-service, social engineering, persistence or real-customer data access is authorized by this repository package.

## Evidence rules

- Every test and retest names the exact 40-character repository SHA, deployment/configuration version, target environment and UTC window.
- Evidence stored in this directory must be sanitized and content-addressed with SHA-256. Raw secrets, credentials, customer data and exploit material capable of harming the live service stay in the assessor's restricted evidence system.
- Each finding has severity, affected asset, reproduction reference, owner, remediation, retest result and disposition.
- Critical and high findings must be closed and independently retested. Medium/low residual risk requires a named owner, treatment and review/expiry date.
- The independent assessor, Security Governance and Project Owner must each approve. A CI pass is not an owner approval.

## Execution sequence

1. Appoint and verify the independent assessor; record non-secret appointment evidence.
2. Freeze the assessed release and production configuration version.
3. Approve the rules of engagement and target allowlist.
4. Execute all six workstreams and populate `assessment-manifest.json`.
5. Remediate findings in normal change control; deploy a new exact candidate version.
6. Have the independent assessor retest every material finding and regression-sensitive control.
7. Run the closure validator and obtain all three approvals.
8. Publish a separate dated closure decision. Only then may current-status authorities move `REM-P0-006` from open to closed.

## Validation

Preparation/structure check:

```bash
python3 scripts/ci/validate_independent_security_assurance.py --mode readiness
```

Fail-closed closure check:

```bash
python3 scripts/ci/validate_independent_security_assurance.py --mode closure
```

Closure mode rejects missing independence proof, incomplete workstreams, absent or tampered evidence, open critical/high findings and missing approvals.
