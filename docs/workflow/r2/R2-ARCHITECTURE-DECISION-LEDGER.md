# R2 Architecture Decision Ledger — Workflow Experience & Intelligence

SOURCE_LOCK_SHA: 56827d76b3383c17e81ed545dbff2cc51f4cf7cb
STATUS: FROZEN (GATE R2.2). Any deviation requires a new superseding entry appended below.

## AD-1 Notification Policy Model
Authoritative policy lives in `workflow_notification_policies` (tenant-scoped, RLS).
Fields: channel, event_type, recipient_source (USER|EXTERNAL_PARTICIPANT), template_key,
locale, timing (IMMEDIATE|BEFORE_DUE|ON_BREACH), offset_seconds, retry_policy
(MAX_ATTEMPTS bound), dedup_strategy (PER_TIMER|PER_ACTION|PER_EVENT|NONE),
escalation (NONE|SUPERVISOR_ALERT -> IN_APP evidence only), fallback_channel
(IN_APP only in R2), enabled, priority. Bounded validated JSONB extras; non-executable:
no script, no SQL, no shell, no tenant-supplied HTTP code. Missing policy for a
non-IN_APP channel => intent fails closed (CONFIG_ERROR, FAILED_TERMINAL).
IN_APP is always permitted as primary qualifying channel.

## AD-2 Provider SPI Boundary
`WorkflowChannelProvider` SPI: channel(), providerType(), deliver(WorkflowDeliveryRequest)
-> WorkflowDeliveryResult(providerMessageId, status, retryable, failureCategory).
No raw DB access, no arbitrary URL execution, no shell, no reflection, no dynamic class
loading, no unbounded scripts behind the SPI. Registry validates at startup:
unknown channel -> FAIL_CLOSED at dispatch (CONFIG_ERROR); duplicate provider for a
channel -> FAIL_STARTUP (ConfigurationError). Providers: InApp (writes
workflow_user_notifications), Email (delegates platform email stack), Push (Expo push
contract), WhatsApp (provider-neutral contract), Webhook (governed destinations).

## AD-3 Delivery Intent Lifecycle
`workflow_notification_intents` extended (ADDITIVE). delivery_status state machine:
PENDING -> PROCESSING -> DELIVERED | RETRY_WAIT -> PROCESSING -> DELIVERED |
FAILED_RETRYABLE -> RETRY_WAIT ... | FAILED_TERMINAL | CANCELLED.
Legacy SENT/FAILED remain valid terminal values for pre-R2 rows (mapped: SENT==DELIVERED
semantics, FAILED==FAILED_RETRYABLE semantics). attempt_count cap = 8; exponential
backoff via next_attempt_at. Stable idempotency identity = (tenant_id, deduplication_key)
via existing uq_wf_notification_dedup; at-least-once delivery; NO exactly-once claim.
Persisted per intent: tenant, instance, journey correlation, timer/work item/approval/
external action refs, recipient (user or external participant), channel, policy id,
correlation_id, causation_id, idempotency (dedup) key, attempt_count, provider_type,
provider_message_id, failure_category, timestamps, priority, deep_link, payload JSONB.

## AD-4 Correlation Model
correlation_id = workflow_instance_id (when available) else intent id.
causation_id = originating journey event_key hash / timer id / external action id.
All channel emissions append Journey evidence (governed 30-family vocabulary:
REMINDER_SENT, EXTERNAL_NOTIFIED, EXTERNAL_VIEWED, EXTERNAL_RESPONSE, DEADLINE_WARNING).

## AD-5 Journey Integration
Journey (workflow_journey) remains the authoritative append-only evidence ledger.
R2 emits at: notification enqueue (where governed), portal view/respond, timer
notification worker, feedback receipt. No journey row is ever updated/deleted.

## AD-6 Timer Integration (notification worker != deadline worker)
New `WorkflowTimerNotificationWorker`: scans workflow_timers (RUNNING, EXECUTION_DEADLINE)
due within warning window or breached -> enqueues notification intents with dedup keys
`"<stage>:"+timerId` (stages: REMINDER, DEADLINE_WARNING, DEADLINE_BREACH, TIMEOUT,
REASSIGNMENT). NEVER mutates timer/instance/work-item state. Deadline enforcement and
state transitions remain the exclusive responsibility of WorkflowDeadlineEnforcementWorker
(unmodified). External action expiry reminders dedup per action.

## AD-7 CRM Verified Contact Resolution (WhatsApp/Email for external participants)
`WorkflowExternalRecipientResolver` resolves recipients from CRM source entities via
`crm_communication_methods` (owner ACCOUNT/PERSON, method_type EMAIL/WHATSAPP, status
ACTIVE, verified=TRUE, ORDER BY preferred DESC, verified DESC, updated_at ASC). Workflow
NEVER stores a separate customer phone/email truth. communication_reference (free text)
is display-only fallback for IN_APP, never used for outbound WhatsApp.

## AD-8 External Action Security (Portal)
HTTP surface `/api/v1/workflows/external/portal/**` (token-scoped, no SNAD session):
- Token model unchanged from R1: 32-byte SecureRandom opaque token, SHA-256 hash stored,
  returned exactly once, scoped to ONE external action, tenant+participant bound.
- view: validates hash, expiry, revocation, status; marks VIEWED; returns bounded
  action descriptor (no sensitive data beyond action context).
