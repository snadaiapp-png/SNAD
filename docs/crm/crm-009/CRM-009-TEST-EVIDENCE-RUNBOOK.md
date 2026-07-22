# CRM-009 — Test and Immutable Evidence Runbook

> **Control issue:** #692  
> **State:** `PREPARED / TEST_EXECUTION_NOT_AUTHORIZED`  
> **Prerequisite:** Formal CRM-008 closure and separate CRM-009 implementation authorization

## 1. Evidence principle

Formal CRM-009 acceptance must be proven on one unchanged candidate commit. Source, CI, deployments, contract versions, test runs, artifacts, and production proof must resolve to that exact SHA.

Preparation of this runbook does not authorize execution against production.

## 2. Required verification layers

### T1 — Static and architecture controls

- CRM dependency rules prevent direct model-provider clients.
- CRM dependency rules prevent introduction of a second workflow runtime.
- Integration adapters remain behind CRM-owned ports.
- No secret, provider credential, or unsafe runtime default is committed.
- Contract versions and deprecation rules are documented.

### T2 — Unit and domain tests

- Request/result envelope validation.
- Idempotency and replay decisions.
- Stale, expired, cancelled, duplicate, unsupported-version, and cross-tenant rejection.
- Human-confirmation requirement for high-impact action.
- AI recommendation separated from executable CRM command.
- AI/workflow failure preserves CRM domain state.

### T3 — Contract tests

- CRM consumer contracts against central Workflow Engine.
- Workflow callback/provider contracts against CRM.
- CRM consumer contracts against AI Gateway.
- Required and optional field compatibility.
- Error taxonomy and unsupported-version behavior.
- Authentication/integrity and correlation fields.

### T4 — Integration and persistence tests

- Same-tenant accepted path.
- Cross-tenant request and callback rejection.
- Capability denied path.
- Duplicate delivery and safe retry.
- Concurrent entity update versus delayed callback.
- Audit correlation across outbound request, platform execution, callback, and CRM command.
- Projection consistency after success, rejection, cancellation, timeout, and reconciliation.

### T5 — Security and privacy tests

- Tenant-isolation matrix for every operation and callback.
- Field-level authorization and minimized AI input.
- Sensitive/prohibited field redaction.
- Callback spoofing, replay, tampering, and correlation-ID abuse.
- Prompt/content injection and unsafe tool/action attempt.
- Log/trace/cache inspection for secrets and cross-tenant data.
- Retention, deletion, and consent-revocation behavior.
- Authorization recheck at recommendation execution time.

### T6 — Resilience and failure-injection tests

- Workflow unavailable, slow, timeout, duplicate, out-of-order, and unknown-result states.
- AI Gateway unavailable, slow, timeout, rate-limited, unsafe-output, malformed-output, and partial-output states.
- Circuit breaker open/half-open/closed behavior.
- Bounded retry and dead-letter/reconciliation path.
- CRM transaction success when optional AI enrichment fails.
- Existing owner/opportunity/activity state preserved on failed orchestration.

### T7 — Frontend acceptance

- Workflow pending, approved, rejected, expired, cancelled, unavailable, and stale states.
- Generated-summary disclosure, sources, freshness, and unavailable state.
- Recommendation explanation, confidence, expiry, denial, confirmation, and feedback states.
- No optimistic UI claim before authoritative completion.
- Arabic and English coverage.
- RTL/LTR layout.
- Keyboard-only navigation.
- Screen-reader names, status announcements, and focus management.
- Responsive desktop/tablet/mobile coverage.

### T8 — Performance acceptance

Thresholds must be resolved after the production-like baseline is measured. Evidence must include:

- CRM latency without optional integration;
- dispatch latency and callback processing latency;
- AI and workflow timeout behavior;
- queue/backlog growth and recovery;
- duplicate/replay processing cost;
- projection freshness;
- resource saturation and error rate under expected load.

No threshold may be weakened solely to make a failing gate pass.

### T9 — Exact-SHA deployment and production proof

Execute only after explicit production authorization:

