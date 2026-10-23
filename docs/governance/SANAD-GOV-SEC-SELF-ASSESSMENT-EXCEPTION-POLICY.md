# SANAD Governance Exception Policy — Approved Internal Security Reviewer

**Document ID:** `SANAD-GOV-SEC-SELF-ASSESSMENT-001`  
**Document status:** `PROJECT-OWNER AUTHORIZATION RECORDED — IMPLEMENTATION CONTROLS PENDING`  
**Scope:** Internal security reviews and evidence preparation associated with `REM-P0-006`  
**Proposed designated internal security reviewer account:** `abdulrhmansenan1985@gmail.com`  
**Designation requested:** `APPROVED INTERNAL SECURITY REVIEWER — NOT INDEPENDENT`  
**Owner authorization date:** 2026-10-23  
**Effective status:** Project-owner delegation is granted as recorded below. Operational activation remains subject to identity verification, approved technical access, and any mandatory legal/contractual or scheme-specific requirements.

> **Classification notice:** The project owner has authorized the named account for the internal review and internal-document issuance/export scope defined below. This designation is not an independent assessor appointment, external certification, or authorization to approve release-gate closure.

## 1. Purpose

Establish a controlled internal-only security-assurance route for SANAD and record the project owner's request to avoid enhanced independent-assessment costs where independent review is optional. The named account may perform and document internal reviews and issue clearly labelled internal attestations. A waiver of an external review requirement is effective only after a documented applicability check confirms that no law, regulation, contract, customer commitment, certification scheme, regulator, or mandatory project/release control requires an independent assessor. This policy cannot waive obligations imposed by another authority or turn an internal review into independent certification.

## 2. Designation and permitted authority

By the project owner's authorization recorded in Section 6, and after the account owner's identity has been verified, `abdulrhmansenan1985@gmail.com` is designated:

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

Before operational access is enabled, an authorized administrator must verify the account owner's identity, record the accountable individual, confirm the intended scope, and grant appropriate least-privilege access. The email address alone is not proof of identity, competence, or independence. The owner authorization establishes the delegated policy scope; it does not itself provision credentials or repository/system permissions.

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

## 5. Internal-only route, conditional waiver, and non-waivable controls

**Project-owner direction:** Prefer internal security reviews by `abdulrhmansenan1985@gmail.com` instead of commissioning enhanced independent assurance when, and only when, independent assurance is not mandatory under the applicable law, regulation, contract, customer commitment, certification scheme, or formally approved release policy. This is a request for a scoped governance exception, not a blanket legal or regulatory exemption.

Before relying on the internal-only route for a particular release or certificate request, the governance owner must record: (a) the exact release/SHA and intended claims; (b) the applicable legal, regulatory, contractual, customer, insurer, and certification-scheme requirements checked; (c) the authority empowered to approve any project-internal exception; (d) the risks accepted and by whom; and (e) the explicit statement that the output is an internal review attestation, not independent or accredited certification. If any mandatory requirement is unknown or cannot be evidenced, the waiver is `NOT ESTABLISHED` and the affected external certification/release claim must not be made.

For the current `REM-P0-006` gate, this document alone does not establish that independent assurance can be waived. Until the gate owner and applicable requirement owners document a valid exception and all other release criteria are satisfied, the existing `NOT_READY` state and security hold remain unchanged.

This policy does not:
- Change `REM-P0-006` closure state from `NOT_READY` by itself.
- Falsify, bypass, or silently mark complete independent-assessor appointment, independence verification, trust-policy verification, formal approvals, release-provenance requirements, or any required security cases.
- Allow the internal reviewer to self-approve independent assurance, certify their own independence, accept risks outside their delegated authority, or approve release-gate closure.
- Authorize issuance or export approval for independent, accredited, regulatory, customer-mandated, or third-party security certificates unless the applicable scheme explicitly authorizes the named person and all required qualifications and approvals are independently verified.
- Change the required assessment target SHA or bind evidence to a different release.
- Disable branch protection, protected-environment reviewers, OIDC, signing, provenance, or audit logging.
- Authorize merge, deployment, rollback, production changes, commercial go-live, or payment activation.
- Override applicable legal, regulatory, contractual, customer, or certification-body requirements.

