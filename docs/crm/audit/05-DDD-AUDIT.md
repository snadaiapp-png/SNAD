# Domain-Driven Design Audit

**Audit Scope:** Anemic domain model, aggregate boundary violations, repository misuse, transaction boundary correctness, domain isolation, domain services vs. application services, value objects, domain events.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** CRITICAL -- Pervasive anemic domain model with significant aggregate boundary violations.

---

## Executive Summary

The SNAD CRM codebase nominally follows DDD tactical patterns with packages organized by domain concept (`activity`, `intelligence`, `ownership`, `party`, `lead`, `opportunity`, etc.) and clear separation of `domain`, `application`, and `infrastructure` within each bounded context. However, the domain model is predominantly anemic -- domain objects are primarily data holders with minimal behavior. Aggregate boundaries are inconsistently enforced, domain events are absent for core entity operations, and the distinction between domain services and application services is blurred, particularly in the `application` packages.

---

## Finding DDD-01: Pervasive Anemic Domain Model

**Severity:** CRITICAL
**Category:** Anemic Domain Model

### Description
Domain objects across the codebase are predominantly data holders with getters and setters but minimal business logic. Behavior that should reside in domain entities is instead implemented in application or infrastructure services.

### Evidence
The following domain objects in the `intelligence` context are primarily data records with little to no behavior:
- `CustomerProfile.java` -- Data holder, no behavior
- `CustomerScores.java` -- Data holder, no behavior
- `HealthScore.java` -- has `bandFor()` static method but primarily data
- `EngagementScore.java` -- Data holder
- `LoyaltyScore.java` -- Data holder
- `RiskScore.java` -- Data holder
- `ScoreSnapshot.java` -- Data holder
- `ScoreComponent.java` -- Data holder
- `ScoreHistoryEntry.java` -- Data holder
- `NextBestAction.java` -- Data holder
- `ScoringModel.java` -- Data holder
- `Segment.java` -- Data holder
- `SegmentMembership.java` -- Data holder

### Affected Files (representative sample)
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\CustomerProfile.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\CustomerScores.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\NextBestAction.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\ScoringModel.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\Segment.java`

### Impact
- Business logic leaks into application services, making it non-discoverable and non-reusable
- Domain rules are not encapsulated; any service can manipulate domain state incorrectly
- Changing business rules requires modifying multiple application services rather than a single domain entity
- Domain model provides no behavioral contract -- impossible to reason about business rules from the domain alone

### Recommendation
1. Apply the "Tell Don't Ask" principle: move business logic from services into domain entities
2. For each anemic domain class, identify the business rules that operate on its state and encapsulate them as methods
3. Add domain invariants that are enforced on every state mutation
4. Use code reviews to enforce: if business logic operates on domain state, it belongs in the domain class

---

## Finding DDD-02: Blurred Domain Service vs. Application Service Distinction

**Severity:** HIGH
**Category:** Misplaced Responsibility

### Description
Several classes in the `application` package contain both orchestration logic (application concern) and domain business rules (domain concern). The `CustomerScoringService` is a prime example: it orchestrates scoring workflow (application concern) while also containing rule-based scoring calculations (domain concern).

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\application\CustomerScoringService.java`

### Code Evidence
```java
// Application orchestration (correct)
@Transactional
public StoredScore calculateHealthScore(UUID tenantId, UUID accountId, UUID actorId, ...) {
    validator.validateCustomer(tenantId, accountId);
    var indicators = aiOrchestrator.buildHealthIndicators(...);
    var aiResult = aiOrchestrator.requestScore(...);
    // ...
}

// Domain logic mixed in (should be in domain)
private double calculateRuleBasedHealth(int daysSinceLastActivity, int meetingFreq30d,
                                         double responseTimeAvgHours, int openOpportunities) {
    double engagementScore = Math.max(0, 100 - daysSinceLastActivity * 2);
    double meetingScore = Math.min(100, meetingFreq30d * 15);
    double responseScore = Math.max(0, 100 - responseTimeAvgHours * 5);
    double pipelineScore = Math.min(100, openOpportunities * 20);
    return (engagementScore * 0.30 + meetingScore * 0.25 + responseScore * 0.20 + pipelineScore * 0.25);
}
```

