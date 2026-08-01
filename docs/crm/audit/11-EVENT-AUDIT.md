# Event Audit Report — CRM v2.0.0

**Audit Date:** 2026-07-30  
**Scope:** Event-driven patterns across CRM-002 through CRM-019  
**Severity Assessment:** CRITICAL  

---

## Executive Summary

The CRM codebase exhibits significant deficiencies in its event-driven architecture. Of the 5 event-related findings identified, 3 are classified as Critical and 2 as High. The most severe issues include silent swallowing of event publication failures, absence of domain events for core entity operations, and lack of an outbox pattern for guaranteed delivery. These defects create risks of undetected state inconsistency, data loss during broker outages, and tight coupling between bounded contexts.

**Event Health Score: 35/100 — POOR**

---

## 1. Event Publishing Failures Silently Swallowed

**ID:** C-04  
**Severity:** CRITICAL  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/application/SpringCustomerIntelligenceEventPublisher.java`

**Description:**  
The `SpringCustomerIntelligenceEventPublisher` wraps event publication in a try-catch block that logs at DEBUG level and does not rethrow. Any failure in event delivery — whether due to broker unavailability, serialization error, or listener exception — is silently absorbed. Downstream consumers never receive the event, and upstream callers receive no feedback that publication failed.

**Impact:**
- Customer score changes are not propagated to downstream systems
- Segment membership changes go undetected
- State inconsistency between CRM and integrated systems is guaranteed during any broker disruption
- Debug-level logging means even operators monitoring logs will miss failures in production

**Evidence:**  
The publisher catches `Exception` generically, logs at `log.debug()`, and returns normally. No metric counter is incremented, no alert is raised, and no dead-letter queue exists.

**Recommendation:**
1. Re-throw the exception after logging (or wrap in a domain exception)
2. Implement a retry mechanism with exponential backoff
3. Add metric instrumentation (`Micrometer` counter on failure)
4. Apply `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to ensure events fire only after successful transaction commit
5. Implement a dead-letter queue for events that exceed retry limits

---

## 2. No Domain Events for Core Entity Operations

**ID:** C-12  
**Severity:** CRITICAL  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/` (domain model packages across CRM-002 through CRM-007)

**Description:**  
Core entity operations — creation, update, archiving of Account, Contact, Opportunity, Lead, and Activity entities — do not emit domain events. The codebase relies on direct service-to-service calls for cross-domain communication. This creates tight coupling between bounded contexts and prevents reactive/event-driven workflows.

**Evidence:**  
Examination of domain entity records shows no event publishing mechanism. No `DomainEvent` base class or interface exists. No `ApplicationEventPublisher.publishEvent()` calls are found in use case classes. Integration modules instead poll for changes or use direct repository calls.

**Impact:**
- Cross-domain communication hardwired through service calls
- No audit trail of domain state transitions at the event level
- Cannot implement CQRS with event sourcing
- Reactive workflows (e.g., "when account is created, trigger scoring") require polling or procedural orchestration
- New integrations require modifying existing service code rather than subscribing to events

**Recommendation:**
1. Define a `DomainEvent` base interface with `eventId`, `occurredOn`, `aggregateId`, `aggregateType`, and `eventType`
2. Implement concrete events: `AccountCreated`, `AccountUpdated`, `AccountArchived`, `ContactAssigned`, `OpportunityStageChanged`, `LeadConverted`
3. Emit events from use case classes via `ApplicationEventPublisher`
4. Consider adopting the outbox pattern (see Finding 5) for reliable delivery

---

## 3. Event Outbox Pattern Not Implemented

**ID:** C-04 (related)  
**Severity:** CRITICAL  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/application/CrmIntegrationOutboxWorker.java`

**Description:**  
The codebase contains an `CrmIntegrationOutboxWorker` class, indicating awareness of the outbox pattern. However, this mechanism is limited to integration/workflow events and is not used for customer intelligence domain events. The `SpringCustomerIntelligenceEventPublisher` publishes directly to Spring's `ApplicationEventPublisher` without transactional outbox guarantees.

**Evidence:**  
The integration outbox worker only processes workflow-related events. No generic domain-event outbox table or mechanism exists. Under broker failure, intelligence events are lost without recovery.

**Impact:**
- No at-least-once delivery guarantee for domain events
- Broker outages cause permanent event loss
- No mechanism for replaying historical events
- No ordering guarantees during recovery

**Recommendation:**
1. Create a `domain_event_outbox` table with columns: `id`, `aggregate_id`, `aggregate_type`, `event_type`, `payload` (JSONB), `created_at`, `processed_at`
2. Write events to the outbox table within the same transaction as the domain operation
3. Implement a scheduled worker (or use Debezium CDC) to publish from the outbox to the broker
4. Ensure idempotent processing of events on the consumer side

---

## 4. Missing Event Versioning and Schema Contracts

**ID:** H-01 (new)  
**Severity:** HIGH  
**Files Affected:**
- All event publisher and subscriber classes across CRM modules

