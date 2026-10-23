# SANAD Request for External-Assurance Deferral and Gate-Transition Decision

**Status: REQUEST SUBMITTED FOR GOVERNANCE DECISION — NOT APPROVED**  
**Requested by:** Project owner / SANAD project governance  
**Scope:** Optional external security accreditation procurement and development-phase sequencing  
**Related controls:** `REM-P0-006`, `P0 SECURITY HOLD`, `G1 = HOLD`, `NO DEPLOY`, `PAYMENTS = OFF`

## 1. Decision requested

Request that the authorized SANAD governance and risk owners:

1. Approve deferral of **optional external security-assessment and certification procurement** until formal project completion, where no law, regulation, contract, customer commitment, insurer term, certification scheme, or release condition requires it earlier.
2. Decide explicitly whether the project may proceed to a named **non-production development/testing phase** without the optional external certificate, subject to documented risk acceptance and the controls below.
3. Identify any mandatory assurance requirements that cannot be deferred, the accountable decision-maker, the approver, and the conditions and expiry date of any approved exception.
4. Record the decision, scope, rationale, residual risks, compensating controls, and review date in the official governance register.

## 2. Explicit limits of this request

This document is a request, not an approval, waiver, security finding, or certificate. It does **not** authorize or perform any of the following:

- Clearing `P0 SECURITY HOLD` or changing `G1 = HOLD`.
- Enabling production deployment, payments, commercial go-live, or customer-facing release.
- Bypassing tenant-isolation/RLS verification, database-role restrictions, pre-authentication privilege controls, or other mandatory security acceptance criteria.
- Marking `REM-P0-006` ready/closed, changing evidence results, or representing an internal review as independent/accredited certification.
- Treating a mergeable pull request or passing readiness/static CI checks as proof that runtime isolation or independent assurance passed.

## 3. Proposed bounded route while the request is pending

- Continue local and isolated non-production development and testing only, within existing authorization.
- Keep production and payment restrictions in force.
- Complete and retain security evidence against one explicitly identified candidate release SHA.
- Record unresolved findings and risk owners; do not mark tests complete without evidence and retest results.
- If any mandatory legal, contractual, customer, or release requirement applies before project completion, escalate it and satisfy it before the affected release or claim unless the competent authority provides a valid written exemption.
- At formal project completion, freeze release scope/SHA, consolidate the evidence package, and commission a qualified independent assessment where required or approved.

## 4. Required governance decision record

All fields below remain pending until completed by the authorized decision-maker:

- Decision: `PENDING`
- Decision authority and identity verification: `PENDING`
- Requirements reviewed (legal, regulatory, contractual, customer, insurer, scheme, release): `PENDING`
- Approved scope and phase, if any: `PENDING`
- Residual risks and compensating controls: `PENDING`
- Conditions, expiry, and review date: `PENDING`
- Approval evidence / ticket / signature: `PENDING`

## 5. Current control state

Until an authorized decision is recorded and each applicable technical gate independently passes, retain:

- `REM-P0-006 = NOT_READY`
- `P0 SECURITY HOLD = ACTIVE`
- `G1 = HOLD`
- `NO DEPLOY`
- `PAYMENTS = OFF`
- `commercial go-live = NOT_APPROVED`

**This request seeks a governance decision on deferring optional external assurance and, if authorized, a bounded non-production phase transition. It does not request falsification or silent bypass of mandatory security controls.**
