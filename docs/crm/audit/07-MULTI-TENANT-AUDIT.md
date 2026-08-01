# Multi-Tenant Audit

**Audit Scope:** Tenant isolation design, RLS implementation, tenant context propagation, cross-tenant leakage risks, tenant-scoped queries, tenant FK constraints.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** HIGH -- Tenant isolation relies on application-level `WHERE tenant_id = ?` with no database-level Row-Level Security enforcement. Several gaps create cross-tenant leakage risks.

---

## Executive Summary

The SNAD CRM implements tenant isolation exclusively at the application layer. Every CRM table includes a `tenant_id` column, and queries consistently include a `WHERE tenant_id = :tenantId` clause derived from the authenticated user's context. However, there is no database-level Row-Level Security (RLS) enforcement, no foreign key constraints linking `tenant_id` to the tenants table, and no systematic verification that every query is tenant-scoped. The zero-UUID tenant in seed data further undermines the isolation model. While the application layer pattern is consistent, the lack of defense-in-depth means a single bug in query construction can cause cross-tenant data leakage.

---

## Finding MTN-01: No Row-Level Security at Database Level

**Severity:** HIGH
**Category:** Missing RLS Enforcement

### Description
The PostgreSQL database has no Row-Level Security policies on any CRM table. Tenant isolation relies entirely on the application layer including `tenant_id` in every query's WHERE clause. There is no database-level enforcement if the application layer fails to include the tenant filter.

### Evidence
- None of the SQL migration files for CRM tables include `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` or `CREATE POLICY` statements
- The V20260729_1 migration creates tables with `tenant_id` columns and indexes but no RLS policies
- Other migrations (V1 through V19, V2026xxxx series) similarly lack RLS

### Impact
- Any application bug that omits the tenant_id filter from a query results in cross-tenant data access
- Direct database queries (operational scripts, reporting, migrations) are not tenant-scoped
- Compromised application credentials allow access to all tenants' data
- No defense-in-depth: a single layer of protection (application WHERE clause) is the only barrier

### Recommendation
1. Implement PostgreSQL Row-Level Security on all tenant-scoped tables
2. Create a policy function that extracts tenant_id from the current session setting
3. Set the tenant context at connection setup time (e.g., `SET app.current_tenant_id = :tid`)
4. Add Flyway migration to enable RLS and create policies
5. Verify RLS enforcement with integration tests that attempt cross-tenant queries

---

## Finding MTN-02: Missing Foreign Key Constraints on tenant_id

**Severity:** HIGH
**Category:** Referential Integrity / Isolation

### Description
No CRM table has a foreign key constraint linking `tenant_id` to the `tenants` table. This means:
- Records can be created with tenant_id values that don't correspond to any actual tenant
- Deleting a tenant does not cascade or restrict deletion of its CRM data
- Orphaned records can accumulate over time

### Affected Tables (CRM-010 and others)
- `crm_customer_scores` -- no FK on `tenant_id`
- `crm_customer_score_history` -- no FK on `tenant_id`
- `crm_customer_segments` -- no FK on `tenant_id`
- `crm_segment_memberships` -- no FK on `tenant_id`
- `crm_next_best_actions` -- no FK on `tenant_id`
- `crm_scoring_models` -- no FK on `tenant_id`
- All other CRM tables with `tenant_id`

### Impact
- Data integrity violations are not caught at the database level
- Orphaned tenant data persists indefinitely
- Migration tooling must handle referential anomalies

### Recommendation
1. Add foreign key constraints referencing `tenants(id)` on all tenant-scoped tables
2. Use `ON DELETE CASCADE` for data cleanup (or `ON DELETE RESTRICT` if stronger isolation is required)
3. Add these constraints in a Flyway migration with precondition checks

---

## Finding MTN-03: Tenant Context Propagation -- Inconsistent Extraction

**Severity:** HIGH
**Category:** Tenant Context Propagation

### Description
Tenant context extraction is duplicated across multiple layers with no shared utility:
- **Controllers:** Extract tenant from `Authentication.getDetails()` map
- **Aspects:** Re-extract tenant from `SecurityContextHolder.getContext().getAuthentication().getDetails()`
- **Repositories:** Receive tenant_id as a method parameter
- **No global holder:** There is no `TenantContextHolder` or similar mechanism to propagate tenant context across layers

### Evidence
- `CrmOwnershipAtomicIfMatchAspect` extracts tenant via `SecurityContextHolder` (lines 157-169)
- `CrmController` methods take `Authentication` parameter and extract tenant inline
- Repository methods take explicit `UUID tenantId` parameter (correct, but inconsistently from controller extraction)

### Impact
- If a method has a bug in tenant extraction, it may use the wrong tenant_id
- No single point of control for tenant resolution means fixes must be applied in many places
- Testing tenant propagation requires testing across multiple layers independently

### Recommendation
1. Create a `TenantContextHolder` utility that extracts and stores tenant context from `Authentication`
2. Use a request-scoped bean or `ThreadLocal` to hold the tenant context throughout the request
3. Replace manual extraction in controllers and aspects with a call to the holder
4. Add a filter that sets the tenant context early in the request lifecycle

---

## Finding MTN-04: No Systematic Tenant ID Verification in Queries

**Severity:** MEDIUM
**Category:** Query Tenant Scoping

