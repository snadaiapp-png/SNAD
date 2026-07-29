# CRM-010 Customer360 Architecture Decision Record (ADR)

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** ACCEPTED

---

## 1. Context

CRM-010 requires a unified customer view that aggregates data from multiple CRM modules (accounts, contacts, opportunities, activities, timeline) and enriches it with AI-generated intelligence (health scores, CLV, churn risk, next best actions). The architecture must support:

- Low-latency reads (<300ms) for the Customer 360 dashboard
- Eventually-consistent AI score updates (async via outbox)
- Tenant isolation at all layers
- Forward-only schema evolution
- No external system dependencies for v1

---

## 2. Decision

**Selected: CQRS Read-Model Projection with Async Intelligence Enrichment**

```
┌──────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Write Side  │────▶│  Domain Events   │────▶│  Read Model     │
│ (CRM writes) │     │  (outbox)        │     │  Projection     │
└──────────────┘     └──────────────────┘     └────────┬────────┘
                                                        │
┌──────────────┐     ┌──────────────────┐     ┌────────▼────────┐
│  AI Gateway  │◀───▶│  Scoring Worker  │◀───▶│  Score Store    │
│  (external)  │     │  (scheduled)     │     │  (scores table) │
└──────────────┘     └──────────────────┘     └────────┬────────┘
                                                        │
                     ┌──────────────────┐     ┌────────▼────────┐
                     │  Customer360     │◀───▶│  Query Port     │
                     │  Controller      │     │  (aggregation)  │
                     └──────────────────┘     └─────────────────┘
```

---

## 3. Alternatives Considered

### Alternative A: Synchronous Multi-Query Aggregation

**Rejected** — Querying 6+ tables on every request causes unacceptable latency (>1s) and N+1 problems. No caching = repeated work.

### Alternative B: Materialized View Refresh

**Rejected** — PostgreSQL materialized views require `REFRESH` (blocking), cannot be tenant-filtered efficiently, and don't support incremental updates well.

### Alternative C: Event Sourcing with Full Replay

**Rejected** — Overkill for this use case. Adds complexity (event store, projection management, replay infrastructure) without proportional benefit since CRM already has audit tables.

### Alternative D: Denormalized "Wide Table"

**Partially adopted** — The `crm_customer_scores` table IS a denormalized projection, but we avoid a single monolithic wide table in favor of modular projections (scores separate from profile data).

---

## 4. Decision Rationale

| Factor | CQRS Read-Model | Multi-Query | Materialized View | Event Sourcing |
|--------|----------------|-------------|-------------------|----------------|
| Read latency | ✅ <100ms | ❌ >1s | ✅ <100ms | ✅ <100ms |
| Write impact | ✅ None | ✅ None | ⚠️ REFRESH blocks | ⚠️ Event store write |
| Incremental update | ✅ Yes | N/A | ❌ No | ✅ Yes |
| Tenant isolation | ✅ Easy | ✅ Easy | ❌ Hard | ✅ Easy |
| Complexity | ✅ Moderate | ✅ Low | ✅ Low | ❌ High |
| AI async support | ✅ Yes | ❌ No | ❌ No | ✅ Yes |

---

## 5. Data Flow

### 5.1 Write Flow (CRM mutations)

```
1. CRM module writes (e.g., Activity created)
2. AuditPort records change
3. TimelineEventPort records event
4. Write committed
5. Scoring worker detects stale score (next scheduled run)
6. AI Gateway recalculates score (async)
7. Score store updated
8. AuditPort records score change
```

### 5.2 Read Flow (Customer 360 query)

```
1. Controller receives GET /customer-360/{accountId}
2. Customer360QueryPort.findById()
3. Aggregates from: crm_accounts, contacts, opportunities, activities, timeline
4. Left-joins crm_customer_scores (latest per type)
5. Left-joins crm_segment_memberships
6. Returns Customer360View (single response)
```

### 5.3 Intelligence Flow (AI score request)

```
1. Scheduled/manual trigger
2. ScoringUseCases creates score request via outbox
3. ScoringOutboxWorker claims event
4. Calls AiGatewayPort.request() (HEALTH_SCORING)
5. Receives AiResult
6. transitionWithResult() stores score (immutable)
7. Score history recorded
8. Audit + timeline emitted
```

---

## 6. Synchronization Strategy

| Data Type | Sync Strategy | Latency |
|-----------|--------------|---------|
| Profile data (accounts, contacts) | Real-time (DB read) | <100ms |
| Scores (health, CLV, etc.) | Async (scheduled rescore) | 1–24h |
| Timeline events | Real-time (DB read) | <100ms |
| Segment memberships | Real-time (DB read) | <100ms |
| Next Best Actions | On-demand (AI request) | 5–10s |

**Rationale:** Profile data changes infrequently and is read-heavy → direct DB read. Scores change periodically → scheduled recalculation is sufficient and avoids AI Gateway load.

---

## 7. Event Flow

```
CRM Write ──▶ Audit ──▶ Timeline
                │
                ▼
         Score Staleness Detector
                │
                ▼ (scheduled)
         Scoring Outbox Event
                │
                ▼
         AI Gateway Request
                │
                ▼
         Score Update ──▶ Score History
                │
                ├──▶ Audit
                ├──▶ Timeline
                └──▶ Threshold Check ──▶ Workflow Trigger (optional)
```

---

## 8. Read Model Strategy

### 8.1 Query Aggregation (No Separate Read Store)

The `Customer360QueryPort` performs multi-table joins at query time. This is viable because:
- All tables are tenant-partitioned by `tenant_id` (indexed)
- Account ID lookups are O(1) via primary key
- Intelligence scores are in a separate table (1:1 with account)
- Result set is bounded (1 account per query)

### 8.2 Score Cache Table

`crm_customer_scores` acts as a cache of the latest AI-computed score. This separates expensive AI computation from read-path queries.

---

## 9. Caching Strategy

| Layer | Strategy | TTL | Invalidation |
|-------|----------|-----|--------------|
| Application (Caffeine) | Customer360View by accountId | 5 min | On score update event |
| Database | Indexes on all query paths | — | — |
| AI Score Cache | crm_customer_scores table | Until rescored | On rescore |
| HTTP (CDN) | Not cached (dynamic per-user) | — | — |

**Rationale:** 5-minute application cache balances freshness with performance. CRM data changes are eventually consistent; scores update on a longer cycle anyway.

---

## 10. Scalability Considerations

| Concern | Mitigation |
|---------|------------|
| Large tenant (100K+ accounts) | Keyset pagination on search; batch scoring (100/batch) |
| Score recalculation load | Off-peak scheduling; configurable batch size; rate limiting |
| AI Gateway throughput | Outbox pattern prevents flooding; configurable worker interval |
| Timeline aggregation growth | Partitioned by time; paginated reads |
| Search performance | Trigram index on display_name; GIN on email |

---

**ADR Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ ACCEPTED