- respond: re-reads authoritative row; validates tenant, participant, status, expiry,
  revocation, requested action within allowed_actions, single-use idempotency
  (repeated valid response replays prior response, no duplicate graph effect).
- Rate limiting: workflow_portal_access_log bounded counters per token (10/h) and
  coarse per-IP; exceeded -> 429 fail-closed.
- OTP optional when policy requires: workflow_portal_otp_challenges (6-digit,
  SHA-256 stored, 5-minute TTL, max 5 attempts, bound to action+participant, never
  logged in plaintext).
- Replay protection: single-shot response + access log; expired action -> EXPIRED state,
  never approved; revoked -> denied.
- RLS bridge: portal service sets app.tenant_id GUC via TenantRlsTransactionContext from
  the token row (trusted internal boundary), then all reads tenant-scoped.
- PORTAL_RESPONSE_ADVANCES_GRAPH=NO in R2: response evidence + Journey EXTERNAL_RESPONSE
  recorded; internal governed path remains responsible for graph advancement. External
  participants are never converted to User/Employee (R1 invariant preserved).

## AD-9 Analytics Projection Architecture
Deterministic, rebuildable projections derived ONLY from Journey +
workflow_responsibility_segments + workflow_timers (Journey = authoritative evidence):
- `workflow_analytics_process_facts` — per instance: started/completed/cancelled,
  process duration, sla breach count/compliance, late completion, timeout count,
  reassignment count, first response, external response time, completion, customer wait,
  system wait, queue wait (distinct columns per segment_type, never mixed).
- `workflow_analytics_step_facts` — per step instance: step duration, queue/employee/
  customer/system wait segments, breach flags.
Rebuild = full deterministic recompute per instance (idempotent upsert), driven by
staleness-bounded `WorkflowAnalyticsProjectionWorker` + explicit rebuild API
(WORKFLOW.ADMIN). Dimensions: tenant, definition, version, instance, module, source
entity, process/step, work item, approval, employee, team, customer, channel, calendar,
SLA mode, timer, date bucket. Non-computable metric => NULL, never invented.

## AD-10 Dashboard Query Architecture
Server-side set-based aggregate APIs under `/api/v1/workflows/analytics/dashboards/*`
(SERVICE, EMPLOYEE, TEAM, EXECUTIVE, BOTTLENECK, SLA, CUSTOMER). Tenant isolation via
RLS + capability WORKFLOW.MONITOR (read) on every endpoint. Date + dimension filters,
bounded windows, no cross-tenant aggregation, no N+1 (single SQL per view), empty state
= empty payload with zero counters.

## AD-11 Performance Metric Calculation Model
`WorkflowPerformanceMetricsService` computes employee/team responsibility metrics from
responsibility segments within a bounded window; output embeds explanation: metric
definition, measurement period, source event counts, included responsibility segments,
excluded waits, calculation inputs. NO AUTOMATED EMPLOYMENT DECISION: service is
read-only; it writes nothing to HR domain and no policy/system consumes it automatically
for terminate/demote/discipline/promotion/salary/employment-status decisions.

## AD-12 Feedback Model
`workflow_customer_feedback`: tenant-scoped; linked to customer/contact source entity,
process/instance, optional external action; rating 1..5 + bounded comment; submitted
by external participant via portal token or recorded by tenant users (capability
WORKFLOW.WRITE); no public enumeration; no fake customer records. Feedback is evidence
input only — no autonomous business decision consumes it.

## AD-13 Mobile Authorization Flow
Existing React Native + Expo foundation extended (no parallel mobile architecture).
Action Center = screens over existing authenticated APIs (work-items mine/pool,
approvals, notifications). Before ANY mutation the SERVER revalidates: current user,
tenant, Employee mapping (requireActionableEmployee), RBAC capability, assignment/
candidate eligibility, current object state, version, SoD, self-approval rules.
Mobile UI state is never authorization. Client caches are read-convenience only.

## AD-14 AI Context Boundary
`WorkflowAiContextService` produces bounded, tenant-scoped, authorization-scoped,
source-attributable context descriptors (workflow context, journey summary, task
context, approval context, analytics explanation) via `/api/v1/workflows/ai-context`
(capability WORKFLOW.VIEW). Hard size caps; no PII beyond authorization scope.
LIVE_AUTONOMOUS_AI=OFF: no agent execution, no auto approve/reject/assign/send.

## AD-15 Database & Migration Discipline
ADDITIVE FORWARD_ONLY FLYWAY. New migrations V20260912_* define every new table with
tenant_id, PK, FKs, indexes, uniqueness/idempotency constraints, timestamps, state
CHECK constraints, ENABLE ROW LEVEL SECURITY + fail-closed tenant_isolation policy.
CHECK-constraint widening on workflow_notification_intents (channel/delivery_status)
preserves legacy values (ADDITIVE enumeration). No repair, no rewrite, no drops.
POSTGRESQL 17 host-native Direct; no Docker/Testcontainers/H2.

## AD-16 Decoupling Invariant (state transition vs provider delivery)
Workflow state transaction NEVER calls a provider. Sequence: state commit ->
durable intent (same transaction) -> dispatcher worker delivers (separate transaction)
-> provider result recorded on intent. Provider outage never rolls back a committed
Workflow transition; no external call inside a critical Workflow transaction.
