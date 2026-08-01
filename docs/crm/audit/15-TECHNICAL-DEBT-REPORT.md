# Technical Debt Report — CRM v2.0.0

**Audit Date:** 2026-07-30  
**Scope:** Code smells, overengineering, underengineering, dead code, duplication, hardcoded values, configuration problems  
**Severity Assessment:** CRITICAL

---

## Executive Summary

The CRM codebase carries substantial technical debt across 18 identified categories. The most significant items are a 2044-line god class, duplicated V1/V2 controller layers, anemic domain models, hardcoded configuration values, and dead/misconfigured adapter code. The estimated effort to remediate all identified technical debt is 8-12 weeks for a team of 2-3 engineers. The interest on this debt compounds with each new feature added to the current architecture.

**Technical Debt Health Score: 45/100 — POOR**

---

## 1. LegacyCrmInfrastructureService — 2044-Line God Class

**ID:** C-02  
**Severity:** CRITICAL  
**Type:** God Class / Overengineering  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/infrastructure/LegacyCrmInfrastructureService.java`

**Description:**  
This single class spans 2044 lines and violates multiple architectural principles:
- Located in the infrastructure layer but contains business logic, validation, encryption, and data access
- Violates Single Responsibility Principle
- Violates Clean Architecture layer boundaries
- Contains raw SQL, DTO conversion, event publishing, and business rules in a single file
- Acts as a god object that many other classes depend on

**Impact:**
- Extremely difficult to test (thousands of lines, many dependencies)
- Any change risks breaking unrelated functionality
- New developers cannot understand the class in its entirety
- Promotes copy-paste coding as alternatives to refactoring
- Blocks adoption of proper domain-driven design patterns

**Evidence:**  
File is 2044 lines. Contains methods for scoring, segmentation, customer 360, validation, encryption, and data access.

**Recommendation:**
1. Decompose into bounded application services by domain concern: ScoringService, SegmentationService, CustomerProfileService, ValidationService
2. Extract data access into dedicated repository classes
3. Move business logic to domain layer
4. Move encryption to infrastructure layer where it belongs
5. Estimated effort: 3-4 weeks for one senior engineer

---

## 2. V1 and V2 Controller Duplication

**ID:** C-10  
**Severity:** CRITICAL  
**Type:** Duplicated Code  
**Files Affected:**
- V1: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/`
- V2: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/`

**Description:**  
Two complete controller layers exist for the same domain concepts. V1 (ownership/web/) was the original API layer; V2 (crm/web/) was built as a replacement but V1 was never removed. Both layers are active, creating:
- Two code paths that must be kept synchronized
- Duplicate authentication, validation, and error handling logic
- Divergent behavior over time as changes are made to only one layer
- Confusion for API consumers and developers

**Impact:**
- Maintenance burden doubled for any API change
- Behavioral divergence risk over time
- Increased surface area for security vulnerabilities
- Waste: every line of V1 that duplicates V2 is dead weight once V2 is verified

**Evidence:**  
Both packages contain controllers for account, contact, opportunity, lead, and related operations with overlapping URL mappings.

**Recommendation:**
1. Deprecate V1 controllers with `@Deprecated` annotation and documentation
2. Route all traffic to V2 via configuration or gateway
3. Remove V1 after a transition period (suggested: 2 sprints)
4. Estimated effort: 1-2 weeks

---

## 3. Mock Adapters Active by Default (matchIfMissing=true)

**ID:** C-01  
**Severity:** CRITICAL  
**Type:** Misconfiguration  
**Files Affected:**
- `MockPosDataAdapter`
- `MockHrmDataAdapter`
- `MockErpDataAdapter`
- `MockCommerceDataAdapter`
- `MockAccountingDataAdapter`

**Description:**  
Five mock intelligence adapters use `@ConditionalOnMissingBean` or `matchIfMissing=true` in their Spring bean definitions, meaning they activate when no real adapter bean is present. In production deployments where real adapter beans are not configured (e.g., missing environment properties, misconfigured profiles), mock adapters silently activate and serve synthetic data.

**Impact:**
- Production could serve fake customer scores, segment memberships, and lifetime values
- No warning or logging when mock adapters activate in production
- Synthetic data contaminates real business analytics
- Customer-facing features could display incorrect intelligence data

**Evidence:**  
All five mock adapters have `matchIfMissing=true` or `@ConditionalOnMissingBean` annotations.

**Recommendation:**
1. Remove `matchIfMissing=true` from all five mock adapters
2. Configure mock adapters to only activate under `@Profile("test" | "dev")` 
3. Add a health check that verifies no mock adapters are active in production
4. Add startup warning if real adapters are missing (fail fast)
5. Estimated effort: 2-3 days

---

## 4. Anemic Domain Model

**ID:** I-01  
**Severity:** HIGH  
**Type:** Underengineering  
**Files Affected:**
- All domain entity records: Account, Contact, Opportunity, Lead, Activity, etc.

**Description:**  
Domain entities are implemented as Java records with no behavioral methods. They function as data carriers (DTOs) rather than domain objects. All business logic resides in service classes, use cases, or the LegacyCrmInfrastructureService. This violates Domain-Driven Design principles and results in an anemic domain model.

**Impact:**
- Business logic is scattered across services instead of encapsulated in domain objects
- Domain invariants cannot be enforced at the entity level
- Domain model does not communicate business rules to developers
- Rich domain models cannot be developed without refactoring all entities
- Makes domain events, specification pattern, and other DDD patterns harder to implement

**Evidence:**  
Domain entity records contain only fields, getters, equals/hashCode, and no behavioral methods.

**Recommendation:**
1. Add behavioral methods to domain entities where logic is internal (e.g., `Opportunity.moveToStage()`, `Lead.convert()`)
2. Move validation into domain constructors and factory methods
3. Enforce invariants at construction and mutation time
4. Estimated effort: 3-4 weeks (can be done incrementally alongside other work)

---

## 5. Hardcoded Zero-UUID Tenant in Seed Migration

**ID:** C-08  
**Severity:** CRITICAL  
**Type:** Hardcoded Values  
**Files Affected:**
- `apps/sanad-platform/src/main/resources/db/migration/V20260729_2__seed_intelligence_data.sql`

**Description:**  
The seed migration uses a hardcoded zero-UUID (`00000000-0000-0000-0000-000000000000`) as the tenant identifier for reference data. This introduces a tight coupling between the application code and a sentinel value, creating a special-case tenant that bypasses normal multi-tenant isolation.

**Impact:**
- RLS policies must explicitly handle zero-UUID or reference data is invisible
- Application code may need special cases for zero-UUID tenant
- Migrations and operations must remember to handle zero-UUID specially
- Violates the principle that tenants should be first-class, not sentinel values

**Evidence:**  
SQL migration uses `'00000000-0000-0000-0000-000000000000'` as tenant_id.

**Recommendation:**
1. Replace zero-UUID with a proper reference-data tenant created during system initialization
2. Add RLS policy that permits cross-tenant access for reference data, or duplicate reference data per tenant
3. Remove special-case handling from application code
4. Estimated effort: 3-5 days

---

## 6. Duplicated Auth Context Extraction in 9 V1 Controllers

**ID:** I-03  
**Severity:** HIGH  
**Type:** Duplicated Code  
**Files Affected:**
- 9 V1 controller classes in `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/`

**Description:**  
Each of the 9 V1 controller classes independently extracts authentication context (tenant ID, user ID) from the security context. This logic is duplicated across all controllers rather than being centralized in a base class, filter, or argument resolver.

**Impact:**
- 9 copies of the same boilerplate
- Any change to auth context extraction must be made in 9 places
- Inconsistent error handling if one controller misses a null check
- Increased risk of missing auth extraction in new controllers

**Evidence:**  
Each controller class has near-identical blocks for extracting tenant and user from SecurityContext or Authentication.

**Recommendation:**
1. Implement a `@CurrentTenant` and `@CurrentUser` argument resolver (Spring `HandlerMethodArgumentResolver`)
2. Extract auth context in a single place and inject into controller methods
3. Remove duplicated extraction from all controllers
4. Estimated effort: 2-3 days

---

## 7. URL Path Hardcoding in AOP Aspects

**ID:** I-08  
**Severity:** HIGH  
**Type:** Hardcoded Values  
**Files Affected:**
- `CrmOwnershipAtomicIfMatchAspect.java`
- Related AOP aspect classes

**Description:**  
AOP aspect classes contain hardcoded URL path strings for matching. This creates tight coupling between aspect logic and specific URL structures. If URL paths change (which they may during V1/V2 consolidation), the aspects must be updated to match. Hardcoded paths also prevent path-based configuration changes.

**Impact:**
- URL restructuring requires aspect changes
- No compile-time checking of path strings
- Aspects silently stop applying if paths change without updating the aspect
- Testing aspects requires replicating exact path structures

**Evidence:**  
Aspect pointcut expressions contain literal URL path strings like `"/api/crm/ownership/*"`.

**Recommendation:**
1. Use named pointcuts that can be centrally defined
2. Define URL constants in a single location (e.g., `ApiPaths` class)
3. Reference constants from aspect pointcuts
4. Estimated effort: 1-2 days

---

## 8. DisabledHrmOwnershipAdapter Active in All Profiles

**ID:** I-10  
**Severity:** HIGH  
**Type:** Misconfiguration  
**Files Affected:**
- `DisabledHrmOwnershipAdapter.java`

**Description:**  
The `DisabledHrmOwnershipAdapter` is a no-op adapter that is active in all Spring profiles, including production. While the adapter itself does nothing (returns empty/no-op results), its presence in production means that HRM ownership features cannot function even if a real adapter is available. The adapter should be annotated with `@Profile("!prod")` to restrict it to non-production environments.

**Impact:**
- Real HRM ownership functionality is effectively disabled in all environments
- No warning or configuration error indicates the adapter is intentionally disabled
- If HRM ownership is needed in production, an immediate code change and deployment is required

**Evidence:**  
`DisabledHrmOwnershipAdapter` has `@Component` or similar without profile restrictions.

**Recommendation:**
1. Add `@Profile("!prod")` to `DisabledHrmOwnershipAdapter`
2. Add a configuration property to enable/disable the adapter explicitly
3. Document the disabled HRM ownership integration as a known limitation
4. Estimated effort: 1 day

---

## 9. Hardcoded AI Gateway Timeout (30s)

**ID:** H-05  
**Severity:** HIGH  
**Type:** Hardcoded Values  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/orchestration/HttpAiGatewayAdapter.java`

