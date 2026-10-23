# SANAD Governance Exception Policy — Approved Internal Security Reviewer

**Document ID:** `SANAD-GOV-SEC-SELF-ASSESSMENT-001`  
**Document status:** `PROPOSED — PENDING FORMAL APPROVAL`  
**Scope:** Internal security reviews and evidence preparation associated with `REM-P0-006`  
**Proposed designated internal security reviewer account:** `abdulrhmansenan1985@gmail.com`  
**Designation requested:** `APPROVED INTERNAL SECURITY REVIEWER — NOT INDEPENDENT`  
**Effective date:** Not effective until formally approved and recorded.

> **Classification notice:** Upon formal approval of this policy, the named account is designated as SANAD's approved **internal** security reviewer for the limited scope defined below. This designation is not an independent assessor appointment, external certification, or authorization to approve release-gate closure.

## 1. Purpose

Establish a controlled route for internal security review and evidence preparation while independent assurance is pending. The exception allows the named account to perform and document an authorized internal review; it does not substitute for independent assurance required by law, contract, customer commitment, certification scheme, or the governing release gate.

## 2. Designation and permitted authority

Upon formal approval of this policy, and after the account owner's identity has been verified, `abdulrhmansenan1985@gmail.com` is designated:

**SANAD Approved Internal Security Reviewer — `INTERNAL / NOT INDEPENDENT`.**

Within the documented scope of `REM-P0-006`, and subject to the technical permissions actually granted, the designated account may:

- Plan, coordinate, and conduct internal security reviews and authorized security tests.
- Inspect and assess security evidence, test results, remediation records, and retest outcomes.
- Record, classify, prioritize, and track findings, limitations, and residual risks.
- Review evidence traceability to an exact commit SHA, build, artifact, environment, and release scope.
- Produce and sign internal review reports, identify the review as approved internal work, and recommend remediation or further testing.
- Issue internal security review attestations and other internal security documents within the approved scope, provided every document clearly states its scope, evidence basis, limitations, validity period if applicable, and the classification `INTERNAL — NOT INDEPENDENT CERTIFICATION`.
- Approve the export or release of internal security reports, review attestations, findings summaries, and related security evidence to named recipients, subject to the export controls in Section 3A.
- Verify completion of internal review tasks and attest to the accuracy of its own internal review record.

This is an **internal review and controlled internal-document issuance/export authority only**. It does not authorize the account to issue, approve, sign, or represent any third-party, regulator-recognized, accredited, or independent security certificate; any certificate of conformity or compliance requiring a separate authorized issuer; or any document that asserts production readiness or closure of a release gate. It does not grant the account owner repository, production, protected-environment, secret, deployment, merge, or administrative permissions. Those permissions must be separately granted by authorized administrators through normal least-privilege access controls. The policy cannot bypass branch protection, required reviews, environment approvals, or access-control rules.

## 3. Identity, accountability, and evidence

Before the designation is activated, an authorized governance owner must verify the account owner's identity, record the accountable individual, confirm the intended scope, and ensure appropriate least-privilege access. The email address alone is not proof of identity, competence, or independence.

Each review record must include:
- The actual person/account performing the work and the designated internal-review role.
- Approval record for this policy, review date, scope, environment, and exact commit SHA.
- Test method, evidence references, reproducible run identifiers where available, and results.
- Failed tests, unresolved findings, limitations, and retest outcomes.
- Conflicts of interest, development/remediation involvement, and any self-review.
- The authorized decision-maker for residual-risk acceptance, where risk acceptance is permitted.

Internal review reports must be clearly labelled:

`APPROVED INTERNAL SECURITY REVIEW — NOT INDEPENDENT ASSURANCE`

The reviewer must not describe missing findings or incomplete evidence as proof that no vulnerabilities exist. The reviewer may recommend gate closure but may not declare `REM-P0-006` closed or issue an independent security certificate under this designation.

## 3A. Issuance, approval, and export of security documents

The designated account may issue internal security review reports, internal review attestations, findings summaries, remediation-status statements, and other internal security documents only within the approved scope. The document must not be titled or formatted in a way that falsely implies external accreditation, independent certification, regulatory approval, or release authorization.

