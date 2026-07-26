# REM-P0-006 — Independent Security Assurance

**Current state:** `OPEN / NOT_READY`  
**Independent assessor:** `NOT_APPOINTED`  
**Broad commercial go-live:** `NOT_APPROVED`

This directory is the fail-closed execution and evidence boundary for REM-P0-006. Internal developers and automated scanners may prepare the package and remediate findings, but they cannot certify their own work as independent assurance.

## Required workstreams

1. Authenticated and unauthenticated penetration testing.
2. Tenant-boundary, BOLA/IDOR and object-level authorization testing using isolated tenants and multiple privilege levels.
3. Production configuration, identity, access, logging, encryption and secret-management review.
4. Source, dependency, container, CI/CD and software-supply-chain review.
5. Privacy/data-flow review and independent challenge of the threat model.
6. Remediation verification and independent retest on the exact candidate release.

## Evidence model

The manifest is not authoritative by itself. Closure validation reconciles all of the following:

- `assessment-manifest.json`
- `TEST-COVERAGE-MATRIX.json`
- `findings-register.json`
- `evidence-index.json`
- sanitized local evidence or immutable restricted external references

The validator rejects missing coverage, self-declared finding counts, reused cross-workstream evidence shortcuts, evidence digest mismatch, open critical/high findings, unverified assessor independence, non-distinct approvals, approval timestamps preceding assessment completion and an assessed release SHA that differs from the workflow-authorized SHA.

## State sequence

1. Keep `closure_state=NOT_READY` during appointment, testing and remediation.
2. Set `READY_FOR_APPROVAL` only after every required case passes, the findings register is complete, every workstream has dedicated evidence and all material findings pass independent retest.
3. Obtain three separate approvals: independent assessor, Security Governance and Project Owner.
4. Run workflow dispatch in `closure` mode on `main`, supplying the exact assessed release SHA. The job is bound to the protected `rem-p0-006-closure` GitHub Environment.
5. Publish a separate dated closure decision. Only that governed change may set `ACCEPTED` and update current-status authorities.

## Validation commands

```bash
python3 -m unittest tests/ci/test_validate_independent_security_assurance.py -v
python3 scripts/ci/validate_independent_security_assurance.py --mode readiness
python3 scripts/ci/validate_independent_security_assurance.py \
  --mode closure \
  --expected-release-sha <40-character-release-sha>
```

A CI pass does not appoint an assessor, authorize testing, accept residual risk, close REM-P0-006 or approve commercial go-live.
