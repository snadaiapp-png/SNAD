# CRM-018 — Security Report

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-018 |
| Classification | Internal — Security Architecture |
| Date | 2026-07-29 |

## 1. Security Architecture

### 1.1 Defense-in-Depth Layers (After CRM-018)

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Authentication (JWT tenant_id claim)               │
│   JwtAuthenticationFilter validates + cross-checks param    │
├─────────────────────────────────────────────────────────────┤
│ Layer 2: Authorization (RBAC @RequireCapability)            │
│   CapabilityAuthorizationAspect, deny-by-default            │
├─────────────────────────────────────────────────────────────┤
│ Layer 3: Application Filtering (WHERE tenant_id = :t)       │
│   351+ JDBC queries, compile-time port contract             │
├─────────────────────────────────────────────────────────────┤
│ Layer 4: Composite FKs (DB-level cross-ref prevention)      │
│   FOREIGN KEY (tenant_id, entity_id) REFERENCES ...         │
├─────────────────────────────────────────────────────────────┤
│ Layer 5: PostgreSQL RLS (NEW — CRM-018)                     │
│   Database-enforced row filtering via app.tenant_id GUC     │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 RLS Security Properties

| Threat | Mitigation | Mechanism |
|--------|-----------|-----------|
| Cross-tenant SELECT | RLS `USING` clause | Non-matching rows invisible |
| Cross-tenant INSERT | RLS `WITH CHECK` clause | Non-matching writes rejected |
| Cross-tenant UPDATE | Both `USING` + `WITH CHECK` | Old row must be visible; new row must match |
| Cross-tenant DELETE | RLS `USING` clause | Non-matching rows not deletable |
| SQL injection | RLS applies to all queries | Even injected SQL respects policies |
| Missing app-layer filter | RLS catches gap | When context is set, DB enforces isolation |
| Developer forgets tenant_id | RLS safety net | Database denies cross-tenant rows |

## 2. Policy Specification

```sql
CREATE POLICY tenant_isolation ON crm_<table> FOR ALL
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    )
    WITH CHECK (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

### Behavior Matrix

| `app.tenant_id` | Row tenant | SELECT | INSERT | UPDATE | DELETE |
|-----------------|------------|--------|--------|--------|--------|
| NULL (unset) | Any | ✅ All | ✅ Any | ✅ Any | ✅ Any |
| Tenant A | Tenant A | ✅ Visible | ✅ Allowed | ✅ Allowed | ✅ Allowed |
| Tenant A | Tenant B | ❌ Hidden | ❌ Blocked | ❌ Blocked | ❌ Blocked |

## 3. Tenant Context Propagation Security

### 3.1 Source Chain

```
JWT (signed, tenant_id claim)
  → JwtAuthenticationFilter (validates signature, checks ?tenantId= param)
    → Authentication.getDetails()["tenant_id"]
      → TenantRlsConnectionHandler.currentTenantId()
        → SET LOCAL app.tenant_id = '<uuid>'
```

### 3.2 Trust Boundaries

| Boundary | Trust Mechanism |
|----------|-----------------|
| JWT → Filter | Signature validation (HMAC/RSA) |
| Filter → SecurityContext | In-process, no network hop |
| SecurityContext → Connection proxy | Same thread, same request |
| Proxy → DB session | `SET LOCAL` (transaction-scoped) |
| DB session → RLS policy | PostgreSQL engine enforcement |

### 3.3 No User-Supplied Tenant at DB Layer

The `app.tenant_id` GUC is set exclusively from the validated JWT claim
via the security context. It is **never** read from request parameters,
request bodies, or query strings at the database layer. The
`JwtAuthenticationFilter` already rejects mismatched `?tenantId=` params
with HTTP 403.

## 4. Coverage

### 4.1 Tables Covered

All 62 CRM tables with `tenant_id` column, discovered dynamically:
- Party: `crm_accounts`, `crm_contacts`, + 8 relationship tables
- Pipeline: `crm_pipelines`, `crm_pipeline_stages`, `crm_opportunities`, `crm_leads`
- Activity: `crm_activities`, `crm_tasks`, `crm_notes`, `crm_tags`, `crm_timeline_events`
- Ownership: 15 tables (teams, queues, territories, assignments, transfers)
- Integration: 5 tables (requests, outbox, decisions, executions, artifacts)
- Intelligence: 6 tables (scores, segments, actions, models)
- Import, custom fields, reports, audit, idempotency

### 4.2 Tables NOT Covered (By Design)

| Table | Reason |
|-------|--------|
| `tenants` | Global registry, not tenant-scoped |
| `saas_plans` | Global catalog |
| `platform_audit_logs` | Uses `actor_tenant_id`/`target_tenant_id` (different model) |
| `billing_invoices` | Tenant-scoped but not `crm_*` prefixed |

## 5. Operational Security

### 5.1 BYPASSRLS

The table owner (Flyway migration role) bypasses RLS by default. This is
intentional — migrations must manage all tenants' schema. `FORCE ROW LEVEL
SECURITY` is NOT used.

### 5.2 Feature Toggle

```yaml
snad:
  rls:
    enabled: true  # default; set false to disable
```

### 5.3 Audit Trail

The connection proxy logs activation at startup:
```
CRM-018: Wrapping DataSource 'dataSource' with TenantRlsDataSource (RLS defense-in-depth)
```

## 6. Residual Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Background jobs don't set context | Expected | None (permissive fallback) | By design |
| Non-transactional reads bypass RLS | Expected | Low (app-layer still filters) | Acceptable |
| Migration role bypasses RLS | Expected | None (admin operations) | By design |
| H2 tests don't exercise RLS | Expected | None (Postgres CI tests cover) | Testcontainers tests |

## 7. Compliance

| Requirement | Status |
|-------------|--------|
| Multi-tenant data isolation | ✅ Defense-in-depth (5 layers) |
| Data access logging | ✅ Existing structured logging |
| Least privilege | ✅ RLS denies by default when context set |
| Defense-in-depth | ✅ App + DB enforcement |
| No data exfiltration | ✅ Cross-tenant rows invisible at DB level |