The designated account may approve export of those internal documents and associated evidence to specifically identified recipients when all of the following are recorded:
- Document identifier, version, classification, purpose, and exact SHA/release scope where relevant.
- Recipient identity, organization, and destination/channel; business purpose and authorization to receive the information.
- Data-classification and disclosure review, including removal or masking of credentials, secrets, personal data, exploitable details, and unrelated tenant/customer data unless disclosure is explicitly authorized and necessary.
- Exporter's identity, approving account, timestamp, approved files, and a durable audit record of what was sent and to whom.
- Any restrictions on onward sharing, retention, or deletion, plus the applicable legal, contractual, customer, and incident-response requirements.

Approval to export does not override legal privilege, confidentiality obligations, customer permissions, data-protection requirements, incident-response controls, or secure-channel requirements. If recipient authorization or classification is uncertain, export must be held for review by the relevant data/security owner.

Any internal certificate or attestation issued under this policy must prominently state:

`INTERNAL SECURITY REVIEW ATTESTATION — NOT AN INDEPENDENT SECURITY CERTIFICATE`

The account may not issue or approve export of a document that falsely claims independent assurance, accredited certification, regulatory endorsement, or `REM-P0-006` closure. Independent or externally accredited certificates must be issued by the separately authorized and qualified body/person under the applicable scheme.

## 4. Independence and separation of duties

The designated reviewer may contribute to development or remediation only if that involvement is disclosed in the relevant record. Where practical, another authorized person should verify material evidence and any risk-acceptance decision. If the designated reviewer reviews their own work, the record must explicitly identify it as self-review.

The designation does not establish independence. The named account and its owner must not be represented as an independent assessor solely by virtue of this policy. Any later independent-assessor appointment requires separate identity, qualification, conflict-of-interest, and independence verification under the applicable governance process.

## 5. Non-waivable controls and prohibited effects

This policy does not:
- Change `REM-P0-006` closure state from `NOT_READY`.
- Satisfy or waive independent-assessor appointment, independence verification, trust-policy verification, formal approvals, release-provenance requirements, or any of the required security cases.
- Allow the internal reviewer to self-approve independent assurance, certify their own independence, accept risks outside their delegated authority, or approve release-gate closure.
- Authorize issuance or export approval for independent, accredited, regulatory, customer-mandated, or third-party security certificates unless the applicable scheme explicitly authorizes the named person and all required qualifications and approvals are independently verified.
- Change the required assessment target SHA or bind evidence to a different release.
- Disable branch protection, protected-environment reviewers, OIDC, signing, provenance, or audit logging.
- Authorize merge, deployment, rollback, production changes, commercial go-live, or payment activation.
- Override applicable legal, regulatory, contractual, customer, or certification-body requirements.

Until the authorized governance owners separately decide otherwise on documented evidence, the existing security hold, `G1 = HOLD`, `NO DEPLOY`, and `PAYMENTS = OFF` remain in force.

## 6. Approval and activation

This document remains a proposal and the designation is **not active** until authorized governance approval is recorded in the repository's authoritative governance process. The approval record must identify each approver, role, decision, timestamp, scope, evidence reference, accountable individual for the named account, and the review/expiry date. No approval may be entered on behalf of another person.

Following approval, the authorized repository administrator may implement only the access changes explicitly approved and necessary for the internal-review scope. Policy approval itself does not change repository permissions, environment protection, or gate status.

The policy may be cross-referenced from authoritative governance records only after formal approval. The assessment manifest must not be edited merely to imply that an independent assessor has been verified or that the gate is ready.

## 7. Review, expiry, and exit

The approving authority must set an explicit review date and expiry condition before the policy becomes effective. Reassess it after a material security incident, material release or architecture change, scope change, or appointment of an independent assessor.

The exception ends when the approved period expires or the independent-assurance path becomes available, whichever comes first, unless the authorized governance body explicitly renews it. Expiry does not convert prior internal reviews into independent assessments.

## 8. Decision requested

Approve the limited designation of `abdulrhmansenan1985@gmail.com` as **SANAD Approved Internal Security Reviewer — NOT INDEPENDENT**, including authority to issue internal security review attestations and approve controlled exports of internal security documents, subject to identity verification, least-privilege access, recipient authorization, data classification/redaction, complete audit logging, evidence traceability, conflict disclosure, and a defined review/expiry date. This does not authorize independent/accredited certificate issuance or release-gate closure.

Preserve all existing release and security gates. Independent certification remains a separate process requiring an actually independent evaluation and the approvals applicable to the requested certification.

**Approver:** Pending authorized governance decision  
**Accountable verified individual:** Pending  
**Approval evidence:** Pending  
**Effective date:** Pending  
**Review/expiry date:** Pending