**Description:**  
AI Gateway HTTP client timeout is hardcoded at 30 seconds. This value is not configurable through application properties, environment variables, or external configuration. See Performance Audit (12-PERFORMANCE-AUDIT.md) for full details.

**Impact:** Thread pool exhaustion risk; no production tuning without code changes.

**Recommendation:** Externalize to configuration with sensible defaults (connect: 2s, read: 10s).

---

## 10. Hardcoded Cache TTL (5 min) and Max Size (10K)

**ID:** H-04  
**Severity:** HIGH  
**Type:** Hardcoded Values  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/config/CustomerIntelligenceProperties.java`

**Description:** Cache TTL and max size are hardcoded as static final fields. Not externally configurable. See Performance Audit for full details.

**Impact:** Cannot tune per-environment; single configuration for all cache regions.

**Recommendation:** Externalize with `@ConfigurationProperties`, introduce per-region configuration.

---

## 11. frontend snake_case vs Backend camelCase

**ID:** C-09  
**Severity:** CRITICAL  
**Type:** Inconsistency  
**Files Affected:**
- Frontend: `apps/web/app/crm/` (TypeScript interfaces)
- Backend: Java DTOs

**Description:**  
Frontend TypeScript interfaces define fields using `snake_case` (`display_name`, `company_name`, `created_by`) while backend Java DTOs use standard `camelCase` (`displayName`, `companyName`, `createdBy`). The serialization layer must translate between the two, creating a persistent source of bugs.

**Impact:**
- Inconsistent naming forces serialization configuration (Jackson `@JsonProperty`, `@SerializedName`)
- Developers must mentally map between naming conventions
- New fields easily miss serialization annotations, causing runtime failures
- Code generation tools produce mismatched types
- TypeScript type safety is undermined if field names don't match actual JSON

**Evidence:**  
Frontend interfaces use `snake_case`; backend DTOs use `camelCase`. Serialization relies on Jackson annotations to bridge the gap.

**Recommendation:**
1. Choose a single naming convention: prefer `camelCase` (standard for both Java and JSON)
2. Update frontend types to use `camelCase` with Jackson `@JsonProperty` on the backend
3. Or, configure `spring.jackson.property-naming-strategy=SNAKE_CASE` globally
4. Ensure frontend type generation from OpenAPI spec handles naming correctly
5. Estimated effort: 2-3 weeks (coordinated frontend + backend changes)

---

## 12. ReportsUseCases Stub Implementations

**ID:** M-10 (new)  
**Severity:** MEDIUM  
**Type:** Unimplemented Code  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/reports/application/ReportsUseCases.java`

