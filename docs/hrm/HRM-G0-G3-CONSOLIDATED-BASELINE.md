# HRM G0–G3 Consolidated Governance Baseline

> STATUS_AUTHORITY: CURRENT
> Scope: completed HRM stages G0 through G3
> Engineering baseline status: CLOSED
> Production-operational status for G3: CERTIFIED
> Next engineering stage: G4 — Payroll Integration

## Authoritative stage matrix

| Stage | Scope | Engineering | Security / PostgreSQL | Production | Authority |
|---|---|---|---|---|---|
| G0 | Foundation / employee records / org structure | APPROVED | PASS | Not independently released as a stage | PR #990 + G0 certificate |
| G1 | Recruitment & Onboarding | APPROVED | PASS | Independent from G1 engineering certificate | PR #1119 + G1 certificate |
| G2 | Time, Attendance, Timesheets & Leave | APPROVED | PASS | Independent from G2 engineering certificate | PR #1173 + G2 certificate |
| G3 | Performance Reviews & Goals | APPROVED | PASS | PASS | PR #1234 + production closure PR #1281 |

## Current exact-main production closure

```text
MAIN_SHA = 2ba84d4c146ca0b9b4f105fed53d81cfa7d2f581
VERCEL_MAIN_PRODUCTION_RECONCILE = 37329097684 / SUCCESS
PRODUCTION_OPERATIONAL_SMOKE = 37329356654 / SUCCESS
POST_MERGE_MAIN_VERIFICATION = 37329097719 / SUCCESS
POSTGRESQL_DIRECT = PASS
HRM_SECURITY_RLS = PASS
SECURITY_GOVERNANCE = PASS
FINAL_EVIDENCE_AGGREGATION = PASS
```

## G0 approval reconciliation

G0 engineering completion was already proven by its closure certificate. The
remaining dashboard PENDING state was stale: reconciliation PR #990 received an
independent APPROVED review from `abdulrhmansenan1985-creator` at
`2026-09-07T17:22:20Z` before protected merge. The dashboard and regression
contract now bind G0 engineering certification to that evidence.

This does not approve legal compliance.

## Historical PR policy

Superseded RED, diagnostic, recertification, and alternate remediation PRs from
G1/G2 are not valid merge candidates after the certified successor baselines.
They must be closed with a pointer to the canonical successor evidence. Git
history remains the audit record; closure does not delete evidence.

## Independent open authority: legal / Saudi Country Pack

```text
LEGAL_REVIEW = PENDING_HUMAN
SA_COUNTRY_PACK = DRAFT
SAUDI_LEGAL_COMPLIANT = NOT_CLAIMED
```

This is an external compliance authority, not an engineering defect. It must not
be auto-approved by code or CI. G4 planning may proceed, but any payroll behavior
that depends on Saudi statutory rules must remain fail-closed until the relevant
legal/Country Pack authority is approved.

## G4 entry decision

```text
G0_ENGINEERING = CLOSED
G1_ENGINEERING = CLOSED
G2_ENGINEERING = CLOSED
G3_ENGINEERING = CLOSED
G3_PRODUCTION_OPERATIONAL = PASS
G4_ENGINEERING_ENTRY_GATE = PASS
G4_SAUDI_STATUTORY_RULES_GATE = OPEN
```

The engineering dependency chain for G4 is satisfied. Legal/statutory payroll
activation remains an explicit independent gate.
