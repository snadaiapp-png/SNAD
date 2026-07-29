# CRM-018 — Security Assessment

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-018 |
| Title | Add row-level security as defense-in-depth |
| Milestone | CRM-G4 |
| Assessor | CRM-018 Security Implementation Authority |
| Date | 2026-07-29 |
| Classification | Internal — Security Architecture |

## 1. Executive Summary

The SNAD platform is a multi-tenant SaaS application using a **shared-database,
shared-schema** tenancy model. Tenant isolation is currently enforced **exclusively
at the application layer** — every JDBC query includes an explicit
`WHERE tenant_id = :tenantId` predicate. There is **no database-level Row-Level
Security (RLS)**, no Hibernate `@Filter`, no interceptor, and no AOP aspect that
enforces tenant scoping on data access.

This is a **defense-in-depth gap**: correctness depends entirely on developer
discipline. A single missing `tenant_id` predicate in a new repository method
would silently leak cross-tenant data with no safety net.

**Recommendation:** Add PostgreSQL native RLS as a defense-in-depth layer on all
62 CRM tables. RLS policies will be transparent to the application — they activate
when a session-local `app.tenant_id` setting is present, and fall back to the
application-layer filtering when it is not. This ensures backward compatibility
while providing a database-level guarantee that no query can return cross-tenant
rows.

## 2. Current Tenant Isolation Architecture

### 2.1 Tenancy Model

| Aspect | Value |
|--------|-------|
| Tenancy model | Shared database, shared schema |
| Tenant identifier | `tenant_id UUID NOT NULL` column on every tenant-scoped table |
| Tenant registry | `tenants` table (global, not tenant-scoped) |
| Tenant source of truth | JWT claim `tenant_id` |
| Tenant-scoped tables | 62 CRM tables + identity/RBAC tables |

### 2.2 Tenant Context Propagation Chain

```
JWT (tenant_id claim)
  → JwtAuthenticationFilter (validates, cross-checks ?tenantId= param, 403 on mismatch)
    → Authentication.getDetails() Map ("tenant_id" key)
      → SpringTenantContextAdapter implements TenantContextPort
        → Application services pass UUID tenantId as first parameter to every repository method
          → Jdbc*Repository includes WHERE tenant_id = :tenantId in every query
```

**Evidence:**
- JWT minting: `security/service/JwtTokenProvider.java:81-102`
- Filter validation: `security/filter/JwtAuthenticationFilter.java:67-81`
- Context port: `crm/integration/domain/TenantContextPort.java`
- Context adapter: `crm/integration/infrastructure/SpringTenantContextAdapter.java:12-17`
- Repository contract: `crm/party/domain/AccountRepository.java:15-25` (every method takes `UUID tenantId` first)

### 2.3 Enforcement Mechanisms (Current)

| Layer | Mechanism | Coverage |
|-------|-----------|----------|
| Authentication | JWT extraction in `JwtAuthenticationFilter` | All requests |
| Authorization | `?tenantId=` param cross-check → 403 on mismatch | Param-driven endpoints |
| RBAC | `@RequireCapability` AOP aspect, deny-by-default | Per-endpoint capability |
| Application query | Explicit `WHERE tenant_id = :tenantId` in 351+ JDBC queries | All CRM reads/writes |
| Database FK | Composite FKs `(tenant_id, entity_id)` prevent cross-tenant references | Child→parent refs |
| Database RLS | **NONE** | — |
| Hibernate filter | **NONE** (CRM uses raw JDBC, not JPA) | — |
| Interceptor/aspect | **NONE** for tenant data scoping | — |

### 2.4 Coverage Analysis

**Quantitative scan:** 351 `WHERE tenant_id` clauses across 89 files in the CRM module.
All 60 repository ports (30 domain interfaces + 30 JDBC adapters) enforce tenant
scoping as a compile-time contract (`UUID tenantId` as first parameter).

**Exceptions (reviewed, all safe):**
1. **Idempotency records** (`JdbcIdempotencyService`): keyed by server-generated
   `operationId`, never user-supplied. The `begin()` lookup is tenant-scoped;
   `complete()`/`fail()` use the server handle. Acceptable.
2. **Import worker queue** (`LegacyCrmInfrastructureService`): intentionally
   cross-tenant — a global scheduler claims jobs across tenants by `worker_id`.
   User-facing read paths re-scope by tenant. Acceptable.