### Description
While most queries appear to include `tenant_id` filters, there is no systematic verification that every query method in every repository includes a tenant_id condition. An ArchUnit test or similar automated check is absent.

### Impact
- A repository method that accidentally omits the tenant_id filter will return data for all tenants
- New code may not include tenant scoping if the developer misses the pattern
- Code review is the only guard against cross-tenant leakage

### Recommendation
1. Add an ArchUnit test: every method in a `*Repository` class that executes SQL must contain a `tenant_id` parameter and filter
2. Consider using a base repository class that enforces tenant scoping
3. Add integration tests that verify tenant isolation for every repository

---

## Finding MTN-05: Zero-UUID Tenant in Seed Data

**Severity:** CRITICAL
**Category:** Tenant Isolation Breach

### Description
The `V20260729_2__seed_default_scoring_models.sql` migration inserts records with `tenant_id = '00000000-0000-0000-0000-000000000000'`. This invalid tenant ID exists in a tenant-scoped table (`crm_scoring_models`), creating records that:
- Do not belong to any real tenant
- May be unintentionally included or excluded depending on query patterns
- Represent a vector for cross-tenant data contamination

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\resources\db\vendor\postgresql\V20260729_2__seed_default_scoring_models.sql`

### Impact
- If tenant_id filtering is inconsistent (e.g., a query uses `tenant_id IS NULL` vs `tenant_id = '0000...'`), seed data may leak to all tenants or no tenants
- The zero-UUID has no referential integrity -- it doesn't exist in the tenants table
- No mechanism exists to clone seed models per tenant on first use

### Recommendation
1. Create a separate `crm_default_scoring_models` table that is not tenant-scoped
2. Remove zero-UUID records from `crm_scoring_models`
3. Implement a service that materializes per-tenant copies of default models
4. Add a check constraint to prevent zero-UUID tenant_id insertion

---

## Finding MTN-06: No tenant_id Index on Certain Junction Tables

**Severity:** MEDIUM
**Category:** Performance / Isolation

### Description
While the CRM-010 migration creates indexes on `tenant_id` columns for most tables, some junction tables (notably `crm_segment_memberships` has indexes but others may be missing). Missing tenant_id indexes can cause full-table scans for tenant-scoped queries, making cross-tenant performance attacks feasible.

### Recommendation
1. Audit all tenant-scoped tables for missing `tenant_id` indexes
2. Add composite indexes on `(tenant_id, id)` for all tenant-scoped tables
3. Verify index coverage with query plan analysis

---

## Finding MTN-07: CrmOwnershipAtomicIfMatchAspect -- Tenant Locking Without Tenant Verification

**Severity:** MEDIUM
**Category:** Cross-Tenant Lock Contention

### Description
The `CrmOwnershipAtomicIfMatchAspect` locks rows using `SELECT ... FOR UPDATE` filtered by `tenant_id`. If the tenant extraction in the aspect is incorrect or bypassed, a request could lock (or worse, modify) a row in the wrong tenant. The aspect resolves `tenant_id` from `SecurityContextHolder` independently of the controller, creating two paths for potential errors.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\infrastructure\CrmOwnershipAtomicIfMatchAspect.java`

### Recommendation
1. Reduce tenant extraction to a single path
2. Have the aspect receive tenant context as part of the join point metadata rather than re-extracting
3. Add cross-tenant locking integration tests

---

## Finding MTN-08: DisabledHrmOwnershipAdapter Active in All Profiles

**Severity:** HIGH
**Category:** Multi-Tenant Isolation

### Description
The `DisabledHrmOwnershipAdapter` implements the HRM port with a "stub" that returns disabled/no-op behavior. Because it has no `@Profile` annotation, it is active in all profiles. This means HRM-dependent tenant features (absence-driven reassignment, team-based approval routing) always receive stub responses regardless of the tenant configuration.

### Impact
- Tenant-specific HRM integration is not possible since the stub is always active
- HRM-dependent multi-tenant features cannot be tested end-to-end
- Production tenant isolation for HRM features is non-functional

### Recommendation
Add `@Profile("!production")` or `@ConditionalOnProperty` to control when the disabled adapter is active.

---

## Finding MTN-09: Tenant-Scoped Cache Keys

**Severity:** LOW
**Category:** Positive Finding

### Description
The `CustomerIntelligenceCache` correctly includes `tenantId` in cache keys:

```java
private static String scoresKey(UUID tenantId, UUID accountId) {
    return "scores:v1:" + tenantId + ":" + accountId;
}
```

This is correctly implemented and prevents tenant cache contamination. This finding is recorded as a positive pattern that should be replicated across all caches.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\CustomerIntelligenceCache.java` (line 51)

### Recommendation
Verify that all cache implementations in the codebase follow the same tenant-scoped key pattern.

---

## Conclusion

The SNAD CRM tenant isolation model is application-layer-only, with no database-level RLS enforcement. While the pattern of including `tenant_id` in queries is consistent, the lack of defense-in-depth means a single query bug can cause cross-tenant data leakage. The zero-UUID tenant seed data further undermines isolation. The highest-priority remediation is implementing PostgreSQL Row-Level Security and adding foreign key constraints on `tenant_id`. Centralizing tenant context propagation and adding systematic verification of tenant-scoped queries should follow.

**Overall Multi-Tenant Score: 4/10 -- Application-layer isolation only; no database-level enforcement.**
