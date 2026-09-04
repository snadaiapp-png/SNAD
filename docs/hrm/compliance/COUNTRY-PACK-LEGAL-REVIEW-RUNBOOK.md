# Country Pack Legal Review Runbook — HRM-G0

Status: **ACTIVE RUNBOOK / GATE CONTROLLER**
Directive: SNAD HRM-G0 MASTER MODULE COMPLETION & FINAL CLOSURE DIRECTIVE (WS3 Task 5)
Owner: designated legal reviewer (human) — the AI/agent MUST NOT self-certify.

---

## 1. Purpose

Production use of any localized country compliance pack (starting with the
Kingdom of Saudi Arabia pack `SA-HR-FOUNDATION-v1`) is gated behind an explicit
human legal review. Engineering may build, test, and stage packs; ONLY the
designated legal reviewer may declare a pack legally reviewed. This runbook is
the single procedure that produces the evidence required for
`LEGAL_REVIEW_STATUS = APPROVED`.

Until that approval exists:

```
LEGAL_REVIEW_STATUS = BLOCKED
SA_PACK_RESOLUTION  = BLOCKED
SA pack lifecycle   = DRAFT (never promoted to ACTIVE)
GLOBAL_CERTIFICATION = BLOCKED (HRM-G0 cannot be certified while this gate is open)
```

All independent engineering work continues regardless of this gate.

## 2. Who may review

- One designated legal reviewer per jurisdiction, identified by name, role, and
  employing entity, recorded in the pack document (`reviewer_identity`,
  `reviewer_role`, `legal_reviewed_at`).
- The AI/agent, engineers, and product owners are NOT valid legal reviewers.
- Fabricating, guessing, or inferring `LEGAL_REVIEW_STATUS=APPROVED` without
  the reviewer's explicit recorded evidence is a governance violation.

## 3. Authoritative sources ONLY

For the Saudi pack, evidence may cite ONLY official/competent authorities:

- HRSD — Ministry of Human Resources and Social Development
- Qiwa (HRSD digital labor platform) for platform-administered obligations
- GOSI — General Organization for Social Insurance
- Other competent Saudi government authorities (e.g., Mudad, official gazette
  publications of the Labor Law and its implementing regulations)

NOT acceptable as legal authority: blogs, vendor summaries, social posts,
AI-generated legal claims, secondary aggregators, or any source the reviewer
cannot trace to an official publication.

## 4. Required evidence per production-authoritative rule

Every rule the pack proposes to enforce in production MUST carry ALL of the
following, recorded in the pack document per rule:

| Field | Meaning |
|-------|---------|
| `country_code` | ISO country the rule applies to (e.g. `SA`) |
| `pack_code` / `pack_version` | Pack identity (e.g. `SA-HR-FOUNDATION` / `v1`) |
| `rule_code` | Stable rule identifier inside the pack |
| `operation_code` | Compliance operation the rule gates (see §5) |
| `official_authority` | Competent authority (HRSD / Qiwa / GOSI / gazette) |
| `official_source_uri` | Deep link to the official publication |
| `retrieved_at` | Retrieval timestamp (UTC) |
| `source_snapshot_sha256` | SHA-256 of the retained source snapshot |
| `legal_citation` | Law / regulation / article number |
| `effective_from` / `effective_to` | Validity window of the cited text |
| `reviewer_identity` / `reviewer_role` | The designated legal reviewer |
| `legal_reviewed_at` | Review timestamp |
| `automated_test_reference` | Test class/method enforcing the rule in CI |

A rule with ANY missing field is NOT production-authoritative and must remain
`DRAFT / UNVERIFIED`.

## 5. Operation codes (compliance engine contract)

The compliance engine classifies operations as `GENERIC_HR` or
`LOCAL_STATUTORY` and enforces rules at the levels
`MANDATORY_HARD`, `MANDATORY_WITH_EXCEPTION`, `REGULATORY_GUIDANCE`,
`TENANT_POLICY`. The reviewer must confirm, per rule:

- the operation is genuinely `LOCAL_STATUTORY` (jurisdiction-specific legal
  duty) versus generic HR policy;
- the proposed enforcement level matches the legal consequence of violation
  (an illegal-to-waive duty must be `MANDATORY_HARD`; a waivable-with-
  approval duty may be `MANDATORY_WITH_EXCEPTION`).

## 6. Procedure

1. **Inventory** — engineering proposes the rule list (see the SA pack draft).
2. **Retrieval** — reviewer retrieves each official source; snapshot retained;
   SHA-256 computed; URI + `retrieved_at` recorded.
3. **Citation** — reviewer records article/section citations and the
   effective window.
4. **Classification** — reviewer confirms operation_code and enforcement level
   per §5.
5. **Traceability** — engineering binds each verified rule to its automated
   test reference (test class/method in the backend suite).
6. **Decision** — reviewer records `legal_reviewed_at`, identity, and per-rule
   verdict. Any rule failing review stays `UNVERIFIED` and its enforcement
   level must be `MANDATORY_HARD`-blocked (fail closed) in the engine config
   until re-reviewed.
7. **Promotion** — only when ALL production-authoritative rules carry complete
   evidence may the reviewer sign `LEGAL_REVIEW_STATUS=APPROVED` and the pack
   lifecycle may move `DRAFT → ACTIVE` through the normal protected workflow.
8. **Re-review** — any change to cited law/regulation, pack version bump, or
   engine semantic change re-opens the gate for the affected rules.

## 7. Failure / abstention

If no designated reviewer exists, or any evidence field cannot be completed:

```
LEGAL_REVIEW_STATUS = BLOCKED
SA_PACK_RESOLUTION  = BLOCKED
```

Do NOT downgrade enforcement, disable rules to "unblock", or ship the pack as
ACTIVE. Report the blocker exactly; it is a legitimate stop condition for final
certification, not an engineering defect.
