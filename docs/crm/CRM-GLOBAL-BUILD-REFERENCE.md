# SNAD Global CRM Build Reference

## 1. Document control

```text
Document: CRM-GLOBAL-BUILD-REFERENCE
Version: 1.0-draft
Status: BUILD-READINESS BASELINE
Product: SNAD CRM Platform
Target: Global multi-tenant organizations
Production authorization: NO
```

## 2. Product intent

SNAD CRM is the customer operating layer of the SNAD Business Operating System. It must provide a unified, tenant-isolated customer record and support the complete commercial lifecycle from acquisition through service, retention, expansion, and customer intelligence.

The CRM must be usable by small and medium businesses while retaining the governance, extensibility, data residency, localization, and scale controls expected by larger organizations.

## 3. Product outcomes

The platform shall enable organizations to:

- maintain a trusted Customer 360 record;
- manage accounts, people, leads, opportunities, activities, and interactions;
- automate assignments, approvals, reminders, and lifecycle transitions through the central Workflow Engine;
- use AI through the central AI Gateway for summarization, recommendations, classification, next-best action, and forecasting;
- integrate with ERP, Accounting, Ecommerce, Support, POS, HRM, Data, and external channels;
- operate in Arabic and English from the first release and add locales without changing domain logic;
- enforce privacy, consent, retention, audit, and regional data controls;
- expose stable APIs and events for partner and enterprise integration.

## 4. Scope model

### 4.1 CRM foundation

- Customer and account records.
- Contacts and relationships.
- Leads and qualification.
- Opportunities and pipelines.
- Activities, tasks, notes, and timeline.
- Ownership, territories, teams, and queues.
- Custom fields, classifications, tags, and configurable layouts.
- Import, export, duplicate detection, merge, and data-quality controls.

### 4.2 Sales execution

- Multiple pipelines and stages.
- Products, price references, and quotation integration points.
- Forecast categories and weighted values.
- Competitors and win/loss reasons.
- Assignment and routing.
- Approval integration.
- Sales targets and performance metrics.

### 4.3 Engagement and service integration

- Omnichannel interaction history.
- Email, messaging, call, meeting, web, commerce, and support interaction adapters.
- Customer journeys and campaigns through governed integrations.
- Case and service context as linked domains, not duplicated systems of record.

### 4.4 Intelligence

- Customer summaries.
- Lead and opportunity scoring.
- Relationship health.
- Churn and expansion signals.
- Forecast assistance.
- Duplicate and anomaly detection.
- Natural-language search and analysis through approved AI controls.

### 4.5 Global operation

- Multiple languages, currencies, time zones, calendars, address formats, phone formats, and naming conventions.
- Regional privacy, consent, retention, deletion, and export policies.
- Data residency and regional deployment compatibility.
- Configurable fiscal and reporting contexts.
- Accessible and responsive user experience.

## 5. Explicit exclusions from CRM source-of-truth ownership

The CRM must reference or integrate with these domains rather than duplicate their authoritative records:

- invoices, journals, payments, tax, and financial posting — Accounting;
- inventory, purchasing, fulfillment, projects, assets, and contracts — ERP;
- employee master data — HRM;
- orders, carts, checkout, and storefront catalog ownership — Ecommerce;
- point-of-sale transaction ownership — POS;
- authentication, tenants, subscriptions, licensing, and platform authorization — SaaS Core;
- workflow execution history — Workflow Engine;
- model credentials, prompt governance, and AI policy — AI Core;
- enterprise analytical models and historical warehouse ownership — Data Platform.

## 6. Architectural principles

### 6.1 Modular service-oriented delivery

The first CRM implementation should be a well-separated CRM module or bounded application within the existing Spring Boot platform unless measurable scale, team autonomy, regulatory isolation, or reliability requirements justify extraction.

No independent service may be created without:

- a defined bounded context;
- an owner;
- an API or event contract;
- an independent data-ownership justification;
- an SLO and runbook;
- deployment and operational cost acceptance.

### 6.2 Multi-tenancy

Every record, query, index, cache key, event, export, search document, analytics row, and audit entry must be tenant-scoped.

Tenant identity must come from an authenticated and validated context, not from an untrusted request body or query parameter.

### 6.3 Workflow-first

CRM lifecycle transitions requiring approvals, timers, escalations, assignment rules, or cross-domain orchestration must call the central Workflow Engine. Business code must not embed a second workflow engine.

### 6.4 AI-first with policy control

