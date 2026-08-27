# HRM-G0 WS3 Country and Compliance Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement deterministic Country/Jurisdiction resolution, dynamic localized Country Packs, safe Global Mode, versioned compliance rules, and the governed legal-exception workflow required by HRM-G0.

**Architecture:** Employment labor jurisdiction is resolved first. An ACTIVE/CERTIFIED Country Pack effective on the operation date enables localized policy evaluation; otherwise generic HR uses Global Mode and statutory-sensitive operations are blocked/unverified. Compliance rules are versioned with official-source provenance. Hard statutory rules are never overrideable; legally allowed exceptions require four-eyes System Manager approval.

**Tech Stack:** Java 17, Spring Boot 3.5.6, JdbcTemplate, PostgreSQL 17 Direct, Flyway, Jackson JSON, Jakarta Validation, JUnit 5/AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- No country logic such as `if (country.equals("SA"))` may be scattered through HR domain services; all localized behavior goes through `CountryPolicyResolver`/`ComplianceEngine`.
- Tenant default country is never the authoritative labor jurisdiction.
- Nationality is a worker-classification input, not labor jurisdiction.
- Country Pack/rule selection uses the operation effective date, not server current time alone.
- `MANDATORY_HARD` is non-overridable even for ADMIN/System Manager.
- `MANDATORY_WITH_EXCEPTION` is only overrideable when the rule metadata explicitly permits the legal exception path.
- Requester and final approver must be different users.
- Overrides are scoped and time-bounded; an override never edits the source Country Rule.
- Unsupported/unapproved country uses Global Mode for generic HR only. Saudi/GCC law is never a fallback for another country.
- No statutory number/formula becomes production-authoritative from this plan without an official source, source snapshot hash, effective date, designated legal review, and automated rule test.
- The Saudi foundation pack may be seeded as DRAFT; production promotion to CERTIFIED/ACTIVE is a legal-review gate, not an engineering shortcut.
- Full Saudi/GCC payroll, leave, social-insurance, EOSB, quota, and government-submission rule catalogs remain outside G0.

---

## File Structure

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_7__create_hr_country_compliance_foundation.sql

apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance/domain/
  CountryPack.java
  CountryPackStatus.java
  CountryOperatingMode.java
  ComplianceRule.java
  ComplianceEnforcementLevel.java
  ComplianceDecision.java
  ComplianceDecisionType.java
  ComplianceOperationType.java
  ComplianceOverrideRequest.java
  ComplianceOverrideStatus.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance/application/
  CountryPolicyResolver.java
  ComplianceEngine.java
  ComplianceOverrideService.java
  WorkerClassificationResolver.java
  ComplianceAuditPort.java
  ComplianceEventPort.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance/infrastructure/
  JdbcCountryPackRepository.java
  JdbcComplianceRuleRepository.java
  JdbcComplianceOverrideRepository.java
  JdbcComplianceDecisionRepository.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance/
  HrCountryPolicyResolverTest.java
  HrComplianceEngineTest.java
  HrComplianceOverrideIntegrationTest.java
  HrCountryPackLifecycleIntegrationTest.java

resources/docs:
  docs/hrm/compliance/COUNTRY-PACK-LEGAL-REVIEW-RUNBOOK.md
  docs/hrm/compliance/SA/SA-HR-FOUNDATION-v1.md
```

### Task 1: Create Country Pack, compliance-rule, decision and override schema

**Files:**
- Create: `V20260827_7__create_hr_country_compliance_foundation.sql`
- Create: `HrCountryPackLifecycleIntegrationTest.java`
- Create: `HrComplianceOverrideIntegrationTest.java`

**Interfaces:**
- Produces tables: `hr_country_packs`, `hr_compliance_rules`, `hr_compliance_decisions`, `hr_compliance_override_requests`.

- [ ] **Step 1: Write failing schema/lifecycle tests**

Required behavior:

```java
@Test
void countryPackVersionsCannotOverlapWhileActive() { /* expect constraint failure */ }

