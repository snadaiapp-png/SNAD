# SANAD — Canonical Continuous Security & Release Governance
**Decision reference:** DEC-GOV-CONTINUOUS-SECURITY-2026-11-14-001  
**Adopted:** 2026-11-14 through protected merge [PR #1531](https://github.com/snadaiapp-png/SNAD/pull/1531) | **Authority:** project owner `snadaiapp-png` (owner-directed governance change)  
**Baseline assessed:** `fa12c64eb0a9b45a7a0201fdd6a0a86e84d11dd4`  
**Change status:** MERGED / EFFECTIVE ON `main` at `6d4effbcf41128b72979d0affe7c862495510a8f`; **NOT A PRODUCTION RELEASE AUTHORIZATION**. Post-merge verification [run #43636059170](https://github.com/snadaiapp-png/SNAD/actions/runs/43636059170) returned `PMV_FINAL_GATE=PASS`, with [manifest artifact #14132292219](https://github.com/snadaiapp-png/SNAD/actions/runs/43636059170/artifacts/14132292219). The production three-user gate was `SKIPPED`, not PASS.

## 1. Decision and precedence

SANAD adopts **Continuous Security & Release Gates** instead of a mandatory project-internal *independent security reviewer appointment*, *independent G1/C4 sign-off* or *certificate-issuance workflow* for ordinary development, milestone completion or routine releases.

- Project owner or explicitly delegated release owner is the internal administrative decision maker, with a SHA-bound, evidence-based record; this does **not** confer authority to approve missing tests, vulnerabilities, obligations or an untested release.
- A second technical opinion or specialist assessment is **risk-triggered**, not required for every PR/release as a standing certificate condition. Request it for novel high-risk security design, unresolved critical/high vulnerabilities, inconclusive test coverage, external contractual or regulatory requirements, or material risk of self-review.
- Where law, regulation, customer contract, insurance or external certification scheme requires an independent assessor, that obligation prevails. This internal policy cannot waive it.
- A regular PR code-review rule, CODEOWNERS setting, required GitHub status check or protected environment reviewer (if actually configured) is a **separate engineering control**, not automatically retired by removing security-certificate bureaucracy. Changes to settings need owner approval and implementation evidence.
- Historic review packets, PRs, security certifications and declarations stay available as archival records, not current truth or authority.

**Precedence for new internal work:** this adopted decision overrides conflicting *administrative independent-review/certificate prerequisites* in [SANAD-LEAN-SECURITY-GOVERNANCE-2026-11-12.md](SANAD-LEAN-SECURITY-GOVERNANCE-2026-11-12.md), older stage/assurance documents, and issue templates. It builds on [2026-10-25 project-wide revocation](SANAD-SECURITY-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25.md). It does not automatically amend protected GitHub settings or issue-body checkboxes and does not supersede technical controls or external duties.

## 2. Two deliberately separate lanes

### Development / merging

Routine development may proceed without an independent security certificate or named third-party reviewer.

1. Code and migration changes use ordinary branch/PR workflow and existing branch protection.
2. Required CI/build/test checks must reach terminal successful status **on the exact SHA under review**. Skips, pending, stale and failed checks are not PASS.
3. For changes involving authentication, tenancy, RLS, database grants, privileged functions, payments, audit, personal data or cryptography, attach targeted negative and positive tests, the threat boundary and rollback approach.
4. A **recorded developer/owner review** is allowed if compatible with actual branch-protection rules. Do not impersonate or silently bypass configured review requirements.
5. Merge is not production deployment and does not certify the security of untested application paths.

### Production / payment release

Production requires **separate** explicitly authorized risk and release decision with the exact source SHA and actual deployed digest/configuration.

Minimal evidence for a production GO decision:
- terminal required CI and target-specific security tests; independent test/verification where a documented risk or external obligation demands it;
- production drift check (including Flyway migration/version and DB restricted runtime role);
- tenant A/B and missing-GUC isolation; authentication/session, authorization, cross-tenant BOLA/IDOR negative tests;
- ACL and SECURITY DEFINER least-privilege review when affected, pre-auth token lookup/revocation checks, audit integrity;
- protected and authorized backup/full-scope isolated restore with measurable RPO/RTO or explicit separately adjudicated provider-specific limitation, recovery keys and rollback;
- protected deployment plan with operator/approver, change window and expected digest/SHA, rollback/stop conditions;
- actual smoke/functional/role-based acceptance for the affected production release (not a skipped gate);
- any compliance/contractual sign-offs that apply.

**Decision:** `GO`, `NO-GO` or `HOLD` recorded by the authorized project owner/delegated release owner, including risk findings and evidence links. **Payments have a separate authorization and are OFF until approved.** Critical/high unmitigated tenant isolation, credential, authorization or recovery risk is not silently transformed into an accepted PASS by an owner's signature.

## 3. Required evidence and failure semantics

For every release decision record:
`SOURCE_SHA`, `DEPLOYED_DIGEST`, `DB_FLYWAY_VERSION`, environment, time, actor, relevant test run/artifact URLs, PASS/FAIL/SKIPPED/UNVERIFIED classifications, risk owner, mitigation, rollback and final explicit release decision.

Fail closed for:
- proven critical cross-tenant data access or privileged role escalation;
- unsafe production migration without backup/restore/rollback feasibility;
- missing mandatory evidence for a material security impact;
- unapproved production change or external legal/contractual security requirement.

Risk acceptance must document an actual compensating control and time-bound remediation plan. It must never fabricate a test result or bypass an enforceable external obligation.

## 4. Current non-transferable security findings and hold

As of the evidence baseline `fa12c64e`, production read-only pg_catalog inspections recorded 12 SECURITY DEFINER functions, 26 RLS-off tables, and default runtime grants requiring least-privilege adjudication; the `C4-TOKEN-01` fix `V20261113_1` passed a 7-case isolated regression but was not recorded as applied in production (Flyway last success `20261111.1`). These are unclosed technical findings; they are **not cleared by cancelling reviewer issue #1529**.

- [#1529](https://github.com/snadaiapp-png/SNAD/issues/1529): independent review process CANCELLED as `not_planned`; **not** a security PASS.
- [#1462](https://github.com/snadaiapp-png/SNAD/issues/1462): G1 P0 security issue remains OPEN; checklist cannot be ticked based on the new governance policy.
- [#1528](https://github.com/snadaiapp-png/SNAD/issues/1528): exact SHA CAB/release NOT APPROVED.
- [#1530](https://github.com/snadaiapp-png/SNAD/issues/1530), [#1481](https://github.com/snadaiapp-png/SNAD/issues/1481): recovery authorization/full restore pending.

**Status retained:** `P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`. Neither merge of this policy nor a green docs CI automatically changes them.

## 5. Operational transition / accountability

1. Retire the mandatory independent *appointment and certificate* steps in **current** governance (not erase historical evidence). #1529 already cancelled; no new reviewer declaration required solely for routine G1.
2. Keep existing test workflows, CI checks, CODEOWNERS and branch/environment rules intact while auditing whether any specific mandatory check solely enforces obsolete paperwork. Propose technical gate changes separately, with file-by-file inspection and regression/CI evidence.
3. Replace independent-security certificate wording in future release templates with **Continuous Security Release Evidence Record**. Legacy historical certificates remain archived, unchanged.
4. Track unresolved security findings in #1462 and the operational CAB/restoration issues until completed or formally remediated. Do not close them as `completed` by removing a reviewer.
5. Any material change in development/production policy requires a protected PR, actual review under configured branch rules, a versioned decision, and owner approval. A later exact-SHA production GO is a separate decision.

**Record format:** `decision_id | project/release/SHA | decision owner | tests/links/limitations | material security findings | legal/contract checks | rollback/restore | GO/NO-GO/HOLD | time/signature`.

## 6. Verification criteria for this governance PR

- [ ] Source governance changes are reviewed and merged under actual branch protection.
- [ ] No required CI/security test, RLS policy, ACL, credential, environment or deployment setting removed or changed in this governance-only PR.
- [ ] Confirm future new releases cite this document rather than demanding independent certificate as a default.
- [ ] Record CI results and any remaining contradictory documents or workflow gates for a follow-up.
- [ ] #1462 / #1528 stay OPEN and no production/payment write is attempted.

## 7. Governance reconciliation addendum — 2026-11-15

The release-management requirement is **evidence-based internal owner/delegate release authorization**, not a separate independent security **certificate**. Historic G1/C4 packets remain truthful archival records and cannot have their PENDING or HOLD statuses retroactively converted to PASS. For current gates, consult [#1462](https://github.com/snadaiapp-png/SNAD/issues/1462) (technical security findings) and [#1528](https://github.com/snadaiapp-png/SNAD/issues/1528) (operational release request). Any existing issue checklist claiming a mandatory independent sign-off must be transitioned to **risk-based technical validation of the same underlying privileged functions/tables**; unverified validations remain unchecked. #1529 is cancelled, not a technical closure. The security findings and production restrictions are unchanged.

Evidence boundary: documentation merge + CI success do not establish that V20261113_1 migrated into production, production restore succeeded, real tenant paths were retested or an approved GO exists. A new SHA after any further document change requires its own checks.
