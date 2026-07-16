# CRM-005 — Enterprise Account and Customer Master

## Status

```text
EXEC-PROMPT-CRM-005: IN PROGRESS
CRM-G3: OPEN
MERGE: PROHIBITED UNTIL EXACT-SHA VERIFICATION
```

## Purpose

CRM Account is the tenant-scoped Customer/Organization Master. It owns customer identity, classification, hierarchy, risk metadata, external identity mappings, lifecycle history and ownership history.

It does **not** own ERP orders, accounting balances, ecommerce orders or customer-service cases. Those systems remain sources of truth and publish read-only projections into CRM.

## Aggregate boundaries

### Core Account

Existing `crm_accounts` remains the stable aggregate root and API identity. It owns:

- display identity;
- account type;
- lifecycle status;
- direct parent reference;
- owner;
- locale, currency and source;
- optimistic version and audit columns.

### Enterprise profile

`crm_account_profiles` is a one-to-one extension of Account and owns:

- legal and trade names;
- commercial and tax registration identifiers;
- industry and organization size;
- website;
- customer tier;
- classification and segment references;
- risk level and flags;
- merge-candidate state;
- independent optimistic version.

### Taxonomies

`crm_account_taxonomies` provides tenant-configurable classifications and customer segments. Taxonomy records are tenant-scoped and may form a same-type parent hierarchy.

### Relationships

`crm_account_relationships` stores explicit directional relationships:

- `PARENT`;
- `SUBSIDIARY`;
- `BRANCH`;
- `PARTNER`.

Application rules reject self-links and cycles for hierarchical relationship types. Ending a relationship is a state transition; records are not hard-deleted.

### External identifiers

`crm_account_external_identifiers` maps Account to provider/system-scoped identifiers. Uniqueness is enforced on:

```text
tenant + provider + system scope + external ID
```

Identifiers are deactivated rather than hard-deleted.

### Histories

- `crm_account_status_history` records every lifecycle transition.
- `crm_account_ownership_history` records every owner transition.

Both are append-only business histories and complement centralized audit logs and CRM timeline events.

### Projection contracts

`crm_account_projection_snapshots` accepts read-only snapshots for:

- `FINANCIAL_SUMMARY` from Accounting;
- `ORDERS` from ERP/Ecommerce;
- `SERVICE` from Customer Service.

CRM never joins or queries those source-domain tables directly. When a provider has not published a snapshot, the API returns `NOT_CONNECTED` with no fabricated payload.

## API boundaries

```text
GET  /api/v2/crm/accounts/{accountId}/master
PUT  /api/v2/crm/accounts/{accountId}/master/profile
GET  /api/v2/crm/accounts/{accountId}/master/risk
PUT  /api/v2/crm/accounts/{accountId}/master/risk
GET  /api/v2/crm/accounts/{accountId}/relationships
POST /api/v2/crm/accounts/{accountId}/relationships
PATCH /api/v2/crm/accounts/{accountId}/relationships/{relationshipId}/end
GET  /api/v2/crm/accounts/{accountId}/external-identifiers
POST /api/v2/crm/accounts/{accountId}/external-identifiers
DELETE /api/v2/crm/accounts/{accountId}/external-identifiers/{identifierId}
GET  /api/v2/crm/accounts/{accountId}/history
GET  /api/v2/crm/accounts/{accountId}/projections
PATCH /api/v2/crm/accounts/{accountId}/reactivate
GET  /api/v2/crm/account-taxonomies?type=CLASSIFICATION|SEGMENT
POST /api/v2/crm/account-taxonomies
```

Profile, risk and relationship state changes use optimistic concurrency and ETags. All repository operations include `tenant_id` in reads and writes.

## RBAC

```text
CRM.ACCOUNT.MASTER.READ
CRM.ACCOUNT.MASTER.WRITE
CRM.ACCOUNT.RELATIONSHIP.READ
CRM.ACCOUNT.RELATIONSHIP.WRITE
CRM.ACCOUNT.IDENTIFIER.READ
CRM.ACCOUNT.IDENTIFIER.WRITE
CRM.ACCOUNT.HISTORY.READ
CRM.ACCOUNT.RISK.READ
CRM.ACCOUNT.RISK.WRITE
```

## UI

Account list and create expose enterprise identity fields. Account Detail is a real Customer 360 workspace with tabs:

1. Overview
2. Contacts
3. Opportunities
4. Activities
5. Interactions
6. Financial Summary
7. Orders
8. Service
9. Audit & History

Projection tabs explicitly show `NOT_CONNECTED` when no provider snapshot exists.

## Non-negotiable invariants

1. No cross-tenant access.
2. No hard delete for Accounts, relationships, identifiers or histories.
3. No direct CRM dependency on ERP, Accounting, Ecommerce or Customer Service implementations.
4. No synthetic financial, order or service figures.
5. Every mutation is auditable and tenant-scoped.
6. Hierarchical relationships cannot form cycles.
7. External identity uniqueness is provider/system scoped.