Until the authorized governance owners separately decide otherwise on documented evidence, including a valid applicability determination for any proposed exception, the existing security hold, `G1 = HOLD`, `NO DEPLOY`, and `PAYMENTS = OFF` remain in force.

## 6. Approval and activation

The project owner has granted the delegation requested in this conversation, recorded as an owner decision dated 2026-10-23. This record is limited to the internal review and internal-document issuance/export authority defined here. The repository governance record should retain the owner decision, scope, timestamp, accountable individual for the named account, and review/expiry date. This policy does not claim cryptographic proof of the conversation or of the account owner's identity.

Operational activation requires identity verification and separate implementation of only the approved least-privilege access changes by an authorized administrator. This policy does not itself change repository permissions, environment protection, or gate status. Where a legally binding or scheme-specific approval is required, this owner delegation does not replace it.

The recorded project-owner decision may be cross-referenced from authoritative governance records. Any mandatory governance workflow or legally required approval still applies. The assessment manifest must not be edited merely to imply that an independent assessor has been verified or that the gate is ready.

## 7. Review, expiry, and exit

The approving authority must set an explicit review date and expiry condition before the policy becomes effective. Reassess it after a material security incident, material release or architecture change, scope change, or appointment of an independent assessor.

The exception ends when the approved period expires or the independent-assurance path becomes available, whichever comes first, unless the authorized governance body explicitly renews it. Expiry does not convert prior internal reviews into independent assessments.

## 8. Recorded project-owner decisions

**Decision A — Internal delegation:** `GRANTED — LIMITED INTERNAL DELEGATION`  
**Decision date:** `2026-10-23`  
**Decision source:** Explicit instruction from the project owner in this ChatGPT conversation to grant the named account internal security review, internal security document issuance, and controlled export-approval authority.  
**Delegated account:** `abdulrhmansenan1985@gmail.com`  
**Delegated role:** `SANAD Internal Security Reviewer and Internal Security Document Issuance/Export Approver — NOT INDEPENDENT`  
**Scope:** Internal security review reports, attestations, findings summaries, remediation-status statements, and controlled exports of internal security evidence, subject to Sections 2 and 3A.  
**Excluded authority:** Independent/accredited/regulatory/third-party certification; declaration of REM-P0-006 closure; release approval; merge/deploy/production/payment authority; or access beyond separately granted least-privilege permissions.  
**Accountable verified individual:** Pending identity verification.  
**Operational activation:** Pending identity verification and separate technical access provisioning.  
**Review/expiry date:** Must be recorded by the project owner or delegated governance administrator before operational access is provisioned.

**Decision B — Request for conditional internal-only assurance route:** `REQUESTED — APPLICABILITY REVIEW REQUIRED; NOT A BLANKET WAIVER`  
**Decision date:** `2026-10-23`  
**Owner direction:** Prefer reviews performed by the delegated internal reviewer and internal review attestations over enhanced independent assessment when external independence is not mandatory and the competent governance authority approves the scoped exception.  
**Required before use:** Documented requirement/applicability review for the target release and intended claims; confirmation of the approving authority; recorded risk acceptance where permitted; completion of the applicable security test/evidence set; and explicit internal-only labelling.  
**Current status for REM-P0-006:** `NOT_READY — NO WAIVER ESTABLISHED BY THIS POLICY ALONE`.

This records the project owner's requested governance direction, but does not itself waive any external requirement, certify the account as independent, authorize an accredited or regulatory certificate, or close `REM-P0-006`. Any internal attestation must not be marketed or represented as an independent/accredited security certificate. All existing security and release gates remain in force until the authorized gate owners document a valid decision based on applicable requirements and evidence.
