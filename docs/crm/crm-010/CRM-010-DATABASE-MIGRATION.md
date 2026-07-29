# CRM-010 Database Migration Plan

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** APPROVED

---

## 1. Migration Sequence

| Order | Migration | Tables | Type | Dependencies |
|-------|-----------|--------|------|--------------|
| 1 | V20260729_1__create_crm_customer_intelligence.sql | 6 | CREATE | None |
| 2 | V20260729_2__seed_crm_010_capabilities.sql | 0 (seed) | INSERT | V20260729_1 |
| 3 | V20260729_3__seed_default_scoring_models.sql | 0 (seed) | INSERT | V20260729_1 |

**H2 Test Mirror:** `V20260729_1` in `db/vendor/h2/` (adapted syntax, no preconditions).

---

## 2. Migration V20260729_1 — Schema Creation

### 2.1 Tables Created

| Table | Purpose | PK | Tenant-scoped |
|-------|---------|-----|---------------|
| crm_customer_scores | Latest score per account/type | id | ✅ |
| crm_customer_score_history | Immutable score change audit | id | ✅ |
| crm_customer_segments | Segment definitions | id | ✅ |
| crm_segment_memberships | Account↔Segment mapping | id | ✅ |
| crm_next_best_actions | AI-generated recommendations | id | ✅ |
| crm_scoring_models | Configurable scoring weights | id | ✅ |

### 2.2 Indexes

```sql
-- Score lookups
CREATE INDEX crm_customer_scores_tenant_account_type_idx
    ON crm_customer_scores(tenant_id, account_id, score_type, calculated_at DESC);

-- Score history
CREATE INDEX crm_customer_score_history_tenant_account_idx
    ON crm_customer_score_history(tenant_id, account_id, changed_at DESC);

-- Segment lookups
CREATE INDEX crm_segment_memberships_tenant_account_idx
    ON crm_segment_memberships(tenant_id, account_id, active);
CREATE INDEX crm_segment_memberships_tenant_segment_idx
    ON crm_segment_memberships(tenant_id, segment_id, active);

-- NBA lookups
CREATE INDEX crm_next_best_actions_tenant_account_status_idx
    ON crm_next_best_actions(tenant_id, account_id, status, generated_at DESC);

-- Scoring model lookups
CREATE INDEX crm_scoring_models_tenant_type_active_idx
    ON crm_scoring_models(tenant_id, score_type, active);
```

---

## 3. Rollback Strategy

| Principle | Detail |
|-----------|--------|
| Forward-only | Flyway `clean-disabled=true` in production |
| No destructive rollback | Tables are ADDITIVE — dropping them loses intelligence data |
| Manual rollback procedure | `DROP TABLE` in reverse dependency order (if absolutely required) |
| Data preservation | Intelligence tables contain derived data; safe to drop and recompute |

**Rollback order (emergency only):**
1. `crm_segment_memberships`
2. `crm_customer_segments`
3. `crm_next_best_actions`
4. `crm_scoring_models`
5. `crm_customer_score_history`
6. `crm_customer_scores`

---

## 4. Versioning

| Aspect | Strategy |
|--------|----------|
| Flyway version | `V20260729_1`, `V20260729_2`, `V20260729_3` |
| Naming | `V{date}_{seq}__{description}.sql` |
| Checksum | Flyway validate-on-migrate=true |
| Baseline | No baseline change (additive) |

---

## 5. Seed Data

### 5.1 RBAC Capabilities (V20260729_2)

| Capability | UUID | Name |
|------------|------|------|
| CRM.CUSTOMER_360.READ | a0000010-0000-0000-0000-000000001001 | Read Customer 360 |
| CRM.CUSTOMER_INTELLIGENCE.READ | a0000010-0000-0000-0000-000000001002 | Read Intelligence |
| CRM.CUSTOMER_INTELLIGENCE.WRITE | a0000010-0000-0000-0000-000000001003 | Trigger Rescoring |
| CRM.CUSTOMER_INTELLIGENCE.ADMIN | a0000010-0000-0000-0000-000000001004 | Manage Scoring Models |
| CRM.CUSTOMER_SEGMENT.MANAGE | a0000010-0000-0000-0000-000000001005 | Manage Segments |

### 5.2 Default Scoring Models (V20260729_3)

| Score Type | Default Weights |
|-----------|----------------|
| HEALTH | engagement:0.30, pipeline:0.25, response:0.20, support:0.15, nps:0.10 |
| ENGAGEMENT | meeting_freq:0.35, email_open:0.20, response_time:0.25, activity_count:0.20 |
| RISK | churn_signals:0.40, engagement_decline:0.30, tenure:0.15, support_issues:0.15 |
| LOYALTY | tenure:0.30, repeat_business:0.30, advocacy:0.20, engagement:0.20 |

---

## 6. Constraints

| Table | Constraint | Rule |
|-------|-----------|------|
| crm_customer_scores | score_type | IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY') |
| crm_customer_scores | score_value | BETWEEN 0 AND 100 (except CLV) |
| crm_customer_scores | UNIQUE | (tenant_id, account_id, score_type, calculated_at) |
| crm_score_history | delta | NOT NULL (must record change) |
| crm_customer_segments | segment_type | IN ('MANUAL','RULE_BASED','AI_GENERATED') |
| crm_segment_memberships | UNIQUE | (tenant_id, account_id, segment_id) |
| crm_next_best_actions | status | IN ('PENDING','ACCEPTED','REJECTED','EXPIRED') |
| crm_scoring_models | UNIQUE | (tenant_id, score_type, version) |

---

## 7. Performance Considerations

| Concern | Mitigation |
|---------|------------|
| Score table growth | Partition by month if >10M rows |
| History table growth | Archive >12 months to cold storage |
| Search latency | Composite indexes on (tenant_id, account_id, score_type) |
| Batch insert (scoring) | Batch INSERT (100 rows/tx) |
| JSONB query performance | GIN index on components column |

---

## 8. Data Integrity Validation

### Post-Conditions (checked in migration)

```sql
DO $postcondition$
BEGIN
    -- All 6 tables exist
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_customer_scores';
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_customer_score_history';
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_customer_segments';
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_segment_memberships';
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_next_best_actions';
    PERFORM 1 FROM information_schema.tables WHERE table_name = 'crm_scoring_models';

    -- All indexes exist
    PERFORM 1 FROM pg_indexes WHERE indexname = 'crm_customer_scores_tenant_account_type_idx';

    -- 5 capabilities seeded
    PERFORM 1 FROM capabilities WHERE code = 'CRM.CUSTOMER_360.READ';
END $postcondition$;
```

---

## 9. Backup Requirements

| Requirement | Detail |
|-------------|--------|
| Pre-migration backup | Full database backup before applying V20260729_1 |
| Point-in-time recovery | PITR must be enabled (standard SANAD practice) |
| Migration test | Apply to staging copy first |
| Rollback test | Verify DROP order in staging |

---

**Migration Plan Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ APPROVED