### Impact
- Domain logic is not reusable across application services
- Domain rules cannot be tested independently of application concerns
- Violates the Single Responsibility Principle at the service boundary

### Recommendation
1. Extract domain rules into domain services (e.g., `HealthScoreCalculationService` in the domain package)
2. Application services should orchestrate domain services, not implement domain rules
3. Move `calculateRuleBasedHealth()` into the `HealthScore` value object or a `ScoringDomainService`

---

## Finding DDD-03: Incorrect Aggregate Boundaries

**Severity:** HIGH
**Category:** Aggregate Boundary Violation

### Description
Aggregate boundaries are inconsistently applied. In several places, application services directly modify multiple aggregate instances without going through aggregate roots, or cross aggregate boundaries in a single transaction without considering consistency boundaries.

### Evidence
- `refreshAllScores()` in `CustomerScoringService` updates a score aggregate and then directly queries scores, mixing command and query within the same aggregate boundary
- The scoring model seed migration inserts records directly without going through a domain aggregate
- Several services perform multi-table updates without a clear aggregate root

### Recommendation
1. Define aggregate roots explicitly for each bounded context
2. Ensure that external references to an aggregate go through its root entity
3. Use repository interfaces that accept aggregate roots, not individual entities
4. Document aggregate boundaries in an ADR or code comments

---

## Finding DDD-04: Repository Misuse -- Direct SQL in Application Layer

**Severity:** HIGH
**Category:** Repository Pattern Violation

### Description
Application services and infrastructure directly execute SQL queries rather than going through repository interfaces. This is most evident in `LegacyCrmInfrastructureService`, which contains raw SQL embedded within business logic methods. Even in the newer architecture, the AOP aspects execute raw SQL directly.

### Evidence
- `LegacyCrmInfrastructureService` -- extensive raw SQL throughout
- `CrmOwnershipAtomicIfMatchAspect` -- `SELECT ... FOR UPDATE` raw SQL in aspect
- `CustomerScoringService` -- uses `scoringPort.saveScore()` which is a repository, but also directly interacts with `queryAdapter` which is an application-layer adapter wrapping a repository

### Impact
- Repository pattern is inconsistently applied -- some data access goes through repositories, some bypasses them
- Cannot easily swap database implementations or add cross-repository concerns (caching, auditing, tracing)
- Raw SQL in business logic is a maintenance hazard and security risk (SQL injection if not parameterized)

### Recommendation
1. All data access must go through repository interfaces
2. Add ArchUnit rule: no `JdbcTemplate`, `NamedParameterJdbcTemplate`, or `DataSource` injection outside of `infrastructure` packages
3. Extract raw SQL from `LegacyCrmInfrastructureService` into dedicated repository classes

---

## Finding DDD-05: No Domain Events for Core Entity Operations

**Severity:** HIGH
**Category:** Missing Domain Events

### Description
The codebase lacks domain events for core entity lifecycle operations. The intelligence module has its own event system (`CustomerIntelligenceEventPublisher`) but it only covers scoring and recommendation events. Entity operations like account creation, contact updates, opportunity stage changes, and lead conversion do not publish events.

### Evidence
- No `AccountCreated`, `AccountUpdated`, `ContactCreated`, `OpportunityStageChanged`, `LeadConverted` events found in the codebase
- The `CustomerIntelligenceEventPublisher` only handles score/segment/NBA events
- Core entity mutations in application services do not emit events

### Affected Contexts
- `party` (accounts, contacts)
- `lead` (lead lifecycle)
- `opportunity` (pipeline stage transitions)
- `activity` (activity lifecycle)
- `ownership` (assignment changes, transfers)

### Impact
- No mechanism for reactive behavior (notifications, sync, analytics) triggered by entity changes
- Audit trails must be implemented imperatively at each mutation site
- Event-driven integrations require invasive changes to add event publishing retroactively

### Recommendation
1. Define domain events for all core aggregate operations
2. Publish events from domain methods, not from application services
3. Use a consistent event publishing infrastructure shared across bounded contexts
4. Consider adopting an event sourcing or event-driven architecture for key aggregates

---

## Finding DDD-06: TransferRequest Domain Object Contains Minimal Behavior

**Severity:** MEDIUM
**Category:** Anemic Domain Model

