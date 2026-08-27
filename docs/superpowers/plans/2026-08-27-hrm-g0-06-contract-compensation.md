# HRM-G0 WS6 Contract and Compensation Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add effective-dated Employment Contract and Compensation Terms foundations that are country-policy-aware, independently permissioned/audited, and explicitly stop before Payroll calculation or financial posting.

**Architecture:** Contracts use stable legal-instrument identity plus immutable effective-dated versions. Compensation changes create new effective packages rather than overwriting history. Both services resolve Employment jurisdiction and run the Compliance Engine before mutation, then rely on scoped authorization, local audit, and outbox infrastructure from WS4.

**Tech Stack:** Java 17, Spring Boot 3.5.6, JdbcTemplate, PostgreSQL 17 Direct, Flyway, Jackson JSON for schema-validated country-extension terms, JUnit 5/AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- HRM owns contract/compensation terms only.
- Payroll calculation, earnings/deductions, statutory contributions, payslips, payroll runs, bank-payment execution, and Accounting/GL posting are outside G0.
- Historical effective contract/compensation terms are immutable.
- Contract amendment creates a new version; a materially new legal instrument creates a new Contract.
- At most one effective primary Contract per Employment/date.
- At most one effective Compensation Package per Employment/date.
- Compensation read/write has independent capabilities and sensitive-read audit.
- Contract read/write has independent capabilities; sensitive contract reads are audited.
- Base salary/allowance values must never be copied into unrestricted employee-directory DTOs, logs, domain events, or general audit payloads.
- Country-specific contract extension fields are typed/validated through a Country Pack schema/handler; do not execute scripts stored in JSON.
- Contract/compensation changes in Global Mode are generic terms only and do not claim statutory correctness.
- No hard-coded Saudi/GCC statutory formula enters these services.

---

## File Structure

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_10__create_hr_contract_compensation_foundation.sql

apps/sanad-platform/src/main/java/com/sanad/platform/hr/contract/
  domain/EmploymentContract.java
  domain/EmploymentContractVersion.java
  domain/EmploymentContractStatus.java
  domain/EmploymentContractRepository.java
  application/EmploymentContractService.java
  application/CountryContractTermsValidator.java
  infrastructure/JdbcEmploymentContractRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/compensation/
  domain/CompensationPackage.java
  domain/CompensationComponent.java
  domain/CompensationComponentType.java
  domain/CompensationRepository.java
  application/CompensationService.java
  infrastructure/JdbcCompensationRepository.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/contract/
  HrEmploymentContractIntegrationTest.java
  HrContractCountryPolicyIntegrationTest.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/compensation/
  HrCompensationIntegrationTest.java
  HrCompensationAuthorizationAuditIntegrationTest.java
```

### Task 1: Create effective-dated contract and compensation schema

**Files:**
- Create: `V20260827_10__create_hr_contract_compensation_foundation.sql`
- Create: `HrEmploymentContractIntegrationTest.java`
- Create: `HrCompensationIntegrationTest.java`

**Interfaces:**
- Produces: `hr_employment_contracts`, `hr_employment_contract_versions`, `hr_compensation_packages`, `hr_compensation_components`.

- [ ] **Step 1: Write failing temporal tests**

```java
@Test
void employmentCannotHaveTwoOverlappingPrimaryContracts() { /* expect exclusion constraint */ }

@Test
void employmentCannotHaveOverlappingCompensationPackages() { /* expect exclusion constraint */ }
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrCompensationIntegrationTest \
  test
```

- [ ] **Step 3: Implement contract schema**

```sql
CREATE TABLE hr_employment_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    employment_id UUID NOT NULL REFERENCES hr_employees(id),
    contract_number VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    predecessor_contract_id UUID REFERENCES hr_employment_contracts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_contract_number UNIQUE (tenant_id, employment_id, contract_number)
);

CREATE TABLE hr_employment_contract_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    contract_id UUID NOT NULL REFERENCES hr_employment_contracts(id),
    version_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    contract_term_type VARCHAR(30) NOT NULL,
    contract_start_date DATE NOT NULL,
    contract_end_date DATE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    document_reference VARCHAR(500),
    country_terms JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_contract_version UNIQUE (contract_id, version_number),
    CONSTRAINT ck_hr_contract_status CHECK (status IN (
      'DRAFT','PENDING_SIGNATURE','ACTIVE','EXPIRED','TERMINATED','SUPERSEDED','VOIDED')),
    CONSTRAINT ck_hr_contract_term_type CHECK (contract_term_type IN ('FIXED_TERM','INDEFINITE','OTHER')),
    CONSTRAINT ck_hr_contract_dates CHECK (
      (contract_end_date IS NULL OR contract_end_date >= contract_start_date)
      AND (effective_to IS NULL OR effective_to >= effective_from)
    )
);
```

Add exclusion constraints preventing overlapping effective versions for one Contract and overlapping ACTIVE primary Contracts for one Employment.

- [ ] **Step 4: Implement compensation schema**

```sql
CREATE TABLE hr_compensation_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    employment_id UUID NOT NULL REFERENCES hr_employees(id),
    currency_code CHAR(3) NOT NULL,
    pay_frequency VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL,
    predecessor_package_id UUID REFERENCES hr_compensation_packages(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_compensation_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_compensation_status CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED','ENDED','VOIDED'))
);

