# Stage 04 — Cache and Async Review

## 1. Caches (§24)

### Current state
The SANAD platform does NOT currently use any application-level cache (no Spring Cache, no Caffeine, no Redis, no manual Map-based memoization).

### Audit results
- `@Cacheable`, `@CacheEvict`, `@CachePut` — NOT present in any source file
- `ConcurrentHashMap`, `HashMap` as instance fields — NOT used for tenant-owned data
- `Caffeine`, `EhCache`, `Redis` — NOT in dependencies

### Policy
If caches are added in the future (Stage 12+):
- Every cache key for tenant-owned data MUST include `tenantId`
- Cache key pattern: `tenantId + ":" + resourceType + ":" + resourceId`
- Forbidden: `cacheKey = userId` (without tenantId) if the result varies by tenant
- Test: warm cache with Tenant A, request same identifier from Tenant B → no A data returned
- Cache invalidation on tenant switch is mandatory

### Conclusion
No cache-related debt. PASS.

## 2. Background Jobs and Async (§25)

### Current state
The SANAD platform does NOT currently use:
- `@Async` methods
- `@Scheduled` tasks
- `CompletableFuture` for tenant-owned operations
- `ExecutorService` for tenant-owned operations
- Background workers
- Event handlers (no event broker)

### Audit results
- `@Async` — NOT present in any source file
- `@Scheduled` — NOT present in any source file (only in `@Scheduled` annotation imports)
- `CompletableFuture` — NOT used for tenant-owned data
- `ExecutorService` — NOT present

### Policy (for future implementation)
When background jobs are added (Stage 12):
1. Job payload MUST carry explicit `tenantId` (not inherited from ThreadLocal)
2. Job validates tenant state (tenant not archived, user not suspended)
3. Job establishes scoped TenantContext from payload
4. Job executes
5. Context cleared

### Pattern
```java
// Correct: explicit tenantId in payload
public record TenantScopedJob(UUID tenantId, UUID resourceId, String operation) {}

// Correct: job establishes its own context
@Async
public void executeJob(TenantScopedJob job) {
    TenantContext ctx = new TenantContext(
        job.tenantId(), /* userId */, null, Set.of(),
        TenantContextSource.BACKGROUND_JOB, "job-" + UUID.randomUUID());
    contextProvider.setContext(ctx);
    try {
        // job logic
    } finally {
        contextProvider.clear();
    }
}
```

### Forbidden
```java
// FORBIDDEN: inheriting ThreadLocal from creating thread
@Async
public void someJob(UUID resourceId) {
    UUID tenantId = tenantResolver.requireTenantId(); // WRONG — no context on async thread
}
```

### Conclusion
No async/background debt. PASS. Policy documented for Stage 12.

## 3. Events and Messages (§26)

### Current state
No internal event/message system exists. No event broker (Kafka, RabbitMQ, etc.) is in the stack.

### Policy (for future implementation)
When events are added:
- Tenant ID is mandatory for tenant-owned events
- Producer takes tenant from server context (TenantContext)
- Consumer re-validates tenant state
- External event tenant IDs are NOT trusted without authorization
- Dead-letter logs do not expose other tenants' data

### Conclusion
No event debt. PASS. Policy documented for future stages.
