# SANAD — Single-Approval Internal Security Governance
**Revision:** 2026-11-12 | **Status:** PROPOSED — effective only after authorized merge into main

## 1. Sole administrative sign-off

For project-internal security reviews, G1 development-stage approvals, acceptance records, and **internal** security certificates, the **only additional administrative sign-off required** is a GitHub **APPROVE** review submitted personally from the designated independent reviewer's verified, authorized GitHub account on the relevant approval pull request.

That single native GitHub approval is the internal electronic signature, decision timestamp and authorization record. **No** paper signature, handwritten image, PDF certificate, separate declaration or oath, repeated issue-comment approval, redundant attestations, notarization, or mandatory public/permanent ZIP hyperlink is required for internal sign-off. The project may automatically generate a certificate or issue-status record from the approved PR; this is a record of internal acceptance, not a third-party certification.

The reviewer needs no prescribed approval-text template. Ordinary GitHub review metadata (account, APPROVED status, PR and reviewed commit) is sufficient as a decision record. No other person's account may issue approval in the reviewer's name. GitHub approval of a PR alone does not establish that an account belongs to a particular named human or is independent; the designated account-to-person identity and independence must have been established *once* through the authorized reviewer appointment process, not re-certified for every task.

## 2. Approved scope and retained evidence

The approval is valid for the review's actual commit SHA and defined scope. The underlying engineering evidence must exist, be inspectable by authorized parties, and truthfully support the stated result. Evidence can be retained in existing CI artifacts or controlled storage; a duplicate ZIP upload, public URL or separate evidence-manifest approval is **not mandatory**. Technical failures, missing runs, skipped gates, unsupported PASS claims and material vulnerabilities cannot be converted to PASS merely by clicking APPROVE.

**Independent reviewer:** independence is factual, not inferred from a username or the owner's assertion. If the proposed account is owned or controlled by the project owner or remediation executor, its approval is **internal owner/implementer approval**, not an independent review. Independence of a human reviewer must not be fabricated; a signed document from another account does not cure it.

## 3. Internal certificate issuance

When the single authorized independent reviewer's native GitHub APPROVE occurs, the project may issue an **internal, scope-limited acceptance certificate** stating the PR, SHA, reviewed scope, decision and date. No separate certificate-issuer approval or reviewer signature is required under *internal* policy. Any resulting record must accurately state technical caveats and must not claim externally accredited, independent third-party or regulator-recognized certification unless that scheme's own requirements have actually been met. A certificate cannot be marked technical PASS without adequate underlying evidence.

## 4. Non-waivable boundaries

This policy changes **paperwork and approval workflow only**. It does not relax RLS/ACL, fail-closed tenant isolation, source integrity, authentication, privileged-role separation, secure backup and restore, production checks, incident response, current required branch/environment protections, or any applicable law, contract, regulator/customer commitment or external certification rules. External obligations take precedence; this internal change cannot waive them.

A reviewer APPROVE may complete the internal documentary sign-off for **G1 Criterion 3** after the technical tests are adequately evidenced. It does **not** itself certify a live production deployment, approve an operational change, activate payments, or satisfy other untested G1 gates.

## 5. Application and transition

Existing contradictory approvals/comments are retained for audit. Future internal approvals use the single native GitHub APPROVE mechanism after this proposal merges. Pending historical approvals may be reconciled to a specific reviewed commit/PR by authorized maintainers, without impersonating a reviewer or backdating a sign-off.

Until separately authorized release gates and production tests have actually succeeded:
`P0_SECURITY_HOLD=ACTIVE` / `G1=HOLD` / `PRODUCTION_RELEASE=NO-GO` / `PAYMENTS=OFF`.

This document implements the documentary simplification consistent with `SANAD-SECURITY-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25.md`; that existing decision already waived **optional** independent certificates as a general project-development completion condition.
