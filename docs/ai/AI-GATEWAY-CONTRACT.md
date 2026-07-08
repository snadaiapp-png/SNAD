# SANAD AI Gateway Contract

Status: mandatory contract for all CRM AI functionality.

## Contract principles

- Provider-neutral gateway.
- No direct model-provider dependency inside CRM packages.
- Tenant-scoped context only.
- Permission-filtered outputs.
- Redaction and audit metadata included in every request and response.
- Deterministic fallback for critical CRM flows.
- Human confirmation required for high-impact actions.

## Request envelope

| Field | Required | Purpose |
|---|---:|---|
| tenantId | yes | Tenant boundary |
| actorId | yes | Authenticated user |
| permissions | yes | Capability enforcement |
| purpose | yes | Approved business purpose |
| input | yes | User/task input |
| contextRefs | yes | Approved CRM record references only |
| policyFlags | yes | Redaction, safety, confirmation, budget rules |
| correlationId | yes | Audit traceability |

## Response envelope

| Field | Required | Purpose |
|---|---:|---|
| output | yes | AI response |
| groundingRefs | yes | Source records used |
| confidence | yes | Quality indicator |
| policyDecision | yes | Allow, deny, fallback, or human-review |
| redactionStatus | yes | Whether redaction was applied |
| costMetadata | yes | Budget and token tracking |
| evaluationMetadata | yes | Safety/quality checks |
| correlationId | yes | Audit traceability |

## Required tests

- Missing permission returns denial.
- Cross-tenant context is rejected.
- PII policy is enforced.
- Provider outage triggers deterministic fallback.
- Budget breach triggers safe failure or fallback.
- High-impact action requires human confirmation.

## Acceptance

#328 can close only after contract tests, dependency scan, secret scan, fallback tests, and exact-SHA evidence pass.
