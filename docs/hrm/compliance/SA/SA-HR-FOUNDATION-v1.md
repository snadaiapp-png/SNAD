# Saudi Arabia — HR Foundation Country Pack

```
country_code          = SA
pack_code             = SA-HR-FOUNDATION
pack_version          = v1
lifecycle_status      = DRAFT
LEGAL_REVIEW_STATUS   = BLOCKED
SA_PACK_RESOLUTION    = BLOCKED
```

Directive: SNAD HRM-G0 MASTER MODULE COMPLETION & FINAL CLOSURE DIRECTIVE (WS3 Task 5)
Runbook: `docs/hrm/compliance/COUNTRY-PACK-LEGAL-REVIEW-RUNBOOK.md`

---

## 1. Why this pack is BLOCKED

No designated Saudi legal reviewer has performed the review defined by the
runbook. Therefore NO rule in this pack carries a verified
`source_snapshot_sha256`, `legal_citation`, or `legal_reviewed_at`, and the
pack MUST NOT be promoted to `ACTIVE` or used as production-legal authority.

This document is the structured DRAFT the designated reviewer will complete.
All evidence fields are intentionally `PENDING` — they are NOT to be filled by
the AI/agent or by engineers.

## 2. Engine mapping (how this pack is enforced)

- Operations classified `LOCAL_STATUTORY` for an unsupported/unverified pack
  resolve to `LEGAL_REVIEW_REQUIRED` / `BLOCKED` by the compliance engine
  (fail-closed Global Mode behavior; proven by
  `HrCountryPolicyResolverTest` / `HrComplianceEngineTest`).
- While `lifecycle_status = DRAFT`, no rule in this pack may be evaluated as
  `MANDATORY_*` for production decisions.
- `MANDATORY_HARD` rules can NEVER be satisfied by a compliance override
  (four-eyes governed override applies to `MANDATORY_WITH_EXCEPTION` rules
  with `exception_allowed = true` only).

## 3. Draft rule inventory (evidence PENDING — reviewer completes)

Designated authorities: HRSD (Ministry of Human Resources and Social
Development), Qiwa, GOSI, official gazette publications of the Saudi Labor Law
and its implementing regulations.

| # | rule_code | operation area | proposed enforcement | proposed operation_code |
|---|-----------|----------------|----------------------|-------------------------|
| 1 | SA-PROBATION-LIMIT | probation period limits | MANDATORY_HARD | LOCAL_STATUTORY |
| 2 | SA-WAGE-PROTECTION | wage payment via WPS (Mudad) | MANDATORY_HARD | LOCAL_STATUTORY |
| 3 | SA-WORKING-HOURS-LIMIT | daily/weekly working hour limits | MANDATORY_HARD | LOCAL_STATUTORY |
| 4 | SA-OVERTIME-RATE | overtime compensation rate | MANDATORY_HARD | LOCAL_STATUTORY |
| 5 | SA-GOSI-REGISTRATION | social insurance registration of employees | MANDATORY_HARD | LOCAL_STATUTORY |
| 6 | SA-ANNUAL-LEAVE-MIN | minimum annual leave entitlement | MANDATORY_HARD | LOCAL_STATUTORY |
| 7 | SA-notice-PERIOD | termination notice periods | MANDATORY_WITH_EXCEPTION (exception_allowed=false unless reviewer says otherwise) | LOCAL_STATUTORY |
| 8 | SA-EOSB-ENTITLEMENT | end-of-service benefit accrual | MANDATORY_HARD | LOCAL_STATUTORY |
| 9 | SA-CONTRACT-DOCUMENTATION | written contract documentation (Qiwa) | MANDATORY_HARD | LOCAL_STATUTORY |
| 10 | SA-RECORD-RETENTION | statutory record retention | MANDATORY_WITH_EXCEPTION | LOCAL_STATUTORY |

> The inventory above is the engineering PROPOSAL of what a foundation pack
> must cover. It asserts nothing about the current legal text. Every row
> requires the full runbook §4 evidence set before it may be treated as
> production-authoritative.

## 4. Per-rule evidence record (template — reviewer completes)

For each `rule_code` in §3, the reviewer records:

```
country_code            = SA
pack_code               = SA-HR-FOUNDATION
pack_version            = v1
rule_code               = <from §3>
operation_code          = <confirmed per runbook §5>
official_authority      = PENDING   (HRSD / Qiwa / GOSI / official gazette)
official_source_uri     = PENDING
retrieved_at            = PENDING   (UTC)
source_snapshot_sha256  = PENDING
legal_citation          = PENDING   (law / implementing regulation / article)
effective_from          = PENDING
effective_to            = PENDING
reviewer_identity       = PENDING
reviewer_role           = PENDING
legal_reviewed_at       = PENDING
automated_test_reference= PENDING   (backend test class#method bound after verification)
verdict                 = UNVERIFIED
```

## 5. Reviewer sign-off block (completed ONLY by the designated legal reviewer)

```
LEGAL_REVIEW_STATUS     = BLOCKED   (changes to APPROVED only via runbook §6 step 7)
reviewer_identity       = PENDING
reviewer_role           = PENDING
legal_reviewed_at       = PENDING
pack_decision           = REMAINS DRAFT
notes                   = -
```

## 6. What engineering may do meanwhile

- Keep the pack `DRAFT`; keep engine behavior fail-closed for
  `LOCAL_STATUTORY` operations in unsupported/unverified jurisdictions.
- Continue all independent engineering work (WS6, WS5, certification
  machinery). Final HRM-G0 certification remains BLOCKED until this gate is
  resolved — report it exactly; do not work around it.
