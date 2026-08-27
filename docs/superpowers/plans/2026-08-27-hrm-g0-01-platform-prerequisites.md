# HRM-G0 WS1 Platform Prerequisites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the minimum reusable Platform masters and security/integration contracts HRM-G0 requires without duplicating Organization, IAM, Finance, or future module-owned data.

**Architecture:** Extend the existing Platform with a country registry, Legal Entity master, effective-dated LegalEntity↔Organization eligibility, and Work Location master. Add reusable cryptography, event-envelope, audit-sink, and idempotency interfaces; keep HR-specific durable storage in later HR workstreams.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Jakarta Validation/JPA where the Organization package already uses JPA, JdbcTemplate for PostgreSQL-specific invariants, PostgreSQL 17 Direct, Flyway, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- Do not change the semantic meaning of the existing `organizations` aggregate.
- Legal Entity is a separate Employer-of-Record aggregate; Organization remains an operational aggregate.
- Work Location is separate from Organizational Unit and Cost Center.
- All tenant-owned new tables use fail-closed RLS with explicit `WITH CHECK`.
- `platform_countries` is reference/master data and is not tenant-owned.
- Country codes use uppercase ISO-3166-1 alpha-2 codes; no `GLOBAL` pseudo-country is stored as a country code.
- Unsupported countries are supported later through Global Mode; absence of a Country Pack must never prevent a valid ISO country from being registered.
- No country-law numeric rules are stored in this workstream.
- No new HR capabilities are assigned to `HR_MANAGER` here.
- Cryptographic keys are never committed or stored in application tables.
- Do not reuse the CRM legacy encryption service as the platform crypto abstraction; it is reference evidence only.

---

## File Structure

New/changed responsibilities:

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_1__create_hrm_platform_country_and_employer_prerequisites.sql

apps/sanad-platform/src/main/java/com/sanad/platform/globalization/country/
  CountryCode.java                 // ISO code validation
  PlatformCountry.java             // country master projection
  PlatformCountryRepository.java   // tenant-independent country reads/writes
  JdbcPlatformCountryRepository.java
  PlatformCountryService.java

apps/sanad-platform/src/main/java/com/sanad/platform/organization/legalentity/
  LegalEntity.java
  LegalEntityStatus.java
  LegalEntityRepository.java
  JdbcLegalEntityRepository.java
  LegalEntityService.java
  LegalEntityOrganizationEligibility.java
  LegalEntityOrganizationEligibilityRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/organization/worklocation/
  WorkLocation.java
  WorkLocationStatus.java
  WorkLocationRepository.java
  JdbcWorkLocationRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/security/crypto/
  PlatformCryptographyService.java
  EncryptedValue.java
  BlindIndex.java
  KeyMaterialProvider.java
  EnvironmentKeyMaterialProvider.java
  JcePlatformCryptographyService.java

apps/sanad-platform/src/main/java/com/sanad/platform/integration/events/
  DomainEventEnvelope.java

apps/sanad-platform/src/main/java/com/sanad/platform/audit/
  PlatformAuditSink.java
  ExistingPlatformAuditSinkAdapter.java

apps/sanad-platform/src/main/java/com/sanad/platform/idempotency/
  RequestIdempotencyService.java
  IdempotencyBeginResult.java

apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/
  PlatformCountryRegistryIntegrationTest.java
  LegalEntityOrganizationEligibilityIntegrationTest.java
  PlatformPrerequisiteRlsIntegrationTest.java
  PlatformCryptographyServiceTest.java
  HrmSharedContractArchitectureTest.java
```

### Task 1: Create country, Legal Entity, eligibility and Work Location schema

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260827_1__create_hrm_platform_country_and_employer_prerequisites.sql`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/PlatformCountryRegistryIntegrationTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/LegalEntityOrganizationEligibilityIntegrationTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/PlatformPrerequisiteRlsIntegrationTest.java`

**Interfaces:**
- Produces tables: `platform_countries`, `legal_entities`, `organization_legal_entities`, `work_locations`.
- Produces invariant: one effective eligibility interval per LegalEntity/Organization pair at a time.

- [ ] **Step 1: Write failing PostgreSQL integration assertions for the tables and key constraints**

Create tests that assert at minimum:

```java
assertThat(tableExists("platform_countries")).isTrue();
assertThat(tableExists("legal_entities")).isTrue();
assertThat(tableExists("organization_legal_entities")).isTrue();
assertThat(tableExists("work_locations")).isTrue();
```

and an overlap test:

```java
assertThatThrownBy(() -> jdbc.update("""
    INSERT INTO organization_legal_entities
      (id, tenant_id, organization_id, legal_entity_id, effective_from, effective_to, status)
    VALUES (gen_random_uuid(), ?, ?, ?, DATE '2026-06-01', NULL, 'ACTIVE')
    """, tenantId, organizationId, legalEntityId))
    .hasMessageContaining("organization_legal_entities");
