# CRM-008 — RBAC Matrix

> 17 capabilities + 2 new special roles, all tenant-scoped, all deny-by-default.

---

## 1. New Capabilities (17)

| Code | Description | Required for |
|---|---|---|
| `CRM.ASSIGNMENT.READ` | View current assignments and history | Read access to ownership panel, My Work, assignment history |
| `CRM.ASSIGNMENT.WRITE` | Manually assign or reassign a record | Direct reassign button on Account/Lead/Opportunity |
| `CRM.ASSIGNMENT.ADMIN` | Bulk assignment, override rules, force-assign | Bulk assignment UI, override rule decisions |
| `CRM.TRANSFER.READ` | View transfer requests (incoming + outgoing) | Transfers list page |
| `CRM.TRANSFER.REQUEST` | Create a transfer request | Transfer button on records |
| `CRM.TRANSFER.APPROVE` | Approve or reject a transfer request | Approval UI in transfers page |
| `CRM.TRANSFER.EXECUTE` | Execute an approved transfer | System-internal (workflow callback) — not granted to humans directly |
| `CRM.TEAM.READ` | View sales teams and members | Teams page |
| `CRM.TEAM.ADMIN` | Create/edit/suspend teams, change manager | Teams admin UI |
| `CRM.QUEUE.READ` | View queues and their items | Queues page |
| `CRM.QUEUE.CLAIM` | Claim a queue item | Claim button on queue items |
| `CRM.QUEUE.ADMIN` | Create/edit queues, manage members | Queues admin UI |
| `CRM.TERRITORY.READ` | View territory hierarchy and assignments | Territories page |
| `CRM.TERRITORY.ADMIN` | Create/edit territories, manage assignments | Territories admin UI |
| `CRM.ASSIGNMENT_RULE.READ` | View assignment rules and versions | Rules page |
| `CRM.ASSIGNMENT_RULE.ADMIN` | Create/edit/simulate/activate/deactivate rules | Rules admin UI |
| `CRM.OWNERSHIP_HISTORY.READ` | View ownership history for any record | Ownership history tab on record details |

All capabilities follow the existing SANAD pattern:
- Stored in `access_capabilities` table (seeded in V7 + V15 + V20260717_5 + V20260717_101 + new V20260720_8)
- All start with `a0000007-0000-0000-0000-0000000000XX` UUID prefix (reserved range for CRM-008)
- Each capability has `code`, `display_name`, `description`, `status='ACTIVE'`

---

## 2. New Roles (2)

### `SALES_MANAGER`
- Display name: "Sales Manager"
- Description: "Manages sales teams, approves transfers, administers assignment rules"
- Capabilities granted:
  - `CRM.ASSIGNMENT.READ`
  - `CRM.ASSIGNMENT.WRITE`
  - `CRM.ASSIGNMENT.ADMIN`
  - `CRM.TRANSFER.READ`
  - `CRM.TRANSFER.REQUEST`
  - `CRM.TRANSFER.APPROVE`
  - `CRM.TEAM.READ`
  - `CRM.TEAM.ADMIN`
  - `CRM.QUEUE.READ`
  - `CRM.QUEUE.ADMIN`
  - `CRM.TERRITORY.READ`
  - `CRM.TERRITORY.ADMIN`
  - `CRM.ASSIGNMENT_RULE.READ`
  - `CRM.ASSIGNMENT_RULE.ADMIN`
  - `CRM.OWNERSHIP_HISTORY.READ`
- NOT granted: `CRM.TRANSFER.EXECUTE` (internal-only), `CRM.QUEUE.CLAIM` (managers should claim via team workflow, not directly)

### `SALES_REPRESENTATIVE`
- Display name: "Sales Representative"
- Description: "Frontline sales user — works on assigned records, claims queue items, requests transfers"
- Capabilities granted:
  - `CRM.ASSIGNMENT.READ` (own records only — enforced by app layer, not just by capability)
  - `CRM.ASSIGNMENT.WRITE` (own records only)
  - `CRM.TRANSFER.READ` (own incoming/outgoing only)
  - `CRM.TRANSFER.REQUEST`
  - `CRM.TEAM.READ`
  - `CRM.QUEUE.READ`
  - `CRM.QUEUE.CLAIM`
  - `CRM.TERRITORY.READ`
  - `CRM.ASSIGNMENT_RULE.READ` (read-only, for transparency)
  - `CRM.OWNERSHIP_HISTORY.READ` (own records only)

---

## 3. Existing `ADMIN` role augmentation

