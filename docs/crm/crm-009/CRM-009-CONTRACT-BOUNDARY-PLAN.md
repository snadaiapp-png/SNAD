# CRM-009 — Workflow & AI Contract Boundary Plan

> **Control issue:** #692  
> **State:** `DESIGN_PREPARED / IMPLEMENTATION_BLOCKED_BY_CRM_008`

## 1. Architectural rule

CRM owns CRM business state and invariants. The central Workflow Engine owns orchestration, timers, approvals, retries, and workflow history. The AI Gateway owns provider routing, model policy, redaction enforcement, safety controls, and provider observability.

No integration result becomes CRM business state until the normal CRM application command revalidates tenant, actor, capability, entity version, and domain invariants.

## 2. Shared request envelope

Every outbound request must define:

- contract name and semantic version;
- tenant ID and authorized actor ID;
- correlation ID, causation ID, and idempotency key;
- source entity type, entity ID, and entity version;
- request timestamp, expiry, locale, and channel;
- required capability and policy decision reference;
- minimized payload classification and retention class.

Every result/callback must define:

- original correlation and idempotency identifiers;
- authoritative service execution/reference ID;
- result status and version;
- produced-at and expiry timestamps;
- error classification safe for CRM handling;
- integrity/authentication proof required by the platform contract;
- optional advisory output separated from executable command data.

## 3. Workflow boundary

### 3.1 Assignment and transfer

Workflow may orchestrate approval and timing. CRM-008 remains authoritative for ownership, assignment, queue claim/release, transfer state, and immutable ownership history.

Required safeguards:

- callback references the exact CRM aggregate version;
- cross-tenant, unknown, duplicate, expired, cancelled, or stale callbacks fail closed;
- completion invokes a normal CRM-008 command, never a direct database mutation;
- failed workflow leaves current ownership unchanged;
- CRM projection stores only governed references/status needed for reads.

### 3.2 Opportunity approval

Workflow evaluates an immutable approval snapshot. Any material change to amount, currency, stage, owner, products, probability, or policy-relevant data invalidates a pending approval unless the approved contract explicitly permits continuation.

Workflow approval does not bypass opportunity lifecycle validation and does not post accounting entries.

### 3.3 Reminders and escalations

Workflow owns schedules and timers. Notification delivery remains owned by the notification platform. CRM supplies authorized references and consumes governed status/results.

Completion, archive, cancellation, tenant removal, or permission loss must suppress future action according to the platform contract.

## 4. AI boundary

### 4.1 Customer summary

CRM builds an authorized, purpose-limited read projection. The AI Gateway performs redaction/policy checks and provider routing. The response must include:

- generated-content disclosure;
- source references restricted to records the user may read;
- generated timestamp and freshness boundary;
- confidence/limitations where defined by policy;
- safe unavailable/partial/unsafe-output classifications.

The response is not a source of truth and cannot overwrite CRM records automatically.

### 4.2 Next-best action

The AI result is an advisory recommendation with action code, explanation, confidence, expiry, policy status, and required confirmation level.

Execution is a separate CRM command that rechecks:

- current user and tenant;
- current capability and field-level permission;
- entity state/version;
- recommendation validity and expiry;
- business invariants;
- required human confirmation.

### 4.3 Scoring and explanation

Scores must identify score type, bounded scale, model/policy version, generated time, applicable entity version, freshness, explanation factors, and unsupported/unknown state.

Sensitive/prohibited attributes and proxy use are governed by AI Gateway policy. A score cannot independently authorize a high-impact CRM mutation.

## 5. Failure semantics

| Failure | CRM behavior | Mutation permitted |
|---|---|---|
| Workflow unavailable | Preserve CRM state; expose retryable/unavailable status | No |
| Workflow timeout | Treat outcome as unknown until reconciled; do not assume failure or success | No |
| Duplicate callback | Return idempotent prior result | No duplicate mutation |
| Stale callback | Reject and audit | No |
| AI unavailable | Show safe fallback/empty state | No |
| AI unsafe output | Suppress output and record policy event | No |
| AI timeout | Show retryable state; preserve CRM transaction | No |
| Policy denied | Explain denial at permitted detail level | No |
| Cross-tenant context | Reject, alert, and audit as security event | No |

## 6. Security and privacy checklist

- [ ] Tenant context is required and cannot be supplied solely by client-controlled payload.
- [ ] Actor identity and capabilities are resolved server-side.
- [ ] Callback authenticity and replay protection are enforced.
- [ ] Field minimization and classification occur before integration dispatch.
- [ ] Secrets, tokens, raw prompts, private fields, and provider payloads are excluded from ordinary logs.
- [ ] Trace/log metadata is tenant-safe.
- [ ] Retention and deletion propagation are specified.
- [ ] Prompt/content injection is handled at the AI Gateway boundary.
- [ ] Tool/action allowlists are policy-controlled.
- [ ] Human confirmation is explicit for high-impact actions.
- [ ] Failure never weakens authorization or tenant isolation.

## 7. Contract compatibility

- Semantic contract versions are explicit.
- Additive fields are optional until all consumers are updated.
- Breaking changes require a new major version and migration plan.
- Unsupported versions fail closed with a governed error.
- Consumer/provider contract tests run in CI.
- Deprecation includes owner, deadline, usage evidence, and rollback strategy.

## 8. Decisions deferred until post-CRM-008 reconciliation

The following values are intentionally unresolved during preparation:

- exact Workflow Engine endpoint/event transport;
- exact AI Gateway endpoint/event transport;
- contract version numbers;
- callback authentication mechanism;
- timeout/retry numeric thresholds;
- model/provider selection;
- retention durations;
- implementation branch and migration range.

These must be resolved from the actual post-CRM-008 platform baseline, not guessed in advance.
