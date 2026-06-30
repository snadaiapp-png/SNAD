# CRM Data, API, and Event Contract

## Purpose

Define the initial data, API, event, audit, privacy, localization, and compatibility contracts for SNAD CRM.

## Common CRM record

Every CRM record is tenant-owned and includes a UUID, tenant reference, version, creation and update timestamps, actor references, status, and optional archival timestamp.

Rules:

- tenant identity comes from trusted authentication context;
- updates use optimistic concurrency;
- every high-risk change produces audit evidence;
- display identifiers never replace internal UUID identity;
- deletion and retention follow tenant and jurisdiction policy.

## Initial entities

### Account

Display name, normalized name, type, lifecycle status, owner, parent account, currency, locale, time zone, source, external identifiers, tags, custom fields, and privacy classification.

### Contact

Names, preferred display name, locale, time zone, owner, status, communication methods, account relationships, consent summary, external identifiers, tags, custom fields, and privacy classification.

### Lead

Person or organization attributes, source, qualification state, owner or queue, optional score, consent summary, and conversion references.

### Opportunity

Account, owner, pipeline, stage, value, ISO currency, probability, forecast category, expected close date, actual close date, outcome reason, source, competitors, and custom fields.

### Activity

Type, subject, classified description, owner, assignee or queue, related records, participants, schedule, completion state, channel reference, and priority.

## Custom fields

Initial supported types:

- text and long text;
- integer and decimal;
- boolean;
- date and datetime;
- currency;
- single and multiple selection;
- user, account, and contact references;
- URL, phone, and email.

Each definition has a stable key, localized label, validation, sensitivity level, search/reporting eligibility, required-state rules, and deprecation state.

## API conventions

Base path:

```text
/api/v1/crm
```

Initial resources:

```text
/accounts
/contacts
/leads
/opportunities
/activities
/pipelines
/queues
/custom-fields
/import-jobs
/duplicate-candidates
```

Required behavior:

- JSON contracts and ISO-8601 UTC timestamps;
- ISO 4217 currency codes, BCP 47 locales, and IANA time zones;
- stable cursor pagination and deterministic sorting;
- allowlisted filters;
- idempotency for conversion, merge, import, and selected create operations;
- optimistic concurrency for sensitive updates;
- stable error codes with request identifiers and field errors;
- no request-controlled tenant override.

## Search

Search is tenant-scoped, authorization-aware, Arabic/Latin normalized, replayable from domain events, and free from cross-tenant cache keys. Privacy deletion must propagate to search projections.

## Event envelope

Every CRM event includes:

- event ID and type;
- version and occurrence time;
- tenant, correlation, and causation identifiers;
- actor, producer, and subject identifiers;
- data classification;
- minimal payload.

Consumers are idempotent. Breaking changes use a new version. Publication uses an outbox or approved reliable equivalent.

## Initial events

Party:

- `crm.account.created`
- `crm.account.updated`
- `crm.account.status-changed`
- `crm.account.merged`
- `crm.contact.created`
- `crm.contact.updated`
- `crm.contact.consent-changed`
- `crm.contact.merged`

Lead:

- `crm.lead.created`
- `crm.lead.assigned`
- `crm.lead.qualified`
- `crm.lead.disqualified`
- `crm.lead.converted`

Opportunity:

- `crm.opportunity.created`
- `crm.opportunity.stage-changed`
- `crm.opportunity.value-changed`
- `crm.opportunity.closed-won`
- `crm.opportunity.closed-lost`

Activity and quality:

- `crm.activity.created`
- `crm.activity.assigned`
- `crm.activity.completed`
- `crm.activity.overdue`
- `crm.import.started`
- `crm.import.completed`
- `crm.import.failed`
- `crm.duplicate.detected`
- `crm.merge.completed`

## Workflow integration

CRM requests assignment, approval, reminder, escalation, privacy-review, and bulk-export workflows through the central Workflow Engine. CRM stores only the workflow reference and required projection state.

## AI integration

CRM uses the central AI Gateway for customer summaries, next-action recommendations, classification, scoring, duplicate detection, and forecast explanations. Calls include tenant and purpose context, approved policy references, data minimization, trace identifiers, and a non-AI fallback. High-impact changes require approved policy and human confirmation.

## Audit

Audit is required for customer changes, owner changes, merges, imports, exports, consent changes, privacy actions, bulk operations, configuration changes, opportunity outcomes, and accepted AI recommendations.

Audit records include tenant, actor, action, target, request/correlation ID, timestamp, result, and safe changed-field metadata.

## Privacy and retention

The model supports data category, sensitivity, collection source, purpose, lawful basis where configured, consent reference, retention policy, deletion restriction, legal hold, and residency region.

## Import

Imports are asynchronous, validated, mapped explicitly, optionally dry-run, processed in bounded batches, restart-safe, fully audited, and accompanied by authorized error reports.

## Compatibility

- prefer additive changes;
- deprecate before removal;
- use tolerant enum consumers;
- version breaking API and event changes;
- document migrations and recovery;
- never rely on undocumented response fields.

## Acceptance

Implementation begins only after architecture, security, privacy, API, event ownership, migration, and test reviews are recorded.
