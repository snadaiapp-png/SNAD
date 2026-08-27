# SNAD HRM-G0 Foundation Design

**Date:** 2026-08-27  
**Baseline:** `snadaiapp-png/SNAD@2dd8d1151ec0b231a51c13ee20722da6598e89e3`  
**Status:** Cross-decision reviewed design specification  
**Architecture:** Evolutionary Modular HRM / Country-first / Saudi-first / GCC-ready / Global fallback

## 1. Purpose

HRM-G0 establishes the authoritative HR foundation for SNAD without rebuilding the existing HR implementation from zero. The current repository already contains `hr_employees`, `hr_departments`, `hr_positions`, `/api/v1/hr`, HR capabilities and web integration. G0 therefore performs a controlled re-baseline and migration to a canonical model that separates human identity, employment, assignment, legal employer, IAM identity, organizational structure, sensitive data, country compliance and historical state.

The foundation must support Saudi Arabia as the first complete localized path, all GCC countries through dynamic Country Packs, and a Global HR fallback for countries without an approved localized pack. Global fallback must never claim statutory compliance that has not been certified.

## 2. Existing-system constraints

The design is constrained by the current repository:

- `apps/web/app/hr/hr-execution-data.ts` defines G0 as **Foundation: Employee Records & Org Structure**, but its task wording assumes a simpler new employees/org_units model and is now stale relative to implemented HR backend/database work.
- `V20260819_1__create_hr_employees.sql` already creates `hr_departments`, `hr_positions`, `hr_employees` and legacy HR capabilities.
- Current HR RLS permits access when `app.tenant_id` is missing; G0 must replace this with fail-closed RLS.
- Current `HrController` uses `/api/v1/hr`, generic `Map<String,Object>` writes, generic status mutation, and `DELETE /employees/{id}`.
- Current JDBC repository physically deletes employee rows. This conflicts with the approved lifecycle model and must be removed from the operational path.
- Current canonical `HR_MANAGER` role is enforced as exactly `HR.EMPLOYEE.READ`, `HR.EMPLOYEE.WRITE`, `HR.EMPLOYEE.ARCHIVE`; new capabilities must not silently expand that template.
- Existing `organizations` is a Tenant-owned operational aggregate, not an Employer-of-Record concept.
- SNAD API Governance requires a new major API version for breaking contracts.
- APP-SEP-001 requires gradual module separation, module-owned data, and shared platform capabilities through contracts/SDKs rather than cross-application database access.

## 3. Architecture principles

### 3.1 Identity separation

```text
IAM User
   ↕ optional identity link
HR Person
   ↓
Employment(s)
   ↓
Assignment(s)
```

- **User** = authentication/access identity owned by IAM.
- **Person** = canonical human identity owned by HRM.
- **Employment** = relationship with an Employer of Record.
- **Assignment** = operational placement inside an Organization.

Invariant: `User != Person != Employment != Assignment`.

`Person.user_id` is optional. Linking Person↔User does not grant a role, capability, account activation or employment-derived access automatically.

### 3.2 Employer vs operational organization

- Legal Entity = Employer of Record, Platform-owned.
- Organization = operational/business grouping, Platform-owned.
- Organizational Unit = HR administrative hierarchy inside an Organization.
- Work Location = Platform-owned physical/remote location.
- Cost Center = Accounting/Finance-owned financial dimension.

Employment is scoped by Legal Entity. Assignment is scoped by Organization.

Legal Entity ↔ Organization is a many-to-many effective-dated eligibility relationship. HRM must reject an Assignment whose Organization is not eligible for the Employment Legal Entity for the relevant period.

### 3.3 Migration strategy

```text
EXPAND
→ BACKFILL
→ RECONCILE
→ VERIFIED CUTOVER
→ V1 COMPATIBILITY
→ CONTRACT LEGACY MODEL
```

Rules: no big-bang destructive migration, no permanent dual-write, no guessing during backfill, and no removal of legacy fields until reconciliation and consumer migration are proven.

### 3.4 Shared Platform capabilities are contracts