### Description
`TransferRequest` is a domain entity (in `ownership.domain`) but it acts primarily as a data carrier. The business logic for state transitions is implemented in the application service (`TransferUseCases`), not in the entity itself. The entity does not enforce its own state machine -- any code can set any state.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\domain\TransferRequest.java` (inferred from usage)
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\application\TransferUseCases.java`

### Impact
- State transitions are scattered across the application service rather than encapsulated in the entity
- Any code path that bypasses `TransferUseCases` can put the entity into an invalid state
- Domain invariants (e.g., "approved transfers must have an approver") are not enforced by the entity itself

### Recommendation
1. Move state machine logic into `TransferRequest` as methods (`submit()`, `approve()`, `reject()`, `cancel()`)
2. The entity should throw domain exceptions for invalid state transitions
3. Application service should call entity methods, then persist

---

## Finding DDD-07: Value Objects Lack Full Behavior

**Severity:** MEDIUM
**Category:** Incomplete Value Objects

### Description
Value objects in the `intelligence` domain (e.g., `HealthScore`, `RiskScore`, `EngagementScore`, `LoyaltyScore`) have some behavior (`bandFor()` static methods) but are not fully encapsulated. They expose raw fields (score value, components) rather than providing behavior-rich interfaces.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\HealthScore.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\RiskScore.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\EngagementScore.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\domain\LoyaltyScore.java`

### Impact
- Value objects do not enforce their own invariants
- External code can create inconsistent value object instances
- Behavior that should be on the value object (e.g., "is this score in the danger zone?") is in services

### Recommendation
1. Make value object fields private with behavior-rich methods
2. Add factory methods (e.g., `HealthScore.calculate(...)`) that enforce creation invariants
3. Add query methods (e.g., `isCritical()`, `needsAttention()`, `trend()`) that encapsulate business rules

---

## Finding DDD-08: TransferRequest Doesn't Enforce All State Machine Transitions

**Severity:** MEDIUM
**Category:** Aggregate Boundary / State Enforcement

### Description
The `TransferRequest` aggregate does not enforce the state machine within its own boundary. The application service (`TransferUseCases`) directly calls `transfers.updateState()` on the repository, bypassing any state validation that the entity could provide. This creates a risk of invalid state transitions.

### Evidence
In `TransferUseCases.decide()`, state is updated via:
```java
transfers.updateState(tenantId, transferId, TransferState.REJECTED, null, null);
```
This bypasses any transition validation the entity might enforce.

### Recommendation
The repository or entity should validate that state transitions are legal (e.g., `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> COMPLETED`), not allow arbitrary state changes.

---

## Finding DDD-09: Domain Isolation Between Contexts Is Weak

**Severity:** HIGH
**Category:** Bounded Context Isolation

### Description
While the codebase has package-level bounded context separation (e.g., `crm.intelligence`, `crm.ownership`, `crm.party`), classes freely reference each other across context boundaries without anti-corruption layers or published language interfaces.

### Evidence
- `CustomerScoringService` (intelligence) imports `TimelineEventPort` (integration context)
- `TransferUseCases` (ownership) imports `WorkflowPort`, `HrmPort`, `AuditPort`, `TimelineEventPort`
- `LegacyCrmInfrastructureService` imports from virtually every CRM context

### Impact
- Changes in one bounded context can directly break another
- Contexts cannot be developed, tested, or deployed independently
- There is no translation layer between context-specific domain models

### Recommendation
1. Define explicit context maps with relationships (partnership, shared kernel, customer-supplier)
2. Introduce anti-corruption layers between bounded contexts that have different ubiquitous languages
3. Use domain events for cross-context communication rather than direct service calls
4. Consider using separate database schemas for different bounded contexts

---

## Conclusion

The SNAD CRM codebase has a credible package structure following DDD tactical patterns, but the implementation is predominantly anemic. Domain objects lack behavior, aggregate boundaries are not enforced, state transitions are managed by application services rather than domain entities, and domain events are largely absent for core operations. The bounded contexts are not properly isolated, with cross-context dependencies occurring without translation layers.

The highest priority remediation is to enrich the domain model with behavior, enforce aggregate boundaries, and introduce domain events for core entity operations. This is a multi-sprint effort that should be guided by a DDD coach and enforced via architecture tests.

**Overall DDD Score: 3/10 -- Anemic domain model with weak aggregate boundaries.**