CREATE TABLE hr_compensation_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    package_id UUID NOT NULL REFERENCES hr_compensation_packages(id),
    component_type VARCHAR(40) NOT NULL,
    code VARCHAR(80) NOT NULL,
    amount NUMERIC(19,4),
    percentage NUMERIC(9,4),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_hr_comp_component_type CHECK (component_type IN (
      'BASE_SALARY','ALLOWANCE','BENEFIT','VARIABLE_TARGET','OTHER')),
    CONSTRAINT ck_hr_comp_amount_or_percentage CHECK (
      (amount IS NOT NULL AND percentage IS NULL) OR
      (amount IS NULL AND percentage IS NOT NULL)
    )
);
```

Add exclusion constraint preventing overlapping ACTIVE packages for one Employment. Enable FORCE fail-closed RLS on every table.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrCompensationIntegrationTest \
  test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_10__create_hr_contract_compensation_foundation.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/contract/HrEmploymentContractIntegrationTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compensation/HrCompensationIntegrationTest.java
git commit -m "feat(hrm): add contract and compensation persistence"
```

### Task 2: Implement immutable Contract versioning and Country Pack validation

**Files:**
- Create: Contract domain/application/infrastructure files listed above.
- Create: `HrContractCountryPolicyIntegrationTest.java`

**Interfaces:**

```java
EmploymentContract createDraft(HrCommandContext ctx, CreateContractCommand command);
EmploymentContractVersion amend(HrCommandContext ctx, UUID contractId, AmendContractCommand command);
EmploymentContractVersion activate(HrCommandContext ctx, UUID contractId, int versionNumber, LocalDate effectiveDate);
EmploymentContract terminate(HrCommandContext ctx, UUID contractId, LocalDate effectiveDate, String reasonCode);
```

- [ ] **Step 1: Write immutable-history tests**

```java
@Test
void amendmentCreatesNewVersionAndPreservesOldVersion() {
    var v1 = fixture.activeContractVersion();
    var v2 = service.amend(ctx, v1.contractId(), amendment);
    assertThat(v2.versionNumber()).isEqualTo(v1.versionNumber() + 1);
    assertThat(repository.findVersion(v1.id())).contains(v1);
}

@Test
void effectiveHistoricalVersionCannotBeUpdatedInPlace() {
    assertThatThrownBy(() -> repository.updateHistoricalTerms(historicalId, changedTerms))
        .hasMessageContaining("IMMUTABLE");
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrContractCountryPolicyIntegrationTest \
  test
```

- [ ] **Step 3: Implement Contract aggregate/version repository**

Never expose an `updateTerms(contractVersionId, ...)` operation for an already-effective version. `amend()` closes/supersedes the current version as appropriate and inserts a new version in one transaction.

- [ ] **Step 4: Resolve jurisdiction and validate country extension terms**

Before activation/amendment:

```java
ResolvedCountryPolicy policy = countryPolicyResolver.resolve(
    ctx.tenantId(), contract.employmentId(), command.effectiveDate());
countryContractTermsValidator.validate(policy, command.countryTerms());
ComplianceDecision decision = complianceEngine.evaluate(
    ctx, "HRM.CONTRACT.ACTIVATE", ComplianceOperationType.GENERIC_HR,
    command.effectiveDate(), resource);
```

If the active Country Pack requires a term/schema the request does not satisfy, return a structured compliance violation. In Global Mode accept only the generic schema and mark the resulting view `LOCAL_COMPLIANCE_UNVERIFIED`.

- [ ] **Step 5: Append redacted audit/outbox events**

Events:

```text
HRM.CONTRACT.CREATED.v1
HRM.CONTRACT.AMENDED.v1
HRM.CONTRACT.ACTIVATED.v1
HRM.CONTRACT.TERMINATED.v1
```

