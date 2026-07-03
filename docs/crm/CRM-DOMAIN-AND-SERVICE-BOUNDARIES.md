# CRM Domain and Service Boundaries

## 1. Purpose

Define the bounded contexts, module boundaries, ownership, integration points, and extraction criteria for the SNAD CRM platform.

## 2. Delivery model

The initial delivery model is a modular CRM bounded context within the existing Spring Boot platform. Internal modules must communicate through explicit application services and events. Direct cross-module repository access is prohibited.

Potential future service extraction is allowed only after evidence of independent scaling, reliability, regulatory isolation, or team ownership needs.

## 3. Bounded contexts

### 3.1 Party and Customer Profile

Owns:

- account/customer organization profile;
- person/contact profile;
- account-contact relationships;
- addresses, phones, emails, communication preferences;
- customer classifications, tags, and lifecycle status;
- duplicate candidates and merge decisions;
- customer external identifiers.

Does not own:

- SNAD tenant organizations;
- user identities;
- employee identities;
- invoices or orders.

Recommended module:

```text
com.sanad.platform.crm.party
```

### 3.2 Lead Management

Owns:

- lead record;
- lead source and campaign reference;
- qualification state;
- assignment and queue reference;
- conversion decision;
- lead-to-account/contact/opportunity linkage.

Recommended module:

```text
com.sanad.platform.crm.lead
```

### 3.3 Opportunity and Pipeline

Owns:

- opportunity;
- pipeline and stage configuration;
- expected value and currency;
- probability and forecast category;
- close date;
- opportunity participants;
- competitor references;
- win/loss reason;
- stage history.

Recommended module:

```text
com.sanad.platform.crm.opportunity
```

### 3.4 Activity and Interaction Timeline

Owns:

- CRM tasks;
- calls, meetings, emails, notes, and interaction references;
- activity participants;
- due dates and completion state;
- timeline projection for CRM entities.

It stores references and normalized interaction metadata. Channel-specific authoritative payloads remain in the owning communication or support system.

Recommended module:

```text
com.sanad.platform.crm.activity
```

### 3.5 Sales Configuration

Owns tenant-configurable CRM reference data:

- pipelines;
- stages;
- lead sources;
- customer statuses;
- loss reasons;
- territories;
- queues;
- CRM custom fields;
- configurable record layouts.

Recommended module:

```text
com.sanad.platform.crm.configuration
```

### 3.6 CRM Import and Data Quality

Owns:

- import jobs;
- validation results;
- row-level failures;
- mapping configuration;
- duplicate-detection requests;
- merge proposals;
- data-quality rules and scores.

Recommended module:

```text
com.sanad.platform.crm.dataquality
```

### 3.7 CRM Search and Read Models

Owns tenant-scoped projections optimized for:

- global CRM search;
- Customer 360 read views;
- timeline views;
- pipeline board views;
- CRM dashboards.

This module must not become an alternate system of record.

Recommended module:

```text
com.sanad.platform.crm.query
```

## 4. Shared platform dependencies

| Dependency | CRM usage | Constraint |
|---|---|---|
| Tenant Context | isolate every operation | no request-supplied tenant override |
| Identity and Users | actor and owner references | CRM never owns authentication |
| RBAC and Capabilities | authorize CRM actions | deny by default |
| Audit | record sensitive operations | no duplicated audit subsystem |
| Workflow Engine | assignments, approvals, timers, escalation | CRM must not embed workflow runtime |
| AI Gateway | scoring, summary, recommendations | advisory and policy controlled |
| Notification Platform | reminders and approved messages | no provider credentials in CRM |
| Event Platform | reliable domain integration | use versioned tenant-scoped events |
| Data Platform | analytical history and KPIs | operational CRM remains source of truth |
| Object Storage | attachments and import files | malware and access controls required |

## 5. Cross-domain integrations

### ERP

CRM publishes customer and commercial-intent events. ERP may consume accepted customer references, quotations, contracts, projects, or order handoff events. CRM must not update ERP tables directly.

