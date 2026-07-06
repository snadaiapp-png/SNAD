# SANAD Stage 08 — AI Agent Security Model

**Document ID:** `SANAD-ST08-AI-SEC-001`
**Track:** 8.5
**Date:** 2026-07-06

---

## 1. Prohibited Agent Actions

* Final financial operations without approved policy.
* Data deletion without approval.
* Permission changes.
* Disclosing another tenant's data.
* Disclosing secrets.
* Executing uncontrolled SQL.
* Bypassing workflow approvals.
* Exceeding budget limits.
* Using shared memory across tenants.
* Executing unaudited actions.

---

## 2. Required Audit Fields per Execution

```text
Tenant
User
Agent
Version
Model
Prompt Version
Tools Used
Inputs Classification
Output Classification
Decision
Approval
Execution Result
Cost
Latency
Timestamp
Correlation ID
```

---

## 3. Prompt Injection Defenses

* Input sanitization (strip control characters, markup).
* Tool call allowlist.
* Output filtering (sensitive data redaction).
* Tool result sanitization before re-injection.
* Periodic red-team prompt injection tests.

---

## 4. Memory Isolation

* Per-tenant memory store.
* No cross-tenant reads (enforced at storage layer).
* Memory retention policy per tenant.
* Memory purge on tenant offboarding.

---

## 5. Emergency Disablement

* Single switch per agent.
* Single switch per tenant.
* Disablement propagates within 30 seconds.
* In-flight executions drained (allowed to complete if < 60s remaining; otherwise cancelled).
