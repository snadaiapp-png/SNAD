# HRM-G0 WS2 Core and Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the existing `hr_employees`, `hr_departments`, and `hr_positions` implementation into the canonical Person → Employment → Assignment foundation with effective-dated structure/history, deterministic legacy backfill, and fail-closed tenant isolation.

**Architecture:** Use an expand-first migration. New canonical tables/columns are added while v1 remains readable; deterministic mapping/backfill scripts populate canonical identities only when legal/organization context is authoritative. Canonical services become the source of truth before legacy columns are contracted in a later release.

**Tech Stack:** Java 17, Spring Boot 3.5.6, JdbcTemplate, PostgreSQL 17 Direct, Flyway, PostgreSQL `btree_gist` exclusion constraints, JUnit 5/AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- `User != Person != Employment != Assignment`.
- `hr_employees` evolves into the physical Employment table; its legacy profile/department/position/manager columns remain compatibility-only during expand/backfill.
- `hr_departments` remains legacy compatibility data during migration; canonical hierarchy is `hr_org_units` + `hr_org_unit_versions`.
- Existing `hr_positions` becomes the stable Position identity after canonical context is populated; legacy title/department/grade columns remain compatibility-only until verified cutover.
- No operational hard delete.
- Generic status mutation is forbidden in the canonical service.
- Every lifecycle state change is effective-dated and writes history.
- Every ACTIVE Employment has exactly one effective PRIMARY Assignment.
- Position is optional by policy; if an assignment occupies a Position, temporal seat exclusivity is enforced in PostgreSQL.
- Reporting uses `reports_to_assignment_id`; no reporting cycles.
- Backfill never chooses the first Legal Entity/Organization when more than one authoritative candidate exists.
- New and legacy HR tables must use fail-closed RLS. No `current_setting(...) IS NULL OR ...` policy survives WS2.
- PostgreSQL Direct is mandatory for focused tests.

---

## File Structure

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_2__create_hr_people_and_sensitive_identity.sql
  V20260827_3__expand_hr_employment_and_history.sql
  V20260827_4__create_hr_structure_versions.sql
  V20260827_5__create_hr_assignments_and_temporal_guards.sql
  V20260827_6__harden_hr_fail_closed_rls.sql

scripts/hrm/
  g0-backfill-precheck.sql
  g0-backfill.sql
  g0-reconcile.sql

apps/sanad-platform/src/main/java/com/sanad/platform/hr/person/
  domain/HrPerson.java
  domain/HrPersonIdentifier.java
  domain/HrPersonPrivate.java
  domain/HrPersonRepository.java
  application/HrPersonService.java
  infrastructure/JdbcHrPersonRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/employment/
  domain/Employment.java
  domain/EmploymentStatus.java
  domain/EmploymentRepository.java
  application/EmploymentCommandService.java
  application/EmploymentTransitionPolicy.java
  infrastructure/JdbcEmploymentRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/structure/
  domain/HrOrgUnit.java
  domain/HrOrgUnitVersion.java
  domain/HrJob.java
  domain/HrJobVersion.java
  domain/HrPositionSeat.java
  domain/HrPositionVersion.java
  application/HrStructureService.java
  infrastructure/JdbcHrStructureRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/assignment/
  domain/HrAssignment.java
  domain/AssignmentType.java
  domain/OccupancyMode.java
  application/HrAssignmentService.java
  infrastructure/JdbcHrAssignmentRepository.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/
  HrPersonIdentityIntegrationTest.java
  HrEmploymentLifecycleIntegrationTest.java
  HrStructureVersioningIntegrationTest.java
  HrAssignmentTemporalConstraintTest.java
  HrReportingCycleIntegrationTest.java
  HrRlsFailClosedIntegrationTest.java
  HrCanonicalBackfillIntegrationTest.java
```

### Task 1: Add Person, restricted PII, and encrypted identifier schema/domain

**Files:**
- Create: `V20260827_2__create_hr_people_and_sensitive_identity.sql`
- Create: Person Java files listed above.
- Create: `HrPersonIdentityIntegrationTest.java`

**Interfaces:**
- Produces: `hr_people`, `hr_person_private`, `hr_person_identifiers`.
- Produces: `HrPersonService.createPerson`, `linkUser`, `addIdentifier`, `findExactIdentifierMatch`.

- [ ] **Step 1: Write failing integration tests for Person↔User uniqueness and identifier duplicate detection**

```java
@Test
void oneUserLinksToAtMostOnePersonPerTenant() {
    UUID p1 = people.createPerson(tenantId, userId, "A", "One").id();
    assertThatThrownBy(() -> people.createPerson(tenantId, userId, "B", "Two"))
        .hasMessageContaining("user");
}

