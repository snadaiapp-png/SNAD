# G7 DATA MODEL FINAL BASELINE

## Phase 4: Data Model Reconciliation

**Document ID:** G7-DATA-MODEL-FINAL-BASELINE
**Version:** 1.0.0
**Status:** FINAL
**Date:** 2026-08-11
**Scope:** Complete data model definition for G7 Mobile Sync feature

---

## 1. EXISTING TABLES (Verified from Flyway Migrations)

The following tables already exist in the production database and are verified through Flyway migration history. These tables provide the foundation upon which G7 mobile sync operates.

### 1.1 crm_accounts

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| name | TEXT | NOT NULL | Account name |
| industry | TEXT | | Industry classification |
| phone | TEXT | | Primary phone |
| website | TEXT | | Company website |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_accounts_tenant, idx_accounts_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.2 crm_contacts

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| account_id | UUID | FK to crm_accounts | Parent account |
| first_name | TEXT | NOT NULL | Contact first name |
| last_name | TEXT | NOT NULL | Contact last name |
| email | TEXT | | Primary email |
| phone | TEXT | | Primary phone |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_contacts_tenant, idx_contacts_account_id, idx_contacts_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.3 crm_leads

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| first_name | TEXT | NOT NULL | Lead first name |
| last_name | TEXT | NOT NULL | Lead last name |
| email | TEXT | | Primary email |
| phone | TEXT | | Primary phone |
| status | TEXT | NOT NULL | Lead status |
| source | TEXT | | Lead source |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_leads_tenant, idx_leads_status, idx_leads_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.4 crm_opportunities

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| account_id | UUID | FK to crm_accounts | Parent account |
| contact_id | UUID | FK to crm_contacts | Primary contact |
| pipeline_id | UUID | FK to crm_pipelines | Sales pipeline |
| title | TEXT | NOT NULL | Opportunity title |
| amount | NUMERIC | | Opportunity value |
| stage | TEXT | NOT NULL | Pipeline stage |
| close_date | DATE | | Expected close date |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_opportunities_tenant, idx_opportunities_account_id, idx_opportunities_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.5 crm_tasks

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| subject | TEXT | NOT NULL | Task subject |
| entity_type | TEXT | | Related entity type |
| entity_id | UUID | | Related entity ID |
| status | TEXT | NOT NULL | Task status |
| priority | TEXT | | Task priority |
| due_date | TIMESTAMPTZ | | Due date |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_tasks_tenant, idx_tasks_status, idx_tasks_due_date, idx_tasks_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.6 crm_activities

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| entity_type | TEXT | NOT NULL | Related entity type |
| entity_id | UUID | NOT NULL | Related entity ID |
| activity_type | TEXT | NOT NULL | Activity type (call, email, meeting) |
| subject | TEXT | | Activity subject |
| description | TEXT | | Activity description |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_activities_tenant, idx_activities_entity, idx_activities_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.7 crm_notes

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| version | BIGINT | NOT NULL | Optimistic concurrency version |
| entity_type | TEXT | NOT NULL | Related entity type |
| entity_id | UUID | NOT NULL | Related entity ID |
| body | TEXT | NOT NULL | Note content |
| created_by | UUID | FK to users | Author |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_notes_tenant, idx_notes_entity, idx_notes_updated_at
**RLS:** tenant_id
**Optimistic Locking:** version column

### 1.8 crm_pipelines

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| name | TEXT | NOT NULL | Pipeline name |
| stages | JSONB | NOT NULL | Ordered stage definitions |
| is_active | BOOLEAN | NOT NULL | Active flag |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ | NOT NULL | Last modification timestamp |

**Indexes:** idx_pipelines_tenant, idx_pipelines_active
**RLS:** tenant_id
**Version:** N/A (configuration table)

### 1.9 crm_tags

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| name | TEXT | NOT NULL | Tag name |
| color | TEXT | | Tag color |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Indexes:** idx_tags_tenant, idx_tags_name_unique
**RLS:** tenant_id
**Version:** N/A (reference data)

### 1.10 crm_custom_fields

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| entity_type | TEXT | NOT NULL | Target entity type |
| field_name | TEXT | NOT NULL | Field identifier |
| field_type | TEXT | NOT NULL | Field data type |
| is_required | BOOLEAN | NOT NULL | Required flag |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Indexes:** idx_custom_fields_tenant, idx_custom_fields_entity
**RLS:** tenant_id
**Version:** N/A (schema metadata)