Per `CredentialBootstrapService.ensureAdminAllCapabilities()` (already in the codebase), the `ADMIN` role automatically receives ALL active capabilities. When V20260720_8 seeds the 17 new capabilities, the next `ADMIN` bootstrap (or a one-time `ensureAdminAllCapabilities` trigger) will grant them all to the ADMIN role.

**No code change required** — the existing bootstrap flow handles this.

---

## 4. RBAC Enforcement Matrix (key scenarios)

| Scenario | Required capability | Additional check |
|---|---|---|
| View My Work page | `CRM.ASSIGNMENT.READ` | None (always allowed for own user) |
| Reassign a Lead to another user | `CRM.ASSIGNMENT.WRITE` | Actor must be in same tenant; target user must be ACTIVE in same tenant |
| Bulk reassign 50 records | `CRM.ASSIGNMENT.ADMIN` | Bulk audit row created; requires `actor_user_id` and `reason` |
| Create a transfer request for an Account | `CRM.TRANSFER.REQUEST` | Separation of Duties: requester ≠ proposed new owner when policy requires approval |
| Approve a transfer request | `CRM.TRANSFER.APPROVE` | Separation of Duties: approver ≠ requester |
| Claim a queue item | `CRM.QUEUE.CLAIM` | User must be ACTIVE member of the queue; capacity not exceeded |
| Create a sales team | `CRM.TEAM.ADMIN` | Manager (if specified) must be ACTIVE user |
| Modify a territory hierarchy | `CRM.TERRITORY.ADMIN` | Cycle detection passes; children of archived territories must also be archived |
| Activate an assignment rule | `CRM.ASSIGNMENT_RULE.ADMIN` | Only one ACTIVE version per (tenant, code) — enforced by DB |
| View ownership history for any record | `CRM.OWNERSHIP_HISTORY.READ` | Always tenant-scoped — never crosses tenants |

---

## 5. Cross-tenant isolation guarantees (AC-01)

Every capability check is **always** paired with tenant scoping:

1. `JwtAuthenticationFilter` extracts `tenant_id` from JWT → puts in `SecurityContext.authentication.details`
2. `CapabilityAuthorizationAspect` (existing) checks the capability code
3. Every repository method takes `tenantId` as its first parameter (CONSTITUTION §3.4)
4. Every SQL query has `WHERE tenant_id = :tenantId`
5. Cross-tenant access attempts return HTTP 403 (not 404 — to avoid existence leakage)

**Tenant isolation tests required:**
- `SalesTeamTenantIsolationTest` — user in tenant A cannot see teams in tenant B
- `QueueTenantIsolationTest` — same for queues
- `TerritoryTenantIsolationTest` — same for territories
- `AssignmentTenantIsolationTest` — same for assignments
- `TransferRequestTenantIsolationTest` — same for transfer requests
- `OwnershipHistoryTenantIsolationTest` — same for history

---

## 6. Privilege escalation prevention (AC security test)

| Escalation attempt | Defense |
|---|---|
| User grants themselves ownership of a record they don't own | `CRM.ASSIGNMENT.WRITE` only allows reassigning records the user already owns or has explicit grant on |
| User approves their own transfer request | Separation of Duties check: `requester_user_id != approver_user_id` enforced in `TransferRequestService.approve()` |
| User with `CRM.TEAM.READ` tries to add a member | Requires `CRM.TEAM.ADMIN` — AOP denies |
| User with `CRM.QUEUE.CLAIM` tries to create a new queue | Requires `CRM.QUEUE.ADMIN` — AOP denies |
| User with `CRM.ASSIGNMENT_RULE.READ` tries to activate a rule | Requires `CRM.ASSIGNMENT_RULE.ADMIN` — AOP denies |
| User sets `tenant_id` in request body to another tenant | `tenant_id` is never read from request body (CONSTITUTION §3.4) — always from SecurityContext |
| User includes `platform_admin=true` in PATCH /users/me | `platform_admin` flag is never settable via API — only via bootstrap |

---

## 7. Audit requirements for every capability invocation

Every successful or failed invocation of a write capability produces:

1. A row in `platform_audit_logs` with:
   - `tenant_id`, `actor_user_id`, `action` (capability code), `result` (SUCCESS/FAILURE)
   - `correlation_id` (UUID, propagated to all related writes)
   - `ip_address`, `user_agent` (from request)
   - `details` JSONB (record ids, before/after states — no secrets)
2. A row in `crm_ownership_history` (for ownership-changing operations only)
3. A domain event published to the existing CRM event sink (for notifications + analytics)

**Failed authorizations (403) are also audited** — to enable detection of probing/escalation attempts.