@Test
void exactSensitiveIdentifierDuplicateIsDetectedWithoutPlaintextStorage() {
    people.addIdentifier(tenantId, personA, "NATIONAL_ID", "SA", "1234567890");
    assertThat(people.findExactIdentifierMatch(tenantId, "NATIONAL_ID", "SA", "1234567890"))
        .contains(personA);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM hr_person_identifiers WHERE identifier_ciphertext LIKE '%1234567890%'",
        Integer.class)).isZero();
}
```

- [ ] **Step 2: Run tests and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrPersonIdentityIntegrationTest test
```

- [ ] **Step 3: Implement Person migration**

Core shape:

```sql
CREATE TABLE hr_people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID REFERENCES users(id),
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_hr_people_tenant_user
    ON hr_people(tenant_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE hr_person_private (
    person_id UUID PRIMARY KEY REFERENCES hr_people(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    date_of_birth DATE,
    nationality_country_code CHAR(2) REFERENCES platform_countries(country_code),
    marital_status VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE hr_person_identifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    person_id UUID NOT NULL REFERENCES hr_people(id),
    identifier_type VARCHAR(40) NOT NULL,
    issuing_country_code CHAR(2) REFERENCES platform_countries(country_code),
    identifier_ciphertext TEXT NOT NULL,
    blind_index CHAR(64) NOT NULL,
    encryption_key_version VARCHAR(40) NOT NULL,
    blind_index_key_version VARCHAR(40) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_person_identifier_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_hr_person_identifier_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))
);
CREATE UNIQUE INDEX uq_hr_identifier_active_exact
    ON hr_person_identifiers(tenant_id, identifier_type, issuing_country_code, blind_index)
    WHERE status = 'ACTIVE';
```

Enable FORCE RLS with fail-closed `USING`/`WITH CHECK` on all three tenant tables.

- [ ] **Step 4: Implement `HrPersonService` using `PlatformCryptographyService`**

Identifier flow:

```java
String normalized = IdentifierNormalizer.normalize(type, rawValue);
BlindIndex index = crypto.blindIndex(tenantId, type + ":" + issuingCountry, normalized);
EncryptedValue encrypted = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER", normalized);
repository.insertIdentifier(personId, type, issuingCountry, encrypted, index);
```

Do not return ciphertext/blind index from API-facing domain projections.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrPersonIdentityIntegrationTest test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_2__create_hr_people_and_sensitive_identity.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/person \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrPersonIdentityIntegrationTest.java
git commit -m "feat(hrm): add canonical person identity foundation"
```

### Task 2: Expand Employment and implement the lifecycle state machine

**Files:**
- Create: `V20260827_3__expand_hr_employment_and_history.sql`
- Create: Employment Java files listed above.
- Create: `HrEmploymentLifecycleIntegrationTest.java`
- Keep: legacy `HrEmployee.java` and repository until v1 cutover; do not delete yet.

**Interfaces:**
- Produces: canonical `Employment` projection over `hr_employees`.
- Produces: `hr_employment_status_periods`, `hr_employment_jurisdiction_periods`, `hr_legacy_employee_mappings`, `hr_migration_review_items`.
- Produces explicit lifecycle methods; no `setStatus` API.

- [ ] **Step 1: Write failing lifecycle tests**

```java
@Test
void terminatedEmploymentCannotBeReactivated() {
    Employment e = fixture.activeEmployment();
    commands.terminate(ctx, e.id(), effectiveDate, "END_OF_RELATIONSHIP");
    assertThatThrownBy(() -> commands.activate(ctx, e.id(), effectiveDate.plusDays(1)))
        .hasMessageContaining("INVALID_STATE_TRANSITION");
}

