# SNAD HRM-G0 — Cross-Decision Consistency Review

**Date:** 2026-08-27  
**Baseline:** `snadaiapp-png/SNAD@2dd8d1151ec0b231a51c13ee20722da6598e89e3`  
**Result:** **PASS WITH CORRECTIONS** — all corrections are incorporated into `2026-08-27-hrm-g0-foundation-design.md`.

## Review objective

Check all approved HRM-G0 architectural decisions against each other and against the current SNAD repository. The review does not re-open accepted direction unless two decisions cannot safely coexist. When a conflict exists, the previously authorized recommended option is applied and recorded.

## Repository evidence considered

- `apps/web/app/hr/hr-execution-data.ts`: G0 is Foundation: Employee Records & Org Structure; original tasks still describe a simplified employee/org_units build.
- `apps/sanad-platform/src/main/resources/db/migration/V20260819_1__create_hr_employees.sql`: existing HR tables/capabilities and fail-open RLS behavior when tenant context is absent.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/HrController.java`: current `/api/v1/hr`, broad map-based writes, status mutation, and DELETE.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`: physical SQL DELETE.
- `apps/sanad-platform/src/main/resources/db/migration/V2__create_organizations_table.sql`: Organization is an operational aggregate under Tenant, not an Employer of Record.
- `apps/sanad-platform/src/main/resources/db/migration/V20260820_7__rbac_exact_matrix_9_of_9.sql`: canonical HR_MANAGER currently has exactly three HR capabilities.
- `docs/stage-08/developer/API-GOVERNANCE.md`: major URL versioning and breaking-change rules.
- `docs/stage-08/globalization/COUNTRY-ONBOARDING-FRAMEWORK.md`: country configuration, legal review, localization, pilot and GA gates.
- GitHub issue `#701 APP-SEP-001`: Strangler Pattern, module-owned data, no cross-application direct DB access, shared platform capabilities through contracts/SDKs.
- Existing CRM idempotency/outbox implementations as reference patterns, not canonical HR dependencies.

## Material findings and resolutions

| # | Conflict / risk | Severity | Canonical resolution |
|---|---|---:|---|
| 1 | One central shared outbox table conflicts with future module-owned databases. | Critical | Shared Platform Outbox **contract/SDK/envelope** with producer-local transactional outbox storage. |
| 2 | Central audit cannot be atomically transaction-coupled after module separation. | Critical | Producer-local immutable audit ledger + reliable delivery to centralized Platform Audit Sink. |
| 3 | Central idempotency DB would create cross-module coupling. | High | Shared idempotency contract/library + producer-local durable records. |
| 4 | Earlier PRIMARY 100% + SECONDARY 20% example contradicted the total-allocation rule. | High | Allocation means actual FTE allocation; active assignments total 100% by default, e.g. PRIMARY 80% + SECONDARY 20%. |
| 5 | Requiring Position for every active Employment is too rigid for SME tenants. | High | Exactly one PRIMARY Assignment is mandatory; Position is policy-driven/optional. One-seat rules apply whenever Position is used. |
| 6 | Freezing `country_pack_version` on Employment would leave active staff on obsolete law after rule changes. | Critical | Employment stores effective-dated labor jurisdiction. Effective pack/rule version is resolved per decision date and recorded as provenance. |
| 7 | Jurisdiction transfer conflicts with Employer-of-Record if Legal Entity changes. | Critical | Legal Entity change creates a new Employment. Same-employer cross-border jurisdiction change requires governed effective-dated transition and compliance review. |
| 8 | `reports_to_assignment_id` lacked explicit cycle prevention. | High | Reporting cycles forbidden. LINE_MANAGER defaults to same Organization; matrix/dotted-line reporting is separate future semantics. |
| 9 | REPORTING_TREE could accidentally grant access based on historical management. | High | Current authorization grants/scopes govern historical `asOf` reads. Historical management never resurrects access. |
| 10 | New `HRM.*` capabilities could silently broaden exact canonical HR_MANAGER. | Critical | Explicit compatibility mapping and separately reviewed role-template redesign. No automatic privilege expansion. |
| 11 | The architecture grew beyond the original five simple G0 tasks. | Critical | Keep one foundation architecture but decompose implementation into bounded workstreams; full Payroll/GCC statutory catalogs remain outside G0. |
| 12 | Strict country-law enforcement conflicts with unsupported-country Global Mode. | Critical | Global Mode provides generic HR only; statutory/local-compliance functions are blocked or explicitly UNVERIFIED. No Saudi/GCC fallback law. |
| 13 | v1 write payload lacks Legal Entity/Organization context. | Critical | v1 write compatibility only when authoritative defaults are unambiguous; otherwise structured `HRM_MIGRATION_REQUIRED`. |
| 14 | Legacy DELETE cannot safely preserve semantics under no-hard-delete. | Critical | Never preserve physical delete. Unsafe DELETE is retired/blocked before HRM-G0 production certification. |
| 15 | Legal Entity/Work Location are Platform-owned and may require work before HR cutover. | High | Explicit Platform prerequisite workstream; HRM never duplicates these masters. |
| 16 | Cost Center is Accounting-owned and may not be available in G0. | Medium | Optional G0 reference; validate through Accounting contract when present. |
| 17 | Existing RLS is fail-open when tenant context is missing. | Critical | Mandatory fail-closed RLS, WITH CHECK, no BYPASSRLS/superuser runtime, dedicated regression tests. |
| 18 | Shared capabilities could become hidden synchronous cross-module coupling. | High | Explicit ports/contracts; cross-module state changes are event-driven through outbox. |
| 19 | Payment/bank data ownership was implied too early under generic PII. | Medium | Keep only approved HR PII in G0; payment-instrument ownership deferred to Payroll/Finance. |
| 20 | Fixed legacy employment-type enum is not globally extensible. | High | Canonical worker classification + Country Pack mapping/validation instead of a rigid global DB CHECK. |
| 21 | Country rules could be activated without legal provenance. | Critical | ACTIVE/CERTIFIED requires official source, effective dates, legal review and automated test evidence. |
| 22 | Org hierarchy cycle detection must account for effective dates. | High | Period-aware cycle validation; reject any proposed version producing a cycle during overlapping effective intervals. |
| 23 | Position occupancy and Position lifecycle were conflated. | Medium | Store staffability lifecycle only; VACANT/OCCUPIED is derived from Assignments. |
| 24 | Tenant country alone can select the wrong labor law. | Critical | Canonical legal input is effective Employment labor jurisdiction, validated against Legal Entity, work location and worker classification. |

