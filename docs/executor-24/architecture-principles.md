# Executor #24 — Architecture Principles and Guardrails

## Architecture style

SANAD uses a pragmatic evolutionary architecture:

```text
Modular Monolith where cohesion is high
Microservice where independent scale, security boundary, data ownership or deployment cadence requires it
Event-driven integration for cross-domain propagation
API-first integration for synchronous commands and lookups
```

## Non-negotiable guardrails

1. Every service belongs to exactly one Domain and one Bounded Context.
2. Every System of Record service owns its schema or database boundary.
3. Cross-domain writes through shared databases are prohibited.
4. Cross-domain integration must use APIs or Events.
5. All APIs are versioned and authenticated.
6. All events have schema versions and retention class.
7. Tenant, organization and authorization context must be present in every request and event.
8. Audit events are mandatory for security, finance, HR, workflow and administrative actions.
9. Services must expose health and readiness endpoints before production promotion.
10. Backlog references must link each service to Executor #23 identifiers.

## Decomposition criteria

A capability becomes a separate service when at least one applies:

- It owns a distinct business lifecycle.
- It owns regulated or high-sensitivity data.
- It requires independent scaling.
- It has different availability or recovery targets.
- It is managed by a distinct squad.
- It emits or consumes high-value domain events.
- It requires separate deployment or release cadence.

Otherwise, it remains a module inside the owning bounded context until operational pressure justifies extraction.

## Data ownership

- Command Services own write models.
- Query Services own projections.
- Analytics Services own aggregated analytical models.
- Integration Services own adapters, cursors and synchronization state.
- Admin Services own configuration and administrative views.

## Security model

Every service boundary enforces:

- Tenant isolation.
- Organization isolation.
- RBAC and ABAC.
- Least privilege.
- Idempotency for mutating APIs.
- Audit logging.
- Sensitive data minimization.
- No plaintext secrets.

## Event model

Events must include:

- Event ID.
- Event type.
- Schema version.
- Tenant ID.
- Organization ID.
- Aggregate ID.
- Correlation ID.
- Causation ID.
- Actor context.
- Occurred-at timestamp.

## Runtime dependency rules

- P0 services must avoid unnecessary synchronous dependencies.
- Security dependencies fail closed.
- Non-critical read dependencies may use cached projections if approved.
- Async delivery must support retry, dead-letter and replay.
- Dependency cycles are prohibited unless they are asynchronous and explicitly reviewed.

## Delivery connection

Executor #24 does not replace Executor #23. It consumes Executor #23 backlog identifiers and produces architecture mapping for build sequencing, team ownership and integration contracts.