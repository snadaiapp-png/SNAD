# Stage 09 + Stage 10 — Risk Register

| ID | Risk | Severity | Control | Status |
|---|---|---:|---|---|
| R-0910-001 | Cross-tenant CRM or AI context leakage | Critical | Server-derived tenant context, capability checks, isolation tests, no shared prompt memory | OPEN |
| R-0910-002 | Direct provider integration bypasses central AI governance | Critical | `CrmAiGateway` contract; architecture tests; provider dependencies prohibited in CRM packages | MITIGATED IN BASELINE |
| R-0910-003 | Secrets committed to Git or exposed in logs | Critical | Secure Platform/Vercel secret storage; secret scanning; redaction | OPEN |
| R-0910-004 | AI mutates CRM records without accountable approval | High | Advisory-first outputs; workflow handoff; explicit human confirmation | OPEN |
| R-0910-005 | Hallucinated or ungrounded sales recommendations | High | Evidence references, confidence, deterministic fallback, evaluations | OPEN |
| R-0910-006 | Biased or opaque lead scoring | High | Explainable reason codes, override, monitored outcomes, no protected-class features | PARTIALLY MITIGATED |
| R-0910-007 | AI unavailability breaks CRM transactions | High | Non-AI fallback, timeouts, circuit breakers, transaction isolation | PARTIALLY MITIGATED |
| R-0910-008 | Prompt injection through CRM notes/imports | Critical | Context allowlist, content boundaries, instruction isolation, output validation | OPEN |
| R-0910-009 | Excessive model cost or token usage | Medium | Tenant quotas, budgets, caching policy, usage metrics, hard limits | OPEN |
| R-0910-010 | Existing CRM docs no longer match runtime | High | Gate 9A reconciliation and exact-SHA traceability | OPEN |
| R-0910-011 | Production email credential debt affects CRM workflows | High | Keep production email disabled; retain Issue #173 as external blocker | OPEN |
| R-0910-012 | Mobile scope is claimed without implementation | Medium | Maintain separate mobile backlog and explicit status | OPEN |

## Closure Rule

No Critical risk may remain unmitigated before production authorization. High risks require remediation or explicit, time-bounded Project Manager acceptance with an owner and due date.
