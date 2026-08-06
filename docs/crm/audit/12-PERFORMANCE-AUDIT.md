# Performance Audit Report — CRM v2.0.0

**Audit Date:** 2026-07-30  
**Scope:** Query performance, caching, pagination, indexing, memory usage across CRM modules  
**Severity Assessment:** HIGH

---

## Executive Summary

The CRM codebase exhibits 8 performance-related findings spanning N+1 query patterns, improper caching configurations, missing indexes, pagination defects, and connection pool concerns. While no single finding constitutes an immediate production outage risk, the cumulative effect under growth scenarios is significant. The intelligence module (CRM-010/019) has the highest density of performance defects.

**Performance Health Score: 58/100 — MODERATE**

---

## 1. Hardcoded Cache TTL and Max Size

**ID:** H-04  
**Severity:** HIGH  
**Category:** Caching Misconfiguration  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/config/CustomerIntelligenceProperties.java`
- Cache configuration classes in CRM-010

**Description:**  
Cache time-to-live (TTL) is hardcoded at 5 minutes with a maximum cache size of 10,000 entries. These values are not configurable through environment properties or external configuration. Different data types (customer profiles, scores, segment memberships) have different staleness tolerances and cardinalities, but share the same cache configuration.

**Impact:**
- 5-minute TTL is too short for stable customer attributes (e.g., account tier) causing unnecessary cache misses and database load
- 5-minute TTL is too long for volatile data (e.g., real-time scores) risking stale data presentation
- 10,000 entry cap may cause cache thrashing under multi-tenant production loads with hundreds of thousands of customers
- Cannot tune per-tenant or per-data-type without code change

**Evidence:**  
The `CustomerIntelligenceProperties` class defines static integer fields for `CACHE_TTL_MINUTES = 5` and `MAX_CACHE_SIZE = 10000`. No `@ConfigurationProperties` binding to application.yml exists.

**Recommendation:**
1. Externalize cache TTL and max size to `application.yml` via `@ConfigurationProperties`
2. Implement per-cache-region configuration (profile cache vs score cache vs segment cache)
3. Set region-specific defaults: profile cache 30 min, score cache 2 min, segment cache 15 min
4. Add cache hit/miss ratio metrics to monitor effectiveness

---

## 2. Hardcoded AI Gateway Timeout

**ID:** H-05  
**Severity:** HIGH  
**Category:** Configuration Hardcoding  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/orchestration/HttpAiGatewayAdapter.java`

**Description:**  
The AI Gateway HTTP client timeout is hardcoded at 30 seconds. This is not configurable through environment properties. Under network latency or upstream service degradation, the 30-second timeout can cause thread pool exhaustion in the application server, especially under concurrent request load.

**Impact:**
- 30 seconds is excessively long for synchronous request processing; thread pool may be exhausted under moderate concurrency
- Cannot reduce timeout in production without code change and redeployment
- No circuit breaker pattern to fail fast when AI Gateway is degraded
- No timeout differentiation between connect, read, and request timeouts

**Evidence:**  
The `HttpAiGatewayAdapter` creates `HttpClient` instances with a hardcoded timeout value. No configuration binding is present.

**Recommendation:**
1. Externalize connect timeout, read timeout, and request timeout to application.yml
2. Implement a circuit breaker (Resilience4j) with reasonable defaults: connect 2s, read 10s
3. Add graceful degradation path when AI Gateway timeout occurs (fallback to local scoring)

---

## 3. Pipeline Board Missing Virtualization for Large Lists

**ID:** H-06  
**Severity:** MEDIUM  
**Category:** UI Performance  
**Files Affected:**
- `apps/web/app/crm/(operational)/pipelines/page.tsx`
- `apps/web/app/crm/components/pipeline-tab.tsx`

**Description:**  
The pipeline board component renders all opportunities in each pipeline stage column without virtualizing the list. For organizations with hundreds of opportunities per stage, this causes significant DOM rendering overhead, degraded scroll performance, and increased memory usage on the client.

**Impact:**
- Performance degradation proportional to total opportunity count
- Browser frame drops and jank on large pipeline boards
- Memory pressure on client devices, especially mobile
- No graceful degradation; large datasets become unusable

**Evidence:**  
The pipeline board uses `useMemo` for filtering but no virtualization library (e.g., `react-window`, `react-virtuoso`) is imported or used. Stage columns render all items unconditionally.

**Recommendation:**
1. Implement windowed rendering using `react-virtuoso` or `react-window`
2. Set a reasonable page size (e.g., 25 items per stage) with infinite scroll
3. Add "Show More" pagination as fallback for oversized datasets
4. Consider server-side pagination for stage queries if latency is acceptable

---

## 4. Customer 360 Raw Type Suppression Hides Performance Issues

