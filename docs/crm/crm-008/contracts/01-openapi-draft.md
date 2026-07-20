# CRM-008 — OpenAPI Contract Draft (Summary)

> Full OpenAPI JSON will be generated in CRM-008B implementation phase. This document is the human-readable summary.
>
> All endpoints under `/api/v2/crm/` — follows the existing v2 CRM contract pattern (`CrmContractController`, `CrmV2AtomicMutationInfrastructureService`).

---

## Endpoint Summary (38 endpoints)

### Teams (8)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/teams` | `CRM.TEAM.READ` | List teams (paginated, tenant-scoped) |
| POST | `/api/v2/crm/teams` | `CRM.TEAM.ADMIN` | Create team |
| GET | `/api/v2/crm/teams/{teamId}` | `CRM.TEAM.READ` | Get team detail |
| PATCH | `/api/v2/crm/teams/{teamId}` | `CRM.TEAM.ADMIN` | Update team (name, status, manager) |
| GET | `/api/v2/crm/teams/{teamId}/memberships` | `CRM.TEAM.READ` | List team memberships |
| POST | `/api/v2/crm/teams/{teamId}/memberships` | `CRM.TEAM.ADMIN` | Add member |
| PATCH | `/api/v2/crm/teams/{teamId}/memberships/{membershipId}` | `CRM.TEAM.ADMIN` | Update member role / set primary |
| DELETE | `/api/v2/crm/teams/{teamId}/memberships/{membershipId}` | `CRM.TEAM.ADMIN` | End membership (sets status=ENDED, left_at=now) |

### Queues (8)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/queues` | `CRM.QUEUE.READ` | List queues |
| POST | `/api/v2/crm/queues` | `CRM.QUEUE.ADMIN` | Create queue |
| GET | `/api/v2/crm/queues/{queueId}` | `CRM.QUEUE.READ` | Get queue detail (with waiting count, oldest item, SLA violations) |
| PATCH | `/api/v2/crm/queues/{queueId}` | `CRM.QUEUE.ADMIN` | Update queue (status, max_items, sla_minutes) |
| GET | `/api/v2/crm/queues/{queueId}/items` | `CRM.QUEUE.READ` | List queue items (paginated, filterable by status) |
| POST | `/api/v2/crm/queues/{queueId}/items/{itemId}/claim` | `CRM.QUEUE.CLAIM` | Claim an item (idempotent, concurrent-safe) |
| POST | `/api/v2/crm/queues/{queueId}/items/{itemId}/release` | `CRM.QUEUE.CLAIM` | Release an item back to the queue |
| GET | `/api/v2/crm/queues/{queueId}/memberships` | `CRM.QUEUE.READ` | List queue members |

### Territories (6)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/territories` | `CRM.TERRITORY.READ` | List territories (flat or hierarchical) |
| POST | `/api/v2/crm/territories` | `CRM.TERRITORY.ADMIN` | Create territory (cycle-checked) |
| GET | `/api/v2/crm/territories/{territoryId}` | `CRM.TERRITORY.READ` | Get territory with children and assignments |
| PATCH | `/api/v2/crm/territories/{territoryId}` | `CRM.TERRITORY.ADMIN` | Update territory (parent change → cycle re-checked) |
| POST | `/api/v2/crm/territories/{territoryId}/assignments` | `CRM.TERRITORY.ADMIN` | Assign team/user to territory |
| DELETE | `/api/v2/crm/territories/{territoryId}/assignments/{assignmentId}` | `CRM.TERRITORY.ADMIN` | Remove territory assignment |