@Test
void hardRuleCannotCreateOverrideRequest() {
    assertThatThrownBy(() -> overrides.request(ctx, hardRuleId, resource, reason, null))
        .hasMessageContaining("HRM_COMPLIANCE_BLOCKED");
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCountryPackLifecycleIntegrationTest,HrComplianceOverrideIntegrationTest \
  test
```

- [ ] **Step 3: Implement schema**

Core table shape:

```sql
CREATE TABLE hr_country_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code CHAR(2) NOT NULL REFERENCES platform_countries(country_code),
    pack_code VARCHAR(80) NOT NULL,
    pack_version VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    legal_reviewed_at TIMESTAMPTZ,
    legal_reviewed_by VARCHAR(200),
    certification_reference VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_country_pack_version UNIQUE (country_code, pack_code, pack_version),
    CONSTRAINT ck_hr_country_pack_status CHECK (status IN (
      'DRAFT','SOURCE_VERIFIED','LEGAL_REVIEWED','TESTED','CERTIFIED','ACTIVE','SUSPENDED','RETIRED')),
    CONSTRAINT ck_hr_country_pack_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE hr_compliance_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_pack_id UUID NOT NULL REFERENCES hr_country_packs(id),
    rule_code VARCHAR(120) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    operation_code VARCHAR(120) NOT NULL,
    enforcement_level VARCHAR(40) NOT NULL,
    exception_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    official_source_uri VARCHAR(1000) NOT NULL,
    legal_citation VARCHAR(1000) NOT NULL,
    source_snapshot_sha256 CHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    last_legal_review_at TIMESTAMPTZ NOT NULL,
    reviewed_by VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_hr_compliance_rule_version UNIQUE (country_pack_id, rule_code, rule_version),
    CONSTRAINT ck_hr_compliance_enforcement CHECK (enforcement_level IN (
      'MANDATORY_HARD','MANDATORY_WITH_EXCEPTION','REGULATORY_GUIDANCE','TENANT_POLICY')),
    CONSTRAINT ck_hr_compliance_rule_status CHECK (status IN ('ACTIVE','SUSPENDED','RETIRED'))
);
```

`hr_compliance_decisions` is tenant-owned and records employment/resource, operation/effective date, jurisdiction, operating mode, pack/rule versions, decision type and non-secret reason metadata.

`hr_compliance_override_requests` is tenant-owned and includes rule/version, resource, requested/compliant value snapshots in redacted JSON, requester, justification, evidence reference, approver, approval comment, `valid_from`, `valid_until`, status, timestamps, and executed/audit references.

- [ ] **Step 4: Add DB invariants**

Add exclusion constraint preventing overlapping ACTIVE/CERTIFIED pack intervals for the same `(country_code, pack_code)` and fail-closed RLS to tenant-owned decision/override tables.

Add CHECK constraints:

```sql
CHECK (valid_until IS NULL OR valid_until >= valid_from)
CHECK (approved_by IS NULL OR approved_by <> requester_user_id)
```

The application also enforces four-eyes because requester/approver can change during workflow races.

- [ ] **Step 5: Seed GCC pack shells safely**

Insert one DRAFT foundation pack shell for each `SA`, `AE`, `QA`, `BH`, `KW`, `OM`. DRAFT rows contain no fabricated legal rules and cannot drive localized statutory decisions.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCountryPackLifecycleIntegrationTest,HrComplianceOverrideIntegrationTest \
  test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_7__create_hr_country_compliance_foundation.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance
git commit -m "feat(hrm): add country compliance persistence foundation"
```

### Task 2: Implement CountryPolicyResolver and worker-classification context

**Files:**
- Create: compliance domain/application/infrastructure files for pack/rule resolution.
- Create: `HrCountryPolicyResolverTest.java`

**Interfaces:**

```java
ResolvedCountryPolicy resolve(
    UUID tenantId,
    UUID employmentId,
    LocalDate effectiveDate
);
```

Representative result:

```java
public record ResolvedCountryPolicy(
    String laborJurisdiction,
    CountryOperatingMode mode,
    String packCode,
    String packVersion,
    String workerClassification,
    LocalDate effectiveDate
) {}
```

- [ ] **Step 1: Write resolver tests first**

```java
@Test
void resolvesCertifiedEffectivePackFromEmploymentJurisdiction() {
    fixture.jurisdiction(employmentId, "SA", date);
    fixture.activePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
    assertThat(resolver.resolve(tenantId, employmentId, date).mode())
        .isEqualTo(CountryOperatingMode.LOCALIZED);
}

@Test
void unsupportedOrUncertifiedCountryFallsBackToGlobalMode() {
    fixture.registerCountry("EG");
    fixture.jurisdiction(employmentId, "EG", date);
    assertThat(resolver.resolve(tenantId, employmentId, date).mode())
        .isEqualTo(CountryOperatingMode.GLOBAL);
}
```

