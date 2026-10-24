# SANAD Governance Exception Policy — Historical Internal Reviewer Delegation and Superseding Revocation

**Document ID:** `SANAD-GOV-SEC-SELF-ASSESSMENT-001`  
**Document status:** `HISTORICAL RECORD — reviewer designation and certificate plan are not current project-completion requirements`  
**Scope:** Internal security reviews and evidence preparation associated with `REM-P0-006`  
**Proposed designated internal security reviewer account:** `abdulrhmansenan1985@gmail.com`  
**Designation requested:** `APPROVED INTERNAL SECURITY REVIEWER — NOT INDEPENDENT`  
**Owner authorization date:** 2026-10-23  
**Effective status:** Project-owner delegation is granted as recorded below. Operational activation remains subject to identity verification, approved technical access, and any mandatory legal/contractual or scheme-specific requirements.  
**Superseding decisions (2026-10-24 and 2026-10-25):** `DEC-SEC-CERT-REVOCATION-REM-P0-006-001` (`docs/governance/SANAD-SECURITY-CERTIFICATION-REQUIREMENT-REVOCATION-2026-10-24.md`) revokes the security-certificate requirement as a project-completion condition and cancels the post-completion unified-certificate plan recorded in Decision B below. Sections 1–8 and Decisions A–C are retained as historical audit record. Project-wide Decision D below supersedes any language that treats a named reviewer, independent assessor, or certificate as mandatory for development, module closure, or project completion.

> **Classification notice:** The project owner has authorized the named account for the internal review and internal-document issuance/export scope defined below. This designation is not an independent assessor appointment, external certification, or authorization to approve release-gate closure.

## 1. Purpose and temporary project-development policy

Record the project owner's direction that, during active development and until formal project completion, SANAD should use internal security reviews by `abdulrhmansenan1985@gmail.com` as its default assurance activity and should not initiate or procure optional enhanced independent assessments, external security certificates, or other optional third-party approval services. After formal project completion, the project intends to consolidate the full-project security evidence package and seek a unified assessment/certificate from an appropriately qualified independent body.

This is a project procurement and sequencing decision for optional services, not a legal or regulatory exemption. It cannot cancel, suppress, or waive an obligation imposed by law, regulation, contract, customer commitment, insurer, certification scheme, regulator, or a mandatory security/release gate. If such an obligation applies, the project must record it and handle it before making the affected release, deployment, customer assurance, or certification claim. Internal reviews and attestations remain explicitly internal and are not substitutes for mandatory independent assurance.

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

## 5. Temporary deferral of optional external assurance and non-waivable controls

**Project-owner direction:** Until formal project completion is recorded, do not initiate or procure optional independent security reviews, external security certificates, or optional third-party approvals. Use internal reviews by `abdulrhmansenan1985@gmail.com` as the default development-phase review route, with internal-only attestations. After completion, prepare one consolidated project-wide evidence package and pursue a unified assessment/certificate from an appropriately qualified independent body, subject to the applicable scheme and its scope.

This deferral applies only to optional procurement and scheduling. It does not prohibit recording, disclosing, escalating, or satisfying a mandatory requirement. It does not permit the team to conceal a requirement, skip a required control, claim a waiver without authority, or release/deploy where a mandatory approval is outstanding. If a mandatory requirement is discovered, the accountable governance owner must record the source, applicability, affected scope, deadline, and required decision; any affected release or claim remains blocked until the requirement is met or a valid written waiver is issued by the authority legally/contractually empowered to grant it.

Before relying on the internal-only route for a particular release or certificate request, the governance owner must record: (a) the exact release/SHA and intended claims; (b) the applicable legal, regulatory, contractual, customer, insurer, and certification-scheme requirements checked; (c) the authority empowered to approve any project-internal exception; (d) the risks accepted and by whom; and (e) the explicit statement that the output is an internal review attestation, not independent or accredited certification. If any mandatory requirement is unknown or cannot be evidenced, the waiver is `NOT ESTABLISHED` and the affected external certification/release claim must not be made.

For the current `REM-P0-006` gate, this document alone does not establish that independent assurance can be waived. The temporary deferral does not close or bypass that gate. Until the gate owner documents a valid disposition consistent with all applicable requirements and all other release criteria are satisfied, the existing `NOT_READY` state and security hold remain unchanged.

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

**Decision B — Temporary deferral of optional external assurance:** `OWNER DIRECTION RECORDED — OPTIONAL SERVICES DEFERRED UNTIL FORMAL PROJECT COMPLETION`  
**Decision date:** `2026-10-23`  
**Owner direction:** During active development, use the delegated internal reviewer for internal reviews and do not initiate optional external review/certification procurement. At formal project completion, consolidate the project's security evidence and seek a unified project-wide assessment/certificate from a qualified independent body.  
**Completion trigger:** The project owner must record a formal project-completion decision and identify the frozen release scope/SHA before the consolidated independent assessment is commissioned.  
**Exception boundary:** Mandatory legal, regulatory, contractual, customer, scheme-specific, incident-response, and release-gate requirements remain applicable and cannot be cancelled by this internal policy. If one applies before completion, it must be escalated and satisfied for the affected release/claim; no false waiver or certification claim is permitted.  
**Current status for REM-P0-006:** `NOT_READY — TEMPORARY DEFERRAL DOES NOT WAIVE OR CLOSE THE GATE`.