**Description:**  
The codebase does not implement event versioning or schema contracts for domain events. Events are published as Java objects serialized by Spring's default event infrastructure. No schema registry, no version field in event payloads, and no backward-compatibility checks exist. Changes to event structures will silently break consumers.

**Evidence:**  
No `eventVersion` or `schemaVersion` fields found in event classes. No Avro/Protobuf/JSON Schema definitions exist. Event classes are plain Java objects with no compatibility guarantees documented.

**Impact:**
- Schema evolution breaks downstream consumers silently
- Rolling deployments with event format changes cause deserialization failures
- No mechanism to coordinate event contract changes across teams
- Third-party integrations lack a formal contract to code against

**Recommendation:**
1. Add `eventVersion` (integer) and `eventType` (string) to all event payloads
2. Document event schemas using JSON Schema or AsyncAPI
3. Implement version negotiation or maintain backward-compatible upcasters
4. Consider a schema registry (Confluent Schema Registry or Apicurio) for production deployments

---

## 5. Duplicate Event Definitions Across V1 and V2 Controllers

**ID:** C-10 (related)  
**Severity:** HIGH  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/` (V1 controllers)
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/` (V2 controllers)

**Description:**  
The coexistence of V1 and V2 controller layers creates duplicate event publishing paths for the same domain operations. Depending on which controller version handles a request, different events (or no events) may be published. The two code paths can diverge behaviorally over time, leading to inconsistent event streams.

**Impact:**
- Consumer confusion: same logical operation produces different events depending on API version used
- Maintenance burden: event logic must be kept synchronized across both controller layers
- Behavioral divergence risk as V1 and V2 evolve independently

**Recommendation:**
1. Consolidate event publishing into use case/application service layer — not in controllers
2. Deprecate V1 controllers and route all traffic through V2
3. Ensure all domain operations publish identical events regardless of API version

---

## 6. No Correlation ID Propagation Across Event Boundaries

**ID:** H-02 (new)  
**Severity:** HIGH  
**Files Affected:**
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CorrelationContextPort.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/SpringCorrelationContextAdapter.java`

**Description:**  
While a `CorrelationContextPort` interface exists, correlation IDs are not consistently propagated through the event pipeline. Events published from background workers, scheduled tasks, and asynchronous processes may lack correlation context, making it impossible to trace end-to-end request flows through event-driven workflows.

**Evidence:**  
The `CorrelationContextPort` is implemented but examination shows it relies on thread-local storage that does not automatically propagate across asynchronous event boundaries.

**Impact:**
- Debugging event-driven workflows requires manual log correlation
- Cannot trace a user action through to downstream event-triggered processing
- Audit trails lack end-to-end transaction identifiers

**Recommendation:**
1. Ensure correlation ID is captured in all event headers/payloads
2. Propagate correlation context through async boundaries using `HystrixRequestVariableDefault` or Spring Cloud Sleuth
3. Include correlation ID in outbox table for replay traceability

---

## 7. Event Payloads Lack Metadata (Timestamps, Causation IDs)

**ID:** H-03 (new)  
**Severity:** MEDIUM  
**Files Affected:**
- All event classes across CRM modules

**Description:**  
Event payloads do not consistently include metadata fields such as `occurredAt`, `causationId`, `correlationId`, or `userId`. This impedes event sourcing, audit, and debugging. Without causation IDs, it is impossible to reconstruct which event triggered a subsequent event.

**Impact:**
- Event store cannot support event sourcing without required metadata
- Auditors cannot determine who triggered state changes
- Debugging event chains requires guesswork

**Recommendation:**
1. Define a standard event envelope with: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `causationId`, `userId`, `tenantId`
2. All event classes should extend or include this envelope
3. Populate metadata at publication time from security context

---

## Summary Table

| ID | Finding | Severity | Affected Files | Priority |
|----|---------|----------|----------------|----------|
| C-04 | Event publication failures silently swallowed | CRITICAL | SpringCustomerIntelligenceEventPublisher | P0 |
| C-12 | No domain events for core entity operations | CRITICAL | All domain model packages | P0 |
| C-04b | Event outbox pattern not implemented | CRITICAL | CrmIntegrationOutboxWorker | P0 |
| H-01 | Missing event versioning and schema contracts | HIGH | All event classes | P1 |
| C-10b | Duplicate event definitions across V1/V2 controllers | HIGH | Ownership web controllers | P1 |
| H-02 | No correlation ID propagation across event boundaries | HIGH | CorrelationContextPort | P1 |
| H-03 | Event payloads lack metadata | MEDIUM | All event classes | P2 |

---

## Recommendations Roadmap

**Immediate (P0):**
1. Fix `SpringCustomerIntelligenceEventPublisher` to surface publication failures
2. Implement domain events for Account, Contact, Opportunity, Lead, and Activity operations
3. Implement transactional outbox pattern for all domain events

**Short-term (P1):**
4. Add event versioning and schema contracts
5. Consolidate V1/V2 event publishing paths
6. Implement correlation ID propagation through async event boundaries

**Medium-term (P2):**
7. Add standard event metadata envelope to all event payloads
8. Consider event sourcing for audit-critical aggregates

---

*Report generated by independent forensic audit. 7 event-related findings identified.*
