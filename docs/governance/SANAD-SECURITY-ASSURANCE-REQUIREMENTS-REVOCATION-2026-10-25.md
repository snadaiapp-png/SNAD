# SANAD Project-Wide Security Assurance Requirement Revocation

**Decision ID:** `DEC-SEC-ASSURANCE-REQUIREMENTS-REVOCATION-2026-10-25-001`  
**Decision date:** 2026-10-25 (Asia/Riyadh)  
**Decision authority:** Project Owner (`snadaiapp-png`), by direct written instruction recorded at GitHub Issue #1438, comment 6389802911  
**Decision status:** `RECORDED — IMPLEMENTATION PROPOSED IN GOVERNANCE BRANCH; EFFECTIVE FOR INTERNAL POLICY INTERPRETATION`

## 1. Decision

Across the SANAD project, appointment or verification of a named independent reviewer/assessor and issuance of an independent security certificate are **not mandatory internal project-completion requirements**. No module, development milestone, or project-completion decision may treat the absence of such an appointment or certificate, by itself, as a blocker.

This decision supersedes conflicting internal plans, reviewer designations, deferred-certificate schedules, and workflow descriptions to the extent that they require a named independent reviewer or certificate for project completion. Older records remain in place as historical audit evidence and must be read subject to this decision.

## 2. Scope and limits

This decision changes internal project governance only. It does not:
- waive a requirement imposed by applicable law, regulation, contract, customer commitment, insurer, regulator, or certification scheme;
- authorize anyone to claim that an independent or accredited certificate has been issued when it has not;
- close, downgrade, or mark complete any technical security finding or test without evidence;
- waive applicable security controls, incident-response obligations, release-specific criteria, or competent-authority approvals;
- alter repository permissions, branch protections, environment protections, credentials, secrets, or deployment authority.

Where an external obligation applies, the team must identify the requirement and the authority empowered to grant any waiver. The obligation must be met, or a valid written waiver must be obtained from that competent authority, before the affected release or claim proceeds.

## 3. Technical controls retained

The following remain applicable under their own controls and gates:
- PostgreSQL row-level security (RLS), tenant identity binding, and direct-database tenant-isolation tests using the actual restricted application role;
- authentication, authorization, BOLA/IDOR, privilege-boundary and negative-path tests;
- dependency, build, artifact, signing, provenance and software-supply-chain controls;
- evidence integrity, exact-SHA traceability, honest test results and remediation/retest records;
- branch protection, required reviews where configured, protected environments, OIDC checks, audit logging and incident-response procedures.

The `REM-P0-006` 19-case technical-verification matrix remains at its actual recorded state of **0/19 COMPLETED** until evidence demonstrates otherwise. No independent certificate has been issued or is claimed by this decision.

## 4. Standing gates unchanged

This decision does not change:
- `G1 = HOLD`
- `P0 SECURITY HOLD = ACTIVE`
- `NO MERGE` where required review or other merge controls remain unmet
- `NO DEPLOY`
- `PAYMENTS = OFF`
- `commercial_go_live = NOT_APPROVED`

Each status may change only through its own authorized gate, evidence, and decision process. The removal of a certificate prerequisite is not an approval to merge, deploy, activate payments, or begin broad commercial operations.

## 5. Implementation and audit trail

The implementation branch updates:
- `docs/governance/CURRENT-STATUS.json`
- `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md`
- `docs/governance/SANAD-GOV-SEC-SELF-ASSESSMENT-EXCEPTION-POLICY.md`
- `docs/security/independent-assurance/README.md`
- `.github/workflows/independent-security-assurance.yml`
- CRM-008 acceptance/stage-gate wording that previously named independent assurance as a commercial gate.

The workflow's readiness checks remain technical evidence-integrity checks. Its manual legacy closure path is not a required project-completion check. Technical tests and fail-closed controls must not be removed to implement this governance decision.

No GitHub Secret is used to store this policy decision. Secrets remain for credentials only; no secret, environment protection, branch protection, or reviewer setting is changed by this decision.

## 6. Change control

This decision is not proof that the implementation branch has merged or that the CI validation has passed. Merge remains subject to the repository's normal protected-branch and required-review rules. The current security holds remain unchanged until separately and validly resolved.