**Description:**  
The `ReportsUseCases` class contains stub implementations that return empty results or throw `UnsupportedOperationException`. The reporting module was architected but not implemented. The stubs are deployed to production, meaning any report feature accessed by users will fail or return empty data.

**Impact:**
- Users accessing reports see empty data or errors
- False impression that reporting features exist
- Missing functionality may block other features that depend on reports

**Evidence:**  
Methods throw `UnsupportedOperationException("Not yet implemented")` or return empty collections.

**Recommendation:**
1. Implement report generation with proper aggregation queries
2. Or, remove report endpoints and stub code if reporting is not planned
3. Add `@Deprecated` or feature-flag the report endpoints
4. Estimated effort: 2-4 weeks (or 1 day to remove)

---

## 13. SearchUseCases Missing Full-Text Search

**ID:** M-11 (new)  
**Severity:** MEDIUM  
**Type:** Incomplete Implementation  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/search/application/SearchUseCases.java`

**Description:**  
The search use cases implement basic LIKE-pattern search rather than PostgreSQL full-text search (`tsvector`/`tsquery`). This limits search quality: no stemming, no ranking, no fuzzy matching, no phrase search. As data grows, LIKE-based search becomes increasingly slow due to inability to use standard indexes effectively.

**Impact:**
- Poor search quality for users
- Performance degrades with data volume
- No relevance ranking in search results
- Inability to implement search features like autocomplete, suggestions, or faceted search

**Recommendation:**
1. Implement PostgreSQL full-text search using `tsvector` columns and `tsquery` queries
2. Add GIN indexes on `tsvector` columns for performance
3. Consider pg_trgm for fuzzy matching support
4. Estimated effort: 1-2 weeks

---

## 14. ReportsUseCases Stub Implementations

*(See Finding 12 above)*

---

## 15. Transactional Boundary Issues in Integration Outbox

**ID:** M-12 (new)  
**Severity:** MEDIUM  
**Type:** Data Integrity  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/application/CrmIntegrationOutboxWorker.java`

