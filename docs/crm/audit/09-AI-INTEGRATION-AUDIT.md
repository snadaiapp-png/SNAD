# AI Integration Audit

**Audit Scope:** AI Gateway contracts, scoring service, next-best-action, customer intelligence pipeline, mock adapters in production, AI scoring orchestration, segment-based AI recommendations.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** CRITICAL -- Mock adapters default to production, AI scoring uses hardcoded placeholder values, event exceptions are silently swallowed, and the AI Gateway timeout is hardcoded. The customer intelligence pipeline cannot be trusted for production use without significant remediation.

---

## Executive Summary

The Customer Intelligence module (CRM-010) represents the highest-risk component in the SNAD CRM codebase. All five external data source adapters (POS, HRM, ERP, Commerce, Accounting) default to mock implementations that generate synthetic data. The scoring service contains hardcoded placeholder values that overwrite real computed scores. Event publishing failures are silently swallowed. The AI Gateway timeout is hardcoded. The cache is not configurable. While the architectural structure is well-organized (domain ports, application services, infrastructure adapters), the implementation is not production-ready.

---

## Finding AIN-01: All Five External Data Adapters Default to Mock

**Severity:** CRITICAL
**Category:** Production Readiness

### Description
Five adapters in the `crm.intelligence.infrastructure` package use `@ConditionalOnProperty` with `matchIfMissing = true`:

- `MockPosDataAdapter`
- `MockHrmDataAdapter`
- `MockErpDataAdapter`
- `MockCommerceDataAdapter`
- `MockAccountingDataAdapter`

This means if the corresponding property (`sanad.intelligence.pos.provider`, etc.) is not explicitly set in the environment, the mock adapter is used. The `CustomerIntelligenceProperties` defaults all providers to `"mock"`, compounding the risk.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\MockPosDataAdapter.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\MockHrmDataAdapter.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\MockErpDataAdapter.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\MockCommerceDataAdapter.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\MockAccountingDataAdapter.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\config\CustomerIntelligenceProperties.java`

### Impact
- In production, customer intelligence pipelines process synthetic data from all five external systems
- Customer health scores, CLV, engagement scores, risk scores, and loyalty scores are computed from deterministic hash-based fake data, not real business data
- AI recommendations (next-best-action) are based on synthetic inputs
- Business decisions based on this data are unreliable

### Recommendation
1. Change `matchIfMissing` to `false` on all mock adapters
2. Add explicit production configuration to set all providers to `http` or `disabled` as appropriate
3. Add a startup check that validates no mock adapter is active in production profile
4. Create real HTTP-based adapters for each external system or disable the intelligence pipeline until real adapters are available

---

## Finding AIN-02: CustomerScoringService.refreshAllScores() Uses Hardcoded Values

**Severity:** CRITICAL
**Category:** Data Integrity

### Description
`refreshAllScores()` passes hardcoded literal values to `calculateHealthScore()`:

```java
calculateHealthScore(tenantId, accountId, actorId,
        7, 2, 50000, 3, 8, "ACTIVE");
```

These values (7 days since last activity, 2 open opportunities, $50K pipeline, 3 meetings, 8-hour response time) are meaningless placeholders. They overwrite any previously computed scores when `refreshAllScores()` is called.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\application\CustomerScoringService.java` (lines 145-148)

### Impact
- Any call to `refreshAllScores()` destroys real scoring data
- If a scheduled task calls this method, scores are periodically reset to fake values
- Customer 360 views show incorrect data after refresh
- No indication in the method signature that it uses placeholder values

### Recommendation
1. Remove the hardcoded values and require real inputs or query them from repositories
2. Add a `@Deprecated` marker or exclude from production code paths
3. Refactor the method to accept a parameter object instead of 6+ positional arguments
4. Add integration test that verifies `refreshAllScores()` does not produce placeholder data

---

## Finding AIN-03: SpringCustomerIntelligenceEventPublisher Silently Swallows Exceptions

**Severity:** CRITICAL
**Category:** Reliability / Observability

### Description
The event publisher catches all exceptions from `ApplicationEventPublisher.publishEvent()` and only logs them. Downstream event listeners (cache invalidation, timeline recording, audit logging) may fail without affecting the main flow. This creates silent data inconsistency.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\SpringCustomerIntelligenceEventPublisher.java` (lines 26-33)

### Impact
- Cache may not be invalidated after score updates, serving stale data
- Timeline events may be lost without any alert
- Audit port records may be missing for scoring operations
- Operational monitoring cannot detect these failures

### Recommendation
1. Remove the try-catch block -- let exceptions propagate
2. If a fail-silent policy is required for specific event types, make it explicit and configurable
3. Add metrics for event publication success/failure counts
4. Implement a dead-letter queue for failed events

---

## Finding AIN-04: Hardcoded AI Gateway Timeout (30 Seconds)

**Severity:** MEDIUM
**Category:** Configuration / Reliability

### Description
The AI Gateway request timeout is hardcoded to 30 seconds. This is not configurable via application properties. In a production environment with variable AI service latency, this can cause:
- Thread pool exhaustion if multiple slow requests queue up
- Cascading failures if the AI service becomes degraded
- No ability to tune per-deployment

### Recommendation
1. Externalize the AI Gateway timeout as a configuration property
2. Implement a circuit breaker pattern to fail fast when the AI service is degraded
3. Set a realistic timeout based on SLA requirements and load testing
4. Add connection timeout and read timeout as separate properties

---

## Finding AIN-05: CustomerIntelligenceCache TTL and Max Size Not Configurable

**Severity:** MEDIUM
**Category:** Configuration

### Description
`CustomerIntelligenceCache` hardcodes cache TTL (5 minutes) and max size (10,000 entries). These cannot be tuned without a code change. For tenant isolation, shorter TTLs may be needed for sensitive scoring data. For large deployments, 10K entries may be insufficient.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\CustomerIntelligenceCache.java` (lines 29-30)

