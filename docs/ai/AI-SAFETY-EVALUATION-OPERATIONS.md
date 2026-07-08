# AI Safety, Evaluation, and Operations Gate

Status: not production-ready until all controls are evidenced.

## Safety controls

| Control | Required evidence | Gate |
|---|---|---|
| Prompt-injection resistance | test report | #330 |
| Context allowlist | approved CRM context types | #330 |
| PII handling | redaction/deny policy tests | #330 |
| Tenant isolation | cross-tenant denial tests | #330 |
| Authorization | permission-filtering tests | #330 |
| Human override | reject/revise flow evidence | #330 |

## Evaluation controls

| Evaluation | Minimum evidence |
|---|---|
| Groundedness | output linked to allowed CRM records |
| Hallucination risk | offline test set and failure threshold |
| Lead scoring consistency | deterministic baseline comparison |
| Opportunity intelligence | explainability samples |
| Unsafe action prevention | human-confirmation tests |

## Operations controls

| Metric | Required output |
|---|---|
| Token usage | dashboard or report |
| Cost budget | alert threshold and evidence |
| Latency | p50/p95/p99 evidence |
| Failures | error-rate evidence |
| Fallbacks | fallback invocation evidence |
| Drift | baseline comparison evidence |

## Acceptance

#330 can close only when safety, evaluation, and operations evidence is attached with exact SHA and zero unresolved Critical AI security finding.
