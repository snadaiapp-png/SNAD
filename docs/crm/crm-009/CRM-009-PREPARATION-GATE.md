# CRM-009 — Workflow & AI Integration Preparation Gate

> **Control issue:** #692  
> **Dependency:** CRM-008 control issue #597 and implementation PR #691  
> **Branch:** `prep/crm-009-workflow-ai-20260722`  
> **Status:** `PREPARED / BLOCKED_BY_CRM_008`  
> **Change class:** Documentation and planning only

## 1. Decision

```text
CRM_009_PREPARATION: AUTHORIZED
CRM_009_EXECUTABLE_IMPLEMENTATION: NOT_AUTHORIZED
CRM_009_PREPARATION_PR: DRAFT_ONLY
CRM_009_PREPARATION_PR_MERGE: PROHIBITED
CRM_009_ISSUE_CLOSURE: PROHIBITED
BLOCKED_BY: CRM-008_FORMAL_CLOSURE
AUTO_MERGE: PROHIBITED
PRODUCTION_CHANGE: NONE
```

This package prepares CRM-009 for review. It does not authorize SQL, backend, frontend, workflow, AI, infrastructure, configuration, deployment, migration, production, or release changes.

## 2. Authoritative scope

CRM-009 delivers governed integration points from CRM to the central Workflow Engine and AI Gateway.

### 2.1 Workflow integration

1. Assignment workflow contract.
2. Opportunity approval contract.
3. Reminder and escalation contract.
4. Workflow reference and status projections.

### 2.2 AI integration

1. Customer-summary request/response contract.
2. Next-best-action contract.
3. Scoring and explanation contract.
4. Policy, redaction, fallback, and human-confirmation controls.

## 3. Non-negotiable boundaries

- CRM does not implement a second workflow runtime.
- CRM does not call model providers directly.
- Workflow and AI integrations use platform-owned ports/contracts.
- AI failure cannot corrupt an accepted CRM transaction.
- High-impact mutations remain policy-controlled and require explicit authorization and, where applicable, human confirmation.
- Every request is tenant-scoped, capability-checked, audited, traceable, bounded by timeout, and safe to retry.
- Sensitive fields are minimized and redacted before leaving the CRM boundary.
- Generated recommendations are advisory unless a separately authorized command is executed through normal CRM validation.
- No hidden fallback may silently weaken authorization, tenant isolation, consent, or audit requirements.

## 4. CRM-008 closure gate

Executable CRM-009 work remains blocked until all items below are evidenced:

- [ ] Issue #597 is closed as completed.
- [ ] PR #691 is merged using its exact expected head SHA.
- [ ] CRM-008 formal closure evidence is committed and linked to the merged SHA.
- [ ] All CRM-008 acceptance criteria, required checks, localization, accessibility, performance, and production proof pass.
- [ ] CRM-008 production evidence reports zero unexplained CRM HTTP 5xx.
- [ ] No open CRM-008 defect changes the Workflow or AI contract assumptions.
- [ ] This preparation branch is reconciled against the exact post-CRM-008 `main` SHA.
- [ ] Contract versions and platform dependencies are revalidated.
- [ ] The Project Owner records explicit CRM-009 implementation authorization in issue #692.

Failure of any item keeps the gate closed.

## 5. Allowed preparation work

- Scope decomposition and estimates.
- Contract inventories and boundary decisions.
- Threat, privacy, failure-mode, and tenant-isolation analysis.
- Acceptance criteria and evidence planning.
- Non-executable OpenAPI/event schema drafting for review.
- Test design and operator runbook planning.

## 6. Prohibited work before activation

- Flyway migration creation or execution.
- Java, TypeScript, SQL, workflow definition, prompt, or model-routing implementation.
- New runtime flags or production environment variables.
- Provider credentials, model endpoints, or workflow-engine deployment changes.
- Production smoke execution for CRM-009.
- Marking the PR ready, enabling auto-merge, merging, closing issue #692, or claiming CRM-009 started/completed.

## 7. Activation record template

The following record must be completed only after CRM-008 closure:

```text
CRM_008_CLOSURE_ISSUE: #597 / CLOSED_COMPLETED
CRM_008_MERGED_PR: #691
CRM_008_MERGE_SHA: <exact-sha>
CRM_008_CLOSURE_EVIDENCE: <path-or-artifact>
CRM_008_UNEXPLAINED_HTTP_5XX: 0
CRM_009_RECONCILED_BASE_SHA: <exact-main-sha>
WORKFLOW_CONTRACT_VERSION: <version>
AI_GATEWAY_CONTRACT_VERSION: <version>
CRM_009_IMPLEMENTATION_AUTHORIZATION: <owner-record>
DECISION: GO | HOLD | NO_GO
```

## 8. Definition of preparation complete

Preparation is complete only when:

- issue #692 remains open;
- the preparation PR remains Draft and unmerged;
- the backlog, contract plan, and test/evidence runbook exist;
- all executable change counters remain zero;
- CRM-008 remains the active delivery stage.