### Assignment Rules (7)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/assignment-rules` | `CRM.ASSIGNMENT_RULE.READ` | List rules (current active versions) |
| POST | `/api/v2/crm/assignment-rules` | `CRM.ASSIGNMENT_RULE.ADMIN` | Create rule (v1) |
| GET | `/api/v2/crm/assignment-rules/{ruleId}` | `CRM.ASSIGNMENT_RULE.READ` | Get rule with versions |
| POST | `/api/v2/crm/assignment-rules/{ruleId}/versions` | `CRM.ASSIGNMENT_RULE.ADMIN` | Create new version (bumps current_version) |
| PATCH | `/api/v2/crm/assignment-rules/{ruleId}/versions/{version}/activate` | `CRM.ASSIGNMENT_RULE.ADMIN` | Activate a specific version (deactivates others) |
| POST | `/api/v2/crm/assignment-rules/simulate` | `CRM.ASSIGNMENT_RULE.READ` | Simulate rule evaluation without persisting |
| GET | `/api/v2/crm/assignment-rules/{ruleId}/versions` | `CRM.ASSIGNMENT_RULE.READ` | List all versions |

### Assignments (4)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/assignments/current` | `CRM.ASSIGNMENT.READ` | Get current assignment for a record (`?recordType=&recordId=`) |
| POST | `/api/v2/crm/assignments/reassign` | `CRM.ASSIGNMENT.WRITE` | Manually reassign a record (single) |
| POST | `/api/v2/crm/assignments/bulk-reassign` | `CRM.ASSIGNMENT.ADMIN` | Bulk reassign (audit-heavy, capacity-checked) |
| GET | `/api/v2/crm/ownership-history` | `CRM.OWNERSHIP_HISTORY.READ` | Get ownership history (cursor-paginated) |

### Transfers (5)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/transfers` | `CRM.TRANSFER.READ` | List transfers (incoming/outgoing, filterable) |
| POST | `/api/v2/crm/transfers` | `CRM.TRANSFER.REQUEST` | Create transfer request (DRAFT) |
| POST | `/api/v2/crm/transfers/{transferId}/submit` | `CRM.TRANSFER.REQUEST` | Submit for approval (DRAFT → SUBMITTED) |
| POST | `/api/v2/crm/transfers/{transferId}/approve` | `CRM.TRANSFER.APPROVE` | Approve (or reject with `?decision=reject`) |
| POST | `/api/v2/crm/transfers/{transferId}/cancel` | `CRM.TRANSFER.REQUEST` | Cancel (requester only) |

### My Work (1 — aggregated read)
| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/v2/crm/my-work` | `CRM.ASSIGNMENT.READ` | Aggregated view: my owned records, open leads, open opportunities, due tasks, overdue items, recent transfers, workload summary |

---

## Common patterns (all endpoints)

### Request envelope
All list endpoints accept:
- `page` (1-indexed, default 1)
- `pageSize` (default 25, max 100)
- `sort` (e.g. `createdAt,desc`)
- `cursor` (for ownership-history — opaque, server-issued)

All write endpoints accept:
- `If-Match` header (ETag) for optimistic concurrency
- `Idempotency-Key` header (UUID) for safe retry (only on POST)

### Response envelope
```json
{
  "data": { ... },          // for single-resource responses
  "data": [ ... ],          // for list responses
  "pagination": {            // for list responses
    "page": 1,
    "pageSize": 25,
    "total": 137,
    "cursor": "..."
  },
  "requestId": "uuid",
  "correlationId": "uuid"    // propagated to audit + ownership_history
}
```

### Error envelope (RFC 7807 Problem Details)
```json
{
  "type": "https://docs.sanad.app/errors/concurrent-claim-conflict",
  "title": "Concurrent Claim Conflict",
  "status": 409,
  "detail": "Another user claimed this item first.",
  "instance": "/api/v2/crm/queues/{queueId}/items/{itemId}/claim",
  "code": "concurrent_claim_conflict",
  "correlationId": "uuid",
  "requestId": "uuid"
}
```

### Capability enforcement
Every endpoint declares `@RequireCapability("CRM.<MODULE>.<ACTION>")` — enforced by `CapabilityAuthorizationAspect` (existing). Deny-by-default.

### Tenant scoping
`tenantId` is read from `SecurityContext` (JWT claim) — **never** from request body or query parameter (CONSTITUTION §3.4).

### Audit
Every write endpoint emits:
1. A `platform_audit_logs` row (existing flow)
2. A `crm_ownership_history` row (for ownership-changing operations only)
3. A domain event (for notifications + analytics)

The `correlationId` is propagated across all three to enable cross-system traceability.