3. **No user-reachable query against core CRM tables lacks a tenant predicate.**

## 3. Database Schema Analysis

### 3.1 Schema Overview

- **RDBMS:** PostgreSQL 16 (production), H2 in PostgreSQL mode (local/test)
- **DDL management:** Flyway (`ddl-auto: none`)
- **Migration locations:** `classpath:db/migration` (portable) + `classpath:db/vendor/{vendor}` (vendor-specific)
- **Latest version:** `V20260729_2__seed_default_scoring_models.sql`
- **Connection pool:** HikariCP (max 10, min-idle 2)

### 3.2 CRM Tables (62 tables, all have `tenant_id UUID NOT NULL`)

| Category | Tables |
|----------|--------|
| Party | `crm_accounts`, `crm_contacts`, `crm_contact_lookup_index`, `crm_contact_account_relationships`, `crm_contact_relationship_roles`, `crm_contact_relationship_history`, `crm_account_relationships`, `crm_account_status_history`, `crm_account_merge_history`, `crm_account_identifiers` |
| Addresses | `crm_party_addresses`, `crm_party_address_history`, `crm_account_addresses`, `crm_phone_numbers`, `crm_communication_methods`, `crm_communication_method_history`, `crm_communication_policies` |
| Pipeline | `crm_pipelines`, `crm_pipeline_stages`, `crm_opportunities`, `crm_opportunity_stage_history`, `crm_leads` |
| Activity | `crm_activities`, `crm_tasks`, `crm_notes`, `crm_tags`, `crm_tag_assignments`, `crm_timeline_events` |
| Import | `crm_import_jobs`, `crm_import_files`, `crm_import_errors` |
| Custom fields | `crm_custom_field_definitions`, `crm_custom_field_values` |
| Ownership | `crm_sales_teams`, `crm_team_memberships`, `crm_queues`, `crm_queue_memberships`, `crm_territories`, `crm_territory_assignments`, `crm_territory_closure`, `crm_assignment_rules`, `crm_assignment_rule_versions`, `crm_assignment_rule_counters`, `crm_assignments`, `crm_contact_ownership_history`, `crm_ownership_history`, `crm_transfer_requests`, `crm_transfers`, `crm_transfer_steps` |
| Integration | `crm_integration_requests`, `crm_integration_outbox`, `crm_integration_decisions`, `crm_integration_command_executions`, `crm_integration_command_artifacts` |
| Intelligence | `crm_customer_scores`, `crm_customer_score_history`, `crm_customer_segments`, `crm_segment_memberships`, `crm_next_best_actions`, `crm_scoring_models` |
| Reports | `crm_reports`, `crm_audit_logs` |
| Idempotency | `crm_idempotency_records` |

### 3.3 Indexing

All tenant-scoped tables use **composite indexes leading with `tenant_id`** —
optimal for the tenant-first query pattern. Example:
```sql
idx_crm_accounts_tenant_status ON crm_accounts (tenant_id, lifecycle_status, updated_at DESC)
idx_crm_opportunities_pipeline ON crm_opportunities (tenant_id, pipeline_id, stage_id, status, updated_at DESC)
```

## 4. PostgreSQL RLS Capability Assessment

### 4.1 Native RLS Support

PostgreSQL 15+ supports Row-Level Security natively:
- `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` — activates RLS on a table
- `CREATE POLICY ... USING (...)` — defines which rows are visible/modifiable
- `SET LOCAL app.tenant_id = '...'` — sets a session-local custom GUC
- `current_setting('app.tenant_id')` — reads the GUC within a policy
- `BYPASSRLS` role attribute — exempts a role from RLS (for migrations/admin)

### 4.2 Policy Pattern

```sql
ALTER TABLE crm_accounts ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON crm_accounts
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
```

- `USING` — filters rows visible to SELECT/UPDATE/DELETE
- `WITH CHECK` — validates rows on INSERT/UPDATE (prevents writing wrong tenant)
- `current_setting('app.tenant_id', true)` — returns NULL if unset (no crash)

### 4.3 When RLS Is Inactive (Safe Fallback)

RLS policies using `current_setting('app.tenant_id', true)` return NULL when
the setting is absent. The comparison `tenant_id::text = NULL` evaluates to
NULL (not true), which means **RLS blocks all rows** when no tenant is set.