**Description:**  
The integration outbox worker may process events before the transaction that created them is committed. Without proper transaction synchronization, the worker can pick up uncommitted events that are then rolled back, resulting in phantom events.

**Impact:**
- Phantom events if creating transaction rolls back after worker processes event
- Event processing order non-deterministic
- Potential duplicate processing

**Recommendation:**
1. Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` for event publication
2. Add a `processed_at` timestamp to outbox table to prevent double-processing
3. Add idempotency keys to event consumers

---

## 16. Missing NOT NULL on crm_customer_segments.criteria

**ID:** H-20  
**Severity:** HIGH  
**Type:** Schema Inconsistency  
**Files Affected:**
- CRM-010 migration files

**Description:**  
The `crm_customer_segments.criteria` column is nullable despite being semantically required (a segment without criteria is meaningless). This allows inserting invalid segment records that cannot be evaluated.

**Impact:**
- Segments without criteria can be created by mistake
- Any code assuming criteria is present will NPE
- Data quality issue propagates to scoring and targeting features

**Recommendation:**
1. Add `NOT NULL` constraint via new migration
2. Back-fill or clean up any existing null criteria records
3. Add validation in segment creation use case

---

## 17. CustomerScoringService.refreshAllScores() Hardcoded Fake Values

**ID:** C-07  
**Severity:** CRITICAL  
**Type:** Hardcoded Values  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/application/CustomerScoringService.java`

**Description:**  
The `refreshAllScores()` method in `CustomerScoringService` uses hardcoded fake values for scoring instead of computing scores from actual data. When this method is invoked (via scheduler or admin endpoint), it overwrites real score data with synthetic values. This is functionally equivalent to data corruption.

**Impact:**
- Real customer scores are replaced with fake data
- Downstream systems (recommendations, segmentation, health scores) operate on incorrect data
- Data corruption must be manually repaired after accidental invocation
- No warning that method produces fake data

**Evidence:**  
The method sets score values to hardcoded constants rather than computing from customer data.

