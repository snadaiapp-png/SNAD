# CRM Contact Fix Production Rollout

This marker triggers the protected exact-SHA Production chain.

Runtime remediation generation: `crm-idempotency-reconciliation-v2`.

The remediation workflow:

1. enforces the direct Render runtime contract:
   - `SPRING_PROFILES_ACTIVE=prod`;
   - `FLYWAY_ENABLED=true`;
   - `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`;
2. waits for `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`;
3. redeploys that exact image;
4. canonicalizes only the optional Render transport prefix before requiring the complete owner, image name, and merge-SHA tag to match;
5. verifies health and Flyway `20260721.1` plus `20260721.2` as `SQL / true` using read-only PostgreSQL checks;
6. verifies `crm_idempotency_records` with 12 columns, its uniqueness constraint, and both indexes;
7. dispatches CRM-G1 and waits for exact-SHA success;
8. explicitly dispatches CRM-007.

The application corrections in this generation also:

- perform the `/crm` → `/crm/overview` redirect at the HTTP routing layer before the authenticated SPA boots, preventing duplicate refresh-token rotation;
- bind nullable address and communication list filters with explicit PostgreSQL JDBC types.

The obsolete workflow-run G1 trigger is removed to prevent duplicate G1 evidence and duplicate CRM-007 queues.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA;
- Render is `live` on the exact immutable image;
- Flyway `20260721.2` is `SQL / true` and the idempotency table contract is complete;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- Address and communication lifecycle operations return their exact accepted statuses without 5xx responses;
- Tenant B receives HTTP 404 for Tenant A's records;
- CRM-007 authenticated lifecycle and isolation pass after CRM-G1 success;
- closure workflows perform no Flyway migrate, repair, schema-history edit, manual Production SQL, test skip, timeout increase, or `continue-on-error`.