### Recommendation
1. Externalize TTL and max size as configuration properties (e.g., `sanad.intelligence.cache.ttl-minutes`, `sanad.intelligence.cache.max-size`)
2. Add cache hit rate monitoring to inform sizing
3. Consider per-tenant cache limits

---

## Finding AIN-06: customer360() Uses @SuppressWarnings("unchecked")

**Severity:** MEDIUM
**Category:** Type Safety

### Description
The `getView()` method in `CustomerIntelligenceCache` uses `@SuppressWarnings("unchecked")` for an unchecked cast:

```java
@Override
@SuppressWarnings("unchecked")
public <T> T getView(UUID tenantId, UUID accountId, Class<T> type) {
    Object cached = viewCache.getIfPresent(viewKey(tenantId, accountId));
    return cached != null && type.isInstance(cached) ? (T) cached : null;
}
```

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\CustomerIntelligenceCache.java` (lines 76-80)

### Impact
- Type safety violations are masked and will manifest as `ClassCastException` at runtime
- The suppression annotation is a code smell indicating an architectural design gap

### Recommendation
Refactor to use type-safe cache methods or a generic cache wrapper that preserves type information.

---

## Finding AIN-07: Mock Adapters for Intelligence Have 5 Separate @ConditionalOnProperty Annotations

**Severity:** MEDIUM
**Category:** Configuration Duplication

### Description
Each of the five mock adapters has its own separate `@ConditionalOnProperty` annotation with a different property name. This creates five independent configuration points that must all be set correctly to enable production mode. Missing even one will leave that adapter in mock mode.

### Affected Files
- All five mock adapters (each has its own `@ConditionalOnProperty(name = "sanad.intelligence.{system}.provider", ...)`)

### Recommendation
1. Use a single composite condition or a configuration class that controls all five adapters from one property
2. Add a validation that checks all five providers are set consistently
3. Consider using a profile-based approach (`@Profile("dev")`) for all mock adapters together

---

## Finding AIN-08: ExtractScoreFromExplanation Uses Heuristic Placeholder

**Severity:** MEDIUM
**Category:** Incomplete Implementation

### Description
The `extractScoreFromExplanation()` method in `CustomerScoringService` uses a heuristic to extract scores from AI explanations:

```java
private double extractScoreFromExplanation(String explanation, Double confidence) {
    if (confidence != null) {
        return Math.round(confidence * 100 * 10.0) / 10.0;
    }
    return 50.0; // neutral default
}
```

This does not actually parse the explanation text. It simply converts the confidence score to a 0-100 scale. The comment acknowledges this is a placeholder: "In a real implementation, the AI result would carry the score in a structured field."

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\application\CustomerScoringService.java` (lines 182-189)

### Impact
- AI scores are not actually extracted from AI responses
- The score returned is just a deterministic function of confidence
- The "neutral default" of 50.0 is used when confidence is null

### Recommendation
1. Implement proper score extraction from the AI response structure
2. Add structured score fields to the AI gateway contract
3. Document the AI scoring contract with expected response format
4. Add integration tests that verify score extraction with sample AI responses

---

## Finding AIN-09: AiScoreOrchestrator -- Bridge Between Scoring and AI Gateway

**Severity:** MEDIUM
**Category:** Architecture

### Description
`AiScoreOrchestrator` acts as a bridge between the scoring service and the AI Gateway. Its role is positive (separating concerns) but the implementation should be reviewed for:
- Error handling (AI gateway failures)
- Timeout management
- Retry logic
- Response validation

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\application\AiScoreOrchestrator.java`

### Recommendation
1. Audit `AiScoreOrchestrator` for error handling completeness
2. Add retry with exponential backoff for transient failures
3. Add timeout propagation from caller
4. Add response schema validation

---

## Finding AIN-10: NextBestActionService -- AI Recommendation Pipeline

**Severity:** MEDIUM
**Category:** Completeness

### Description
`NextBestActionService` generates AI recommendations. If it depends on the mock data adapters (which produce synthetic data), the recommendations will be based on invalid inputs. Additionally, the recommendation pipeline should be audited for:
- Business rule integration
- Action code taxonomy
- Human-in-the-loop confirmation workflow (supported by `human_confirmation_required` column)

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\application\NextBestActionService.java`

### Recommendation
1. Verify that NBA pipeline uses real (not mock) data sources
2. Document action code taxonomy
3. Implement the human-in-the-loop confirmation flow end-to-end
4. Add integration tests for the full NBA lifecycle

---

## Conclusion

The Customer Intelligence module (CRM-010) has the architectural foundation for a robust AI integration but is not ready for production. The most critical issues are the five mock adapters defaulting to production operation, the hardcoded placeholder values in `refreshAllScores()`, and the silent swallowing of event publishing exceptions. The AI Gateway timeout and cache configuration should also be made configurable. Until these issues are resolved, the customer intelligence pipeline cannot be trusted for production use.

**Overall AI Integration Score: 2/10 -- Not production-ready.**
Mock data and placeholder values render the pipeline untrustworthy.
