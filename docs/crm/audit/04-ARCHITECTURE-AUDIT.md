# Architecture Audit

**Audit Scope:** Architecture violations, dependency direction, layer separation, Clean Architecture compliance, Hexagonal Architecture compliance, circular dependencies, layer leakage, broken abstractions.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** CRITICAL -- Architectural boundaries breached in multiple locations.

---

## Executive Summary

The SNAD CRM codebase nominally follows a Clean Architecture / Hexagonal Architecture pattern with distinct `domain`, `application`, and `infrastructure` packages. However, this audit reveals systematic violations of architectural boundaries. The most severe is `LegacyCrmInfrastructureService`, a 2,044-line god class in the infrastructure layer that contains business logic, raw SQL, XML parsing, encryption, and HTTP concerns. Additionally, AOP aspects perform database operations, controllers contain business logic, and the demo ownership module is unconditionally active across all profiles.

---

## Finding ARC-01: LegacyCrmInfrastructureService -- Complete Layer Boundary Collapse

**Severity:** CRITICAL
**Category:** Layer Leakage / Infrastructure Leak

### Description
`LegacyCrmInfrastructureService` located in the `infrastructure` package (`com.sanad.platform.crm.legacy.infrastructure`) violates every architectural boundary simultaneously:
- Contains **business logic** (scoring calculations, state transitions, validation rules)
- Contains **raw SQL queries** (directly executing JDBC operations)
- Contains **encryption logic** (AES/GCM cipher operations)
- Contains **XML parsing** (DocumentBuilderFactory)
- Contains **HTTP request handling** (MultipartFile, HttpStatus responses)
- Contains **scheduled tasks** (`@Scheduled` cron jobs)
- Is injected into **controllers** as a dependency

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\legacy\infrastructure\LegacyCrmInfrastructureService.java` (2,044 lines)

### Impact
- Infrastructure layer should contain only technical implementations of ports; this class contains everything
- Cannot independently test, replace, or evolve any architectural layer
- The class is too large and complex for any single developer to reason about comprehensively
- New feature work tends to accrete in this class rather than follow architectural patterns

### Recommendation
1. Extract business rules to domain services in the `domain` package
2. Extract raw SQL to dedicated repository implementations
3. Extract encryption to a dedicated infrastructure service
4. Extract XML parsing to an infrastructure adapter
5. Set a hard class size limit (300 lines) enforced by CI linting
6. Prohibit new method additions via architecture test

---

## Finding ARC-02: CrmController Dependencies on LegacyCrmInfrastructureService

**Severity:** CRITICAL
**Category:** Layer Leakage

### Description
`CrmController` (V1 controller) directly depends on `LegacyCrmInfrastructureService`, injecting it as a collaborator alongside the thinner `CrmService`. This means the controller bypasses the application service layer and interacts directly with a class that itself contains infrastructure, domain, and application logic conflated together.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmController.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\legacy\infrastructure\LegacyCrmInfrastructureService.java`

### Impact
- Controllers should depend on application service interfaces, not infrastructure implementations
- The controller has no abstraction boundary between HTTP concerns and data access
- Testability is severely compromised -- unit-testing the controller requires mocking infrastructure concerns

### Recommendation
Create an application service interface that `LegacyCrmInfrastructureService` implements, and have the controller depend on that interface. Better yet, decompose the god class into use-case-specific services first.

---

## Finding ARC-03: AOP Aspects Performing Database Queries

**Severity:** CRITICAL
**Category:** Dependency Violation / Broken Abstraction