This records the project owner's requested development-phase sequencing decision. It does not certify the account as independent, authorize an accredited or regulatory certificate, or close `REM-P0-006`. Internal attestations must not be marketed or represented as independent/accredited certificates. All existing security and release gates remain in force until their authorized owners document a valid disposition based on applicable requirements and evidence.

## 9. Decision C — Revocation of the security-certificate completion requirement

**Decision:** `REVOKED — THE CERTIFIED SECURITY CERTIFICATE IS NO LONGER A COMPLETION REQUIREMENT`  
**Decision ID:** `DEC-SEC-CERT-REVOCATION-REM-P0-006-001`  
**Decision date:** `2026-10-24` (Asia/Riyadh)  
**Decision source:** Direct written executive order from the project owner — «قرار إلغاء شرط الشهادة الأمنية المعتمدة» — authorizing cancellation of the mandatory independent security certification within project-completion requirements.  
**Authoritative record:** `docs/governance/SANAD-SECURITY-CERTIFICATION-REQUIREMENT-REVOCATION-2026-10-24.md`

Effects of Decision C on this policy:

1. The post-completion unified-certificate pursuit described in Section 1, Section 5 and Decision B is **cancelled as a project-completion element**. Project completion no longer depends on any certificate issued before or after completion, and the certificate is no longer a mandatory deferred item.
2. The independent-assessor appointment, the three closure approvals, and the `rem-p0-006-closure` environment path are **retired-optional**: they are no longer prerequisites for development, module closure, or project completion, and they run only if the project owner explicitly re-activates them by a later recorded decision.
3. The internal-reviewer delegation in Decision A (Sections 2–4, 6) is **unchanged** and remains bounded by its own recorded limits.
4. The technical security controls listed in the revocation decision (RLS/tenant isolation, authorization tests, dependency and supply-chain controls, evidence-integrity validators, branch and environment protection) remain fully in force and are not affected by the revocation.
5. No independent certificate has been issued; nothing in this policy or in the revocation decision claims otherwise. The 19-case coverage matrix remains at its truthful recorded state and is re-scoped as the internal technical-verification requirement — it must not be presented as certification.
6. Standing holds (`G1 = HOLD`, `P0 SECURITY HOLD = ACTIVE`, `NO MERGE` without required review, `NO DEPLOY`, `PAYMENTS = OFF`) and `commercial_go_live = NOT_APPROVED` are **not** changed by Decision C; each is lifted only through its own governed path and its own evidence.
7. If a mandatory legal, regulatory, contractual, customer, insurer, or scheme-specific external-assurance obligation is later identified, it must be recorded and satisfied for the affected release/claim under its own authority — Decision C neither cancels nor waives any such obligation.


## 10. Decision D — Project-wide revocation of mandatory reviewer and certificate requirements

**Decision ID:** `DEC-SEC-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25-001`  
**Decision date:** 2026-10-25 (Asia/Riyadh)  
**Authority:** Project Owner (`snadaiapp-png`), direct written instruction recorded at GitHub Issue #1438, comment 6389802911.  
**Status:** `EFFECTIVE FOR INTERNAL PROJECT GOVERNANCE`

This decision applies across SANAD, not only to `REM-P0-006`:

1. Appointment or verification of a named independent reviewer/assessor is **not** an internal project-completion prerequisite.
2. Issuance of an independent or unified security certificate is **not** an internal prerequisite for development completion, module closure, or project completion. The post-completion certificate plan in Decision B is cancelled as a required deliverable.
3. The account-specific designation in Decision A is historical and optional. It does not make that account the sole required reviewer, and no particular reviewer identity is mandated by this policy.
4. Sections 1–9 and Decisions A–C remain in this file solely to preserve the historical decision trail. Where they describe a future mandatory certificate, a sole required reviewer, or mandatory independent-assessor approvals, those descriptions are superseded by this Decision D.
5. This decision does **not** waive any obligation imposed by applicable law, regulation, contract, customer commitment, insurer, regulator, or certification scheme. Such obligations must be assessed separately and satisfied, or waived in writing by the authority competent to grant the waiver.
6. This decision does not close `REM-P0-006`, declare the 19-case matrix complete, or imply that any certificate has been issued. The matrix remains at its actual recorded state; RLS, tenant isolation, authorization testing, dependency/supply-chain controls, evidence integrity, branch/environment protections, and other technical security controls remain in force.
7. Existing operational holds are unchanged: `G1 = HOLD`, `P0 SECURITY HOLD = ACTIVE`, `NO DEPLOY`, `PAYMENTS = OFF`, and `commercial_go_live = NOT_APPROVED`. Each can change only through its own authorized gate and evidence.

The decision is a change to internal governance requirements, not a change to GitHub Secrets, access credentials, protected environments, or technical security controls. Do not store policy text in Secrets or remove credentials unless a separately authorized credential-rotation/removal action is required.