To remain compatible with future application-owned databases:

- **Outbox:** shared envelope/library/operational contract; producer-local transactional outbox durability.
- **Audit:** shared audit contract and centralized sink; producer-local immutable audit ledger for transaction evidence.
- **Idempotency:** shared contract/library; producer-local durable idempotency records.
- **Cryptography:** Platform Security contract/SDK backed by managed key authority; no HR-owned master keys.

A single shared cross-application outbox/audit/idempotency table is not a long-term architectural dependency.

## 4. Country-first architecture

Country/jurisdiction resolution is HRM **G0.0** and occurs before statutory HR behavior.

```text
Employment labor jurisdiction
→ Country Pack Registry
→ Effective Country/Global Policy
→ Worker Classification
→ Compliance Decision
→ HR Domain Command
```

These are separate concepts:

```text
Tenant default country
!= Legal Entity registered country
!= Employment labor jurisdiction
!= Person nationality
!= Work Location country
```

Tenant country is only a convenience default. Legal Entity country is the default/validation source at employment creation. The authoritative runtime legal input is the approved, effective-dated Employment labor jurisdiction.

Country Pack version is not permanently frozen on Employment. Every compliance-sensitive decision resolves the effective pack/rule version using the operation effective date and records the applied versions as provenance.

### 4.1 GCC packs

First-class dynamic localized packs:

```text
SA  Saudi Arabia
AE  United Arab Emirates
QA  Qatar
BH  Bahrain
KW  Kuwait
OM  Oman
```

### 4.2 Global fallback

No ACTIVE/CERTIFIED localized pack => `GLOBAL_MODE`.

Global Mode allows generic HR foundations such as Person, Employment, Org Structure, Jobs, Positions, Assignments, generic contract data and generic compensation terms.

Global Mode does **not** claim local statutory compliance. Statutory payroll, social-insurance automation, government submissions, statutory end-of-service calculations and similar functions are BLOCKED/UNVERIFIED/configuration-required until a certified pack exists. Saudi/GCC rules are never reused as fallback rules for another country.

### 4.3 Pack lifecycle

```text
DRAFT
→ SOURCE_VERIFIED
→ LEGAL_REVIEWED
→ TESTED
→ CERTIFIED
→ ACTIVE
→ SUSPENDED / RETIRED
```

No statutory rule becomes production-authoritative without official-source reference, legal citation metadata, effective dates, rule version, legal review and automated test evidence.

## 5. Compliance and governed overrides

Priority:

```text
Applicable law/regulation
> Certified Country Pack
> Tenant policy
> User preference
```

Enforcement classes:

```text
MANDATORY_HARD
MANDATORY_WITH_EXCEPTION
REGULATORY_GUIDANCE
TENANT_POLICY
```

- `MANDATORY_HARD`: block; no System Manager override.
- `MANDATORY_WITH_EXCEPTION`: allowed only where law/rule permits an exception; requires warning, justification, evidence when required, independent System Manager approval, re-validation, immutable audit and bounded scope/validity.
- Guidance/Tenant Policy: configurable only within the legal boundary.

Four-eyes invariant: `Requester != Final Approver`.

A controlled exception is a real aggregate (`compliance_override_requests`) containing Tenant, jurisdiction, rule/version, resource, requested/compliant values, requester, justification, evidence, approver, validity period, state and audit reference. An override never modifies the authoritative country rule.

## 6. Canonical data model

### 6.1 Person

`hr_people`: UUID, tenant, optional `user_id`, names/display name, optimistic-lock version, timestamps.

Partial uniqueness: `UNIQUE (tenant_id, user_id) WHERE user_id IS NOT NULL`.

### 6.2 Restricted person data

`hr_person_private`: approved restricted HR PII only, independently permissioned and read-audited. Payroll payment-instrument ownership is deferred to Payroll/Finance unless separately approved.

### 6.3 Person identifiers

`hr_person_identifiers` stores identifier type, issuing country, randomized authenticated ciphertext, tenant-scoped blind index, key versions, validity and status.