### Accounting

CRM may display summarized receivables or payment-risk indicators through read contracts. Accounting remains authoritative for all financial records.

### Ecommerce and POS

Orders and transactions produce customer-interaction and purchase-summary events. CRM stores references and customer intelligence, not order source data.

### Support and Customer Experience

Cases, tickets, and satisfaction signals appear in Customer 360 through APIs or projections. Support remains authoritative for case execution.

### HRM

CRM owners and team references use platform user/employee mappings. CRM does not copy employee master data beyond immutable display snapshots needed for historical audit.

## 6. Core aggregate boundaries

### Account aggregate

Primary invariants:

- tenant-scoped unique normalized identifiers where configured;
- valid lifecycle transition;
- account parent cannot create a cycle;
- merge retains audit and external-identifier traceability;
- sensitive fields require appropriate capability.

### Contact aggregate

Primary invariants:

- tenant-scoped identity;
- multiple communication methods with verified/preferred states;
- relationship to zero or more accounts;
- consent and preference history retained;
- merge and deletion follow privacy policy.

### Lead aggregate

Primary invariants:

- explicit owner or queue;
- qualification transitions are valid;
- conversion is idempotent;
- conversion creates or links canonical account/contact/opportunity records;
- converted leads cannot be converted twice.

### Opportunity aggregate

Primary invariants:

- belongs to one pipeline;
- stage belongs to the same pipeline;
- monetary value includes ISO currency;
- closed-won and closed-lost require outcome data;
- stage changes create immutable history;
- financial posting is prohibited.

### Activity aggregate

Primary invariants:

- at least one related CRM record or participant;
- due/completion times are coherent;
- actor and owner are tenant members;
- sensitive content follows classification and retention rules.

## 7. Package dependency rules

Allowed direction:

```text
api -> application -> domain
infrastructure -> application/domain ports
query projections -> published domain events
```

Prohibited:

```text
controller -> repository
module A repository -> module B repository
CRM -> internal database of ERP/Accounting/HRM
UI -> database
AI adapter -> direct domain mutation without application policy
```

## 8. Transaction and event model

- Domain writes are atomic within the owning aggregate transaction.
- Cross-domain changes use events or workflows.
- Reliable event publication must use an outbox or equivalent approved mechanism.
- Consumers must be idempotent.
- Events must contain tenant, event, correlation, causation, version, producer, timestamp, and entity identifiers.
- Event payloads must minimize personal data.

## 9. Initial ownership model

| Area | Product owner | Engineering owner | Security/Data review |
|---|---|---|---|
| Party and Customer | CRM Product | CRM/Experience Squad | required |
| Leads | Sales Product | CRM/Experience Squad | required |
| Opportunities | Sales Product | CRM/Experience Squad | required |
| Activities | CRM Product | CRM/Experience Squad | required |
| Configuration | CRM Admin Product | Platform + CRM | required |
| Import/Data Quality | Data Steward Product | CRM + Data | required |
| Search/Read Models | CRM Product | CRM + Data | required |

Named individuals are assigned through the operating model and repository ownership controls, not hard-coded in this document.

## 10. Extraction criteria

A CRM module may become a separately deployed service only when at least two of these conditions are met and an ADR is approved:

- materially different scaling profile;
- independent availability objective;
- separate data residency requirement;
- independent release cadence owned by a stable team;
- resource isolation required for bulk/search workloads;
- regulatory or security boundary;
- measured contention inside the modular deployment.

## 11. Forbidden early fragmentation

The following are not independent microservices in the initial release:

- Account Service;
- Contact Service;
- Lead Service;
- Opportunity Service;
- Activity Service.

They are domain modules with clear boundaries inside the CRM bounded context until extraction criteria are met.

## 12. Acceptance conditions

This boundary model is accepted when:

- architecture and security owners review it;
- source-of-truth ownership conflicts are resolved;
- package dependency checks are planned;
- initial API and event contracts are linked;
- CRM backlog stories reference the owning context;
- no implementation begins outside the declared boundary.