This is a critical design decision:
- **For application connections:** A HikariCP connection initializer or a
  per-request `SET LOCAL` will set `app.tenant_id` on every connection used by
  a tenant request. RLS then enforces isolation.
- **For migrations/Flyway/admin:** The migration role must have `BYPASSRLS`
  (typically the table owner or a role with `BYPASSRLS` attribute) so
  migrations can manage all tenants' data.

### 4.4 H2 Compatibility

H2 does not support PostgreSQL RLS. Tests using H2 cannot exercise RLS.
**Solution:** RLS tests must use Testcontainers/PostgreSQL (the existing
pattern from `CrmG1TenantIsolationPostgresTest`). H2-based tests continue to
rely on application-layer filtering and remain unaffected.

## 5. Risk Assessment

### 5.1 Current Risk (Without RLS)

| Risk | Likelihood | Impact | Rating |
|------|-----------|--------|--------|
| New repository method omits `tenant_id` | Medium | Critical (cross-tenant data leak) | **HIGH** |
| Refactored query drops tenant predicate | Low | Critical | **MEDIUM** |
| SQL injection bypasses app filter | Low | Critical | **MEDIUM** |
| Dynamic query builder omits tenant | Low | Critical | **MEDIUM** |

### 5.2 Risk After RLS

| Risk | Likelihood | Impact | Rating |
|------|-----------|--------|--------|
| RLS policy misconfiguration | Low | High | **MEDIUM** |
| `app.tenant_id` not set on connection | Low | Medium (query returns 0 rows, not leak) | **LOW** |
| Performance overhead of policy evaluation | Low | Low (indexed `tenant_id`) | **LOW** |
| Migration role needs BYPASSRLS | Low | Low (operational) | **LOW** |

RLS converts the worst case from **silent data leak** to **query returns no rows**
(fail-closed), which is the desired security posture.

## 6. Recommendations

### 6.1 Implement PostgreSQL Native RLS

Add RLS policies to all 62 CRM tables via a Flyway migration in the
`db/vendor/postgresql/` directory. This ensures:
- H2/local tests are unaffected (migration is vendor-specific)
- Production/Postgres gets the defense-in-depth layer
- The application layer remains the primary enforcement (no behavioral change)
- RLS is a **safety net**, not a replacement

### 6.2 Tenant Session Context Propagation

Set `app.tenant_id` on each database session used by tenant requests. Two options:

**Option A (Recommended): `SET LOCAL` per transaction via existing AOP/filter**
- The existing `JwtAuthenticationFilter` already extracts the tenant ID
- Add a mechanism to set `app.tenant_id` on the JDBC connection for the request
- Use `Connection.unwrap()` + native `SET LOCAL` or Spring's
  `DataSourceUtils` + `Connection.setClientInfo`

**Option B: HikariCP connection initializer**
- Set a default `app.tenant_id` at connection checkout
- Requires per-request update (connection pool reuse)

**Decision: Option A** — set `SET LOCAL app.tenant_id` within the transaction
boundary, matching the existing tenant-extraction flow.

### 6.3 Migration Role with BYPASSRLS

Flyway must run with a role that bypasses RLS (table owner or `BYPASSRLS`).
In PostgreSQL, the **table owner** bypasses RLS by default unless
`FORCE ROW LEVEL SECURITY` is applied. Since we will NOT use `FORCE RLS`,
the migration role (owner) automatically bypasses RLS.

### 6.4 Automated Validation

Add a Testcontainers/PostgreSQL test that:
1. Inserts rows for two tenants
2. Sets `app.tenant_id` to tenant A
3. Verifies only tenant A's rows are visible
4. Verifies INSERT with tenant B's ID is rejected (WITH CHECK fails)
5. Verifies application-layer queries still work correctly

## 7. Conclusion

The current application-layer-only tenant isolation is functional but relies
entirely on developer discipline. Adding PostgreSQL native RLS as a
defense-in-depth layer provides a database-level guarantee that no query can
return or modify cross-tenant rows, even if the application layer has a bug.
The implementation is backward-compatible (vendor-specific migration, no app
behavior change) and testable via the existing Testcontainers pattern.

**Proceed to Phase 1: Design.**
