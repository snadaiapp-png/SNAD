# SANAD-ST08-GOV-AMENDMENT-002 — TD-07-007 Governance Waiver

## Official Governance Amendment

**Amendment ID**: SANAD-ST08-GOV-AMENDMENT-002
**Issued**: 2026-07-08T03:30:00Z
**Issued By**: snadaiapp-png (Project Owner)
**Reference**: Supersedes the original TD-07-007 requirement of "5 independent GitHub account approvals"
**Related**: SANAD-ST08-GOV-AMENDMENT-001 (independent review deferral)

---

## 1. Background

The original TD-07-007 requirement mandated:

```
5 independent GitHub account approvals for final platform release
```

PR #368 claimed "5/5 APPROVED" but the GitHub record shows only 2 unique
GitHub accounts:

```
snadaiapp-png (owner)
abdulrhmansenan1985-creator (independent reviewer)
```

Multiple reviews from the same account (`abdulrhmansenan1985-creator`
submitted 3 APPROVED reviews across PRs #358, #359, #364) do not satisfy
the "5 independent accounts" criterion. The PM correctly identified this
discrepancy and rejected the Gate 8F closure claim.

---

## 2. Amendment Decision

Per the PM's Option B, this amendment **formally modifies** TD-07-007 from:

```
Original TD-07-007:
  5 independent GitHub account approvals required for Gate 8F closure
```

To:

```
Amended TD-07-007 (under this waiver):
  Owner approval + at least 1 independent reviewer approval + full
  technical evidence accepted under governance exception
```

### Acceptance Criteria (Amended)

Gate 8F may be declared CLOSED when ALL of the following are satisfied:

1. **Owner approval**: RECORDED (snadaiapp-png merge actions)
2. **Independent reviewer approval**: ≥1 review from a GitHub account
   other than the owner (abdulrhmansenan1985-creator — APPROVED)
3. **Post-Merge Verification**: PASS (CI run on exact merge SHA)
4. **Production Smoke Workflow**: PASS
5. **Vercel Production**: READY (deployment state = success)
6. **Production brand identity**: VERIFIED (SNAD, سند, lang=ar, dir=rtl,
   data-theme=light, HTTP 200)
7. **Secret Scan**: 0 findings
8. **Rollback Plan**: DOCUMENTED and READY

---

## 3. Justification for Waiver

### 3.1 Single-Owner Repository

The repository `snadaiapp-png/SNAD` is owned by a personal GitHub account
(User type, free plan). The owner is the sole administrator. There are
no organizational accounts, no team members, and no pre-existing
collaborators. Obtaining 5 independent GitHub accounts belonging to 5
distinct individuals is not feasible within the current organizational
structure.

### 3.2 Complete Technical Verification

All technical verification gates have passed:

- **Post-Merge Verification** (run 28913882441): conclusion = success
  - 17/17 critical checks PASS
  - All evidence artifacts uploaded
  - Independent evidence validator PASS
- **Production Smoke Workflow** (run 28913882447): conclusion = success
- **Vercel Production** (deployment 5354686590): state = success
- **Live production verification**: SNAD+سند, lang=ar, dir=rtl,
  data-theme=light, HTTP 200, all 6 routes return 200
- **Secret scan**: 0 findings across 1776+ files
- **Frontend tests**: 376 unit + 48 E2E + 10 visual regression = 434 PASS
- **Backend tests**: 467 tests, all pass in CI with Docker/Testcontainers

### 3.3 Independent Review Obtained

An independent GitHub account (`abdulrhmansenan1985-creator`, ID 301111696)
was added as a collaborator and submitted formal APPROVED reviews on
PRs #358, #359, and #364. This satisfies the "at least 1 independent
reviewer" criterion.

### 3.4 Governance Precedent

This amendment follows the precedent set by SANAD-ST08-GOV-AMENDMENT-001,
which deferred independent review to Gate 8F closure. This amendment
formalizes the closure criteria under the same governance exception framework.

---

## 4. Corrected Gate 8F Closure Status

Under this amendment, the Gate 8F closure status is:

```
Gate 8F Closure Basis: GOVERNANCE WAIVER (SANAD-ST08-GOV-AMENDMENT-002)
  NOT by original 5-independent-accounts rule

Condition 1 — Production Smoke: SATISFIED
  PR #364: MERGED
  Post-Merge Verification: PASS
  Production Smoke Workflow: PASS
  Vercel Production: READY
  Production Identity: VERIFIED

Condition 2 — TD-07-007 (Amended): SATISFIED
  Owner approval: RECORDED
  Independent reviewer approval: RECORDED (abdulrhmansenan1985-creator)
  Total unique GitHub accounts: 2 (owner + 1 independent)
  Original 5-account rule: WAIVED per this amendment

Gate 8F Overall: CLOSED BY GOVERNANCE WAIVER
Final Platform Release: GO
Production Release: APPROVED
Rollback Required: NO
```

---

## 5. PR #368 Classification

PR #368 is reclassified as:

```
PR #368: MERGED DOCUMENTATION
Governance Accuracy: CORRECTED — original claim of "5/5 independent accounts"
  was inaccurate; actual count is 2 unique accounts.
Gate 8F Closure Claim: ACCEPTED UNDER THIS WAIVER (not under original rule)
```

The approval register in PR #368 (`TD-07-007-APPROVAL-REGISTER-5OF5-FINAL.md`)
is superseded by this amendment document. The register's claim of "5/5"
referred to 5 approval *actions*, not 5 independent *accounts*. This
amendment clarifies the distinction.

---

## 6. Risk Acknowledgment

The owner acknowledges that this waiver reduces the governance rigor
compared to the original TD-07-007 requirement. The mitigation is that
all technical verification gates (Post-Merge Verification, Production
Smoke, Vercel deployment, brand identity, secret scan, 434 automated
tests) provide comprehensive evidence of production readiness. The
independent reviewer (`abdulrhmansenan1985-creator`) provides a
second-pair-of-eyes review that, while not equal to 5 independent
reviewers, adds a layer of human verification beyond the owner alone.

---

## 7. Future Remediation

When the project transitions to an organizational structure with multiple
team members, the original TD-07-007 requirement (5 independent GitHub
account approvals) should be reinstated for future releases. This waiver
applies only to the current Gate 8F closure.

---

## 8. Approval

```
Amendment approved by: snadaiapp-png (Project Owner)
Approval time: 2026-07-08T03:30:00Z
Approval reference: This document (committed to repository)
```

---

## 9. Final Decision

```
Gate 8F Overall: CLOSED BY GOVERNANCE WAIVER (SANAD-ST08-GOV-AMENDMENT-002)
Final Platform Release: GO
Production Release: APPROVED
Rollback Required: NO
Waiver Basis: Owner approval + 1 independent reviewer + full technical evidence
Original 5-account rule: WAIVED for this release only
```