**ID:** H-07  
**Severity:** MEDIUM  
**Category:** Query Performance  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/application/Customer360ApplicationService.java`

**Description:**  
The `customer360()` endpoint uses `@SuppressWarnings("unchecked")` with raw `Map<String, Object>` returns from `LegacyCrmInfrastructureService`. This pattern hides the actual query structure and prevents the compiler and static analysis tools from detecting performance issues. The underlying queries may be loading excessive data.

**Impact:**
- Cannot determine actual query paths without runtime analysis
- Suppression masks potential N+1 and cartesian product issues
- Raw type usage suggests untyped/unstructured data access that bypasses the domain model
- Impedes optimization because the data flow is opaque

**Evidence:**  
The `customer360()` method in `Customer360ApplicationService` casts `LegacyCrmInfrastructureService` results to raw `Map<String, Object>` and suppresses unchecked warnings.

**Recommendation:**
1. Replace raw `Map<String, Object>` with a typed `Customer360View` DTO
2. Audit the underlying queries for N+1 and excessive data loading
3. Add query logging to capture actual SQL executed
4. Consider decomposing into separate targeted queries rather than one monolithic fetch

---

## 5. No Pagination on List Custom Fields

**ID:** M-01  
**Severity:** MEDIUM  
**Category:** Pagination  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/configuration/infrastructure/JdbcCustomFieldRepository.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/configuration/application/ConfigurationUseCases.java`

**Description:**  
The `listCustomFields` endpoint does not implement pagination. As custom field definitions grow over time (especially in multi-tenant environments with tenant-specific fields), this endpoint will return increasingly large result sets. The lack of pagination also prevents the frontend from implementing incremental loading.

**Impact:**
- Memory pressure on both server and client as custom field count grows
- No limit on response size; a single request could return thousands of records
- No cursor or offset-based pagination for consistent iteration

**Evidence:**  
`ConfigurationUseCases.listCustomFields()` returns all results without pagination parameters.

**Recommendation:**
1. Add `page`, `size`, and `sort` parameters to the endpoint
2. Implement `Pageable` in Spring Data or manual offset/limit in JDBC queries
3. Set a reasonable default page size (e.g., 50)

---

## 6. Missing Indexes on CRM-010 and CRM-007 Tables

**ID:** C-05 (related)  
**Severity:** CRITICAL  
**Category:** Database Indexing  
**Files Affected:**
- CRM-010 migration files (V20260729_*)
- `crm_customer_segments` table

**Description:**  
Multiple tables in the CRM-010 schema lack necessary indexes for query performance:
- `crm_customer_segments` has no index on the `active` column, causing full table scans for active segment lookups
- CRM-010 junction tables lack `tenant_id` indexes, degrading multi-tenant query performance
- Missing FK constraints also means missing FK indexes on 5 CRM-010 tables

**Impact:**
- Full table scans on `crm_customer_segments.active` lookups
- Cross-tenant queries (if any) will be inefficient on junction tables
- As data grows, query performance degrades linearly with table size
- No index for segment membership queries by customer

**Evidence:**  
Examination of CRM-010 DDL shows no index on `crm_customer_segments(active)` and no `tenant_id` index on junction tables.

**Recommendation:**
1. Add index on `crm_customer_segments(active, tenant_id)`
2. Add `tenant_id` indexes on all CRM-010 junction tables
3. Add FK indexes matching foreign key columns
4. Run `ANALYZE` after adding indexes to update query planner statistics

---

## 7. CrmCoreCursorPaginationAspect Reflection Overhead

**ID:** M-02  
**Severity:** MEDIUM  
**Category:** Pagination Infrastructure  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/infrastructure/CrmCoreCursorPaginationAspect.java`

**Description:**  
The custom cursor-based pagination implementation uses an AOP aspect with reflection to extract cursor fields and build pagination queries. This approach introduces runtime reflection overhead for every paginated query. The reflection-based field extraction is not cached, so every paginated request incurs the cost of reflection lookups.

**Impact:**
- Each paginated query pays reflection overhead (field resolution, method invocation)
- No caching of reflected field mappings
- Harder to optimize and debug compared to direct query construction
- Reflection bypasses compiler safety checks

**Evidence:**  
The aspect uses `Field` objects obtained via `Class.getDeclaredField()` without caching the result.

**Recommendation:**
1. Cache reflected field metadata using `ConcurrentHashMap` keyed by entity class
2. Consider replacing the AOP approach with a repository-level abstraction
3. Benchmark reflection overhead against direct query parameterization

---

## 8. N+1 Query Risk in LegacyCrmInfrastructureService

**ID:** C-02 (related)  
**Severity:** CRITICAL  
**Category:** Query Performance  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/infrastructure/LegacyCrmInfrastructureService.java`

**Description:**  
The 2044-line `LegacyCrmInfrastructureService` contains numerous data access methods that iterate over collections and issue individual queries for each element. The monolithic nature of this service makes it difficult to audit all query paths, but several identified patterns show classic N+1 query antipatterns where a list fetch is followed by individual fetches per item.