### Description
`CrmOwnershipAtomicIfMatchAspect` performs direct database operations (SELECT FOR UPDATE) within an AOP aspect. The aspect locks database rows, validates ETags, and manages transactions -- all within cross-cutting concern code that should not have database access.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\infrastructure\CrmOwnershipAtomicIfMatchAspect.java` (lines 96-109)

### Code Evidence
```java
private Instant lockUpdatedAt(LockTarget target, UUID tenantId) {
    String sql = "SELECT updated_at FROM " + target.table()
            + " WHERE tenant_id=:tenantId AND id=:id FOR UPDATE";
    try {
        Timestamp value = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", target.id()),
                Timestamp.class);
        return value == null ? null : value.toInstant();
    } catch (EmptyResultDataAccessException missing) {
        return null;
    }
}
```

### Impact
- AOP aspects should implement cross-cutting concerns only (logging, security, transactions)
- Direct database access in aspects bypasses repository abstraction and domain logic
- Transaction management in aspects can conflict with `@Transactional` annotations on service methods
- Testing the aspect requires a database, violating the unit-test boundary

### Recommendation
1. Move database locking logic into the repository layer, not the aspect
2. Have the aspect call a repository method rather than executing raw SQL
3. Extract ETag validation into a dedicated application service
4. Consider replacing the AOP approach with explicit optimistic locking in repositories

---

## Finding ARC-04: URL Path Hardcoding in AOP Aspects

**Severity:** HIGH
**Category:** Broken Abstraction / Brittle Design

### Description
`CrmOwnershipAtomicIfMatchAspect` contains hardcoded regex patterns for URL paths (lines 38-51). These patterns duplicate the routing information that is already declared in controller `@RequestMapping` annotations. Any route change requires updating both the controller and the aspect in sync.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\infrastructure\CrmOwnershipAtomicIfMatchAspect.java` (lines 38-51)

### Impact
- URL routing is a controller concern, not an aspect concern
- Changing a route path requires updating regex patterns in the aspect -- a fragile coupling
- The aspect's `pointcut` expression also duplicates controller knowledge

### Recommendation
1. Replace URI pattern matching with annotation-driven metadata or a dedicated routing table
2. Consider moving the If-Match enforcement into a Spring interceptor or filter rather than an AOP aspect
3. Register route-to-resource mappings centrally, not via regex patterns

---

## Finding ARC-05: DisabledHrmOwnershipAdapter Active in All Profiles

**Severity:** HIGH
**Category:** Unconditional Production Path

### Description
`DisabledHrmOwnershipAdapter` (which implements the HRM port with a "stub" behavior) is active in all Spring profiles because it lacks a `@Profile` or `@ConditionalOnProperty` annotation. This means the disabled/stub path is the default HRM behavior in all environments, including production.

### Impact
- Any feature depending on HRM integration (absence-driven reassignment, ownership validation) returns stub results in production
- The adapter's purpose is to provide a safe fallback, but its unconditional activation means the real HRM integration is never exercised
- May mask integration failures until critical business processes fail

### Recommendation
1. Add `@Profile("!production")` or `@ConditionalOnProperty(name = "sanad.hrm.provider", havingValue = "disabled")` to the adapter
2. Add a Spring test that verifies the adapter is not loaded in production profile
3. Ensure the real HRM adapter is activated when `sanad.hrm.provider=http` or similar

---

## Finding ARC-06: Controller Directly Instantiating or Leaking Domain Objects

**Severity:** HIGH
**Category:** Layer Leakage

### Description
Several controllers directly interact with domain objects or infrastructure services rather than going through application use case interfaces. Evidence from the controller inventory shows patterns where controllers:
- Directly call methods on `LegacyCrmInfrastructureService` (bypassing application layer)
- Handle authentication context extraction inline (duplicated across 9 controllers)
- Return framework-specific response types directly from controller methods

### Impact
- Controllers become tightly coupled to implementation details
- Business logic changes can ripple into controller modifications
- Cross-cutting concerns (auth context extraction) are duplicated rather than centralized

### Recommendation
1. Introduce a thin application service layer between controllers and domain logic
2. Extract duplicated auth context extraction into a `@ControllerAdvice` or argument resolver
3. Controller methods should delegate to use cases, not directly to domain or infrastructure

---

## Finding ARC-07: CrmCoreCursorPaginationAspect -- Reflection-Heavy Approach

**Severity:** MEDIUM
**Category:** Architectural Fragility