Raw national ID/residency ID/passport values are never stored in plaintext. UUID remains canonical identity. Encryption and blind-index keys are separate and rotatable.

### 6.4 Employment

Canonical meaning of `hr_employees` becomes Employment:

```text
id
 tenant_id
 person_id
 legal_entity_id
 employee_number
 worker_classification_code
 current_status        // projection only
 employment_start_date
 termination_date
 rehire_of_employee_id
 version
 timestamps
```

Employee-number uniqueness: `UNIQUE (tenant_id, legal_entity_id, employee_number)`.

Maximum one concurrent non-terminal Employment per `(tenant_id, person_id, legal_entity_id)`.

States:

```text
DRAFT
PENDING_ONBOARDING
ACTIVE
ON_LEAVE
SUSPENDED
TERMINATED
VOIDED
```

TERMINATED/VOIDED are terminal. Rehire creates a new Employment tied to the same Person. Operational hard delete is forbidden.

### 6.5 Status history

`hr_employment_status_periods` is the historical system of record. Periods are effective-dated, non-overlapping and immutable once historical. `hr_employees.current_status` is only a current projection.

### 6.6 Jurisdiction history

`hr_employment_jurisdiction_periods` records effective-dated labor jurisdiction and compliance/approval provenance.

Legal Entity change is not a simple jurisdiction edit: it closes the old Employment and creates a new Employment. Same-employer cross-border jurisdiction changes require governed compliance review.

### 6.7 Organizational Units

`hr_org_units` = stable identity. `hr_org_unit_versions` = effective-dated name/code/type/parent/status.

Rules: same Tenant/Organization parentage, no overlapping versions, effective-period-aware cycle detection, immutable history.

Ambiguous `BRANCH` is not a core organizational-unit type. Physical branch semantics belong to Work Location; an administrative branch-like unit uses its actual organizational meaning.

Existing `hr_departments` is evolved/backfilled rather than destructively replaced.

### 6.8 Jobs

`hr_jobs` = stable reusable occupational identity. `hr_job_versions` = effective-dated title/description/family/level/grade/classification mapping/status.

Compensation is not stored in Job. Legacy hard-coded worker-type DB enums are replaced by canonical classifications validated/mapped by country packs.

### 6.9 Positions

`hr_positions` = stable one-seat identity. `hr_position_versions` = effective job/org-unit/title override/grade/work-location/cost-center/staffability.

Stored staffability states: `DRAFT`, `OPEN`, `FROZEN`, `CLOSED`.

`VACANT/OCCUPIED` is derived from occupying Assignments and is never a manually authoritative state.

### 6.10 Assignments

`hr_employee_assignments` contains Employment, Organization, optional Org Unit, optional Position, `reports_to_assignment_id`, optional Work Location/Cost Center, PRIMARY/SECONDARY type, OCCUPYING/NON_OCCUPYING mode, allocation %, effective dates, state and version.

Invariants:

- every ACTIVE Employment has exactly one effective PRIMARY Assignment;
- Position is optional unless Tenant/Country policy requires Position Management;
- one Employment cannot have overlapping PRIMARY Assignments;
- one Position cannot have overlapping OCCUPYING Assignments;
- active FTE allocation totals 100% by default, e.g. PRIMARY 80% + SECONDARY 20%;
- `reports_to_assignment_id` is authoritative line management;
- no reporting cycle;
- LINE_MANAGER defaults to an effective assignment in the same Organization; matrix/dotted-line reporting is a separate future relationship;
- Organization must be eligible for the Employment Legal Entity.

Legacy `department_id`, `position_id`, `manager_id` on `hr_employees` are compatibility-only during migration.

### 6.11 Contracts

Foundation uses stable `hr_employment_contracts` plus effective-dated `hr_employment_contract_versions`.

States: DRAFT, PENDING_SIGNATURE, ACTIVE, EXPIRED, TERMINATED, SUPERSEDED, VOIDED.

Historical terms are immutable. Amendment creates a new version; a new legal instrument creates a new Contract. Full electronic signature execution is outside G0.

