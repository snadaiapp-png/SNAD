# CRM-010 Event Catalog

## Event Interface

All events implement `CustomerIntelligenceEvent`:

```java
public interface CustomerIntelligenceEvent {
    UUID tenantId();
    UUID accountId();
    String eventType();
    Instant occurredAt();
    String correlationId();
}
```

## Published Events

| Event Class | EventType | Trigger | Key Fields |
|-------------|-----------|---------|------------|
| `CustomerScoreCalculatedEvent` | `crm.intelligence.score.calculated` | After score persistence | `scoreType`, `scoreValue`, `scoreBand`, `previousValue`, `delta`, `triggerReason` |
| `CustomerHealthChangedEvent` | `crm.intelligence.health.changed` | When health band changes | `previousBand`, `newBand`, `scoreValue` |
| `CustomerSegmentChangedEvent` | `crm.intelligence.segment.changed` | Add/remove segment membership | `segmentCode`, `membershipType`, `isJoin` |
| `NextBestActionGeneratedEvent` | `crm.intelligence.next_best_action.generated` | After NBA creation | `actionId`, `actionCode`, `confidence` |
| `CustomerLifetimeValueUpdatedEvent` | `crm.intelligence.lifetime_value.updated` | After CLV calculation | `predictedValue`, `tier`, `confidence` |
| `OpportunityScoreUpdatedEvent` | `crm.intelligence.opportunity.updated` | After opportunity detection | `score`, `opportunityType`, `estimatedValue` |

## Event Metadata

Every event carries:
- `tenantId` — Tenant isolation identifier
- `accountId` — Customer identifier
- `correlationId` — Unique ID for tracing (format: `{prefix}-{uuid}`)
- `occurredAt` — Event timestamp (`Instant.now()`)

## Event Publication

Events are published via `CustomerIntelligenceEventPublisher` (Spring `ApplicationEventPublisher` bridge). Events publish **within the transaction boundary** — if the transaction rolls back, events are not published.

## Event Consumers

Events are consumed by:
- **Timeline aggregation** — Each event triggers a timeline record via `TimelineEventPort`
- **Audit logging** — Score changes are audited via `AuditPort`
- **Cache invalidation** — Score/segment events trigger cache invalidation

## Correlation ID Prefixes

| Prefix | Source |
|--------|--------|
| `score-` | Health score calculation |
| `clv-` | CLV calculation |
| `risk-` | Churn risk prediction |
| `segment-` | Segment membership changes |
| `nba-` | NBA generation |
| `opp-` | Opportunity detection |
| `intel-` | AI Gateway requests |
