# HRM-G0 — WS3 Country & Compliance Verification Evidence

```
CURRENT AUTHORITATIVE STATE
---------------------------
HRM_G0_PR_914 = MERGED
HRM_G0_MERGE_SHA = 748e2c6076c94b7a29e2a5e8e4f4a817b0f2fc2b
HRM_G0_IS_ANCESTOR_OF_MAIN = YES
LEGAL_REVIEW = BLOCKED (by design — independent human gate)
SA_PACK = DRAFT (never promoted to ACTIVE)
PRODUCTION_AUTHORIZATION = NO
```

```
HISTORICAL EXECUTION RECORD
---------------------------
The header below reflects the state at the time this evidence was
produced (pre-merge). It is preserved verbatim as history and is
superseded by the CURRENT AUTHORITATIVE STATE block above.
```

Task: WS3 Task 6 — Country/compliance verification gate
Directive: SNAD HRM-G0 MASTER MODULE COMPLETION & FINAL CLOSURE DIRECTIVE (Phase A)
Branch: `feat/hrm-g0-foundation` (PR #914 — OPEN / DRAFT / UNMERGED)

## 1. Verification suite (PostgreSQL Direct, local run at WS4-10 head)

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| HrCountryPolicyResolverTest | 4 | 0 | 0 |
| HrComplianceEngineTest | 13 | 0 | 0 |
| HrComplianceOverrideIntegrationTest | 18 | 0 | 0 |
| HrCountryPackLifecycleIntegrationTest | 2 | 0 | 0 |
| **TOTAL** | **37** | **0** | **0** |

## 2. Static scan — country branching outside the compliance package

Production scan for `equals("SA") / "SA".equals / equals("AE") / switch(country)`
outside `com.sanad.platform.hr.compliance`:

- HR module (`com.sanad.platform.hr..`): **NONE** — all jurisdictional
  branching lives in the compliance package (resolver + engine + handlers).
- Platform-wide: two CRM occurrences unrelated to HR statutory logic —
  `crm/party/domain/PhoneNumberNormalizer.java` (phone numbering hint) and
  `crm/party/application/AddressCommunicationUseCases.java` (Saudi National
  Address parsing). Neither gates any HR decision.

`COUNTRY_BRANCHING_OUTSIDE_COMPLIANCE (HR scope) = NONE`

## 3. Global Mode safety (explicit proof)

Compliance engine semantics (`ComplianceEngine`):

- unsupported country + `GENERIC_HR` operation → `GLOBAL_MODE_ALLOWED`
  (reason `GLOBAL_MODE_GENERIC_HR`) — generic HR continues safely;
- unsupported country + `LOCAL_STATUTORY` operation → `LEGAL_REVIEW_REQUIRED`
  (fail closed) — a local statutory action is never silently executed;
- localized statutory operation with missing effective rule/handler →
  `LEGAL_REVIEW_REQUIRED`;
- `MANDATORY_HARD` rules can never be satisfied by the governed override flow
  (four-eyes override applies only to `MANDATORY_WITH_EXCEPTION` rules with
  `exception_allowed = true` — proven by `HrComplianceOverrideIntegrationTest`).

## 4. Results

```
WS3_COUNTRY_COMPLIANCE = PASS
SA_PACK_RESOLUTION     = BLOCKED_LEGAL_REVIEW
```

The two results are deliberately NOT conflated: the engineering verification
(country resolution, engine semantics, override governance, pack lifecycle)
PASSES, while the Saudi pack itself remains `DRAFT` pending the human legal
review gate (`docs/hrm/compliance/SA/SA-HR-FOUNDATION-v1.md`,
`LEGAL_REVIEW_STATUS = BLOCKED`).