```

The fixture first inserts an existing active interval beginning `2026-01-01` for the same pair.

- [ ] **Step 2: Run the focused tests and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest,PlatformPrerequisiteRlsIntegrationTest \
  test
```

Expected: FAIL because the new tables do not exist.

- [ ] **Step 3: Implement the Flyway migration**

Use this schema shape:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE platform_countries (
    country_code CHAR(2) PRIMARY KEY,
    name_en VARCHAR(120) NOT NULL,
    name_ar VARCHAR(120) NOT NULL,
    default_locale VARCHAR(20),
    default_currency CHAR(3),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_platform_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_platform_country_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE legal_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    registered_country_code CHAR(2) NOT NULL REFERENCES platform_countries(country_code),
    statutory_country_code CHAR(2) NOT NULL REFERENCES platform_countries(country_code),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_legal_entities_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_legal_entity_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

CREATE TABLE organization_legal_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    legal_entity_id UUID NOT NULL REFERENCES legal_entities(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_org_legal_entity_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_org_legal_entity_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

ALTER TABLE organization_legal_entities
ADD CONSTRAINT ex_org_legal_entity_no_overlap
EXCLUDE USING gist (
    tenant_id WITH =,
    organization_id WITH =,
    legal_entity_id WITH =,
    daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
) WHERE (status = 'ACTIVE');

CREATE TABLE work_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    country_code CHAR(2) NOT NULL REFERENCES platform_countries(country_code),
    city VARCHAR(120),
    timezone VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_work_locations_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_work_location_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);
```

Seed only GCC country master rows (`SA`, `AE`, `QA`, `BH`, `KW`, `OM`) with names/currency/locale metadata. Country Pack certification is not seeded here.

- [ ] **Step 4: Add fail-closed RLS to tenant-owned tables**

For each of `legal_entities`, `organization_legal_entities`, and `work_locations`:

```sql
ALTER TABLE legal_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_entities FORCE ROW LEVEL SECURITY;
CREATE POLICY legal_entities_tenant_isolation ON legal_entities
USING (tenant_id::text = current_setting('app.tenant_id', true))
WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
```

Repeat with table-specific policy names. No `IS NULL OR` clause is permitted.

- [ ] **Step 5: Re-run schema tests and confirm GREEN**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest,PlatformPrerequisiteRlsIntegrationTest \
  test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_1__create_hrm_platform_country_and_employer_prerequisites.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform
git commit -m "feat(platform): add HRM country and employer prerequisites"
```

### Task 2: Implement country registry and Legal Entity/Work Location repositories

**Files:**
- Create the `globalization/country`, `organization/legalentity`, and `organization/worklocation` Java files listed in File Structure.
- Modify only existing Organization repository/service code if needed to validate tenant ownership; do not change Organization meaning.
- Test: `PlatformCountryRegistryIntegrationTest.java`
- Test: `LegalEntityOrganizationEligibilityIntegrationTest.java`

**Interfaces:**
- Produces: `CountryCode.of(String)`.
- Produces: `PlatformCountryService.requireActive(String countryCode)`.
- Produces: `LegalEntityService.requireActive(UUID tenantId, UUID legalEntityId)`.
- Produces: `LegalEntityService.requireOrganizationEligibility(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate)`.
- Produces: `WorkLocationRepository.findByTenantIdAndId(UUID tenantId, UUID id)`.

- [ ] **Step 1: Write failing service tests for normalization and eligibility**

```java
@Test
void countryCodeNormalizesAndRejectsInvalidInput() {
    assertThat(CountryCode.of(" sa ").value()).isEqualTo("SA");
    assertThatThrownBy(() -> CountryCode.of("SAU"))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void eligibilityMustBeEffectiveOnRequestedDate() {
    assertThat(service.isOrganizationEligible(tenantId, legalEntityId, organizationId,
        LocalDate.of(2026, 8, 27))).isTrue();
    assertThat(service.isOrganizationEligible(tenantId, legalEntityId, organizationId,
        LocalDate.of(2025, 8, 27))).isFalse();
}
```

- [ ] **Step 2: Run tests and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest \
  test
```

- [ ] **Step 3: Implement `CountryCode` and country repository/service**

```java
public record CountryCode(String value) {
    public CountryCode {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("country code must be ISO alpha-2");
        }
        value = normalized;
    }

    public static CountryCode of(String raw) {
        return new CountryCode(raw);
    }
}
```

`PlatformCountryService.requireActive()` must fail if the registry row is absent/inactive; a platform-admin registration path can add any valid ISO code later without changing HR core.

- [ ] **Step 4: Implement Legal Entity and eligibility reads with tenant checks**

Repository query shape:

```sql
SELECT 1
FROM organization_legal_entities
WHERE tenant_id = :tenantId
  AND legal_entity_id = :legalEntityId
  AND organization_id = :organizationId
  AND status = 'ACTIVE'
  AND effective_from <= :effectiveDate
  AND (effective_to IS NULL OR effective_to >= :effectiveDate)
