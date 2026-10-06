# R0C13 — R13-G10 Protected Release Candidate Certification

## Decision

This document is the protected certification candidate for **R13-G10**.

```text
R13-G09 = CLOSED
R13-G10 = CANDIDATE_PENDING_PROTECTED_REVIEW
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
```

No live-payment authority is created by this certification.

## 1. Baseline capture

| Item | Evidence |
|---|---|
| G09 implementation PR | #1290 |
| Approved implementation exact head | `c710592b693a9f46c5b04f58bc7c4351fce94e0b` |
| G09 merge/main SHA | `a7bec5601b8d15feca983815c9bbb73269f9fed9` |
| G10 branch base main SHA | `c591d24ea42a14cbe3a967c0641f120a886c5c33` |
| G09 post-merge verification | run `37498056105` / #1197 / SUCCESS |
| Stage 07 provenance | run `37498056103` / SUCCESS |
| Backend image publication | run `37498056262` / SUCCESS |

## 2. R13-REL-001..004 reconciliation

### R13-REL-001 — independent approval

PASS.

PR #1290 received a fresh independent APPROVED review from
`abdulrhmansenan1985-creator` on the exact implementation head
`c710592b693a9f46c5b04f58bc7c4351fce94e0b`.

### R13-REL-002 — exact-head checks

PASS.

The required exact-head checks on the approved implementation SHA completed
successfully, including Maven, PostgreSQL Direct acceptance, CRM integration,
web build, provenance, deployment readiness, and tenant-isolation verification.

### R13-REL-003 — PMV on merged exact main

PASS.

PR #1290 merged to
`a7bec5601b8d15feca983815c9bbb73269f9fed9`.
Post-Merge Main Verification run `37498056105` completed SUCCESS on that exact
SHA, including final evidence aggregation.

### R13-REL-004 — immutable exact-SHA provenance

PASS.

For `a7bec5601b8d15feca983815c9bbb73269f9fed9`:

- Stage 07 Artifact Provenance run `37498056103` = SUCCESS.
- Publish Render Backend Image run `37498056262` = SUCCESS.

## 3. Scope-drift proof

The repository advanced by one commit after the R0C13/G09 merge:

```text
a7bec5601b8d15feca983815c9bbb73269f9fed9
  → c591d24ea42a14cbe3a967c0641f120a886c5c33
ahead_by = 1
changed_files = 8
```

All eight changed paths belong to the Users-module acceptance/release work or
web dependency/configuration files associated with that work. The compare has
**zero changes under the R0C13 subscription/billing implementation, evidence,
or R0C13 certification paths**.

Therefore:

```text
SCOPE_DRIFT = NONE
```

This finding is scoped specifically to R0C13 and does not classify unrelated
repository work.

## 4. G10 gate state before protected review

```text
PROTECTED_REVIEW = PENDING
EXACT_HEAD_CHECKS = PENDING
PMV = PASS
PROVENANCE = PASS
SCOPE_DRIFT = NONE
```

The candidate must not be declared G10 PASS merely because the inherited
implementation evidence is green. The certification PR itself must receive
independent approval and exact-head CI, then merge, then obtain post-merge
verification on the actual merge SHA.

## 5. Required closeout sequence

```text
PROTECTED REVIEW
→ EXACT-HEAD CI
→ MERGE WITH EXPECTED HEAD SHA
→ POST-MERGE CERTIFICATION ON ACTUAL MERGE SHA
→ R13-G10 = PASS
```

R13-G11 remains blocked until R13-G10 is formally closed.

## 6. Machine-readable evidence

See:

`evidence/subscription/r0c13-g10-release-candidate.json`
