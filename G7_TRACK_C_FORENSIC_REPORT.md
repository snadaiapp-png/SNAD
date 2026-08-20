# G7 Track C — Forensic Investigation Report

> **Generated:** 2026-08-11
> **Purpose:** Actual file/class/schema evidence for Track C items C1-C5
> **Method:** Forensic codebase investigation — NO assumptions, NO repeats

---

## C1: Multi-Device Conflict — Actual Behavior

### Scenario Under Test

```
Device A reads Contact at version 5
Device B reads Contact at version 5
Device B pushes update first → version becomes 6
Device A pushes update (base_version=5, server is now version 6)
```

### Evidence File 1: JDBC Repository UPDATE Pattern

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcContactRepository.java`
**Lines:** 131-166

```java
int changed = jdbc.update(
    "UPDATE crm_contacts SET ... version = version + 1 " +
    "WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion", ...);
if (changed == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
```

**What this proves:** When Device A pushes with `expectedVersion=5` but the row is now version 6, the WHERE clause matches 0 rows. The `if (changed == 0)` block fires. `CRM_CONCURRENCY_CONFLICT` is thrown.

### Evidence File 2: Error Code Definition

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/CrmErrorCode.java`
**Line:** 63

```java
CRM_CONCURRENCY_CONFLICT(412, "The resource was modified by another operation. Please refresh and retry.", true),
```

**What this proves:**
- HTTP status: **412 Precondition Failed**
- Message: "The resource was modified by another operation. Please refresh and retry."
- Retryable: **true**

### Evidence File 3: Exception Handler

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/CrmExceptionHandler.java`
**Lines:** 89-95, 97-99

```java
@ExceptionHandler(CrmContractException.class)
public ResponseEntity<CrmErrorResponse> handleContract(CrmContractException ex, WebRequest request) {
    UUID requestId = resolveRequestId(request);
    CrmErrorResponse body = CrmErrorResponse.of(ex.code(), ex.userMessage(), requestId);
    return ResponseEntity.status(ex.code().httpStatus()).body(body);
}

@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<CrmErrorResponse> handleOptimisticLock(...) {
    return simple(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, request, ex);
}
```

**What this proves:** Both `CrmContractException` and Spring's `OptimisticLockingFailureException` map to the same HTTP 412 response.

### Evidence File 4: ETag Service — Stale Validation

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/concurrency/ETagService.java`
**Lines:** 64-80

```java
public void validateIfMatch(String ifMatchHeader, String entityType, UUID id, long currentVersion) {
    if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
        throw new CrmContractException(CrmErrorCode.CRM_PRECONDITION_REQUIRED);
    }
    String[] candidates = ifMatchHeader.split(",");
    String current = etag(entityType, id, currentVersion);
    for (String candidate : candidates) {
        String trimmed = candidate.trim();
        if ("*".equals(trimmed) || current.equals(trimmed)) {
            return;
        }
    }
    throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, ...);
}
```

**What this proves:** When Device A sends `If-Match: "contact-<id>-v5-<hex>"` but current version is 6, the ETag comparison fails and `CRM_CONCURRENCY_CONFLICT` is thrown. Missing header → HTTP 428. Stale header → HTTP 412.

### Evidence File 5: CrmConcurrencyContractTest — Stale Scenario

**File:** `apps/sanad-platform/src/test/java/com/sanad/platform/crm/contract/CrmConcurrencyContractTest.java`
**Lines:** 76-85

```java
String staleEtag = etags.etag("account", id, 4L);
long currentVersion = 5L;
CrmContractException ex = assertThrows(CrmContractException.class,
    () -> etags.validateIfMatch(staleEtag, "account", id, currentVersion));
assertEquals(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, ex.code());
assertEquals(412, CrmErrorCode.CRM_CONCURRENCY_CONFLICT.httpStatus());
```

**What this proves:** Stale ETag → `CRM_CONCURRENCY_CONFLICT` → HTTP 412. This is the exact Device A scenario.

### Evidence File 6: Concurrency Test — Two Threads, One Winner

**File:** `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/infrastructure/OwnershipPersistenceConcurrencyPostgresTest.java`
**Lines:** 149-179

```java
CyclicBarrier barrier = new CyclicBarrier(2);
ExecutorService executor = Executors.newFixedThreadPool(2);
Future<Boolean> first = executor.submit(() -> claimTask(barrier, firstUser));
Future<Boolean> second = executor.submit(() -> claimTask(barrier, secondUser));
assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
assertThat(activeAssignmentCount(recordId)).isEqualTo(1);
```

**What this proves:** 2 concurrent claims on the same entity → exactly 1 winner, 1 loser. After both complete, exactly 1 active assignment exists.

### Evidence File 7: Ownership AOP — Pessimistic Lock

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/CrmOwnershipAtomicIfMatchAspect.java`
**Lines:** 96-109

```java
private Instant lockUpdatedAt(LockTarget target, UUID tenantId) {
    String sql = "SELECT updated_at FROM " + target.table()
            + " WHERE tenant_id=:tenantId AND id=:id FOR UPDATE";
    ...
}
```

**What this proves:** For ownership endpoints, the second request **blocks** at the database level (`SELECT ... FOR UPDATE`) until the first transaction commits. Then the ETag check fails with HTTP 412.

### Evidence File 8: Idempotency — Different Keys = Independent

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/idempotency/JdbcIdempotencyService.java`
**Lines:** 65 (UNIQUE constraint), 84-86 (race handling)

```sql
UNIQUE (tenant_id, principal_id, endpoint, idempotency_key)
```

**What this proves:** Different idempotency keys → both processed independently. Same key + same payload → second gets cached response. Same key + different payload → HTTP 409 `CRM_IDEMPOTENCY_CONFLICT`.

### Evidence File 9: All 20 CRM_CONCURRENCY_CONFLICT Throw Sites

| # | File | Line | Operation |
|---|------|------|-----------|
| 1 | `JdbcContactRepository.java` | 166 | update |
| 2 | `JdbcContactRepository.java` | 199 | setLifecycle |
| 3 | `JdbcAccountRepository.java` | 105 | update |
| 4 | `JdbcAccountRepository.java` | 119 | archive |
| 5 | `JdbcAccountRepository.java` | 133 | restore |
| 6 | `JdbcLeadRepository.java` | 67 | changeStatus |
| 7 | `JdbcLeadRepository.java` | 85 | convert |
| 8 | `JdbcOpportunityRepository.java` | 45 | update |
| 9 | `JdbcOpportunityRepository.java` | 51 | moveStage |
| 10 | `JdbcActivityRepository.java` | 47 | update |
| 11 | `JdbcActivityRepository.java` | 53 | complete |
| 12 | `JdbcPipelineRepository.java` | 57 | update |
| 13 | `JdbcCaseRepository.java` | 141 | update |
| 14 | `JdbcCaseRepository.java` | 162 | start |
| 15 | `JdbcCaseRepository.java` | 185 | resolve |
| 16 | `JdbcCaseRepository.java` | 206 | close |
| 17 | `JdbcCaseRepository.java` | 228 | reopen |
| 18 | `JdbcCaseRepository.java` | 245 | assign |
| 19 | `JdbcCustomFieldRepository.java` | 42 | update |
| 20 | `ETagService.java` | 77 | stale If-Match |

### C1 Finding

When Device A and Device B both push mutations based on version 5:

| Step | Device A | Device B |
|------|----------|----------|
| 1 | Reads version 5 | Reads version 5 |
| 2 | Sends update (v5) | Sends update (v5) |
| 3 | — | SQL `WHERE version=5` matches 1 row → version becomes 6 → **HTTP 200** |
| 4 | SQL `WHERE version=5` matches 0 rows → `CRM_CONCURRENCY_CONFLICT` thrown → **HTTP 412** | — |
| 5 | Response: `code: "CRM_CONCURRENCY_CONFLICT"`, `retryable: true` | — |

**Device A must:** re-fetch the entity (now version 6 with Device B's changes), merge changes if needed, and resubmit with the new version.

### C1 Decision

```
C1_STATUS = DEFINED
C1_BEHAVIOR = HTTP 412 + CRM_CONCURRENCY_CONFLICT + retryable
C1_RETRY = Client re-fetches entity, merges changes, resubmits with new version
C1_IDEMPOTENCY = Different idempotency keys → both processed; same key → deduped
C1_LOCKING = Ownership endpoints: SELECT FOR UPDATE (blocks second request)
             Entity endpoints: no blocking, immediate 412
C1_BLOCKER = NO — behavior is fully defined and tested
```

---

## C2: Offline Duration Requirements

### Evidence File 1: Access Token TTL

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/config/SecurityProperties.java`
**Line:** 43

```java
private Duration accessTokenTtl = Duration.ofMinutes(15);
```

**File:** `apps/sanad-platform/src/main/resources/application.yml`
**Line:** 100

```yaml
access-token-ttl: 15m
```

**What this proves:** Access tokens expire after 15 minutes. If a device is offline for >15 minutes, the access token is stale. The device must use the refresh token to get a new access token.

### Evidence File 2: Refresh Token TTL

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/config/SecurityProperties.java`
**Lines:** 60-61

```java
private Duration refreshTokenTtl = Duration.ofDays(7);
```

**File:** `apps/sanad-platform/src/main/resources/application.yml`
**Line:** 103

```yaml
refresh-token-ttl: 168h
```

**What this proves:** Refresh tokens expire after 7 days. If a device is offline for >7 days, the refresh token is stale. The user must re-authenticate.

### Evidence File 3: Refresh Token Expiration Enforcement

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/domain/RefreshToken.java`
**Lines:** 113-116

```java
public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
}
```

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java`
**Lines:** 236-239

```java
if (refreshToken.isExpired()) {
    refreshToken.setStatus(RefreshTokenStatus.REVOKED);
    refreshTokenRepository.save(refreshToken);
    throw new InvalidCredentialsException("رمز التحديث منتهي الصلاحية");
}
```

**What this proves:** Expired refresh tokens are actively revoked on attempted use. There is no grace period.

### Evidence File 4: Session Version for Immediate Revocation

**File:** `apps/sanad-platform/src/main/resources/db/migration/V13__add_session_version.sql`
**Lines:** 14-15

```sql
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
```

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/filter/SessionVersionCache.java`
**Line:** 37

```java
static final long TTL_SECONDS = 5L;
```

**What this proves:** Session version lookups are cached for 5 seconds. Logout/password change propagates within 5 seconds. A device offline for >5 seconds may have a revoked token that it doesn't know about yet.

### Evidence File 5: Idempotency Record Retention

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/idempotency/IdempotencyService.java`
**Line:** 42

```java
Duration DEFAULT_RETENTION = Duration.ofHours(24);
```

**What this proves:** Idempotency records are retained for 24 hours. After 24 hours, the same idempotency key is treated as a new operation (not a replay). If a device is offline for >24 hours, its pending idempotency keys are purged.

### Evidence File 6: No Mobile-Specific Offline Duration Configuration

Searched for: `offline`, `stale.*client`, `max.*offline`, `full.*resync`, `sync.*expire`, `device.*ttl`

**Result:** No mobile-specific offline duration configuration exists in the codebase.

### C2 Finding

| Parameter | Value | Source | Mobile-Specific? |
|-----------|-------|--------|-----------------|
| Access token TTL | 15 minutes | `SecurityProperties.java:43` | NO — server-wide |
| Refresh token TTL | 7 days | `SecurityProperties.java:60` | NO — server-wide |
| Session version cache | 5 seconds | `SessionVersionCache.java:37` | NO — server-wide |
| Idempotency retention | 24 hours | `IdempotencyService.java:42` | NO — server-wide |
| Password reset TTL | 30 minutes | `AuthService.java:55` | NO — server-wide |
| Mobile offline duration limit | **NOT DEFINED** | — | **TRACK C** |
| Full re-sync trigger | **NOT DEFINED** | — | **TRACK C** |
| Stale client detection | **NOT DEFINED** | — | **TRACK C** |

**The codebase has NO mobile-specific offline duration requirements.** The only duration constraints are server-wide (15m access token, 7d refresh token, 24h idempotency retention).

### C2 Decision

```
C2_STATUS = UNDEFINED
C2_EVIDENCE = NOT_FOUND for mobile-specific offline duration
C2_EXISTING = 15m access token, 7d refresh token, 24h idempotency retention (server-wide)
C2_IMPLICATION = Mobile device offline > 7 days → forced re-authentication
                 Mobile device offline > 24 hours → pending idempotency keys purged
                 Mobile device offline > 15 minutes → access token stale (refresh needed)
C2_BLOCKER = YES — mobile-specific offline duration thresholds must be defined before G7 implementation
```

---

## C3: Conflict SLA

### Evidence File 1: No Conflict Log Table Exists

**File:** `apps/sanad-platform/src/main/resources/db/migration/` — all Flyway migrations

Searched for: `mobile_conflict_log`, `conflict_log`, `conflict.*table`

**Result:** No migration creates a `mobile_conflict_log` table. The `mobile_conflict_log` schema is proposed in `ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md` (line 413) but NOT implemented.

### Evidence File 2: No Conflict Resolution Timeout

Searched for: `conflict.*expir`, `conflict.*timeout`, `conflict.*retention`, `resolution.*deadline`, `unresolved`, `conflict.*purge`, `conflict.*cleanup`

**Result:** No conflict resolution timeout, expiration, retention, or cleanup configuration exists.

### Evidence File 3: No Scheduled Cleanup Jobs

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/config/SchedulingConfig.java`
**Lines:** 16-18

```java
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true")
@EnableScheduling
```

**What this proves:** Scheduling is disabled by default. The only scheduled job is `CustomerRescoringJob` (intelligence rescoring). No conflict cleanup/purge jobs exist.

### Evidence File 4: Audit Trail Is Append-Only

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/PlatformAuditWriter.java`

```java
public void write(...) {
    jdbc.update("INSERT INTO platform_audit_logs ...");
}
```

**What this proves:** Audit logs are append-only. No cleanup/purge mechanism exists. Audit records accumulate indefinitely.

### C3 Finding

| Parameter | Status | Evidence |
|-----------|--------|----------|
| `mobile_conflict_log` table | **NOT CREATED** | No migration exists |
| Conflict resolution timeout | **NOT DEFINED** | No config found |
| Conflict expiration/purge | **NOT DEFINED** | No config found |
| Unresolved conflict handling | **NOT DEFINED** | No config found |
| Conflict retention policy | **PROPOSED** (1 year) | `ADR-G7-001` line 374 |
| Resolution deadline | **NOT DEFINED** | No config found |
| Cleanup scheduled job | **NOT EXISTS** | `SchedulingConfig.java` |

### C3 Decision

```
C3_STATUS = UNDEFINED
C3_EVIDENCE = NOT_FOUND for any conflict SLA configuration
C3_PROPOSED = 1-year retention (ADR-G7-001, not implemented)
C3_IMPLICATION = No conflict log exists → no SLA applies → no conflicts are tracked
C3_BLOCKER = YES — conflict log table, SLA, retention, and cleanup must be defined before G7 implementation
```

---

## C4: Cross-Entity Relationships

### Evidence File 1: Account → Contact FK Constraint

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_1__create_unified_crm_core.sql`
**Lines:** 38-70

```sql
CONSTRAINT fk_crm_contacts_account_same_tenant
    FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
```

**No ON DELETE / ON UPDATE clause** → defaults to `NO ACTION` (same as RESTRICT).

**What this proves:** Physical Account deletion blocked if Contacts exist. Soft-archiving an Account does NOT affect Contacts.

### Evidence File 2: Account → Opportunity FK Constraint

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_1__create_unified_crm_core.sql`
**Lines:** 140-171

```sql
CONSTRAINT fk_crm_opportunities_account_same_tenant
    FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
```

**No ON DELETE clause** → `NO ACTION`.

**What this proves:** Physical Account deletion blocked if Opportunities exist. Soft-archiving does NOT affect Opportunities.

### Evidence File 3: Opportunity → Activity — Polymorphic (No FK)

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_1__create_unified_crm_core.sql`
**Lines:** 195-222

```sql
related_type VARCHAR(40),
related_id UUID,
```

**NO FOREIGN KEY** on `crm_activities.related_id`. This is a polymorphic association.

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260807_4__add_activity_result_column_and_related_type_check.sql`
**Lines:** 10-12

```sql
CHECK (related_type IS NULL OR related_type IN
    ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','OTHER'))
```

**What this proves:** Archiving/deleting an Opportunity does NOT affect Activities. Activities can become orphaned (pointing to non-existent entity).

### Evidence File 4: Opportunity → Task — Polymorphic (No FK)

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260716_1__create_crm_tasks.sql`
**Lines:** 19-61

```sql
related_type VARCHAR(40),
related_id UUID,
CONSTRAINT ck_crm_tasks_related_type CHECK (
    related_type IS NULL OR related_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY')
),
```

**NO FOREIGN KEY** on `crm_tasks.related_id`. Same polymorphic pattern as Activities.

### Evidence File 5: Pipeline → Opportunity FK Constraint

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_1__create_unified_crm_core.sql`
**Lines:** 160-167

```sql
CONSTRAINT fk_crm_opportunities_pipeline_same_tenant
    FOREIGN KEY (tenant_id, pipeline_id) REFERENCES crm_pipelines (tenant_id, id),
```

**No ON DELETE clause** → `NO ACTION`. Physical Pipeline deletion blocked if Opportunities exist.

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/opportunity/infrastructure/JdbcPipelineRepository.java`
**Lines:** 100-104

```java
public void deleteStage(UUID tenantId, UUID stageId) {
    jdbc.update("UPDATE crm_pipeline_stages SET active = FALSE WHERE tenant_id = :tenantId AND id = :stageId", ...);
}
```

**What this proves:** Stages are soft-deleted (`active=FALSE`). No physical deletion.

### Evidence File 6: No Aggregate Root Pattern

Each entity type has independent:
- Domain model (e.g., `AccountRecord`, `ContactRecord`)
- Repository interface (e.g., `AccountRepository`, `ContactRepository`)
- JDBC implementation (e.g., `JdbcAccountRepository`, `JdbcContactRepository`)
- Use-case service (e.g., `AccountUseCases`, `ContactUseCases`)

**No entity owns a collection of children.** No cascading lifecycle management.

### Evidence File 7: Cross-Entity Transactions

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/lead/application/LeadConversionUseCases.java`
**Lines:** 62-148

```java
@Transactional
public void convert(...) {
    accountUseCases.create(...);
    contactUseCases.create(...);
    opportunityUseCases.create(...);
    leadUseCases.convert(...);
    // audit trail
}
```

**What this proves:** Lead conversion creates Account + Contact + Opportunity + Lead conversion in ONE transaction. If any step fails, the entire conversion rolls back.

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/TransferUseCases.java`
**Lines:** 206-229

```java
@Transactional
public void submit(...) {
    assertSourceOwnershipUnchanged(request); // stale source detection
    ownershipCommands.transfer(...); // modifies crm_assignments + entity owner columns
    // update transfer request state
}
```

**What this proves:** Ownership transfer modifies multiple tables in one transaction.

### Evidence File 8: Concurrent Modification Detection

**Scenario: Device A modifies Account.owner while Device B adds a Contact to that Account.**

1. **Device A** changes Account owner → goes through `OwnershipCommandUseCases.reassign()` → version-check on Account + `SELECT FOR UPDATE` on assignment → one transaction.
2. **Device B** adds Contact → `ContactUseCases.create()` → writes to `crm_contacts` with `account_id` FK → separate transaction.
3. **Detection:** The two operations operate on **different rows and different tables**. No cross-entity conflict detection for this specific scenario. The only conflict detected is when both devices modify the **same field on the same entity**.

### C4 Finding

| Relationship | FK Constraint | ON DELETE | Cascade? | Conflict Detection |
|-------------|---------------|-----------|----------|-------------------|
| Account → Contact | YES | NO ACTION | NO | Version-based on Account only |
| Account → Opportunity | YES | NO ACTION | NO | Version-based on Account only |
| Opportunity → Activity | **NO FK** | — | NO | None (polymorphic) |
| Opportunity → Task | **NO FK** | — | NO | None (polymorphic) |
| Pipeline → Opportunity | YES | NO ACTION | NO | Version-based on Pipeline only |
| Pipeline → PipelineStage | YES | NO ACTION | NO | Soft-delete only |

**No cross-entity conflict detection exists.** Conflicts are detected only at the individual entity level (version mismatch on the same row). Two devices modifying different entities in a related set will never trigger a conflict.

### C4 Decision

```
C4_STATUS = DEFINED
C4_EVIDENCE = FK constraints, polymorphic references, transaction boundaries confirmed
C4_CROSS_ENTITY_CONFLICT = NOT DETECTED (by design — entity-level versioning only)
C4_CASCADE = NO (all ON DELETE NO ACTION, no application-level cascade)
C4_POLYMORPHIC = Opportunity→Activity and Opportunity→Task are polymorphic (no FK)
C4_MULTI_ENTITY_TX = LeadConversionUseCases.convert() creates 4 entities in one transaction
C4_BLOCKER = NO — cross-entity behavior is understood; no additional design needed for G7
```

---

## C5: Custom Fields Implementation

### Evidence File 1: Definition Table Schema

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_1__create_unified_crm_core.sql`
**Lines:** 259-275

```sql
CREATE TABLE crm_custom_field_definitions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    field_key VARCHAR(120) NOT NULL,
    label_ar VARCHAR(240) NOT NULL,
    label_en VARCHAR(240) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_custom_field_definitions PRIMARY KEY (id),
    CONSTRAINT fk_crm_custom_field_definitions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_crm_custom_field_definitions UNIQUE (tenant_id, entity_type, field_key)
);
```

### Evidence File 2: Version Column Added Later

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_1__reconcile_crm_custom_field_and_pipeline_audit_columns.sql`
**Lines:** 87-96

```sql
ALTER TABLE crm_custom_field_definitions ADD COLUMN IF NOT EXISTS version    BIGINT                   NOT NULL DEFAULT 0;
ALTER TABLE crm_custom_field_definitions ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE crm_custom_field_definitions ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE crm_custom_field_definitions ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE;
```

**What this proves:** Version column was MISSING in the original schema and was added later to fix `BadSqlGrammarException` defects. Pre-existing rows get `version=0`.

### Evidence File 3: Type Constraints

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_3__complete_crm_imports_custom_fields.sql`
**Lines:** 58-65

```sql
ALTER TABLE crm_custom_field_definitions
    ADD CONSTRAINT ck_crm_custom_field_data_type
        CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','DATE','DATETIME','EMAIL','URL'));
ALTER TABLE crm_custom_field_definitions
    ADD CONSTRAINT ck_crm_custom_field_sensitive_searchable
        CHECK (NOT (sensitive = TRUE AND searchable = TRUE));
```

**What this proves:** 7 allowed types. Sensitive and searchable are mutually exclusive.

### Evidence File 4: Value Table Schema

**File:** `apps/sanad-platform/src/main/resources/db/migration/V20260702_3__complete_crm_imports_custom_fields.sql`
**Lines:** 67-103

```sql
CREATE TABLE crm_custom_field_values (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    value_text TEXT,
    value_number NUMERIC(38,12),
    value_boolean BOOLEAN,
    value_date DATE,
    value_timestamp TIMESTAMP WITH TIME ZONE,
    searchable_value VARCHAR(512),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_crm_custom_field_value_exactly_one CHECK (
        (CASE WHEN value_text IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_number IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_boolean IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_date IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_timestamp IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT fk_crm_custom_field_values_definition FOREIGN KEY (tenant_id, definition_id) REFERENCES crm_custom_field_definitions(tenant_id, id),
    CONSTRAINT uk_crm_custom_field_values UNIQUE (tenant_id, definition_id, entity_id)
);
```

**What this proves:**
- **Column-per-type pattern** (hybrid EAV) — not JSON storage
- **No version column** on values — no optimistic concurrency on individual value rows
- **Unique constraint** on `(tenant_id, definition_id, entity_id)` — one value per definition per entity
- **CHECK constraint** ensures exactly one typed column is non-NULL

### Evidence File 5: CRUD Operations

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/configuration/infrastructure/JdbcCustomFieldRepository.java`

**Create (line 26-31):**
```java
jdbc.update("INSERT INTO crm_custom_field_definitions
    (id,tenant_id,version,entity_type,field_key,label_ar,label_en,data_type,sensitive,searchable,required,active,created_by,updated_by,created_at,updated_at)
    VALUES (:id,:t,0,:entityType,:fieldKey,:labelAr,:labelEn,:dataType,:sensitive,:searchable,:required,TRUE,:actorId,:actorId,:now,:now)", ...);
```

**Update (lines 32-44):**
```java
sql.append(" WHERE tenant_id=:t AND id=:id AND version=:expectedVersion");
int updated = jdbc.update(sql.toString(), params);
if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
```

**Delete:** **DOES NOT EXIST.** No `delete` method in the repository interface. No DELETE endpoint in any controller.

### Evidence File 6: Value Upsert Pattern

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/LegacyCustomFieldService.java`
**Lines:** 169-230

```java
@Transactional
public void upsertCustomFieldValuesInternal(...) {
    for (String fieldKey : values.keySet()) {
        // DELETE existing value row for this (tenant_id, definition_id, entity_id)
        jdbc.update("DELETE FROM crm_custom_field_values WHERE ...");
        // INSERT new value row
        jdbc.update("INSERT INTO crm_custom_field_values ...");
    }
}
```

**What this proves:** Values are replaced via DELETE-then-INSERT, not SQL upsert. The entire batch is wrapped in a single `@Transactional`. `created_at` is always set to "now" even on updates.

### Evidence File 7: Type Cannot Be Changed After Creation

**File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/configuration/domain/CustomFieldRepository.java`
**Lines:** 20-21

```java
record UpdateCustomFieldCommand(String labelAr, String labelEn, Boolean sensitive, Boolean searchable, Boolean required) {}
```

**What this proves:** The update command only allows `labelAr`, `labelEn`, `sensitive`, `searchable`, `required`. `dataType` and `entityType` are NOT part of the update command. Type is immutable after creation.

### Evidence File 8: Custom Field Tests

**File:** `apps/sanad-platform/src/test/java/com/sanad/platform/crm/configuration/infrastructure/JdbcCustomFieldRepositoryPostgresTest.java`

| Test | What It Proves |
|------|---------------|
| `create_persistsDefinitionWithZeroVersionAndAuditColumns` | version starts at 0, audit columns populated |
| `findById_roundTripsCreatedDefinition` | round-trip create then read |
| `update_bumpsVersionAndMutatesProvidedFields` | optimistic lock increment, partial field update |
| `update_withStaleVersionThrowsConcurrencyConflict` | stale version → HTTP 412 |
| `findById_whenMissingThrowsNotFound` | missing ID → HTTP 404 |
| `findAll_isTenantScopedAndOptionallyFilteredByEntityType` | tenant isolation |

### Evidence File 9: API Endpoints

**v2 API (CrmContractControllerR1.java):**

| Method | Path | ETag/If-Match? | Idempotent? |
|--------|------|---------------|-------------|
| POST | `/api/v2/crm/custom-fields` | NO | YES |
| PATCH | `/api/v2/crm/custom-fields/{id}` | **YES** | NO |
| GET | `/api/v2/crm/custom-fields` | NO | YES |
| PUT | `/api/v2/crm/custom-fields/values/{entityType}/{entityId}` | NO | YES |
| POST | `/api/v2/crm/custom-fields/values/{entityType}/{entityId}` | NO | YES |
| GET | `/api/v2/crm/custom-fields/search` | NO | YES |

### C5 Conceptual Cases

| # | Case | Evidence | Behavior |
|---|------|----------|----------|
| 1 | Create definition with TEXT type | `JdbcCustomFieldRepository.create()` line 26 | INSERT with version=0, active=TRUE |
| 2 | Update definition label | `JdbcCustomFieldRepository.update()` line 32 | SET version=version+1 WHERE version=:expectedVersion |
| 3 | Concurrent update on definition | `JdbcCustomFieldRepositoryPostgresTest.update_withStaleVersionThrowsConcurrencyConflict` | HTTP 412 |
| 4 | Upsert value for existing entity | `LegacyCustomFieldService.upsertCustomFieldValuesInternal()` line 169 | DELETE old row, INSERT new row (single transaction) |
| 5 | Read values with sensitive field | `LegacyCustomFieldService` lines 232-256 | Sensitive fields show `[REDACTED]` for normal read, decrypted for `/sensitive` endpoint |
| 6 | Delete definition | **DOES NOT EXIST** | No delete operation anywhere in codebase |
| 7 | Change field type | **DOES NOT ALLOWED** | `UpdateCustomFieldCommand` has no `dataType` field |

### C5 Decision

```
C5_STATUS = DEFINED
C5_DEFINITIONS = version=0 on create, optimistic lock on update, NO delete operation
C5_VALUES = Column-per-type (hybrid EAV), DELETE-then-INSERT upsert, NO version column on values
C5_TYPES = 7 types (TEXT, NUMBER, BOOLEAN, DATE, DATETIME, EMAIL, URL) — immutable after creation
C5_SENSITIVE = Encrypted in value_text, mutually exclusive with searchable
C5_TENANT_ISOLATION = All queries scoped by tenant_id
C5_CONFLICT = Definitions have version-based conflict detection (HTTP 412)
              Values have NO conflict detection (atomic DELETE-then-INSERT)
C5_DELETE = Definitions cannot be deleted (no operation exists)
C5_BLOCKER = NO — custom field behavior is fully understood
```

---

## Mandatory Final Table

| Item | Status | Evidence Source | Blocker? |
|------|--------|----------------|----------|
| C1: Multi-device conflict | **DEFINED** | JdbcContactRepository:166, CrmErrorCode:63, ETagService:64, CrmConcurrencyContractTest:76, OwnershipPersistenceConcurrencyPostgresTest:149 | NO |
| C2: Offline duration | **UNDEFINED** | SecurityProperties:43,60 (server-wide only), no mobile-specific config found | **YES** |
| C3: Conflict SLA | **UNDEFINED** | No mobile_conflict_log table, no SLA config, no cleanup jobs | **YES** |
| C4: Cross-entity relationships | **DEFINED** | V20260702_1 FK constraints, V20260716_1 polymorphic refs, LeadConversionUseCases:62 | NO |
| C5: Custom fields | **DEFINED** | V20260702_1, V20260702_3, V20260804_1, JdbcCustomFieldRepository:26, LegacyCustomFieldService:169 | NO |

### Blocker Summary

**2 BLOCKERS (C2, C3) require architectural decisions before G7 implementation:**

1. **C2 — Offline Duration:** Must define mobile-specific thresholds for:
   - Maximum offline period before forced full re-sync
   - Stale client detection mechanism
   - Sync expiration after extended offline

2. **C3 — Conflict SLA:** Must define:
   - Conflict log table schema (currently proposed, not created)
   - Conflict resolution timeout
   - Unresolved conflict handling policy
   - Conflict retention and cleanup strategy

---

**END OF G7_TRACK_C_FORENSIC_REPORT**