@Test
void transitionCreatesNonOverlappingStatusHistory() {
    Employment e = fixture.pendingOnboarding();
    commands.activate(ctx, e.id(), LocalDate.of(2026, 8, 27));
    assertThat(repository.statusPeriods(e.id())).extracting(StatusPeriod::status)
        .containsExactly(PENDING_ONBOARDING, ACTIVE);
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrEmploymentLifecycleIntegrationTest test
```

- [ ] **Step 3: Expand `hr_employees` without breaking v1**

```sql
ALTER TABLE hr_employees ADD COLUMN person_id UUID REFERENCES hr_people(id);
ALTER TABLE hr_employees ADD COLUMN legal_entity_id UUID REFERENCES legal_entities(id);
ALTER TABLE hr_employees ADD COLUMN worker_classification_code VARCHAR(60);
ALTER TABLE hr_employees ADD COLUMN rehire_of_employee_id UUID REFERENCES hr_employees(id);
ALTER TABLE hr_employees ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE hr_employment_status_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    employment_id UUID NOT NULL REFERENCES hr_employees(id),
    status VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason_code VARCHAR(80),
    reason_text VARCHAR(500),
    changed_by UUID,
    transition_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_employment_status CHECK (status IN ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','VOIDED')),
    CONSTRAINT ck_hr_employment_status_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
ALTER TABLE hr_employment_status_periods
ADD CONSTRAINT ex_hr_employment_status_no_overlap
EXCLUDE USING gist (
    employment_id WITH =,
    daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
);

CREATE TABLE hr_employment_jurisdiction_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    employment_id UUID NOT NULL REFERENCES hr_employees(id),
    labor_jurisdiction_code CHAR(2) NOT NULL REFERENCES platform_countries(country_code),
    effective_from DATE NOT NULL,
    effective_to DATE,
    decision_provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_employment_jurisdiction_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
ALTER TABLE hr_employment_jurisdiction_periods
ADD CONSTRAINT ex_hr_employment_jurisdiction_no_overlap
EXCLUDE USING gist (
    employment_id WITH =,
    daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
);
```

Also create migration mapping/review tables with unique legacy IDs so backfill is idempotent.

- [ ] **Step 4: Implement `EmploymentTransitionPolicy`**

```java
private static final Map<EmploymentStatus, Set<EmploymentStatus>> ALLOWED = Map.of(
    DRAFT, Set.of(PENDING_ONBOARDING, VOIDED),
    PENDING_ONBOARDING, Set.of(ACTIVE, VOIDED),
    ACTIVE, Set.of(ON_LEAVE, SUSPENDED, TERMINATED),
    ON_LEAVE, Set.of(ACTIVE, SUSPENDED, TERMINATED),
    SUSPENDED, Set.of(ACTIVE, TERMINATED),
    TERMINATED, Set.of(),
    VOIDED, Set.of()
);
```

A transition transaction closes the current period, inserts the next period, updates legacy/current `hr_employees.status` projection, increments `version`, and rejects stale expected versions.

- [ ] **Step 5: Preserve current v1 status values during expand**

Do not drop the legacy status CHECK in this migration if existing v1 code still requires it. Expand it to include `DRAFT`, `PENDING_ONBOARDING`, and `VOIDED` while retaining existing values; canonical commands control mutations.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrEmploymentLifecycleIntegrationTest test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_3__expand_hr_employment_and_history.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/employment \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrEmploymentLifecycleIntegrationTest.java
git commit -m "feat(hrm): add canonical employment lifecycle"
```

### Task 3: Add effective-dated Org Unit, Job, and Position versions

**Files:**
- Create: `V20260827_4__create_hr_structure_versions.sql`
- Create: Structure Java files listed above.
- Create: `HrStructureVersioningIntegrationTest.java`

**Interfaces:**
- Produces: `hr_org_units`, `hr_org_unit_versions`, `hr_jobs`, `hr_job_versions`, `hr_position_versions`.
- Evolves existing `hr_positions` with canonical `organization_id`/stable-seat context while preserving legacy columns.

- [ ] **Step 1: Write failing temporal/cycle tests**

```java
@Test
void orgUnitVersionsCannotOverlap() { /* insert v1, assert overlapping v2 fails */ }

@Test
void orgHierarchyRejectsCycleForOverlappingEffectivePeriod() {
    UUID a = fixture.orgUnit("A");
    UUID b = fixture.childOrgUnit(a, "B");
    assertThatThrownBy(() -> structure.reviseOrgUnit(a, effectiveDate, v -> v.parentOrgUnitId(b)))
        .hasMessageContaining("cycle");
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrStructureVersioningIntegrationTest test
```

- [ ] **Step 3: Create stable identities and version tables**

Canonical shape:

```sql
CREATE TABLE hr_org_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    stable_code VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_org_unit_code UNIQUE (tenant_id, organization_id, stable_code)
);

CREATE TABLE hr_org_unit_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    org_unit_id UUID NOT NULL REFERENCES hr_org_units(id),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    unit_type VARCHAR(30) NOT NULL,
    parent_org_unit_id UUID REFERENCES hr_org_units(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT ck_hr_org_unit_type CHECK (unit_type IN ('BUSINESS_UNIT','DIVISION','DEPARTMENT','TEAM'))
);
```

Add equivalent stable/version tables for Jobs and Position versions. Reuse existing `hr_positions.id` as stable seat identity; add `organization_id`, `version`, and canonical code constraints initially nullable until backfill.

- [ ] **Step 4: Add PostgreSQL exclusion constraints for every version table**

Use `daterange(..., '[)') &&` keyed by stable aggregate ID so versions never overlap.

- [ ] **Step 5: Implement period-aware hierarchy cycle detection**

Before inserting a proposed Org Unit version, use a recursive CTE constrained to the candidate interval. Reject if the proposed parent chain reaches the same stable Org Unit ID during any overlapping period.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrStructureVersioningIntegrationTest test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_4__create_hr_structure_versions.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/structure \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrStructureVersioningIntegrationTest.java
git commit -m "feat(hrm): add effective-dated HR structure"
```

### Task 4: Add effective-dated Assignments and seat/reporting invariants

**Files:**
- Create: `V20260827_5__create_hr_assignments_and_temporal_guards.sql`
- Create: Assignment Java files listed above.
- Create: `HrAssignmentTemporalConstraintTest.java`
- Create: `HrReportingCycleIntegrationTest.java`

**Interfaces:**
- Produces: `hr_employee_assignments`.
- Produces: canonical reporting via `reports_to_assignment_id`.

- [ ] **Step 1: Write failing PRIMARY and Position occupancy tests**

```java
@Test
void employmentCannotHaveOverlappingPrimaryAssignments() { /* expect constraint violation */ }

@Test
void onePositionCannotHaveOverlappingOccupyingAssignments() { /* expect constraint violation */ }
```

- [ ] **Step 2: Write failing reporting-cycle test**

```java
@Test
void reportingLineCannotCreateCycle() {
    HrAssignment manager = fixture.primaryAssignment();
    HrAssignment employee = fixture.primaryAssignmentReportingTo(manager.id());
    assertThatThrownBy(() -> assignments.changeManager(manager.id(), employee.id(), effectiveDate))
        .hasMessageContaining("REPORTING_CYCLE");
}
```

- [ ] **Step 3: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrAssignmentTemporalConstraintTest,HrReportingCycleIntegrationTest \
  test
```

- [ ] **Step 4: Implement assignment table and temporal constraints**

```sql
CREATE TABLE hr_employee_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    employment_id UUID NOT NULL REFERENCES hr_employees(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    org_unit_id UUID REFERENCES hr_org_units(id),
    position_id UUID REFERENCES hr_positions(id),
    reports_to_assignment_id UUID REFERENCES hr_employee_assignments(id),
    work_location_id UUID REFERENCES work_locations(id),
    cost_center_id UUID,
    assignment_type VARCHAR(20) NOT NULL,
    occupancy_mode VARCHAR(20) NOT NULL,
    allocation_percent NUMERIC(5,2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_assignment_type CHECK (assignment_type IN ('PRIMARY','SECONDARY')),
    CONSTRAINT ck_hr_assignment_occupancy CHECK (occupancy_mode IN ('OCCUPYING','NON_OCCUPYING')),
    CONSTRAINT ck_hr_assignment_allocation CHECK (allocation_percent > 0 AND allocation_percent <= 100),
    CONSTRAINT ck_hr_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_assignment_status CHECK (status IN ('ACTIVE','ENDED','VOIDED'))
);
```

Add one exclusion constraint for overlapping `PRIMARY` periods per Employment and one for overlapping `OCCUPYING` periods per non-null Position. Use partial predicates and `daterange`.

- [ ] **Step 5: Implement application validation**

`HrAssignmentService` must validate same Tenant, LegalEntity↔Organization eligibility, effective Org Unit/Position version, FROZEN/CLOSED position rules, total effective allocation, and reporting-cycle prevention before insert/update. Database constraints remain the final race defense.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrAssignmentTemporalConstraintTest,HrReportingCycleIntegrationTest \
  test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_5__create_hr_assignments_and_temporal_guards.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/assignment \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrAssignmentTemporalConstraintTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrReportingCycleIntegrationTest.java
git commit -m "feat(hrm): add effective-dated employee assignments"
```

### Task 5: Replace fail-open HR RLS with fail-closed policies

**Files:**
- Create: `V20260827_6__harden_hr_fail_closed_rls.sql`
- Create: `HrRlsFailClosedIntegrationTest.java`
- Modify: existing `HrTenantContextRegressionTest.java` only if its comments/test assumptions still mention H2 or fail to test DB RLS.

**Interfaces:**
- Produces: fail-closed RLS on legacy and canonical HR tenant tables.

- [ ] **Step 1: Write database-level failing tests before migration**

Required assertions:

```java
assertThat(countWithoutTenantContext("hr_employees")).isZero();
assertThat(countWithWrongTenant("hr_employees", tenantB)).isZero();
assertThatThrownBy(() -> insertCrossTenantRow()).isInstanceOf(DataAccessException.class);
assertThat(runtimeRoleIsSuperuser()).isFalse();
assertThat(runtimeRoleBypassesRls()).isFalse();
```

Cover `hr_departments`, `hr_positions`, `hr_employees`, `hr_people`, `hr_person_private`, `hr_person_identifiers`, status/jurisdiction histories, canonical structure tables, and assignments.

- [ ] **Step 2: Run against pre-hardening schema and prove RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrRlsFailClosedIntegrationTest test
```

Expected: legacy HR no-context assertion fails because the old policy is fail-open.

- [ ] **Step 3: Replace every legacy `tenant_isolation` policy**

For `hr_employees`, `hr_departments`, and `hr_positions`:

```sql
DROP POLICY IF EXISTS tenant_isolation ON hr_employees;
ALTER TABLE hr_employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employees FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_employees_tenant_isolation ON hr_employees
USING (tenant_id::text = current_setting('app.tenant_id', true))
WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
```

Repeat with table-specific policy names. Apply the same fail-closed pattern to all new HR tenant tables.

- [ ] **Step 4: Re-run and prove GREEN**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrRlsFailClosedIntegrationTest,HrTenantContextRegressionTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_6__harden_hr_fail_closed_rls.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrRlsFailClosedIntegrationTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrTenantContextRegressionTest.java
git commit -m "fix(hrm): enforce fail-closed tenant RLS"
```

### Task 6: Build deterministic legacy backfill and reconciliation

**Files:**
- Create: `scripts/hrm/g0-backfill-precheck.sql`
- Create: `scripts/hrm/g0-backfill.sql`
- Create: `scripts/hrm/g0-reconcile.sql`
- Create: `HrCanonicalBackfillIntegrationTest.java`

**Interfaces:**
- Produces: deterministic mapping from legacy HR rows into Person/Employment/Assignment/Org Unit/Position canonical data.
- Produces: machine-readable unresolved review rows instead of guessed mappings.

- [ ] **Step 1: Write failing fixture tests for one unambiguous and two ambiguous tenants**

Fixtures:

```text
Tenant A: 1 active Legal Entity + 1 eligible Organization → AUTO_READY
Tenant B: 2 active eligible Organizations             → MIGRATION_REVIEW_REQUIRED
Tenant C: 0 active Legal Entities                     → MIGRATION_BLOCKED
```

Expected assertions:

```java
assertThat(precheck(tenantA).unresolved()).isZero();
assertThat(precheck(tenantB).reviewRequired()).isGreaterThan(0);
assertThat(precheck(tenantC).blocked()).isGreaterThan(0);
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrCanonicalBackfillIntegrationTest test
```

- [ ] **Step 3: Implement precheck SQL**

`g0-backfill-precheck.sql` populates/refreshes `hr_migration_review_items` and classifies every legacy Employment. It must explicitly check:

```sql
-- legal entity candidate count
SELECT e.tenant_id, COUNT(*)
FROM legal_entities le
JOIN hr_employees e ON e.tenant_id = le.tenant_id
WHERE le.status = 'ACTIVE'
GROUP BY e.tenant_id;
```

and equivalent effective LegalEntity↔Organization candidate counts. Duplicate non-null legacy `user_id` values within a Tenant are `MIGRATION_REVIEW_REQUIRED`, never silently merged.

- [ ] **Step 4: Implement idempotent backfill SQL**

`g0-backfill.sql` starts with:

```sql
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hr_migration_review_items
    WHERE status IN ('MIGRATION_REVIEW_REQUIRED','MIGRATION_BLOCKED')
  ) THEN
    RAISE EXCEPTION 'HRM_G0_BACKFILL_BLOCKED: unresolved migration review items exist';
  END IF;
END $$;
```

Then, inside one transaction:

1. Create one `hr_people` row per legacy employee lacking `person_id`, preserving names/contact projection and valid optional `user_id`.
2. Set `hr_employees.person_id` from the created mapping.
3. Assign the single authoritative active Legal Entity; never `ORDER BY ... LIMIT 1` across multiple candidates.
4. Insert initial status and jurisdiction periods from authoritative legacy values/date context.
5. Backfill `hr_departments` into `hr_org_units` + initial versions under the single authoritative Organization and write a stable legacy map.
6. Evolve legacy `hr_positions` into stable Position identities and create initial position versions.
7. Create one PRIMARY Assignment per legacy Employment from mapped department/position and authoritative Organization.
8. Resolve legacy `manager_id` only after PRIMARY assignments exist; unresolved/cross-org reporting becomes review data rather than guessed linkage.

- [ ] **Step 5: Implement reconciliation SQL**

`g0-reconcile.sql` returns named gate rows:

```text
LEGACY_EMPLOYEE_COUNT
CANONICAL_EMPLOYMENT_COUNT
PERSON_MAPPING_MISSING
LEGAL_ENTITY_MAPPING_MISSING
PRIMARY_ASSIGNMENT_MISSING
DEPARTMENT_MAPPING_MISSING
POSITION_MAPPING_MISSING
MANAGER_MAPPING_UNRESOLVED
UNRESOLVED_MIGRATION_ROWS
```

Counts must be zero/equal according to the approved gate before cutover.

- [ ] **Step 6: Run fixture tests through precheck → backfill → reconciliation**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrCanonicalBackfillIntegrationTest test
```

Expected: Tenant A backfills and reconciles; Tenant B/C are blocked with explicit review rows.

- [ ] **Step 7: Commit**

```bash
git add scripts/hrm \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/foundation/HrCanonicalBackfillIntegrationTest.java
git commit -m "feat(hrm): add deterministic canonical backfill"
```

### Task 7: WS2 verification gate

**Files:**
- Create: `docs/hrm/g0/evidence/02-core-migration.md`

- [ ] **Step 1: Run the complete WS2 focused suite**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrPersonIdentityIntegrationTest,HrEmploymentLifecycleIntegrationTest,HrStructureVersioningIntegrationTest,HrAssignmentTemporalConstraintTest,HrReportingCycleIntegrationTest,HrRlsFailClosedIntegrationTest,HrCanonicalBackfillIntegrationTest,HrTenantContextRegressionTest \
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Prove no canonical physical delete exists**

```bash
rg -n "DELETE FROM hr_employees" apps/sanad-platform/src/main/java
```

Expected at this stage: only the legacy `JdbcHrEmployeeRepository` may still contain the old delete implementation for v1 compatibility work to retire in WS5; no new canonical repository/service contains it. Record this distinction in evidence.

- [ ] **Step 3: Prove all HR RLS policies are fail closed**

Run the PostgreSQL catalog query used by `HrRlsFailClosedIntegrationTest` and record the policy expressions. No HR policy may contain a null-context allow clause.

- [ ] **Step 4: Record evidence and commit**

```bash
git add docs/hrm/g0/evidence/02-core-migration.md
git commit -m "docs(hrm): record core migration evidence"
```

Expected verdict: `WS2_HR_CORE=PASS` only when all focused tests pass and deterministic backfill fixtures reconcile.
