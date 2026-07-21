# CRM Contact Fix Production Rollout

This marker triggers the protected exact-SHA Production chain.

Runtime remediation generation: `crm-idempotency-reconciliation-v4`.

The reconciliation workflow from PR #648 remains the single orchestrator:

1. initializes evidence after the runner starts through `$RUNNER_TEMP` and `GITHUB_ENV`;
2. publishes and deploys `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>` to Render;
3. verifies Render health, Flyway `20260721.2 = SQL / true`, and the idempotency table contract read-only;
4. dispatches CRM-G1 once and waits for exact-SHA `completed / success`;
5. dispatches CRM-007 once and waits for exact-SHA `completed / success`;
6. uploads immutable success or failure evidence.

CRM-007 generation v4:

- is `workflow_dispatch` only; the obsolete `workflow_run` trigger is removed to prevent duplicate or parallel execution;
- discovers and records the exact-SHA completed/success CRM-G1 run before acceptance;
- verifies exact Vercel and Render deployments, Flyway versions, schema postconditions, and tenant identities read-only;
- executes the unchanged authenticated CRM-007 Playwright contract for addresses, communication methods, duplicate/conflict enforcement, ownership, RBAC, timeline/audit, tenant isolation, and session behavior;
- collects Playwright diagnostics and Render CRM HTTP 500 evidence even on acceptance failure;
- creates an immutable artifact and evidence PR only after the complete gate succeeds.

The application correction merged by PR #651 binds nullable address/communication mutation exclusions as PostgreSQL UUID values.

No Flyway repair, schema-history edit, manual Production SQL, timeout increase, test skip, `continue-on-error`, ngrok, local backend, or HTTP 500 acceptance is permitted.