**Impact:**
- Database round-trips proportional to collection sizes
- Under multi-tenant load, N+1 patterns compound with tenant count
- Connection pool exhaustion risk under concurrent access
- Difficult to identify and fix due to the god class structure

**Evidence:**  
The `LegacyCrmInfrastructureService` contains loops that invoke repository methods inside iteration, creating N+1 patterns. The god class structure makes systematic auditing difficult.

**Recommendation:**
1. Break `LegacyCrmInfrastructureService` into bounded services (see C-02 remediation)
2. Audit all loop-based data access patterns for N+1
3. Replace with batch queries, `IN` clauses, or JOINs
4. Add database query logging to identify N+1 patterns in test and staging environments

---

## 9. Connection Pool Sizing Not Explicitly Configured

**ID:** H-08 (new)  
**Severity:** HIGH  
**Category:** Infrastructure Configuration  
**Files Affected:**
- `apps/sanad-platform/src/main/resources/application.yml` (or environment-specific configs)

**Description:**  
The HikariCP connection pool configuration is not explicitly tuned for the CRM workload. Default pool sizes (typically 10 connections) may be inadequate for the combined load of CRM operations, intelligence scoring, and integration workflows. No pool monitoring or alerting is configured.

**Impact:**
- Connection pool exhaustion under concurrent 360-view and scoring requests
- Thread contention when pool is exhausted
- No visibility into pool utilization; capacity planning is guesswork
- Default pool sizing does not account for AI Gateway timeouts that hold connections open for 30s

**Evidence:**  
Review of configuration files shows no explicit `spring.datasource.hikari.*` settings beyond basic connection URL and credentials.

**Recommendation:**
1. Set explicit pool size based on workload analysis (suggested: `maximum-pool-size: 30`, `minimum-idle: 5`)
2. Configure `connectionTimeout: 5000`, `idleTimeout: 300000`, `maxLifetime: 600000`
3. Add `HikariPoolMXBean` monitoring via Micrometer/Micrometer
4. Set `leakDetectionThreshold: 10000` to detect connection leaks

---

## 10. No Paging for Search and Activity Queries

**ID:** M-03  
**Severity:** MEDIUM  
**Category:** Pagination  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/search/application/SearchUseCases.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/activity/application/ActivityUseCases.java`

**Description:**  
Search results and activity feeds lack proper pagination controls. As the platform accumulates customer activity data, queries will return increasingly large result sets without cursor or offset limits. The search implementation also lacks full-text search capabilities, making it unsuitable for production use at scale.

**Impact:**
- Activity feed queries degrade as data volume grows
- Search without full-text index requires full table scans
- No consistent pagination strategy across query endpoints

**Recommendation:**
1. Implement cursor-based pagination for activity feeds (by `created_at` timestamp)
2. Add full-text search index (PostgreSQL `tsvector`) for search endpoints
3. Set default page sizes with maximum limits to prevent runaway queries

---

## Summary Table

| ID | Finding | Severity | Category | Priority |
|----|---------|----------|----------|----------|
| H-04 | Hardcoded cache TTL (5 min) and max size (10K) | HIGH | Caching | P1 |
| H-05 | Hardcoded AI Gateway timeout (30s) | HIGH | Configuration | P1 |
| H-06 | Pipeline board no virtualization for large lists | MEDIUM | UI Performance | P2 |
| H-07 | customer360() raw type suppression hides issues | MEDIUM | Query Analysis | P2 |
| M-01 | No pagination on listCustomFields | MEDIUM | Pagination | P2 |
| C-05b | Missing indexes on CRM-010 tables (active, tenant_id) | CRITICAL | Indexing | P0 |
| M-02 | CrmCoreCursorPaginationAspect reflection overhead | MEDIUM | Pagination Infrastructure | P2 |
| C-02b | N+1 query risk in LegacyCrmInfrastructureService | CRITICAL | Query Performance | P0 |
| H-08 | Connection pool sizing not explicitly configured | HIGH | Infrastructure | P1 |
| M-03 | No pagination for search and activity queries | MEDIUM | Pagination | P2 |

---

## Recommendations Roadmap

**Immediate (P0):**
1. Add critical indexes on CRM-010 tables (active, tenant_id)
2. Audit and fix N+1 query patterns in `LegacyCrmInfrastructureService`

**Short-term (P1):**
3. Externalize cache TTL, max size, and Gateway timeout to configuration
4. Configure HikariCP connection pool explicitly with monitoring
5. Add pagination to custom fields endpoint

**Medium-term (P2):**
6. Implement virtualization for pipeline board
7. Replace raw types in customer360 with typed DTOs
8. Cache reflection metadata in cursor pagination aspect
9. Implement pagination for search and activity feeds
10. Add full-text search capability

---

*Report generated by independent forensic audit. 10 performance-related findings identified.*
