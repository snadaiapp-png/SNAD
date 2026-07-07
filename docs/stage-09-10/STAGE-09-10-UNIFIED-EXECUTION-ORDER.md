# SNAD Stage 09 + Stage 10 — Unified Execution Order

**Decision ID:** `SNAD-ST09-10-UNIFIED-001`  
**Date:** 2026-07-07  
**Repository:** `snadaiapp-png/SNAD`  
**Base SHA:** `ea332505c8976757e58bc4476e533c1d78e78d06`  
**Authority:** Project Manager  
**Execution Mode:** Controlled non-production construction

## 1. Unified Program

```text
STAGE 09: CRM PLATFORM
STAGE 10: AI CRM PLATFORM
EXECUTION: UNIFIED AND IMMEDIATE
PRODUCTION DEPLOYMENT: SEPARATELY GATED
DIRECT MODEL-PROVIDER CALLS FROM CRM: PROHIBITED
CENTRAL AI GATEWAY: MANDATORY
CENTRAL WORKFLOW ENGINE: MANDATORY
```

Stage 09 delivers the transactional and operational CRM system of record. Stage 10 adds governed AI capabilities over Stage 09 without replacing deterministic CRM rules, authorization, audit, workflow controls, or human accountability.

## 2. Stage 09 Scope — CRM Platform

- Accounts and Customer 360.
- Contacts and relationships.
- Leads, qualification, conversion, queues, and assignment.
- Pipelines, stages, opportunities, forecasts, and outcomes.
- Activities, notes, meetings, calls, reminders, and timeline.
- Data import, duplicate detection, merge, and data quality.
- Custom fields, configuration, localization, accessibility, and responsive UX.
- Workflow integration, audit, observability, tenant isolation, authorization, recovery, and tests.

## 3. Stage 10 Scope — AI CRM Platform

- AI customer and account summaries.
- Explainable lead scoring and qualification assistance.
- Opportunity risk, probability, and forecast assistance.
- Next-best-action recommendations.
- Sales and service copilot contracts.
- Activity and meeting summarization.
- Duplicate and data-quality assistance.
- AI policy, prompt redaction, grounding, tenant isolation, evaluation, fallback, audit, cost controls, and human confirmation.

## 4. Non-Negotiable Controls

1. Every request is tenant-scoped and authorized.
2. CRM never calls OpenAI or another model provider directly; it calls a central AI Gateway contract.
3. Provider credentials remain outside source control.
4. AI output is advisory unless a separately authorized workflow permits mutation.
5. High-impact actions require policy evaluation and human confirmation.
6. CRM transactions must succeed safely when AI is unavailable.
7. Prompts, retrieved context, model responses, costs, evaluations, and user decisions are auditable without storing secrets.
8. No cross-tenant retrieval, prompt context, cache, memory, metric, or model trace.
9. Exact-SHA CI, security, migration, authorization, tenant-isolation, and rollback evidence are mandatory for each implementation PR.
10. Production release remains blocked until production-specific gates are separately approved.

## 5. Unified Epics

### ST09-EPIC-01 — CRM Core and Architecture
Module boundaries, architecture tests, capabilities, tenant context, API and event contracts.

### ST09-EPIC-02 — Customer and Account Foundation
Accounts, contacts, relationships, Customer 360, search, lifecycle, audit, and localization.

### ST09-EPIC-03 — Lead Management
Lead lifecycle, assignment, qualification, conversion, replay protection, events, and tests.

### ST09-EPIC-04 — Pipeline and Opportunity Management
Pipelines, stage history, opportunity values, probabilities, forecasts, outcomes, and events.

### ST09-EPIC-05 — Activities and Timeline
Activities, reminders, notes, communications references, timeline projections, and visibility controls.

### ST09-EPIC-06 — Data Quality and Extensibility
Imports, duplicate detection, merge, custom fields, queues, territories, and configuration governance.

### ST09-EPIC-07 — CRM Experience
Arabic RTL, English LTR, responsive web, accessibility, pipeline workspace, dashboards, and mobile-ready contracts.

### ST10-EPIC-01 — Central AI Gateway Contract
Provider-neutral request/response contracts, policy metadata, redaction, fallback, tracing, cost and evaluation metadata.

### ST10-EPIC-02 — Customer Intelligence
Customer summaries, relationship insights, sentiment and interaction summaries, grounded evidence references.

### ST10-EPIC-03 — Lead Intelligence
Explainable scoring, qualification assistance, priority ranking, reason codes, confidence, fallback, and human override.

### ST10-EPIC-04 — Opportunity Intelligence
Risk detection, probability assistance, forecast explanation, next-best action, and approval controls.

### ST10-EPIC-05 — AI CRM Copilot
Read-only copilot first, permission-filtered retrieval, citations, action proposals, workflow handoff, and confirmation.

### ST10-EPIC-06 — AI Safety, Evaluation, and Operations
Prompt-injection defenses, PII redaction, tenant isolation, offline evaluations, quality thresholds, drift, cost budgets, dashboards, alerts, and incident response.

### ST09-10-EPIC-01 — Integrated Quality and Release
API, event, migration, authorization, tenant-isolation, Arabic/English UX, performance, AI evaluation, rollback, and evidence packages.

## 6. Execution Waves

```text
WAVE 0: Baseline reconciliation and executable backlog
WAVE 1: CRM core, accounts, contacts, and Customer 360
WAVE 2: Leads, conversion, pipelines, and opportunities
WAVE 3: Activities, timeline, import, data quality, and extensibility
WAVE 4: Global CRM UX and workflow integration
WAVE 5: AI Gateway contract and deterministic fallback
WAVE 6: Customer, lead, and opportunity intelligence
WAVE 7: Copilot, evaluation, security, observability, and release evidence
```

## 7. Immediate Execution Slice

The first PR under this order must:

- Reconcile the existing CRM implementation with the accepted backlog.
- Establish the Stage 09+10 traceability and risk registers.
- Define provider-neutral AI CRM contracts.
- Add a deterministic non-AI fallback for lead-scoring requests.
- Add unit tests proving deterministic behavior and no provider dependency.
- Prepare Vercel preview validation for documentation and web changes.
- Keep production deployment disabled.

## 8. Gate Model

```text
GATE 9A: CRM baseline and architecture
GATE 9B: Customer, lead, pipeline, opportunity, and activity runtime
GATE 9C: CRM experience, data quality, security, and operations
GATE 10A: AI Gateway and policy contract
GATE 10B: AI CRM intelligence and copilot
GATE 10C: AI safety, evaluation, cost, observability, and evidence
GATE 9-10F: Integrated final acceptance
```

## 9. Status

```text
STAGE 09: STARTED
STAGE 10: STARTED
UNIFIED PROGRAM: ACTIVE
PRODUCTION RELEASE: NOT AUTHORIZED
```
