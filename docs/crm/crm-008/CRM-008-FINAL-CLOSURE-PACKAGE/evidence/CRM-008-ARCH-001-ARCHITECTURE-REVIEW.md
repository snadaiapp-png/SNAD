# CRM-008 Architecture Review — Agent 1

> **Agent:** Agent 1 — Architecture & Database Foundation
> **Command:** CRM-008-EXECUTION-001
> **Task:** 1 — Review CRM-007 Architecture
> **Date:** 2026-07-28
> **Status:** PASS

---

## 1. Review Scope

Review CRM-007 architecture to ensure CRM-008 Team Management is fully compatible.

---

## 2. Tenant Model

### 2.1 Tenant Entity

| Attribute | Value |
|---|---|
| Table | `tenants` |
| Entity | `com.sanad.platform.tenant.domain.Tenant` |
| Primary Key | `id UUID` |
| Status | `TenantStatus` enum (PENDING, TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED, ARCHIVED) |

### 2.2 Tenant Isolation Pattern

**CRM Domain:**
- Every CRM table has `tenant_id UUID NOT NULL` with FK to `tenants(id)`
- Composite unique constraints include `tenant_id`
- All repository methods accept `UUID tenantId` as first parameter
- `TenantContextPort` interface provides `getTenantId()` and `getPrincipalId()`

**JWT Filter:**
- Extracts `tenant_id` from JWT claims
- Validates tenant binding (request tenant matches JWT tenant)
- Places `tenant_id` and `user_id` in authentication details map

**Controller Pattern:**
```java
private static UUID tenantId(Authentication authentication) {
    return context(authentication, "tenant_id");
}
private static UUID userId(Authentication authentication) {
    return context(authentication, "user_id");
}
```

### 2.3 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will follow the same tenant isolation pattern:
- All new tables will have `tenant_id UUID NOT NULL` with FK to `tenants(id)`
- All repository methods will accept `UUID tenantId` as first parameter
- All queries will include `WHERE tenant_id = :tenantId`

---

## 3. Organization Context

### 3.1 Organization Entity

| Attribute | Value |
|---|---|
| Table | `organizations` |
| Entity | `com.sanad.platform.organization.domain.Organization` |
| Relationships | `@ManyToOne Tenant tenant` |
| Memberships | `organization_memberships` table |

### 3.2 Organization Membership

| Attribute | Value |
|---|---|
| Table | `organization_memberships` |
| Fields | `tenant_id`, `organization_id`, `user_id`, `email`, `display_name`, `status` |

### 3.3 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 Team Management will:
- Teams can be scoped to organization (optional `organization_id` column)
- Team members are linked via `user_id` (from `users` table)
- Organization context is available via `OrganizationMembership`

---

## 4. Identity Integration

### 4.1 JWT Authentication

| Component | Location |
|---|---|
| Token Provider | `com.sanad.platform.security.service.JwtTokenProvider` |
| Auth Filter | `com.sanad.platform.security.filter.JwtAuthenticationFilter` |
| Auth Service | `com.sanad.platform.security.service.AuthService` |
| Security Config | `com.sanad.platform.security.config.SecurityConfig` |

**JWT Claims:**
- `sub` — userId
- `tenant_id` — tenantId
- `email` — user email
- `session_version` — for token revocation
- `credential_rotation_required` — bootstrap session flag

### 4.2 RBAC

| Component | Location |
|---|---|
| Capability | `com.sanad.platform.access.capability.AccessCapability` |
| Role | `com.sanad.platform.access.role.Role` |
| RoleCapability | `com.sanad.platform.access.role.RoleCapability` |
| UserRoleGrant | `com.sanad.platform.access.grant.UserRoleGrant` |
| Annotation | `@RequireCapability("CRM.TEAM.READ")` |
| Aspect | `CapabilityAuthorizationAspect` |

**Capability Code Pattern:** `CRM.{ENTITY}.{ACTION}`
- Examples: `CRM.ACCOUNT.READ`, `CRM.TEAM.WRITE`, `CRM.TEAM.ADMIN`

### 4.3 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will define new capabilities:
- `CRM.TEAM.READ` — Read team information
- `CRM.TEAM.WRITE` — Create/update teams
- `CRM.TEAM.ADMIN` — Manage teams (delete, archive)
- `CRM.TEAM.SCHEDULE.READ` — Read scheduling
- `CRM.TEAM.SCHEDULE.WRITE` — Manage scheduling
- `CRM.TEAM.SKILL.READ` — Read skills
- `CRM.TEAM.SKILL.WRITE` — Manage skills
- `CRM.TEAM.CAPACITY.READ` — Read capacity
- `CRM.TEAM.CAPACITY.WRITE` — Manage capacity

---

## 5. Existing CRM Entities

### 5.1 Core CRM Tables

| Table | Module | Description |
|---|---|---|
| `crm_accounts` | party | Customer accounts |
| `crm_contacts` | party | People/contacts |
| `crm_leads` | lead | Sales leads |
| `crm_opportunities` | opportunity | Sales opportunities |
| `crm_pipelines` | opportunity | Sales pipelines |
| `crm_pipeline_stages` | opportunity | Pipeline stages |
| `crm_activities` | activity | Timeline activities |
| `crm_tasks` | task | Work items |
| `crm_notes` | note | Notes |
| `crm_tags` | tag | Labels |
| `crm_tag_assignments` | tag | Tag-entity links |
| `crm_assignments` | ownership | Ownership records |
| `crm_transfers` | ownership | Transfer workflows |
| `crm_audit_logs` | ownership | Audit trail |
| `crm_timeline_events` | integration | Event timeline |