```

Every service method accepts tenant ID explicitly and verifies referenced Organization belongs to the same Tenant.

- [ ] **Step 5: Implement Work Location repository**

Expose tenant-scoped reads and create/update methods; do not add an HR-owned work-location duplicate.

- [ ] **Step 6: Run focused tests**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest \
  test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/globalization \
  apps/sanad-platform/src/main/java/com/sanad/platform/organization/legalentity \
  apps/sanad-platform/src/main/java/com/sanad/platform/organization/worklocation \
  apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform
git commit -m "feat(platform): expose country employer and work-location masters"
```

### Task 3: Add the Platform Cryptography contract and implementation

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/security/crypto/PlatformCryptographyService.java`
- Create: `.../EncryptedValue.java`
- Create: `.../BlindIndex.java`
- Create: `.../KeyMaterialProvider.java`
- Create: `.../EnvironmentKeyMaterialProvider.java`
- Create: `.../JcePlatformCryptographyService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/PlatformCryptographyServiceTest.java`
- Modify: `apps/sanad-platform/src/main/resources/application-local.yml`
- Modify: `apps/sanad-platform/src/main/resources/application-prod.yml`

**Interfaces:**
- Produces:

```java
EncryptedValue encrypt(UUID tenantId, String purpose, String plaintext);
String decrypt(UUID tenantId, String purpose, EncryptedValue value);
BlindIndex blindIndex(UUID tenantId, String purpose, String normalizedValue);
```

- `EncryptedValue` includes `ciphertext`, `keyVersion`, `algorithm`.
- `BlindIndex` includes `value`, `keyVersion`, `algorithm`.

- [ ] **Step 1: Write cryptography tests before implementation**

```java
@Test
void encryptionIsRandomizedButDecrypts() {
    EncryptedValue a = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER", "1234567890");
    EncryptedValue b = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER", "1234567890");
    assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    assertThat(crypto.decrypt(tenantId, "HR_PERSON_IDENTIFIER", a)).isEqualTo("1234567890");
}

@Test
void blindIndexIsDeterministicWithinTenantAndDifferentAcrossTenants() {
    BlindIndex a1 = crypto.blindIndex(tenantA, "NATIONAL_ID", "1234567890");
    BlindIndex a2 = crypto.blindIndex(tenantA, "NATIONAL_ID", "1234567890");
    BlindIndex b = crypto.blindIndex(tenantB, "NATIONAL_ID", "1234567890");
    assertThat(a1.value()).isEqualTo(a2.value());
    assertThat(a1.value()).isNotEqualTo(b.value());
}
```

- [ ] **Step 2: Run and verify RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformCryptographyServiceTest test
```

- [ ] **Step 3: Implement AES-256-GCM encrypted values**

`JcePlatformCryptographyService` uses a new random 12-byte nonce per encryption and authenticated associated data containing tenant ID + purpose + key version. Store an encoded payload such as `enc:v1:<base64 nonce+ciphertext+tag>` inside `EncryptedValue.ciphertext()`.

Core implementation shape:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
byte[] nonce = new byte[12];
secureRandom.nextBytes(nonce);
cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
cipher.updateAAD((tenantId + "|" + purpose + "|" + keyVersion).getBytes(UTF_8));
byte[] encrypted = cipher.doFinal(plaintext.getBytes(UTF_8));
```

- [ ] **Step 4: Implement a separate HMAC-SHA-256 blind-index key path**

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(blindIndexKey);
byte[] digest = mac.doFinal((tenantId + "|" + purpose + "|" + normalizedValue).getBytes(UTF_8));
```

Never use the encryption key as the blind-index key.

- [ ] **Step 5: Implement versioned deployment-secret key provider**

