# CRM-010 Application Services

## Overview

The Application Layer implements the orchestration logic for Customer Intelligence services following DDD Hexagonal Architecture. All services are stateless Spring `@Service` beans with constructor injection, tenant-aware, and transactional where applicable.

## Service Registry

| Service | Package | Responsibility | Dependencies |
|---------|---------|----------------|--------------|
| `Customer360ApplicationService` | `application` | Aggregates profile + intelligence into unified 360 view | `Customer360QueryPort`, `QueryAdapter`, `AccountRepository`, `Cache` |
| `CustomerScoringService` | `application` | Calculates and persists all score types | `ScoringPort`, `QueryAdapter`, `AiOrchestrator`, `EventPublisher`, `Timeline`, `Cache`, `Validator` |
| `CustomerHealthService` | `application` | Thin facade over health scoring | `CustomerScoringService`, `QueryAdapter` |
| `CustomerLifetimeValueService` | `application` | CLV forecasting with AI enhancement | `AiOrchestrator`, `ScoringPort`, `QueryAdapter`, `EventPublisher`, `Timeline`, `Cache`, `Validator` |
| `ChurnPredictionService` | `application` | Churn risk prediction | `AiOrchestrator`, `ScoringPort`, `QueryAdapter`, `EventPublisher`, `Timeline`, `Cache`, `Validator` |
| `CustomerSegmentationService` | `application` | Segment CRUD and membership management | `SegmentPort`, `QueryAdapter`, `EventPublisher`, `Timeline`, `Cache`, `Validator` |
| `NextBestActionService` | `application` | NBA lifecycle: generate, accept, reject, expire | `NextBestActionPort`, `QueryAdapter`, `EventPublisher`, `Timeline`, `Cache`, `Validator` |
| `OpportunityScoringService` | `application` | Opportunity detection and scoring | `AiOrchestrator`, `NbaService`, `EventPublisher`, `Timeline`, `Validator` |
| `CustomerInsightService` | `application` | Aggregated intelligence summary | `QueryAdapter`, `HealthService`, `ClvService`, `ChurnService` |
| `AiScoreOrchestrator` | `application` | AI Gateway request orchestration | `AiGatewayPort`, `ObjectMapper`, `AuditPort`, `Properties` |
| `CustomerIntelligenceValidator` | `application` | Validation: tenant, customer existence, active status | `AccountRepository` |
| `CustomerIntelligenceQueryPortAdapter` | `application` | Pass-through adapter for query port | `CustomerIntelligenceQueryPort` |

## Transaction Boundaries

All write operations are annotated with `@Transactional`:

- `calculateHealthScore` — score calculation + event publication
- `refreshAllScores` — batch score refresh
- `calculateCLV` — CLV calculation + event publication
- `predictChurnRisk` — churn prediction + event publication
- `createSegment` — segment creation
- `addCustomerToSegment` — membership assignment
- `removeCustomerFromSegment` — membership deactivation
- `generateRecommendation` — NBA creation
- `acceptRecommendation` — NBA acceptance
- `rejectRecommendation` — NBA rejection
- `expireStaleRecommendations` — NBA expiry batch
- `detectOpportunity` — opportunity detection

## Validation Framework

All write operations validate:

1. **Customer existence** — `AccountRepository.findById(tenantId, accountId)` must return non-null
2. **Active status** — `lifecycleStatus` must be `ACTIVE` (case-insensitive)

`CustomerIntelligenceValidator.validateScoreType()` and `validateConfidence()` are defined but not yet called by application services (score types are hardcoded constants).

Validation failures throw `CustomerValidationException` with structured error codes.

## AI Gateway Integration

All AI capabilities are orchestrated through `AiScoreOrchestrator`:

1. Build capability-specific indicators (JSON payload)
2. Create `IntegrationEnvelope` with correlation ID, idempotency key, TTL
3. Call `AiGatewayPort.request()`
4. Audit the request/response
5. Check confidence against configured threshold
6. Return result (or null on failure for fail-closed behavior)

AI capabilities used:
- `crm.customer_intelligence.ai.health_scoring`
- `crm.customer_intelligence.ai.clv_forecast`
- `crm.customer_intelligence.ai.churn_prediction`
- `crm.customer_intelligence.ai.opportunity_scoring`
- `crm.customer_intelligence.ai.next_best_action`
- `crm.customer_intelligence.ai.segmentation`

## Cache Strategy

Two Caffeine caches with 5-minute TTL and 10,000 max entries:

| Cache | Key Pattern | Purpose |
|-------|------------|---------|
| `scores` | `scores:v1:{tenantId}:{accountId}` | Latest scores per account |
| `view` | `view:v1:{tenantId}:{accountId}` | Assembled Customer 360 view |

Most write operations call `cache.invalidateAll(tenantId, accountId)`. Exception: `expireStaleRecommendations()` does not invalidate cache (batch operation).

## Observability

Each service includes:
- Structured SLF4J logging (`log.info`, `log.warn`, `log.error`)
- Timeline event publication for all mutations
- Audit trail via `AuditPort` for AI requests
- Metrics via Caffeine cache statistics (`scoresStats()`, `viewStats()`)
