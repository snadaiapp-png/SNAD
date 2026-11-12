# SANAD — Lean Security Governance (proposed amendment, 2026-11-12)

**Status:** PROPOSED / NOT EFFECTIVE UNTIL MERGED AND APPROVED BY AUTHORIZED GOVERNANCE OWNER  
**Scope:** Project-internal development milestones and the isolated PostgreSQL Criterion 3 of G1; does not itself grant production or payment authorization.  
**Supersedes for this scope:** Any older internal checklist requiring paper signatures, mandatory PDF certificates, multiple attestations by separate accounts, or additional formal links *solely* to accept a technically evidenced internal criterion.  
**Interpretation:** Read with `SANAD-SECURITY-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25.md`; external and technical controls listed there remain mandatory where applicable.

## 1 — One accountable decision, not repeated bureaucracy

An authorized, named reviewer may issue a **single, dated, plain-text GitHub comment** containing: reviewer name and GitHub account, actual review role and any conflicts, exact SHA and environment, decision `APPROVED | HOLD | REJECTED`, scope, material evidence identifier (a SHA-256 manifest or existing repository/Actions artifact reference), and critical open findings. The comment **is the electronic attestation**; a handwritten signature, scanned PDF, notarization, additional named certificate, and repeated approval across multiple issues are **not required by this internal policy**.

A reviewer must actually examine the underlying evidence before asserting approval. An owner or implementer may issue an **internal** acceptance only while honestly identifying it as owner/implementer-attested; **independent** may only be claimed where the reviewer's different identity and separation from implementation have been established. No person may sign or attest for a named reviewer without explicit verified authorization. Reviewer's GitHub identity and human identity are not interchangeable.

## 2 — Evidence proportionate to the risk

One version-bound evidence manifest referenced in the approval comment is enough for *documentary linkage* of an internal development criterion, as long as the underlying evidence is preserved and retrievable by authorized auditors. A publicly accessible permanent binary ZIP URL is **not an independent prerequisite**. Private GitHub artifacts, access-controlled repository evidence, or controlled retained records are acceptable. Do not publish credentials or customer data. Evidence hashes alone do not prove real test execution.

A technical criterion is `PASS` only where the supporting test results and negative-path checks truly passed on the declared environment and SHA. `SKIPPED`, `BLOCKED`, missing cases, and conflicting decisions remain explicitly visible.

## 3 — Distinct approval tiers

- **Development milestone / isolated G1 Criterion 3:** One truthful and accountable GitHub decision, supported by inspectable evidence, may satisfy the *internal documentary acceptance requirement*; a check box may be updated thereafter by an authorized maintainer referencing that decision.
- **Independent security certification:** Must not be claimed from an owner self-review or a purported signature by someone else. If required by an external party, follow its independently applicable requirements.
- **Production RLS, backups/restores, operational tests, release GO/NO-GO, and payments:** Require actual technical proofs and the separate authorized release decision. This document does **not** waive production security gates, emergency change authorization, statutory/contractual controls, branch protection, credentials, or incident response.

## 4 — G1 #1462 application

For Criterion 3, the 66-case isolated matrix / 13 Red Team findings may be cited as **reported technical results** after review, together with SHA `286f905e39fb67723bdc79f46187b8d1671edc3a` and a retained SHA-256 manifest. One reviewer comment suffices in lieu of paper signature and redundant uploads if the evidence is inspectable by authorized reviewers. The reviewer must identify their *actual* account and disclose owner/executor involvement. Previous contradictory/check-all-three approval comments are not cured retroactively by this policy: one unambiguous newer decision is required.

Until its legitimate internal criterion is accepted and recorded, that checkbox remains open. Even after acceptance: `P0_SECURITY_HOLD=ACTIVE`, `G1=HOLD`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF` pending their **independent technical and release gates**.

## 5 — Change control

This document is **a governance proposal** and does not become effective merely by opening a PR. Authorized reviewers must merge it in accordance with existing branch protections; any code/workflow changes to align validation must be separately tested and reviewed. Do not rewrite historical evidence or falsely represent earlier approvals.
