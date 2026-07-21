# CRM Contact Fix Production Rollout

This marker triggers the protected exact-SHA Production chain.

Runtime remediation generation: `crm-idempotency-reconciliation-v3`.

The remediation workflow:

1. initializes evidence only after the GitHub runner starts, using `$RUNNER_TEMP` and `GITHUB_ENV`;
2. enforces the direct Render runtime contract:
   - `SPRING_PROFILES_ACTIVE=prod`;
   - `FLYWAY_ENABLED=true`;
   - `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`;
3. waits for `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`;
4. redeploys that exact image and requires the complete owner, image name, and merge-SHA tag to match after canonicalizing only optional transport prefixes;
5. verifies health and Flyway `20260721.2` as `SQL / true` using read-only PostgreSQL checks;
6. verifies `crm_idempotency_records` with 12 columns, its uniqueness constraint, and both indexes;
7. dispatches CRM-G1 and waits for exact-SHA success;
8. dispatches CRM-007 and waits for exact-SHA success;
9. uploads immutable evidence on both success and failure.

The application corrections in this generation also:

- perform the `/crm` → `/crm/overview` redirect at the HTTP routing layer before the authenticated SPA boots, preventing duplicate refresh-token rotation;
- bind nullable address and communication list filters with explicit PostgreSQL JDBC types.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA;
- Render is `live` on the exact immutable image;
- Flyway `20260721.2` is `SQL / true` and the idempotency table contract is complete;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- address and communication lifecycle operations return their exact accepted statuses without 5xx responses;
- Tenant B receives HTTP 404 for Tenant A's records;
- CRM-G1 and CRM-007 authenticated lifecycle and isolation both pass on the same exact SHA;
- closure workflows perform no Flyway migrate, repair, schema-history edit, manual Production SQL, test skip, timeout increase, or `continue-on-error`.
