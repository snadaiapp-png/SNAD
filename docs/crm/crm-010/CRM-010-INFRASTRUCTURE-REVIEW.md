# CRM-010 Infrastructure Review

**Reviewer:** Agent 3 (CRM-010-AGENT-003)
**Date:** 2026-07-29

---

## 1. JDBC Adapters

### 1.1 JdbcScoringAdapter

| Check | Status | Evidence |
|-------|--------|----------|
| Uses NamedParameterJdbcTemplate | ✅ | All queries use `:param` syntax |
| Tenant isolation in all queries | ✅ | `WHERE tenant_id = :tenantId` on all 6 queries |
| @Transactional on class | ❌ | No transactional annotation anywhere |
| saveScore() atomicity | ❌ | Two independent writes (INSERT + history INSERT) without transaction boundary |
| saveModel() race condition | ❌ | Deactivate-then-insert not atomic, can create duplicate active models |
| saveModel() return value | ❌ | Returns fabricated `UUID.randomUUID()` instead of DB-generated ID |

**saveModel() Race Condition Detail:**
```
Thread 1: UPDATE SET active=false WHERE tenant_id=X AND score_type=Y
Thread 2: UPDATE SET active=false WHERE tenant_id=X AND score_type=Y  (no-op, already inactive)
Thread 1: INSERT ... active=true  ✓
Thread 2: INSERT ... active=true  ✓  -- DUPLICATE ACTIVE MODEL
```

**Fix:** Use `INSERT ... ON CONFLICT (tenant_id, score_type) DO UPDATE SET ...` with `RETURNING id`.

### 1.2 JdbcSegmentAdapter

| Check | Status | Evidence |
|-------|--------|----------|
| Uses NamedParameterJdbcTemplate | ✅ | All queries use `:param` syntax |
| Tenant isolation in all queries | ✅ | `WHERE tenant_id = :tenantId` on all 5 queries |
| Unbounded queries | ⚠️ | `findActiveMemberships()` has no LIMIT |
| @Transactional on class | ❌ | No transactional annotation |

### 1.3 JdbcNextBestActionAdapter

| Check | Status | Evidence |
|-------|--------|----------|
| Uses NamedParameterJdbcTemplate | ✅ | All queries use `:param` syntax |
| Tenant isolation in all queries | ✅ | `WHERE tenant_id = :tenantId` on all 3 queries |
| Optimistic locking | ✅ | Uses `RETURNING version` for concurrent update detection |
| @Transactional on class | ❌ | No transactional annotation |

### 1.4 JdbcCustomerIntelligenceQueryAdapter

| Check | Status | Evidence |
|-------|--------|----------|
| Uses NamedParameterJdbcTemplate | ✅ | All queries use `:param` syntax |
| Tenant isolation in all queries | ✅ | `WHERE tenant_id = :tenantId` on all 6 queries |
| Unbounded queries | ⚠️ | `findAllSegments()` has no LIMIT |
| Index coverage | ⚠️ | Missing index for `expires_at` and `score_type` filters |

---

## 2. Cache Infrastructure

| Check | Status | Evidence |
|-------|--------|----------|
| Tenant-scoped keys | ✅ | Keys: `scores:v1:{tenantId}:{accountId}` |
| TTL appropriate | ✅ | 5 minutes |
| Size bounded | ✅ | MAX_SIZE = 10,000 |
| Stats enabled | ✅ | `recordStats()` for monitoring |
| Defensive copies | ❌ | Mutable collections stored by reference |
| Per-key locking | ❌ | Uses getIfPresent + put instead of cache.get(key, loader) |

**Mutable Collection Risk:**
- `scoresCache` stores `List<StoredScore>` — elements immutable but list wrapper mutable
- `viewCache` stores `Map<String, Object>` — all mutable
- If any caller modifies the returned collection, cached data is permanently corrupted

---

## 3. Event Publisher

| Check | Status | Evidence |
|-------|--------|----------|
| Implements EventPublisherPort | ✅ | Delegates to Spring ApplicationEventPublisher |
| Error handling | ❌ | No try-catch; listener exception rolls back entire transaction |
| Event ordering | ✅ | Published after persistence within transaction |

---

## 4. Configuration

| Check | Status | Evidence |
|-------|--------|----------|
| Properties externalized | ✅ | All via `${ENV_VAR:default}` |
| Dev profile Flyway config | ❌ | Missing `classpath:db/vendor/{vendor}` location |
| Production profile secure | ✅ | No hardcoded secrets, Swagger disabled |

**Dev Profile Issue:**
```yaml
# application-dev.yml line 28
locations: classpath:db/migration
# MISSING: classpath:db/vendor/{vendor}
# Intelligence tables DDL is in db/vendor/postgresql/
```

---

## 5. Migration Files

| File | Tables | Status |
|------|--------|--------|
| `V20260729_1__create_crm_customer_intelligence.sql` | 7 tables | ✅ Exists |
| H2 test mirror | Same 7 tables | ✅ Exists |

Tables: `crm_customer_scores`, `crm_customer_score_history`, `crm_scoring_models`, `crm_customer_segments`, `crm_segment_memberships`, `crm_next_best_actions`, `crm_customer_insights`

---

## 6. Summary

| Category | Critical | High | Medium |
|----------|----------|------|--------|
| JdbcScoringAdapter | 2 | 0 | 0 |
| JdbcSegmentAdapter | 0 | 0 | 1 |
| Cache | 0 | 1 | 1 |
| Event Publisher | 0 | 1 | 0 |
| Configuration | 0 | 1 | 0 |
| **Total** | **2** | **3** | **2** |

**Overall: CRITICAL issues in JdbcScoringAdapter require fix before merge.**