`EnvironmentKeyMaterialProvider` reads key versions and base64-encoded 32-byte material from configuration variable names, not committed values. Production must fail the cryptographic operation if required material is absent. Local/test may use explicitly test-scoped ephemeral material; it must not write the generated key to disk/logs.

Configuration keys:

```yaml
sanad:
  security:
    crypto:
      encryption-key-version: ${HRM_PII_ENCRYPTION_KEY_VERSION:v1}
      encryption-key: ${HRM_PII_ENCRYPTION_KEY:}
      blind-index-key-version: ${HRM_PII_BLIND_INDEX_KEY_VERSION:v1}
      blind-index-key: ${HRM_PII_BLIND_INDEX_KEY:}
```

Do not provide committed secret defaults.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformCryptographyServiceTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/security/crypto \
  apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/PlatformCryptographyServiceTest.java \
  apps/sanad-platform/src/main/resources/application-local.yml \
  apps/sanad-platform/src/main/resources/application-prod.yml
git commit -m "feat(security): add platform cryptography contract"
```

Expected: tests PASS and no secret value appears in git diff.

### Task 4: Define reusable event, audit-sink and idempotency contracts

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/integration/events/DomainEventEnvelope.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/audit/PlatformAuditSink.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/audit/ExistingPlatformAuditSinkAdapter.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/idempotency/RequestIdempotencyService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/idempotency/IdempotencyBeginResult.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/HrmSharedContractArchitectureTest.java`

**Interfaces:**

```java
public record DomainEventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    UUID tenantId,
    UUID organizationId,
    UUID actorUserId,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    String idempotencyKey,
    String dataClassification,
    JsonNode payload
) {}
```

```java
public interface PlatformAuditSink {
    void accept(AuditSinkRecord record);
}
```

```java
public interface RequestIdempotencyService {
    IdempotencyBeginResult begin(UUID tenantId, UUID principalId, String operation,
                                 String idempotencyKey, String requestFingerprint);
    void complete(UUID operationId, int statusCode, String responseBody);
    void fail(UUID operationId);
}
```

- [ ] **Step 1: Write ArchUnit tests that prevent HR contracts from importing CRM implementation packages**

```java
noClasses().that().resideInAPackage("..hr..")
    .should().dependOnClassesThat().resideInAnyPackage("..crm.idempotency..", "..crm.integration..")
    .check(importedClasses);
```

- [ ] **Step 2: Run and verify RED because shared contracts are absent**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrmSharedContractArchitectureTest test
```

- [ ] **Step 3: Implement the shared records/interfaces**

Keep interfaces transport/storage-neutral. Do not move CRM tables or workers. HR durable implementations arrive in WS4.

- [ ] **Step 4: Adapt the existing `PlatformAuditWriter` as the centralized sink**

`ExistingPlatformAuditSinkAdapter` translates a redacted `AuditSinkRecord` into `PlatformAuditWriter.writeSuccess/writeFailure`; it never receives raw PII secrets.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrmSharedContractArchitectureTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/integration/events \
  apps/sanad-platform/src/main/java/com/sanad/platform/audit \
  apps/sanad-platform/src/main/java/com/sanad/platform/idempotency \
  apps/sanad-platform/src/test/java/com/sanad/platform/hrmfoundation/platform/HrmSharedContractArchitectureTest.java
git commit -m "feat(platform): define HRM shared integration contracts"
```

### Task 5: WS1 verification gate

**Files:**
- Create: `docs/hrm/g0/evidence/01-platform-prerequisites.md`

**Interfaces:**
- Produces: `WS1_PLATFORM_PREREQUISITES=PASS|FAIL` evidence.

- [ ] **Step 1: Run all WS1 tests**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest,PlatformPrerequisiteRlsIntegrationTest,PlatformCryptographyServiceTest,HrmSharedContractArchitectureTest \
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Verify no country-law constants or secrets were introduced**

```bash
rg -n "probation|end.?of.?service|gosi|wps|saudization|HRM_PII_ENCRYPTION_KEY=.*[^}]|HRM_PII_BLIND_INDEX_KEY=.*[^}]" \
  apps/sanad-platform/src/main \
  --glob '!**/application-*.yml'
```

Expected: no statutory calculation rules in WS1 and no committed key values.

- [ ] **Step 3: Record evidence and commit**

Write the exact test command, exact SHA and outcome to `docs/hrm/g0/evidence/01-platform-prerequisites.md`, then:

```bash
git add docs/hrm/g0/evidence/01-platform-prerequisites.md
git commit -m "docs(hrm): record platform prerequisite evidence"
```

Expected verdict: `WS1_PLATFORM_PREREQUISITES=PASS` only if all focused tests pass.