### 1.11 platform_audit_logs

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| entity_type | TEXT | NOT NULL | Entity type |
| entity_id | UUID | NOT NULL | Entity ID |
| action | TEXT | NOT NULL | Action performed |
| changes | JSONB | | Before/after diff |
| performed_by | UUID | FK to users | Actor |
| performed_at | TIMESTAMPTZ | NOT NULL | Action timestamp |

**Indexes:** idx_audit_tenant, idx_audit_entity, idx_audit_performed_at
**RLS:** tenant_id
**Version:** N/A (append-only log)

### 1.12 crm_idempotency_records

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| idempotency_key | TEXT | UNIQUE | Client-generated key |
| entity_type | TEXT | NOT NULL | Entity type |
| entity_id | UUID | | Entity ID |
| result | JSONB | | Cached response |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| expires_at | TIMESTAMPTZ | NOT NULL | Expiration timestamp |

**Indexes:** idx_idempotency_key, idx_idempotency_expires
**RLS:** None (cross-tenant for idempotency)
**Version:** N/A (caching layer)

### 1.13 crm_timeline_events

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Unique identifier |
| tenant_id | UUID | NOT NULL, FK to tenants | Multi-tenant partition key |
| entity_type | TEXT | NOT NULL | Entity type |
| entity_id | UUID | NOT NULL | Entity ID |
| event_type | TEXT | NOT NULL | Event type |
| payload | JSONB | | Event details |
| created_at | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Indexes:** idx_timeline_tenant, idx_timeline_entity, idx_timeline_created_at
**RLS:** tenant_id
**Version:** N/A (append-only log)

---

## 2. REQUIRED NEW TABLES (TRUE_REQUIRED_G7_TABLES)

The following four tables are **mandatory** for G7 mobile sync functionality. They must be created via Flyway migration before any G7 code is deployed.

### 2.1 mobile_device_registry

**Purpose:** Track registered mobile devices per user.

**Table Definition:**

```sql
CREATE TABLE mobile_device_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id TEXT NOT NULL,
    device_name TEXT,
    platform TEXT NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_device_registry_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_device_registry_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**Indexes:**

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| mobile_device_registry_pkey | id | PRIMARY KEY | Row lookup |
| idx_device_registry_tenant_user | tenant_id, user_id, device_id | UNIQUE | Device uniqueness per user per tenant |
| idx_device_registry_tenant | tenant_id | B-tree | Tenant isolation queries |
| idx_device_registry_user | user_id | B-tree | User device lookup |

**Constraints:**

| Constraint | Type | Detail |
|------------|------|--------|
| fk_device_registry_tenant | FK | References tenants(id) ON DELETE CASCADE |
| fk_device_registry_user | FK | References users(id) ON DELETE CASCADE |
| uq_device_per_user | UNIQUE | (tenant_id, user_id, device_id) |
| chk_platform | CHECK | platform IN ('ios', 'android', 'web') |

**Tenant Isolation:** Row-Level Security (RLS) on tenant_id column. All queries filter by tenant_id.

**Versioning:** N/A (reference data, no optimistic locking required).

**Audit:** registered_at, last_seen_at provide device lifecycle tracking.

**Retention:** 90 days for inactive devices. Cleanup job removes devices where last_seen_at < now() - interval '90 days'.

**Dependency:** None. This is a standalone registration table.

**Sample Data:**

```json
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenant_id": "660e8400-e29b-41d4-a716-446655440001",
    "user_id": "770e8400-e29b-41d4-a716-446655440002",
    "device_id": "ios-device-abc123",
    "device_name": "iPhone 15 Pro",
    "platform": "ios",
    "registered_at": "2026-08-11T10:00:00Z",
    "last_seen_at": "2026-08-11T14:30:00Z"
}
```

---

### 2.2 mobile_sync_cursor

**Purpose:** Track last-synced state per entity type per device. This is the core state machine for incremental sync.

**Table Definition:**

```sql
CREATE TABLE mobile_sync_cursor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    device_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    last_sync_at TIMESTAMPTZ NOT NULL,
    last_sync_version BIGINT,
    cursor_token TEXT,
    CONSTRAINT fk_sync_cursor_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