### Description
`CrmCoreCursorPaginationAspect` uses reflection to inspect method parameters, invoke getters, and construct paginated responses. This approach bypasses compile-time type safety and creates runtime coupling to method signatures.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\pagination\CrmCoreCursorPaginationAspect.java`

### Impact
- Refactoring method signatures can silently break pagination behavior at runtime
- Reflection-based invocation is slower than direct method calls
- Stack traces from pagination failures are difficult to map to source code
- Type safety violations manifest only at runtime, not compile time

### Recommendation
1. Replace AOP-based cursor pagination with explicit pagination in repository interfaces
2. Use Spring Data's pagination abstractions or a dedicated pagination library
3. If AOP approach is retained, add extensive integration tests covering all paginated endpoints

---

## Finding ARC-08: CrmContractControllerR1 -- Duplicate Controller Tier

**Severity:** HIGH
**Category:** Duplicate Architecture Layer

### Description
`CrmContractControllerR1` exists alongside `CrmContractController` with overlapping responsibility for the same domain. Both are V2 controllers with different method signatures and potentially different behaviors for similar operations. This creates a confusing API surface and duplicate maintenance burden.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractController.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractControllerR1.java`

### Recommendation
Consolidate into a single controller per bounded context. Use a versioning strategy that is documented and consistent, not ad-hoc.

---

## Finding ARC-09: Exports/Web Controllers Lack @RequireCapability

**Severity:** MEDIUM
**Category:** Authorization Gap

### Description
While most CRM controllers use `@RequireCapability` annotations for authorization, the `ExportController` does have them on its public methods. However, other web controllers in the `exports/web` and similar packages may lack proper authorization annotations, creating potential authorization gaps.

### Impact
- Unauthorized access to export functionality
- Inconsistent authorization model across the codebase

### Recommendation
Audit all controller methods for missing `@RequireCapability` annotations. Add a default-deny policy: if no capability annotation is present, the request should be rejected.

---

## Finding ARC-10: CrmContractController Directly Imports and Uses Infrastructure Services

**Severity:** HIGH
**Category:** Layer Leakage

### Description
`CrmContractController` directly imports and uses `CrmV2AtomicMutationInfrastructureService` and `LegacyCrmInfrastructureService`. Controllers should not depend on infrastructure services directly -- they should depend on application service interfaces.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractController.java` (lines 4-5)

### Recommendation
Introduce application service interfaces that encapsulate the coordinated use of infrastructure services. The controller should depend only on these interface.

---

## Finding ARC-11: Uncontrolled Package Dependency Direction

**Severity:** HIGH
**Category:** Dependency Violation

### Description
A systematic review of package-level dependencies reveals that `infrastructure` packages depend on `application` packages (correct), but `application` packages also depend on `infrastructure` packages in several places (violation). Specifically, application services in the `crm.intelligence.application` package directly reference infrastructure implementations.

### Evidence
- `CustomerScoringService` imports `CustomerIntelligenceQueryPortAdapter` which is an application-layer class, but also interacts with `AiScoreOrchestrator` which bridges to infrastructure
- Several application service constructors take implementations, not interfaces

### Recommendation
1. Enforce dependency rule: `domain` <-- `application` <-- `infrastructure` (with domain having no outward dependencies)
2. Use ArchUnit or similar to codify this in CI
3. Application services should depend only on domain interfaces, not on infrastructure or application-layer concrete classes

---

## Finding ARC-12: No Clear Bounded Context Boundaries

**Severity:** HIGH
**Category:** Architectural Cohesion

### Description
The codebase does not enforce bounded context boundaries. Classes from different domains freely reference each other:
- `CustomerScoringService` (intelligence context) imports `TimelineEventPort` (integration context)
- `TransferUseCases` (ownership context) references `WorkflowPort` (workflow context)
- `LegacyCrmInfrastructureService` references virtually all domains

While some cross-context collaboration is valid, the lack of explicit context mapping (e.g., anti-corruption layers, published language) means context boundaries are porous.

### Recommendation
1. Define bounded contexts explicitly in an ADR
2. Use context-mapping patterns (Open-Host Service, Published Language, Anti-Corruption Layer) for cross-context communication
3. Introduce module boundaries using Java modules or package-private visibility where possible

---

## Conclusion

The SNAD CRM architecture exhibits significant Clean Architecture and Hexagonal Architecture violations. The most critical is the `LegacyCrmInfrastructureService` god class that has collapsed all architectural layers into a single file. AOP aspects with database access, controllers bypassing application services, and unconditional stub adapters further weaken the architecture. Layer boundary enforcement via automated tools (ArchUnit) and systematic refactoring of the god class are the highest-priority remediation actions.

**Overall Architecture Score: 3/10 -- Architecture boundaries compromised; systematic refactoring required.**