### 6.12 Compensation

Foundation uses effective-dated `hr_compensation_packages` with components such as BASE_SALARY, ALLOWANCE, BENEFIT, VARIABLE_TARGET and OTHER.

At most one effective primary compensation package per Employment/date. Compensation has independent capabilities and read audit.

Ownership boundary: HRM = compensation terms; Payroll = calculation; Accounting = financial posting. Payroll Engine is outside G0.

## 7. Employment lifecycle

Generic PATCH cannot change lifecycle status.

```text
DRAFT → PENDING_ONBOARDING | VOIDED
PENDING_ONBOARDING → ACTIVE | VOIDED
ACTIVE → ON_LEAVE | SUSPENDED | TERMINATED
ON_LEAVE → ACTIVE | SUSPENDED | TERMINATED
SUSPENDED → ACTIVE | TERMINATED
TERMINATED / VOIDED → terminal
```

Every lifecycle command performs authentication, jurisdiction/policy resolution, capability+scope authorization, state validation, compliance evaluation, invariant validation, state/history mutation, local audit append, local outbox append and commit.

Activation hard requirements: Person, valid Legal Entity, unique Employee Number, start date, resolvable jurisdiction, exactly one PRIMARY Assignment, valid LegalEntity↔Organization eligibility, no temporal conflicts, authorization and compliance pass.

Position, Org Unit, Manager, Work Location, Cost Center, Contract, Compensation and IAM User are policy-driven activation prerequisites.

## 8. Authorization

```text
Authorization
= Capability
+ Access Scope
+ Tenant Boundary
+ Organization/Legal Entity Context
+ Data Classification
+ Resource Context
```

Default deny.

Scopes: SELF, DIRECT_REPORTS, REPORTING_TREE, ORG_UNIT, ORGANIZATION, TENANT.

Role grant defines maximum authority; assignment may narrow it. Direct exceptions are rare, audited and never silently widen authority.

Sensitive PII, compensation, contracts and lifecycle actions have separate capabilities.

### 8.1 Existing role compatibility

Current canonical `HR_MANAGER` is exactly:

```text
HR.EMPLOYEE.READ
HR.EMPLOYEE.WRITE
HR.EMPLOYEE.ARCHIVE
```

New `HRM.*` capabilities must not automatically broaden that template. Migration uses explicit compatibility mapping and a separately reviewed template redesign. Exact-matrix tests remain release gates.

### 8.2 Historical reads

`asOf` changes business data, not authorization time. Current grants/scopes govern historical reads. A former manager does not regain access because they managed the employee historically.

## 9. Tenant isolation

Current fail-open HR RLS is replaced with fail-closed behavior:

```text
no tenant context → DENY/zero rows
wrong tenant      → DENY/zero rows
cross-tenant read → DENY
cross-tenant write→ DENY
```

Use fail-closed `USING`, explicit `WITH CHECK`, runtime non-superuser/non-BYPASSRLS role, FORCE RLS where appropriate, and dedicated no-context/wrong-tenant integration tests.

## 10. Cryptography

Platform Security owns encryption, blind-index generation, key versioning and rotation. HRM consumes a contract/SDK backed by managed key authority.

Principles: envelope encryption; randomized authenticated encryption; separate tenant-scoped blind-index HMAC; no master keys in source/database; versioned rotation; no raw secrets in audit/outbox.

## 11. Audit

Audit Event != Domain Event.

Critical HR mutations append a local immutable audit record as part of the producer transaction. Sensitive reads are audit-recorded through a reliable read-audit path. Local evidence is delivered reliably to the shared Platform Audit Sink for consolidated search/reporting.

Audit stores actor/action/resource/context/classification/correlation/reason and redacted before/after data where appropriate. Raw secrets are never copied into audit.

## 12. Transactional Outbox

Producer transaction:

```text
BEGIN
  write HR state
  write history
  append local audit evidence
  insert producer-local outbox event
COMMIT
```

Guarantees: AT-LEAST-ONCE delivery, idempotent consumers, retry, dead-letter handling, versioned schemas, immutable event history.