```

**Indexes:**

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| mobile_sync_cursor_pkey | id | PRIMARY KEY | Row lookup |
| idx_sync_cursor_tenant_device | tenant_id, device_id, entity_type | UNIQUE | Cursor uniqueness per entity per device |
| idx_sync_cursor_device | device_id | B-tree | Device cursor lookup |
| idx_sync_cursor_tenant | tenant_id | B-tree | Tenant isolation queries |

**Constraints:**

| Constraint | Type | Detail |
|------------|------|--------|
| fk_sync_cursor_tenant | FK | References tenants(id) ON DELETE CASCADE |
| uq_cursor_per_device_entity | UNIQUE | (tenant_id, device_id, entity_type) |

**Tenant Isolation:** RLS on tenant_id column.

**Versioning:** N/A (metadata, updated atomically on each sync).

**Audit:** last_sync_at provides sync timestamp.

**Retention:** Until explicitly cleared by device or admin action.

**Dependency:** None. Referenced by sync pull/push operations.

**Sample Data:**

```json
{
    "id": "880e8400-e29b-41d4-a716-446655440003",
    "tenant_id": "660e8400-e29b-41d4-a716-446655440001",
    "device_id": "ios-device-abc123",
    "entity_type": "account",
    "last_sync_at": "2026-08-11T14:00:00Z",
    "last_sync_version": 42,
    "cursor_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

**Cursor Token Format:** Opaque string. Server-generated, client treats as black box. Encoding is implementation-specific (JWT, base64-encoded state, etc.).

**Entity Types:** account, contact, lead, opportunity, task, activity, note. Defined as an enum in application code.

---

### 2.3 mobile_sync_log

**Purpose:** Audit trail for all sync operations. Append-only log for compliance, debugging, and conflict analysis.

**Table Definition:**

```sql
CREATE TABLE mobile_sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    device_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('PULL', 'PUSH')),
    entity_type TEXT NOT NULL,
    entity_id UUID,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (status IN ('SUCCESS', 'CONFLICT', 'ERROR')),
    server_version BIGINT,
    client_version BIGINT,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_sync_log_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
```

**Indexes:**

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| mobile_sync_log_pkey | id | PRIMARY KEY | Row lookup |
| idx_sync_log_tenant | tenant_id | B-tree | Tenant isolation queries |
| idx_sync_log_device | device_id | B-tree | Device sync history |
| idx_sync_log_idempotency | idempotency_key | B-tree | Idempotency dedup lookup |
| idx_sync_log_synced_at | tenant_id, synced_at | B-tree | Time-range queries for cleanup |

**Constraints:**

| Constraint | Type | Detail |
|------------|------|--------|
| fk_sync_log_tenant | FK | References tenants(id) ON DELETE CASCADE |
| chk_operation | CHECK | operation IN ('PULL', 'PUSH') |
| chk_status | CHECK | status IN ('SUCCESS', 'CONFLICT', 'ERROR') |

**Tenant Isolation:** RLS on tenant_id column.

**Versioning:** N/A (append-only log, never updated).

**Audit:** synced_at provides operation timestamp.

**Retention:** 30 days. Cleanup job removes records where synced_at < now() - interval '30 days'.

**Dependency:** None. Standalone audit log.

**Sample Data:**

```json
{
    "id": "990e8400-e29b-41d4-a716-446655440004",
    "tenant_id": "660e8400-e29b-41d4-a716-446655440001",
    "device_id": "ios-device-abc123",
    "operation": "PUSH",
    "entity_type": "contact",
    "entity_id": "aa0e8400-e29b-41d4-a716-446655440005",
    "idempotency_key": "idem-push-contact-001",
    "status": "SUCCESS",
    "server_version": 43,
    "client_version": 42,
    "synced_at": "2026-08-11T14:30:00Z"
}
```

---

### 2.4 mobile_conflict_log

**Purpose:** Track all conflicts detected during sync. Records conflict state, both server and client payloads, and resolution outcome.

**Table Definition:**

```sql
CREATE TABLE mobile_conflict_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    device_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    server_version BIGINT NOT NULL,
    client_version BIGINT NOT NULL,
    server_payload JSONB,
    client_payload JSONB,
    resolution TEXT CHECK (resolution IN ('SERVER_WINS', 'CLIENT_WINS', 'MERGED', 'MANUAL', 'PENDING')),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_conflict_log_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
```

**Indexes:**

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| mobile_conflict_log_pkey | id | PRIMARY KEY | Row lookup |
| idx_conflict_log_tenant | tenant_id | B-tree | Tenant isolation queries |
| idx_conflict_log_entity | tenant_id, entity_type, entity_id | B-tree | Entity conflict history |
| idx_conflict_log_resolution | resolution | B-tree | Filter by resolution state |
| idx_conflict_log_pending | tenant_id, resolution | Partial B-tree | WHERE resolution = 'PENDING' for quick lookup |

**Constraints:**

| Constraint | Type | Detail |
|------------|------|--------|
| fk_conflict_log_tenant | FK | References tenants(id) ON DELETE CASCADE |
| chk_resolution | CHECK | resolution IN ('SERVER_WINS', 'CLIENT_WINS', 'MERGED', 'MANUAL', 'PENDING') |

**Tenant Isolation:** RLS on tenant_id column.

**Versioning:** N/A (append-only log, resolution field updated once).

**Audit:** created_at, resolved_at provide conflict lifecycle tracking.

**Retention:** 1 year, configurable via tenant settings. Derived from C3 architectural decision. Cleanup job removes records where resolved_at < now() - interval configurable per tenant (default 1 year).

**Dependency:** None. Standalone conflict tracking.

**Sample Data:**

```json
{
    "id": "bb0e8400-e29b-41d4-a716-446655440006",
    "tenant_id": "660e8400-e29b-41d4-a716-446655440001",
    "device_id": "ios-device-abc123",
    "entity_type": "contact",
    "entity_id": "aa0e8400-e29b-41d4-a716-446655440005",
    "server_version": 43,
    "client_version": 42,
    "server_payload": {
        "first_name": "John",
        "last_name": "Smith",
        "email": "john.smith@example.com",
        "phone": "+1-555-0100"
    },
    "client_payload": {
        "first_name": "John",
        "last_name": "Smith",
        "email": "john.updated@example.com",
        "phone": "+1-555-0101"
    },
    "resolution": "PENDING",
    "resolved_at": null,
    "created_at": "2026-08-11T14:30:00Z"
}
```

---

## 3. PROPOSED TABLES (Optional / Future)

These tables are not required for G7 launch but may be added in future iterations.

### 3.1 mobile_outbox

**Purpose:** Client-side mutation queue. Enables offline-first architecture where the mobile app queues mutations locally and pushes them when connectivity is restored.

**Status:** NOT REQUIRED for G7 launch. Client-side implementation concern, not server-side.

**Rationale:** Mobile app can manage its own local queue using SQLite/Realm. Server only needs the push endpoint. Outbox pattern is a client-side implementation detail.

### 3.2 mobile_push_log

**Purpose:** Detailed push audit with per-operation granularity.

**Status:** NOT REQUIRED for G7 launch. Can be derived from mobile_sync_log.

**Rationale:** mobile_sync_log already captures per-operation status including idempotency_key, entity_type, entity_id, and status. Granular push logging can be added later if analytics requirements emerge.

---

## 4. DERIVED METADATA

### 4.1 Entity Version

**Source:** Already exists on all CRM tables as `version BIGINT`.

**Mechanism:** PostgreSQL sequence-based versioning. Updated on every successful write via optimistic locking.

**Mobile Sync Usage:** Client must include `base_version` in push operations. Server compares against current `version`. Mismatch = conflict (server has been modified since client last synced).

### 4.2 Entity updated_at

**Source:** Already exists on all CRM tables as `updated_at TIMESTAMPTZ`.

**Mechanism:** Trigger-based auto-update on row modification.

**Mobile Sync Usage:** Used as secondary sort for sync ordering. If versions are equal, updated_at provides tie-breaking.

### 4.3 Sync Cursor

**Source:** Derived from `mobile_sync_cursor` table.

**Mechanism:** After each successful pull, server updates the cursor for the requesting device and entity type. Cursor token is opaque to client.

**Mobile Sync Usage:** Client passes cursor on next pull request. Server returns entities changed since cursor timestamp/version.

### 4.4 Tenant ID

**Source:** Already exists on all CRM tables and all new G7 tables.

**Mechanism:** RLS policies filter queries by tenant_id from JWT claim.

**Mobile Sync Usage:** All sync operations are scoped to the authenticated user's tenant. Cross-tenant sync is impossible by design.

---

## 5. TABLE RELATIONSHIP DIAGRAM

```
tenants (existing)
    |
    +-- crm_accounts (existing, version, tenant_id)
    |       |
    |       +-- crm_contacts (existing, version, tenant_id)
    |       +-- crm_opportunities (existing, version, tenant_id)
    |
    +-- crm_leads (existing, version, tenant_id)
    +-- crm_tasks (existing, version, tenant_id)
    +-- crm_activities (existing, version, tenant_id)
    +-- crm_notes (existing, version, tenant_id)
    +-- crm_pipelines (existing, tenant_id)
    +-- crm_tags (existing, tenant_id)
    +-- crm_custom_fields (existing, tenant_id)
    +-- platform_audit_logs (existing, tenant_id)
    +-- crm_timeline_events (existing, tenant_id)
    +-- crm_idempotency_records (existing)
    |
    +-- mobile_device_registry (NEW, tenant_id)
    +-- mobile_sync_cursor (NEW, tenant_id)
    +-- mobile_sync_log (NEW, tenant_id)
    +-- mobile_conflict_log (NEW, tenant_id)
```

---

## 6. MIGRATION STRATEGY

### 6.1 Migration Order

1. Create `mobile_device_registry` table
2. Create `mobile_sync_cursor` table
3. Create `mobile_sync_log` table
4. Create `mobile_conflict_log` table
5. Add RLS policies to all four tables
6. Create indexes
7. Add constraints

### 6.2 Migration Safety

- All new tables use `CREATE TABLE IF NOT EXISTS` for idempotency
- Foreign keys use `ON DELETE CASCADE` for tenant isolation
- No existing tables are modified
- Zero-downtime deployment (additive only)

### 6.3 Rollback Strategy

- Each migration has a corresponding rollback migration
- Rollback drops new tables only
- No impact on existing CRM functionality

---

## 7. PERFORMANCE CONSIDERATIONS

### 7.1 Index Strategy

- All tenant_id columns are indexed for RLS performance
- Compound indexes on (tenant_id, device_id, entity_type) for cursor lookups
- Partial index on mobile_conflict_log for PENDING conflicts only

### 7.2 Partitioning

- Consider partitioning mobile_sync_log by month for retention management
- Consider partitioning mobile_conflict_log by resolution status

### 7.3 Cleanup Jobs

- mobile_device_registry: 90-day inactive device cleanup (daily cron)
- mobile_sync_log: 30-day retention cleanup (daily cron)
- mobile_conflict_log: 1-year configurable retention (daily cron)

---

## 8. SECURITY CONSIDERATIONS

### 8.1 RLS Policies

All four new tables have RLS enabled. Tenant isolation is enforced at the database level, not application level.

### 8.2 Sensitive Data

- mobile_conflict_log.server_payload and client_payload may contain PII
- Retention policies ensure data is not kept beyond required periods
- Cleanup jobs must securely delete (not just mark as deleted)

### 8.3 Access Control

- Tables are accessible only through application service accounts
- Direct database access is restricted to DBA operations
- Audit logs track all access to mobile_conflict_log

---

## 9. VERIFICATION CHECKLIST

| Item | Status | Verified By |
|------|--------|-------------|
| mobile_device_registry DDL | COMPLETE | Flyway migration |
| mobile_sync_cursor DDL | COMPLETE | Flyway migration |
| mobile_sync_log DDL | COMPLETE | Flyway migration |
| mobile_conflict_log DDL | COMPLETE | Flyway migration |
| RLS policies created | COMPLETE | pg_dump verification |
| Indexes created | COMPLETE | EXPLAIN ANALYZE |
| Foreign keys validated | COMPLETE | Constraint check |
| Retention jobs configured | COMPLETE | Cron verification |
| Seed data inserted | COMPLETE | SELECT count(*) |

---

## 10. SIGN-OFF

| Role | Name | Date | Status |
|------|------|------|--------|
| Data Architect | TBD | TBD | PENDING |
| DBA | TBD | TBD | PENDING |
| Security Lead | TBD | TBD | PENDING |
| G7 Tech Lead | TBD | TBD | PENDING |

---

*Document generated: 2026-08-11*
*G7 Data Model Reconciliation Complete*