### 5.2 Ownership Module (CRM-008B)

| Table | Description |
|---|---|
| `crm_sales_teams` | Team definitions |
| `crm_team_memberships` | User-team relationships |

**Domain Records:**
- `SalesTeam` — id, tenantId, code, displayName, description, status, managerUserId
- `TeamMembership` — id, tenantId, teamId, userId, role, isPrimary, status, capacityMax

### 5.3 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will extend the ownership module:
- Reuse `crm_sales_teams` and `crm_team_memberships` tables
- Add new tables for scheduling, availability, skills, capacity, workload
- New tables reference existing tables via foreign keys

---

## 6. Naming Standards

### 6.1 Database Naming

| Element | Pattern | Example |
|---|---|---|
| CRM Tables | `crm_{plural_noun}` | `crm_shift_templates` |
| Primary Keys | `pk_{table}` | `pk_crm_shift_templates` |
| Unique Constraints | `uk_{table}_{columns}` | `uk_crm_shift_templates_tenant` |
| Foreign Keys | `fk_{child}_{parent}` | `fk_crm_shift_templates_tenant` |
| Check Constraints | `ck_{table}_{description}` | `ck_crm_shift_templates_status` |
| Indexes | `idx_{table}_{columns}` | `idx_crm_shift_templates_tenant_status` |

### 6.2 Java Naming

| Element | Pattern | Example |
|---|---|---|
| Domain Records | PascalCase | `ShiftTemplate`, `StaffAvailability` |
| Repository Interfaces | PascalCase + Repository | `ShiftTemplateRepository` |
| Command Records | Create/Update + Entity + Command | `CreateShiftTemplateCommand` |
| Enums | PascalCase | `ShiftTemplateStatus`, `AvailabilityType` |
| UseCases | Entity + UseCases | `SchedulingUseCases` |
| Controllers | Entity + Controller | `ShiftController` |

### 6.3 Column Naming

| Element | Pattern | Example |
|---|---|---|
| Tenant ID | `tenant_id` | `tenant_id UUID NOT NULL` |
| Version | `version` | `version BIGINT NOT NULL DEFAULT 0` |
| Created At | `created_at` | `created_at TIMESTAMP WITH TIME ZONE NOT NULL` |
| Updated At | `updated_at` | `updated_at TIMESTAMP WITH TIME ZONE NOT NULL` |
| Created By | `created_by` | `created_by UUID NOT NULL` |
| Updated By | `updated_by` | `updated_by UUID NOT NULL` |

### 6.4 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will follow all naming standards:
- Tables: `crm_shift_templates`, `crm_shift_assignments`, `crm_staff_availability`, etc.
- Columns: `tenant_id`, `version`, `created_at`, `updated_at`, `created_by`, `updated_by`
- Constraints: `pk_crm_shift_templates`, `uk_crm_shift_templates_tenant`, etc.

---

## 7. Architecture Patterns

### 7.1 DDD Hexagonal Architecture

```
crm/ownership/
├── domain/          — Pure interfaces, domain records, exceptions
├── application/     — UseCase services, ModuleConfiguration
├── infrastructure/  — JDBC implementations
└── web/             — REST controllers
```

### 7.2 Repository Pattern

- Domain defines repository interfaces (ports)
- Infrastructure implements JDBC repositories (adapters)
- UseCases depend on domain interfaces, not infrastructure

### 7.3 Command Pattern

- Create/Update commands as inner records in repository interfaces
- Commands carry all data needed for the operation
- Commands include `expectedVersion` for optimistic locking

### 7.4 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will follow all architecture patterns:
- Domain records (not JPA entities)
- Repository interfaces with inner records
- JDBC infrastructure with NamedParameterJdbcTemplate
- UseCase facades with @Transactional
- Controllers with @RequireCapability

---

## 8. Migration Patterns

### 8.1 Standard Migration Structure

```sql
CREATE TABLE IF NOT EXISTS crm_{entity} (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    -- domain columns --
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_{entity} PRIMARY KEY (id),
    CONSTRAINT uk_crm_{entity}_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_{entity}_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_{entity}_status CHECK (status IN (...))
);
CREATE INDEX idx_crm_{entity}_tenant_status ON crm_{entity} (tenant_id, status);
```

### 8.2 Capability Seeding

```sql
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TEAM.READ',  'Read Teams',  'View tenant teams'),
    ('CRM.TEAM.WRITE', 'Write Teams', 'Create and update tenant teams')
) AS capability(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code);
```

### 8.3 CRM-008 Compatibility

✅ **COMPATIBLE** — CRM-008 will follow migration patterns:
- Use `CREATE TABLE IF NOT EXISTS`
- Include tenant_id, version, audit columns
- Define proper constraints and indexes
- Seed capabilities for RBAC

---

## 9. Review Decision

### Decision: **PASS**

All CRM-007 architecture patterns are compatible with CRM-008 Team Management.

| Criterion | Status |
|---|---|
| Tenant Model | ✅ Compatible |
| Organization Context | ✅ Compatible |
| Identity Integration | ✅ Compatible |
| Existing CRM Entities | ✅ Compatible |
| Naming Standards | ✅ Compatible |
| Architecture Patterns | ✅ Compatible |
| Migration Patterns | ✅ Compatible |

**No architectural blockers exist.**

---

**Review Date:** 2026-07-28
**Reviewer:** Agent 1 — Architecture & Database Foundation
**Status:** PASS