Also test that Tenant default country `SA` cannot override an Employment jurisdiction of `AE`.

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrCountryPolicyResolverTest test
```

- [ ] **Step 3: Implement strict resolution order**

Resolution algorithm:

```text
1. Load Employment under tenant.
2. Resolve effective hr_employment_jurisdiction_periods row for effectiveDate.
3. Validate Legal Entity is active and jurisdiction combination is structurally valid.
4. Resolve Person nationality/worker attributes only as classification inputs.
5. Query ACTIVE pack whose effective period contains effectiveDate and whose legal-review/certification fields are present.
6. If found → LOCALIZED with exact pack version.
7. If not found → GLOBAL; never borrow another country's pack.
8. Persist applied pack/rule provenance when a compliance decision is made.
```

No service may infer jurisdiction from Tenant country when Employment jurisdiction is missing; return `HRM_LEGAL_REVIEW_REQUIRED`/activation blocker instead.

- [ ] **Step 4: Implement extensible worker classification**

`WorkerClassificationResolver` returns a string/code defined by the effective Country Pack mapping, not by a global fixed DB enum. In Global Mode it returns a neutral generic classification such as `GENERIC_EMPLOYEE` only for non-statutory workflows.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrCountryPolicyResolverTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance/HrCountryPolicyResolverTest.java
git commit -m "feat(hrm): resolve country policy from employment jurisdiction"
```

### Task 3: Implement ComplianceEngine with safe Global Mode semantics

**Files:**
- Create/modify: `ComplianceEngine.java`, decision/rule domain types.
- Create: `HrComplianceEngineTest.java`

**Interfaces:**

```java
ComplianceDecision evaluate(
    HrCommandContext context,
    String operationCode,
    ComplianceOperationType operationType,
    LocalDate effectiveDate,
    ComplianceResource resource
);
```

Decision types:

```text
COMPLIANT
BLOCKED
CONTROLLED_EXCEPTION_REQUIRED
LEGAL_REVIEW_REQUIRED
GLOBAL_MODE_ALLOWED
```

- [ ] **Step 1: Write decision-matrix tests**

```java
@ParameterizedTest
@MethodSource("decisionCases")
void evaluatesRuleMatrix(TestCase c) {
    assertThat(engine.evaluate(c.context(), c.operation(), c.type(), c.date(), c.resource()).type())
        .isEqualTo(c.expected());
}
```

Required cases:

```text
LOCALIZED + no blocking rule + GENERIC_HR        → COMPLIANT
GLOBAL + GENERIC_HR                              → GLOBAL_MODE_ALLOWED
GLOBAL + LOCAL_STATUTORY                         → LEGAL_REVIEW_REQUIRED/BLOCKED
LOCALIZED + MANDATORY_HARD violation             → BLOCKED
LOCALIZED + MANDATORY_WITH_EXCEPTION violation   → CONTROLLED_EXCEPTION_REQUIRED
LOCALIZED + guidance only                        → COMPLIANT with warnings
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrComplianceEngineTest test
```

- [ ] **Step 3: Implement rule evaluation without dynamic code execution**

Rule `parameters` are typed data interpreted by known rule handlers. Never store Java/SQL/SpEL/script expressions in the database.

Handler interface:

```java
public interface ComplianceRuleHandler {
    String operationCode();
    RuleEvaluation evaluate(ComplianceRule rule, ComplianceEvaluationContext context);
}
```

If a statutory operation has no registered handler/rule for its active pack, return `LEGAL_REVIEW_REQUIRED`; do not guess.

- [ ] **Step 4: Persist provenance**

Every evaluated compliance-sensitive command writes an `hr_compliance_decisions` row containing exact pack/rule versions and effective date. Payload must be redacted and must not contain raw National ID, passport, bank details, passwords, tokens, or keys.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrComplianceEngineTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance/HrComplianceEngineTest.java
git commit -m "feat(hrm): enforce localized and global compliance modes"
```

### Task 4: Implement governed override workflow and four-eyes approval

**Files:**
- Create/modify: `ComplianceOverrideService.java`, repository, ports.
- Create/modify: `HrComplianceOverrideIntegrationTest.java`

**Interfaces:**

```java
UUID requestOverride(...);
ComplianceOverrideRequest approve(UUID tenantId, UUID requestId, UUID approverUserId, String comment);
ComplianceOverrideRequest reject(...);
ComplianceOverrideRequest revoke(...);
```

`ComplianceAuditPort` and `ComplianceEventPort` are ports only in WS3; WS4 supplies durable audit/outbox adapters.

- [ ] **Step 1: Add failing security/workflow tests**

```java
@Test
void requesterCannotApproveOwnOverride() { /* expect HRM_SCOPE_DENIED or approval conflict */ }

@Test
void hardRuleNeverCreatesOverride() { /* expect HRM_COMPLIANCE_BLOCKED */ }

@Test
void expiredOverrideDoesNotAuthorizeAction() { /* effective date after validUntil -> false */ }
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrComplianceOverrideIntegrationTest test
```

- [ ] **Step 3: Implement request state machine**

```text
PENDING_APPROVAL
  → APPROVED
  → REJECTED
APPROVED
  → EXECUTED
  → REVOKED
  → EXPIRED
