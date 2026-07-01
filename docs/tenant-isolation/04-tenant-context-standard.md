# Stage 04 — Tenant Context Standard

## 1. TenantContext Record

```java
public record TenantContext(
    UUID tenantId,           // Verified from JWT — never from client
    UUID userId,             // Verified from JWT subject
    String sessionId,        // Currently stores email (session-id tracking pending)
    Set<String> capabilities, // Empty in current impl; @RequireCapability is source of truth
    TenantContextSource source, // JWT_CLAIM | MEMBERSHIP_SELECTOR | BACKGROUND_JOB | TEST_FIXTURE
    String requestId         // From X-Request-Id header or generated UUID
) {}
```

## 2. TenantContextProvider

- `requireContext()` → returns current context, throws `TenantContextException` if none
- `currentContext()` → returns `Optional<TenantContext>`
- `setContext(context)` → called by TenantContextFilter after auth
- `clear()` → called by TenantContextFilter in `finally`

## 3. Implementation: ThreadLocalTenantContextProvider

- Uses `ThreadLocal<TenantContext>` (NOT `InheritableThreadLocal`)
- Context does NOT propagate to child threads
- Context is cleared in `finally` — never leaks to pooled threads
- MDC is populated for logging but NEVER trusted as authority

## 4. TenantContextFilter

- Runs AFTER `JwtAuthenticationFilter` in the Spring Security filter chain
- Registered via `http.addFilterAfter(...)` in SecurityConfig
- NOT a `@Component` (avoids auto-registration breaking @WebMvcTest slices)
- Reads `Authentication.details` (populated by JwtAuthenticationFilter)
- Builds `TenantContext` from verified `tenant_id` and `user_id` claims
- Sets context via `TenantContextProvider.setContext()`
- Populates MDC: `tenantId`, `userId`
- In `finally`: clears context + MDC

## 5. TenantResolver

Helper for services to get the verified tenantId:
- `requireTenantId()` → from TenantContext, throws if none
- `currentTenantId()` → from TenantContext, null if none
- `validateClientSelector(UUID clientTenantId)` → validates client-supplied tenantId against context (§9 transitional)

## 6. TenantRlsBinder

- Binds `app.current_tenant_id` to the PostgreSQL connection via `SET LOCAL`
- Called inside `@Transactional` methods
- `SET LOCAL` scopes the setting to the current transaction
- Setting is automatically cleared when the transaction ends
- On H2 (local profile): no-op (H2 doesn't support SET LOCAL)
- If no TenantContext: no setting → RLS fails closed (0 rows)

## 7. Lifecycle Rules (§8)

1. Context created AFTER authentication ✓
2. Context cleared in `finally` ✓
3. Context does NOT propagate to other threads (ThreadLocal, not Inheritable) ✓
4. Context does NOT persist after request (cleared in finally) ✓
5. Context NOT shared between requests (ThreadLocal per request) ✓
6. MDC NOT trusted as authority source ✓

## 8. Tests

- `TenantContextLifecycleTest` (10 tests):
  - requireContext throws when no context
  - currentContext empty when no context
  - setContext + requireContext returns same context
  - clear removes context
  - Sequential requests do not share context
  - Context does NOT propagate to child threads
  - Failed request clears context
  - TenantContext is immutable
  - matchesTenant() correctness
  - hasCapability() correctness