Standard envelope includes event id/type/version, aggregate, tenant, optional organization, actor, occurred-at, correlation/causation ids, idempotency key, classification and payload.

Representative events:

```text
HRM.EMPLOYEE.ACTIVATED.v1
HRM.EMPLOYEE.SUSPENDED.v1
HRM.EMPLOYEE.TERMINATED.v1
HRM.EMPLOYEE.USER_LINKED.v1
HRM.ASSIGNMENT.CHANGED.v1
HRM.CONTRACT.CHANGED.v1
HRM.COMPENSATION.CHANGED.v1
```

HRM never directly mutates IAM/Workflow/Payroll/Accounting databases.

## 13. Idempotency and concurrency

Critical commands require `Idempotency-Key` plus canonical request fingerprinting. Same key+same request replays the outcome; same key+different request returns `HRM_IDEMPOTENCY_CONFLICT`.

Aggregates use optimistic concurrency (`version` / expected version / `If-Match` equivalent). Stale mutations return 409 `HRM_CONCURRENCY_CONFLICT`.

Database uniqueness and temporal exclusion constraints remain the final race-condition defense.

## 14. API architecture

Canonical: `/api/v2/hr`. Legacy: `/api/v1/hr` compatibility adapter.

v2 resources include people, employments, assignments, org-units, jobs, positions, contracts and compensation packages. Sensitive subresources have independent authorization/audit.

Lifecycle uses explicit commands; generic PATCH cannot mutate status.

Every v2 endpoint uses typed DTOs, validation, OpenAPI 3.1, capability+scope metadata, audit/idempotency classification and structured domain errors. The current `Map<String,Object>` write style is not carried into v2.

### 14.1 v1 safety

- v1 reads may project Person + Employment + Primary Assignment to the legacy shape.
- v1 writes are compatible only when Legal Entity/Organization/required context resolve authoritatively without guessing.
- ambiguous v1 writes return `HRM_MIGRATION_REQUIRED`.
- v1 status writes cannot bypass the state machine.
- v1 DELETE never performs physical deletion and is retired/blocked before HRM-G0 production certification.
- no new HR features are added to v1.

## 15. Query model and UI

Use CQRS-lite, not Event Sourcing.

Read projections: Employee Directory, Employee 360, Org Chart, Position/Vacancy, Employment Timeline, Assignment Timeline, Contract Timeline, Compensation Timeline, historical `asOf` views.

Sensitive PII is never duplicated into unrestricted directory projections.

G0 UI foundation: HR Dashboard, Employees/Directory, Employee 360, Organization Structure, Jobs, Positions, Assignments and Compliance Status. Contract/Compensation sections are permission-gated foundations; full Payroll UI is outside G0. Arabic/RTL is first-class.

## 16. Migration/backfill

Legacy identity/profile → Person/private/identifiers. Legacy employment fields → canonical Employment. Legacy department/position/manager → effective PRIMARY Assignment.

```text
exact authoritative match   → AUTO_BACKFILL
multiple possible matches   → MIGRATION_REVIEW_REQUIRED
no authoritative match      → MIGRATION_BLOCKED
```

Legal Entity/Organization context is never invented. Cutover requires row-count, identity, employee-number, assignment and tenant-isolation reconciliation, zero unresolved rows, safe v1 projection, and no physical Employee DELETE path.

## 17. Implementation decomposition

One architecture, multiple bounded workstreams:

1. **Platform prerequisites:** Country Registry, Legal Entity master if incomplete, LegalEntity↔Organization eligibility, Work Location contract, shared security/crypto/audit/outbox/idempotency contracts.
2. **HR Core & Migration:** Person/private/identifiers, Employment/status/jurisdiction history, evolved Org Units, Job/Position versioning, Assignments, backfill, fail-closed RLS.
3. **Country/Compliance Foundation:** CountryPolicyResolver, registry integration, Global Mode, compliance contract, governed overrides, Saudi pack bootstrap with verified rules only.
4. **Security/Integration:** fine-grained capabilities/scopes, no automatic HR_MANAGER expansion, local audit+sink, local outbox+shared envelope, local idempotency, concurrency, cryptography integration.
5. **API/UI/Cutover:** v2, structured errors/OpenAPI, safe v1 adapter, Directory, Employee 360, Org Chart, cutover evidence.
6. **Contract/Compensation Foundation:** effective-dated models and access/audit boundaries; no Payroll Engine.

