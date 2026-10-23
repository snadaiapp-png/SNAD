# SANAD Governance Exception Policy — Internal Security Self-Assessment

**Document ID:** `SANAD-GOV-SEC-SELF-ASSESSMENT-001`  
**Document status:** `PROPOSED — PENDING FORMAL APPROVAL`  
**Scope:** Internal security reviews and evidence preparation associated with `REM-P0-006`  
**Proposed coordination account:** `abdulrhmansenan1985@gmail.com`  
**Effective date:** Not effective until formally approved and recorded.

> **Classification notice:** This policy authorizes no independent assessment and issues no security certification. Any work performed under this exception must be labelled **INTERNAL SELF-ASSESSMENT — NOT INDEPENDENT**.

## 1. Purpose

Provide a controlled, temporary route for conducting internal security reviews and organizing remediation evidence while an independently qualified assessor is unavailable. The exception improves internal visibility and remediation progress; it is not a substitute for independent assurance where required by law, contract, customer commitment, or the governing release gate.

## 2. Permitted activities

Once this policy is formally approved, the proposed coordination account may, within the access actually granted to it:

- Coordinate internal reviews and evidence collection.
- Execute or arrange internal tests within its authorized technical scope.
- Record test results, findings, limitations, and residual risks.
- Track remediation and retest evidence against an exact repository SHA and release scope.
- Prepare internal assessment reports and an evidence package for later independent review.

The email address is a proposed role identifier only. Repository administrators must verify the account owner and grant least-privilege access through the repository's normal access-control process. This document itself grants no account, repository, environment, or deployment permissions.

## 3. Mandatory classification and reporting

All work conducted under this exception must use the classification:

`INTERNAL_SELF_ASSESSMENT — NOT INDEPENDENT`

Each record must identify:
- The actual person or account performing the work and their role.
- Assessment date, scope, environment, and exact commit SHA.
- Test method, evidence references, results, and reproducible run identifiers where available.
- Failed tests, unresolved findings, limitations, and retest outcomes.
- Conflicts of interest, self-review, or other independence limitations.
- The decision-maker who accepted any residual risk, where such acceptance is authorized.

No report, dashboard, release note, or certificate may represent this work as independent assurance or as an independent security certification. Missing findings or incomplete evidence must not be described as proof that no vulnerabilities exist.

## 4. Separation of duties and conflicts

The coordinator may contribute to development or remediation only if that involvement is disclosed in the relevant record. Where practical, a different authorized person should verify the evidence and approve risk acceptance. If the same person performs and reviews work, the record must explicitly mark it as self-review.

This exception does not deem the proposed account or its owner independent merely because the account is named in this policy. Independence must be assessed separately for any future independent engagement.

## 5. Non-waivable controls and prohibited effects

This policy does not:
- Change `REM-P0-006` closure state from `NOT_READY`.
- Satisfy or waive independent-assessor appointment, independence verification, trust-policy verification, formal approval, or release-provenance requirements.
- Change the required assessment target SHA or bind evidence to a different release.
- Disable branch protection, protected-environment reviewers, OIDC, signing, provenance, or audit logging.
- Authorize merge, deployment, rollback, production changes, commercial go-live, or payment activation.
- Override applicable legal, regulatory, contractual, customer, or certification-body requirements.

Until the authorized governance owners separately decide otherwise on documented evidence, the existing security hold, `G1 = HOLD`, `NO DEPLOY`, and `PAYMENTS = OFF` remain in force.

## 6. Approval and recordkeeping

This document is a proposal and is not operationally effective until the required authorized approvals are recorded in the repository's authoritative governance process. The approval record must identify each approver, role, decision, timestamp, scope, and evidence reference. Do not populate approvals on behalf of another person.

The policy should be cross-referenced from the authoritative governance status and the `REM-P0-006` tracking record only after formal approval. The assessment manifest must not be edited merely to make it appear that an independent assessor has been verified or that the gate is ready.

## 7. Review, expiry, and exit

The approving authority must set an explicit review date and expiry condition before the policy becomes effective. Review it upon a material security incident, material release or architecture change, a change in scope, or appointment of an independent assessor.

The exception should end when the approved review period expires or the independent-assurance path is available, whichever comes first unless the authorized governance body explicitly renews it. Expiry does not convert prior self-assessments into independent assessments.

## 8. Decision requested

Approve only the limited use of internal self-assessments for remediation, monitoring, and evidence preparation, under the classification `INTERNAL SELF-ASSESSMENT — NOT INDEPENDENT`. Preserve all existing release and security gates. Independent certification remains a separate decision requiring actual independent evaluation and the approvals applicable to the requested certification.

**Approver:** Pending authorized governance decision  
**Approval evidence:** Pending  
**Effective date:** Pending  
**Review/expiry date:** Pending