```

Approval requires `HRM.COMPLIANCE_OVERRIDE.APPROVE` through an authorization port supplied by WS4/WS5 and requires `approverUserId != requesterUserId` both in application and DB constraint.

- [ ] **Step 4: Make approval scoped and revalidated**

Before executing the requested business action, re-run the effective rule at the action date. If the underlying rule became HARD, expired, suspended, or changed incompatibly, the old approval does not bypass the new rule; return a fresh compliance decision.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrComplianceOverrideIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/compliance \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance/HrComplianceOverrideIntegrationTest.java
git commit -m "feat(hrm): add governed compliance override workflow"
```

### Task 5: Establish the Saudi foundation legal-review gate

**Files:**
- Create: `docs/hrm/compliance/COUNTRY-PACK-LEGAL-REVIEW-RUNBOOK.md`
- Create: `docs/hrm/compliance/SA/SA-HR-FOUNDATION-v1.md`
- Modify: Country Pack data through a new forward-only migration only after legal review is complete; do not edit `V20260827_7` after it has been applied.
- Test: `HrCountryPackLifecycleIntegrationTest.java`

**Interfaces:**
- Produces: evidence required to promote `SA-HR-FOUNDATION` from DRAFT through CERTIFIED/ACTIVE.

- [ ] **Step 1: Define required legal evidence fields**

The runbook requires for every production-authoritative rule/source:

```text
country_code
pack_code
pack_version
rule_code
operation_code
official authority
official source URI
retrieved_at
source snapshot SHA-256
legal citation/section
effective_from/effective_to
reviewer identity/role
legal_reviewed_at
automated test reference
```

- [ ] **Step 2: Gather only authoritative Saudi foundation sources**

Use official Saudi sources such as HRSD, Qiwa, GOSI, and other competent government/platform authorities relevant to the foundation rule being modeled. Do not use blogs, vendor summaries, social posts, or AI output as the legal authority.

The G0 foundation review documents only rules that G0 actually enforces. Payroll/EOSB/leave/GOSI/WPS/quotas remain marked outside-G0 rather than being partially implemented.

- [ ] **Step 3: Create `SA-HR-FOUNDATION-v1.md` with source hashes and review disposition**

The document must explicitly state one of:

```text
LEGAL_REVIEW_STATUS=APPROVED
```

or

```text
LEGAL_REVIEW_STATUS=BLOCKED
```

No ambiguous state is permitted. If no designated legal reviewer has approved the pack, status is BLOCKED and the DB pack stays DRAFT.

- [ ] **Step 4: Test promotion guards**

```java
assertThatThrownBy(() -> packService.activate(packMissingReviewEvidence))
    .hasMessageContaining("LEGAL_REVIEW_REQUIRED");
assertThat(packService.activate(fullyReviewedPack).status()).isEqualTo(ACTIVE);
```

- [ ] **Step 5: Run tests and commit documentation**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrCountryPackLifecycleIntegrationTest test
git add docs/hrm/compliance \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compliance/HrCountryPackLifecycleIntegrationTest.java
git commit -m "docs(hrm): establish Saudi country-pack legal review gate"
```

If legal review is APPROVED, add a new forward-only Flyway migration to promote only the reviewed pack/rules and include the migration in the same PR. If BLOCKED, do not fabricate an ACTIVE pack; HRM-G0 certification remains blocked on `SA_PACK_RESOLUTION` while Global Mode stays safe.

### Task 6: WS3 verification gate

**Files:**
- Create: `docs/hrm/g0/evidence/03-country-compliance.md`

- [ ] **Step 1: Run all compliance tests**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCountryPolicyResolverTest,HrComplianceEngineTest,HrComplianceOverrideIntegrationTest,HrCountryPackLifecycleIntegrationTest \
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Static-scan for scattered country branching**

```bash
rg -n 'country.*==|equals\("SA"\)|equals\("AE"\)|switch.*country' \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  --glob '!**/compliance/**'
```

Expected: no localized branching outside the compliance package.

- [ ] **Step 3: Verify Global Mode cannot run statutory operations**

Record focused test evidence for an unsupported country showing generic Employment/Assignment commands are allowed under `GLOBAL_MODE_ALLOWED`, while a `LOCAL_STATUTORY` operation returns `LEGAL_REVIEW_REQUIRED`/BLOCKED.

- [ ] **Step 4: Record legal-review state and commit evidence**

```bash
git add docs/hrm/g0/evidence/03-country-compliance.md
git commit -m "docs(hrm): record country compliance evidence"
```

`WS3_COUNTRY_COMPLIANCE=PASS` requires all automated engine/override tests. `SA_PACK_RESOLUTION=PASS` additionally requires the legally reviewed Saudi foundation pack to be ACTIVE; if legal review is blocked, record the exact blocker and do not claim full G0 certification.