Full GCC statutory catalogs, Payroll calculation, government submissions, attendance/leave engines, recruitment/onboarding, performance and analytics remain outside G0 unless separately promoted.

## 18. Cross-decision consistency corrections

The review found and resolved the following material conflicts:

1. Central shared outbox vs module-owned DBs → shared contract + producer-local storage.
2. Central audit atomicity vs future separation → local immutable ledger + central sink.
3. Central idempotency vs module ownership → shared contract + producer-local store.
4. PRIMARY 100% + SECONDARY 20% example vs total-allocation invariant → total active FTE allocation = 100% by default; e.g. 80/20.
5. Mandatory Position for every active employee was too rigid → PRIMARY Assignment mandatory; Position policy-driven.
6. Freezing Country Pack version on Employment would retain obsolete law → resolve effective rule version per decision date and record provenance.
7. Jurisdiction change vs Employer-of-Record → Legal Entity change creates new Employment; same-employer jurisdiction transfer is governed/effective-dated.
8. Reporting relation lacked cycle control → reporting cycles forbidden; line-manager defaults to same Organization.
9. Historical reporting tree could resurrect access → current authorization governs historical reads.
10. New capabilities could violate exact HR_MANAGER least privilege → explicit compatibility mapping; no automatic template expansion.
11. G0 had grown into a mega-project → architecture retained, execution decomposed into bounded workstreams.
12. Strict country law vs unsupported-country Global Mode → generic HR only; statutory functions blocked/unverified.
13. v1 writes lack legal/organization context → compatible only with authoritative unambiguous defaults; otherwise migration-required.
14. v1 DELETE cannot safely preserve semantics → never physical delete; retire/block before certification.
15. Platform-owned masters may be incomplete → explicit Platform prerequisites.
16. Cost Center is external → optional G0 reference validated through Accounting contract when present.
17. Current RLS is fail-open → mandatory fail-closed remediation.
18. Fixed legacy employment-type enum is not globally extensible → canonical classification + country-pack mapping.
19. Legal rule activation lacked provenance gate → official-source + legal-review + effective-date + test evidence required.
20. Org cycles must be evaluated over effective intervals → period-aware cycle validation.

**Cross-decision result:** PASS WITH CORRECTIONS. Critical unresolved contradictions: **0**.

## 19. Structured errors

Representative codes:

```text
HRM_PERSON_NOT_FOUND
HRM_EMPLOYMENT_NOT_FOUND
HRM_INVALID_STATE_TRANSITION
HRM_ACTIVATION_BLOCKED
HRM_EMPLOYMENT_CONFLICT
HRM_ASSIGNMENT_OVERLAP
HRM_POSITION_OCCUPIED
HRM_REPORTING_CYCLE
HRM_SCOPE_DENIED
HRM_COUNTRY_PACK_NOT_CERTIFIED
HRM_COMPLIANCE_BLOCKED
HRM_OVERRIDE_APPROVAL_REQUIRED
HRM_LEGAL_REVIEW_REQUIRED
HRM_IDEMPOTENCY_CONFLICT
HRM_CONCURRENCY_CONFLICT
HRM_MIGRATION_REQUIRED
```

## 20. Production acceptance gate

Required evidence includes:

```text
SCHEMA_MIGRATIONS              PASS
BACKFILL_RECONCILIATION        PASS
UNRESOLVED_MIGRATION_ROWS      0
PERSON_IDENTITY                PASS
EMPLOYMENT_STATE_MACHINE       PASS
NO_EMPLOYMENT_HARD_DELETE      PASS
ASSIGNMENT_TEMPORAL_RULES      PASS
POSITION_OCCUPANCY             PASS
ORG_HIERARCHY_CYCLE            DENY
REPORTING_LINE_CYCLE           DENY
RLS_NO_CONTEXT                 DENY
RLS_WRONG_TENANT               DENY
RLS_CROSS_TENANT_READ          DENY
RLS_CROSS_TENANT_WRITE         DENY
RUNTIME_BYPASSRLS              FALSE
RUNTIME_SUPERUSER              FALSE
CAPABILITY_MATRIX              PASS
ACCESS_SCOPE                   PASS
SCOPE_ESCALATION               DENY
CANONICAL_ROLE_PRIV_ESCALATION DENY
PII_ENCRYPTION                 PASS
PLAINTEXT_SENSITIVE_IDS        0
SENSITIVE_READ_AUDIT           PASS
PROTECTED_WRITE_AUDIT          PASS
COUNTRY_RESOLUTION             PASS
GLOBAL_FALLBACK                PASS
SA_PACK_RESOLUTION             PASS
UNCERTIFIED_STATUTORY_ACTION   BLOCKED
HARD_RULE_OVERRIDE             DENY
CONTROLLED_OVERRIDE            PASS
FOUR_EYES_APPROVAL             PASS
OFFICIAL_SOURCE_PROVENANCE     PASS
OUTBOX_ATOMICITY               PASS
CONSUMER_IDEMPOTENCY           PASS
RETRY / DEAD_LETTER            PASS
COMMAND_IDEMPOTENCY            PASS
OPTIMISTIC_CONCURRENCY         PASS
API_V2_CONTRACT                PASS
OPENAPI_3_1                    PASS
V1_SAFE_COMPATIBILITY          PASS
EMPLOYEE_DIRECTORY             PASS
EMPLOYEE_PROFILE               PASS
ORG_CHART                      PASS
ARABIC_RTL                     PASS
FULL_BACKEND_TESTS             PASS
SECURITY_REGRESSION            PASS
PRODUCTION_SMOKE               PASS
BACKEND_5XX                    NONE
```

Only after evidence exists may G0 execution metadata move to COMPLETE/CERTIFIED.

## 21. Explicit non-goals

G0 does not implement the full Payroll Engine, complete GCC statutory catalogs, complete Saudi government portal automation, full GOSI/WPS engines, attendance/leave engines, recruitment/onboarding, performance, analytics, or electronic signature engine. These consume the G0 foundation later.

## 22. Legal-source boundary

No statutory numeric value, eligibility formula, contribution rate, leave entitlement, termination calculation or government-submission requirement is production-authoritative until its Country Pack rule includes an official source, effective date, legal review and automated test evidence.

## 23. Final verdict

```text
ARCHITECTURE                         APPROVED
CANONICAL DATA MODEL                 APPROVED WITH REVIEW CORRECTIONS
COUNTRY-FIRST RESOLUTION             APPROVED
GCC DYNAMIC LOCALIZATION             APPROVED
GLOBAL HR FALLBACK                   APPROVED
COMPLIANCE GOVERNED OVERRIDES        APPROVED
PERSON / EMPLOYMENT SEPARATION       APPROVED
LEGAL ENTITY / ORGANIZATION BOUNDARY APPROVED
TEMPORAL MODEL                       APPROVED
FAIL-CLOSED RLS                      APPROVED
SCOPED AUTHORIZATION                 APPROVED
LOCAL AUDIT + PLATFORM SINK          APPROVED
LOCAL OUTBOX + SHARED CONTRACT       APPROVED
LOCAL IDEMPOTENCY + SHARED CONTRACT  APPROVED
API V2 + SAFE V1 ADAPTER             APPROVED
EXPAND/BACKFILL/CUTOVER/CONTRACT     APPROVED
CROSS-DECISION CONSISTENCY           PASS
CRITICAL UNRESOLVED CONTRADICTIONS   0
IMPLEMENTATION                       NOT STARTED
```

The next process gate is human review of this written specification. Only after approval should the detailed implementation plan be produced.