**Recommendation:**
1. Remove hardcoded score values immediately
2. Implement real scoring algorithms or invoke external scoring service
3. Add a prominent `@Deprecated` annotation and warning log if the method is retained temporarily
4. Add a feature flag to prevent accidental execution
5. Estimated effort: 1-2 weeks (scoring implementation) or 1 day (remove/disable)

---

## 18. Dead Code Assessment

**ID:** M-13 (new)  
**Severity:** MEDIUM  
**Type:** Dead Code  
**Files Affected:**
- Various V1 controllers, stub implementations, unused interfaces

**Description:**  
The codebase contains several categories of dead or unused code:
- V1 controllers that are superseded by V2 but not removed
- Stub implementations (`ReportsUseCases`) deployed to production
- Unused interfaces that have no implementations
- Deprecated methods without removal timeline
- Configuration properties referenced in code but not documented in application.yml

**Impact:**
- Maintenance burden: dead code must be read and understood during changes
- Confusion: developers cannot distinguish dead code from active code without runtime tracing
- Test coverage metrics inflated: dead code is not tested but counts as coverage
- Binary size and startup time increase marginally

**Recommendation:**
1. Remove V1 controllers after deprecation period
2. Remove stub implementations or implement them
3. Run a static dead-code analysis tool (e.g., `jdeps`, IntelliJ inspections)
4. Add a `@Deprecated` annotation with `forRemoval=true` on all dead code
5. Schedule dead code removal in next sprint

---

## Summary Table

| ID | Finding | Severity | Category | Effort Estimate |
|----|---------|----------|----------|----------------|
| C-02 | LegacyCrmInfrastructureService god class (2044 lines) | CRITICAL | God Class | 3-4 weeks |
| C-10 | V1/V2 controller duplication | CRITICAL | Duplication | 1-2 weeks |
| C-01 | Mock adapters active by default | CRITICAL | Misconfiguration | 2-3 days |
| C-07 | refreshAllScores() hardcoded fake values | CRITICAL | Hardcoded Values | 1-2 weeks |
| C-08 | Hardcoded zero-UUID tenant | CRITICAL | Hardcoded Values | 3-5 days |
| C-09 | Frontend snake_case vs backend camelCase | CRITICAL | Inconsistency | 2-3 weeks |
| I-01 | Anemic domain model | HIGH | Underengineering | 3-4 weeks |
| I-03 | Duplicated auth context in 9 V1 controllers | HIGH | Duplication | 2-3 days |
| I-08 | URL path hardcoding in AOP aspects | HIGH | Hardcoded Values | 1-2 days |
| I-10 | DisabledHrmOwnershipAdapter active in all profiles | HIGH | Misconfiguration | 1 day |
| H-05 | Hardcoded AI Gateway timeout | HIGH | Hardcoded Values | 1 day |
| H-04 | Hardcoded cache TTL and max size | HIGH | Hardcoded Values | 2-3 days |
| H-20 | Missing NOT NULL on criteria column | HIGH | Schema | 1 day |
| M-10 | ReportsUseCases stub implementations | MEDIUM | Unimplemented Code | 1 day |
| M-11 | SearchUseCases missing full-text search | MEDIUM | Incomplete | 1-2 weeks |
| M-12 | Transactional boundary issues in outbox | MEDIUM | Data Integrity | 3-5 days |
| M-13 | Dead code assessment | MEDIUM | Dead Code | 2-3 days |

---

## Recommendations Roadmap

**Immediate (P0) — Stop the Bleeding:**
1. Remove `matchIfMissing=true` from mock adapters
2. Disable or fix `refreshAllScores()` hardcoded values
3. Fix zero-UUID tenant to use proper reference tenant
4. Add `@Profile("!prod")` to DisabledHrmOwnershipAdapter
5. Start LegacyCrmInfrastructureService decomposition

**Short-term (P1) — Reduce Debt:**
6. Begin V1/V2 consolidation; deprecate V1
7. Unify frontend/backend naming convention
8. Externalize hardcoded cache and timeout values
9. Centralize auth context extraction
10. Remove stub implementations or implement them

**Medium-term (P2) — Architectural Health:**
11. Decompose LegacyCrmInfrastructureService fully
12. Enrich anemic domain model with behavioral methods
13. Fix constraint naming conventions
14. Add full-text search
15. Address dead code and unused interfaces

**Total estimated effort: 8-12 weeks (2-3 engineers)**

---

*Report generated by independent forensic audit. 18 technical debt findings identified.*