1. Record candidate SHA and expected PR head SHA.
2. Pass all required CI/security/contract/browser gates.
3. Deploy Vercel and backend runtime from the same candidate SHA.
4. Verify backend binding targets the authorized hosted backend, not a local tunnel.
5. Verify database migration history without repair, history edits, or manual production SQL.
6. Run authenticated Workflow and AI smoke paths using non-destructive authorized test data.
7. Run tenant-isolation negative paths.
8. Verify audit, logs, metrics, traces, timeouts, and fallback behavior.
9. Confirm zero unexplained CRM HTTP 5xx.
10. Store immutable artifacts and digests.

## 3. Acceptance criteria traceability

| ID | Criterion | Minimum evidence |
|---|---|---|
| AC-01 | CRM uses central Workflow Engine only | Architecture test + dependency report |
| AC-02 | CRM uses AI Gateway only | Architecture test + dependency report |
| AC-03 | Assignment workflow preserves CRM-008 invariants | Integration/concurrency suite |
| AC-04 | Opportunity approval is version-safe | Contract + stale-result tests |
| AC-05 | Reminder/escalation lifecycle is safe | Integration + late-event tests |
| AC-06 | Customer summaries are authorized and redacted | Privacy/security suite |
| AC-07 | Recommendations are advisory until confirmed | Domain + browser tests |
| AC-08 | Scores are versioned, explainable, and policy-governed | Contract + policy tests |
| AC-09 | Every path is tenant isolated | Tenant matrix |
| AC-10 | Every mutation is capability checked | Authorization matrix |
| AC-11 | Delivery is idempotent and replay safe | Duplicate/replay suite |
| AC-12 | Failures do not corrupt CRM transactions | Failure-injection suite |
| AC-13 | Audit correlation is complete | Audit evidence index |
| AC-14 | Localization and accessibility pass | Playwright/accessibility artifacts |
| AC-15 | Performance and resilience pass | Load/failure report |
| AC-16 | Exact-SHA production proof passes | Deployment and smoke artifacts |
| AC-17 | No unexplained CRM 5xx | Logs/run summary |
| AC-18 | No Critical/High unresolved security finding | Security gate report |

## 4. Evidence index template

```text
CONTROL_ISSUE: #692
PREPARATION_PR: <draft-pr>
IMPLEMENTATION_PR: <future-pr>
CANDIDATE_SHA: <exact-sha>
EXPECTED_HEAD_SHA: <exact-sha>
CRM_008_CLOSURE_SHA: <exact-sha>
WORKFLOW_CONTRACT_VERSION: <version>
AI_GATEWAY_CONTRACT_VERSION: <version>
CI_RUNS: <ids>
CONTRACT_TEST_RUNS: <ids>
SECURITY_RUNS: <ids>
TENANT_ISOLATION_RUNS: <ids>
BROWSER_ACCESSIBILITY_RUNS: <ids>
PERFORMANCE_RUNS: <ids>
VERCEL_DEPLOYMENT: <id>
BACKEND_DEPLOYMENT: <id>
DATABASE_EVIDENCE: <artifact>
PRODUCTION_SMOKE: <run-id>
UNEXPLAINED_CRM_HTTP_5XX: 0
ARTIFACTS_AND_DIGESTS: <index>
DECISION: GO | CONDITIONAL_GO | NO_GO
```

## 5. Stop conditions

Stop and hold the gate on any of the following:

- candidate SHA changes after evidence begins;
- CRM-008 closure is reopened or invalidated;
- contract drift or unsupported platform version;
- cross-tenant exposure or authorization bypass;
- direct model-provider or independent workflow-runtime dependency;
- unexplained mutation, duplicate action, stale callback acceptance, or audit gap;
- Critical/High security finding;
- unexplained CRM HTTP 5xx;
- deployment points to an unauthorized backend or local tunnel;
- evidence is mutable, incomplete, or not linked to the candidate SHA.

## 6. Preparation state

```text
RUNBOOK: COMPLETE
TEST_EXECUTION: NOT_STARTED
PRODUCTION_EXECUTION: PROHIBITED
EVIDENCE_CLAIM: NONE
CRM_009_CLOSURE: NOT_AUTHORIZED
```
