# CRM-010 Cache Strategy

## Overview

The Customer Intelligence module uses Caffeine in-memory caching for two data categories: score data and assembled Customer 360 views.

## Cache Configuration

| Property | Value | Rationale |
|----------|-------|-----------|
| TTL | 5 minutes | Balances freshness vs. performance; scores update at most every few minutes |
| Maximum size | 10,000 entries | Prevents unbounded memory growth |
| Statistics | Enabled | Provides cache hit ratio metrics for monitoring |

## Cache Instances

### 1. Scores Cache

| Attribute | Value |
|-----------|-------|
| Name | `scores` |
| Key pattern | `scores:v1:{tenantId}:{accountId}` |
| Value type | `List<StoredScore>` |
| Operations | `getScores`, `putScores`, `invalidateScores` |

**Purpose:** Caches the latest scores for an account, avoiding repeated database queries.

### 2. View Cache

| Attribute | Value |
|-----------|-------|
| Name | `view` |
| Key pattern | `view:v1:{tenantId}:{accountId}` |
| Value type | `Map<String, Object>` (Customer 360 view) |
| Operations | `getView`, `putView`, `invalidateView` |

**Purpose:** Caches the fully assembled Customer 360 view (profile + intelligence).

## Cache Key Design

### Versioned Keys

All cache keys include a version prefix (`v1`) to support cache busting on schema changes:

```
scores:v1:{tenantId}:{accountId}
view:v1:{tenantId}:{accountId}
```

### Tenant Isolation

Cache keys embed `tenantId` to ensure complete tenant isolation. Different tenants never share cache entries, even for the same `accountId`.

## Invalidation Strategy

### Write-Through Invalidation

All write operations call `cache.invalidateAll(tenantId, accountId)` which clears both caches for the affected account:

- Score calculation (health, CLV, churn, all scores)
- Segment membership changes (add/remove)
- NBA lifecycle changes (accept/reject)

### Read-Through Population

Read operations follow cache-through pattern:

```
1. Check cache → if hit, return
2. Query database
3. Populate cache
4. Return
```

### No Proactive Refresh

The cache does not proactively refresh. Stale data is acceptable within the 5-minute TTL window, as scores are recalculated on-demand.

## Cache Metrics

Caffeine statistics are exposed via:

```java
cache.scoresStats()  // Returns Stats (hitCount, missCount, loadCount, etc.)
cache.viewStats()    // Returns Stats for view cache
```

These metrics integrate with Micrometer/Prometheus for monitoring:
- Cache hit ratio
- Eviction count
- Load time

## Performance Targets

| Operation | Target | Max | Cache Impact |
|-----------|--------|-----|--------------|
| Customer 360 load | 100ms | 500ms | Cache hit avoids DB + assembly |
| Score query | 50ms | 200ms | Cache hit returns immediately |
| Score write | 200ms | 1s | Cache invalidation is fast (no blocking) |

## Memory Considerations

With 10,000 maximum entries per cache:
- **Scores cache:** ~100 bytes per score × 5 scores/account × 10,000 = ~5MB
- **View cache:** ~2KB per view × 10,000 = ~20MB
- **Total:** ~25MB maximum heap usage

## Cache Bypass

Cache is bypassed in these scenarios:
- Direct database queries via `CustomerIntelligenceQueryPort` (e.g., score history)
- Transactional writes (writes invalidate, not read)
- AI Gateway calls (always fresh computation)