Payload contains IDs/status/effective dates and pack provenance, not compensation or restricted PII.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrContractCountryPolicyIntegrationTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/contract \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/contract
git commit -m "feat(hrm): add effective-dated employment contracts"
```

### Task 3: Implement effective-dated Compensation packages and components

**Files:**
- Create: Compensation domain/application/infrastructure files listed above.
- Modify: `HrCompensationIntegrationTest.java`
- Create: `HrCompensationAuthorizationAuditIntegrationTest.java`

**Interfaces:**

```java
CompensationPackage createPackage(HrCommandContext ctx, CreateCompensationCommand command);
CompensationPackage revisePackage(HrCommandContext ctx, UUID currentPackageId, ReviseCompensationCommand command);
CompensationPackage endPackage(HrCommandContext ctx, UUID packageId, LocalDate effectiveTo, String reason);
```

- [ ] **Step 1: Write effective-history tests**

```java
@Test
void compensationChangeCreatesNewPackageInsteadOfOverwritingHistory() {
    CompensationPackage old = fixture.activePackage();
    CompensationPackage next = service.revisePackage(ctx, old.id(), revision);
    assertThat(next.predecessorPackageId()).isEqualTo(old.id());
    assertThat(repository.find(old.id()).orElseThrow().effectiveTo())
        .isEqualTo(next.effectiveFrom().minusDays(1));
}
```

- [ ] **Step 2: Write permission/audit tests**

```java
@Test
void employeeViewerCannotReadCompensationWithoutIndependentCapability() { /* DENY */ }

@Test
void authorizedCompensationReadWritesSensitiveReadAuditWithoutAmountsInAuditPayload() { /* PASS */ }
```

- [ ] **Step 3: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCompensationIntegrationTest,HrCompensationAuthorizationAuditIntegrationTest \
  test
```

- [ ] **Step 4: Implement package/component validation**

Rules enforced here are structural only:

```text
one BASE_SALARY component maximum per package
amount/percentage > 0 when present
currency belongs to package, not individual component
no overlapping active package period
historical package not overwritten
```

Any statutory minimum/maximum/contribution treatment belongs to a reviewed Country Rule handler, not `CompensationService`.

- [ ] **Step 5: Integrate scoped authorization and sensitive-read audit**

Use independent capability codes expected by WS5:

```text
HRM.COMPENSATION.VIEW
HRM.COMPENSATION.MANAGE
```

The service must call `SensitiveReadAuditService.recordOrThrow()` before returning component amounts to an authorized caller.

- [ ] **Step 6: Append change event without sensitive amounts**

```text
HRM.COMPENSATION.CHANGED.v1
```

Payload includes Employment/package IDs, effective date, component codes/types, and provenance; amounts are not emitted to the generic event bus.

- [ ] **Step 7: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCompensationIntegrationTest,HrCompensationAuthorizationAuditIntegrationTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/compensation \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compensation
git commit -m "feat(hrm): add effective-dated compensation terms"
```

### Task 4: Prove Payroll/Accounting boundary is not crossed

**Files:**
- Modify/Create: `HrModuleBoundaryArchitectureTest.java`
- Create: `HrContractCompensationBoundaryTest.java`

- [ ] **Step 1: Add forbidden dependency tests**

```java
noClasses().that().resideInAnyPackage("..hr.contract..", "..hr.compensation..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "..payroll..infrastructure..",
        "..accounting..infrastructure..",
        "..finance..infrastructure..")
    .check(importedClasses);
```

- [ ] **Step 2: Add source-level guard against payroll calculation behavior in G0 HR packages**

Test/scan forbidden concepts in production HR compensation classes:

```text
payslip
payroll run
statutory deduction calculator
GL posting
journal entry posting
bank payment execution
```

The word may appear in documentation/comments explaining boundaries but not as executable HR behavior.

- [ ] **Step 3: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrContractCompensationBoundaryTest,HrModuleBoundaryArchitectureTest \
  test
git add apps/sanad-platform/src/test/java/com/sanad/platform/hr
git commit -m "test(hrm): enforce contract compensation boundaries"
```

### Task 5: WS6 verification gate

**Files:**
- Create: `docs/hrm/g0/evidence/06-contract-compensation.md`

- [ ] **Step 1: Run WS6 suite**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrContractCountryPolicyIntegrationTest,HrCompensationIntegrationTest,HrCompensationAuthorizationAuditIntegrationTest,HrContractCompensationBoundaryTest \
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Verify no unrestricted amount leakage**

Run test queries/log assertions proving compensation amounts do not appear in `hr_audit_ledger` generic before/after payloads, `hr_domain_event_outbox` payloads, or unrestricted employee DTO fixtures.

- [ ] **Step 3: Verify Global Mode marking**

For an unsupported-country fixture, generic Contract/Compensation terms may be stored but query responses must expose compliance status `LOCAL_COMPLIANCE_UNVERIFIED`; no statutory result is returned.

- [ ] **Step 4: Record evidence and commit**

```bash
git add docs/hrm/g0/evidence/06-contract-compensation.md
git commit -m "docs(hrm): record contract compensation evidence"
```

Expected verdict: `WS6_CONTRACT_COMPENSATION=PASS` only when temporal history, permissions, audit behavior, country-policy hook, and Payroll/Accounting boundaries all pass.