CRM AI capabilities must use the central AI Gateway. AI output is advisory by default. High-impact updates require policy checks and human confirmation unless an approved automation policy explicitly allows execution.

### 6.5 API and event first

External behavior must be represented through versioned APIs and domain events before UI coupling. Events must be idempotent, tenant-scoped, traceable, and backward compatible.

## 7. Global product requirements

### 7.1 Localization

- Arabic and English are required for MVP.
- Full RTL support is required for Arabic.
- User-facing text must not be hard-coded in domain logic.
- Dates, numbers, currency, names, addresses, and phone numbers must be formatted by locale.
- Search must support Arabic and Latin text normalization.
- Time values must be stored in UTC and rendered in user or organization time zone.

### 7.2 Privacy and consent

- Purpose and lawful-basis metadata must be supportable.
- Communication preferences and consent history must be auditable.
- Subject access export, correction, restriction, and deletion workflows must be supported by policy.
- Retention must be configurable by tenant and jurisdiction.
- Sensitive attributes require classification and field-level control.

### 7.3 Currency and commercial values

- Monetary values must include currency code.
- Base and transaction currency concepts must be supported.
- CRM forecasting must not perform official accounting conversion or posting.
- Exchange-rate sources must be versioned and traceable when used for analytics.

### 7.4 Accessibility

- Keyboard navigation.
- Semantic landmarks and labels.
- WCAG-oriented contrast and focus behavior.
- Screen-reader support.
- Responsive desktop, tablet, and mobile layouts.

## 8. Security baseline

- Deny by default.
- Tenant isolation and record authorization tests for all endpoints.
- Capability-based access for read, create, update, delete, export, merge, assign, and administer actions.
- Field-level protection for sensitive data.
- Rate limits for import, export, search, and bulk operations.
- Malware and content validation for attachments through approved platform controls.
- No secrets, tokens, or sensitive payloads in logs.
- Immutable audit entries for high-risk operations.
- Export and bulk actions require enhanced auditing and optional approval.

## 9. Reliability and performance objectives

Initial non-production targets to validate before production planning:

- p95 read API latency under 400 ms for normal indexed queries.
- p95 write API latency under 700 ms excluding external workflows.
- tenant-scoped list endpoints use cursor or stable pagination.
- import operations are asynchronous and resumable.
- event publication uses an outbox or equivalent reliable pattern.
- duplicate requests are safe through idempotency controls where applicable.
- no unbounded queries or exports.
- failure of AI, analytics, or notification integrations must not corrupt CRM transactions.

Production SLOs require a separate approved operating model.

## 10. Core personas

- Sales representative.
- Sales manager.
- Account manager.
- Marketing operator.
- Customer service agent.
- Data steward.
- CRM administrator.
- Executive and analyst.
- Integration developer.
- Compliance and privacy officer.

## 11. MVP definition

The first CRM MVP is limited to:

- accounts;
- contacts;
- leads;
- opportunities and pipelines;
- activities and timeline;
- ownership and assignment;
- configurable statuses and basic custom fields;
- duplicate detection and controlled merge;
- CSV import with validation and error reporting;
- APIs and domain events;
- Arabic/English UI foundation;
- audit, tenant isolation, authorization, observability, and automated tests;
- Workflow and AI integration points without implementing an independent workflow or model runtime.

## 12. Definition of Ready for CRM implementation

A CRM story may enter development only when:

- business value and actor are defined;
- source-of-truth ownership is clear;
- tenant behavior is explicit;
- API and event impact is reviewed;
- authorization and audit requirements are defined;
- localization and privacy impacts are defined;
- acceptance criteria are testable;
- dependencies and migration needs are known;
- estimate, owner, and target sprint are assigned;
- no unresolved architecture decision blocks implementation.

## 13. Definition of Done

A CRM story is complete only when:

- code review and automated tests pass;
- tenant isolation and authorization are verified;
- migration and rollback behavior are validated;
- API and event contracts are updated;
- audit, logs, metrics, and traces are present;
- Arabic and English behavior is validated where applicable;
- privacy and retention behavior is tested;
- performance is within the accepted budget;
- documentation and runbooks are updated;
- no Critical or High unresolved security finding remains;
- Product, QA, and Security acceptance evidence is linked.

## 14. Build decision

This document authorizes architecture, backlog, contract, test, and readiness preparation only. CRM source-code implementation requires the explicit GO conditions in `CRM-READINESS-GATE.md`.