## Final corrected invariants

### Identity / Employment

- `User != Person != Employment != Assignment`.
- `Person.user_id` optional and unique per Tenant when present.
- One Person may have many historical Employments.
- Maximum one concurrent non-terminal Employment per `(tenant, person, legal_entity)`.
- Rehire creates a new Employment.
- TERMINATED is retained and terminal.
- Operational hard delete is forbidden.

### Organization / Work

- Legal Entity = Employer of Record.
- Organization = operational boundary.
- LegalEntity↔Organization = effective-dated eligibility.
- Every ACTIVE Employment has exactly one PRIMARY Assignment.
- Position optional by policy; if used, one Position = one seat.
- No overlapping PRIMARY assignments or occupying Position assignments.
- FTE allocations total 100% by default.
- No organization hierarchy cycles or reporting-line cycles.

### Country / Compliance

- Country/jurisdiction resolution precedes statutory behavior.
- GCC localized packs: SA, AE, QA, BH, KW, OM.
- Unsupported/unapproved country => Global Mode.
- Global Mode never claims certified local compliance.
- HARD statutory rule => non-overridable block.
- Controlled legal exception => justification + evidence as required + independent System Manager approval + immutable audit + bounded validity.
- Country rules are effective-dated/versioned and tied to official sources/legal review.

### Security

- Authorization = capability + scope + Tenant + Organization/Legal Entity context + classification.
- Default deny.
- Current authorization governs historical/as-of reads.
- Sensitive PII, compensation and contracts have separate capabilities.
- RLS is fail-closed.
- Canonical system-managed role templates never gain capabilities implicitly.

### Platform boundaries

- Shared capabilities are shared contracts/SDKs, not mandatory cross-app shared DB tables.
- Outbox, local audit evidence and idempotency durability stay producer-local after application separation.
- Central sinks/consumers receive reliable events after commit.

## Required implementation decomposition

1. Platform prerequisites.
2. HR Core & Migration.
3. Country/Compliance Foundation.
4. Security/Integration Foundation.
5. API/UI/Cutover.
6. Contract/Compensation Foundation.

Full Payroll, government submission engines, full GCC statutory catalogs, Time & Attendance, Recruitment, Performance and Analytics remain outside G0 unless separately promoted.

## Verdict

```text
CROSS_DECISION_CONSISTENCY       = PASS WITH CORRECTIONS
CRITICAL_CONTRADICTIONS_UNRESOLVED = 0
DESIGN_READY_FOR_CANONICAL_SPEC  = YES
IMPLEMENTATION_READY             = NO
```

Implementation remains blocked only by the written-spec human review gate required by the architectural design process.
