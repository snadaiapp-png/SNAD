# CRM Contact Fix Production Rollout

This marker triggers the protected exact-SHA Production chain.

Runtime remediation generation: `crm-nullable-exclusion-closure-v4`.

Authoritative source fix:

- PR #651 merged successfully.
- Previous HTTP 500 root cause: PostgreSQL could not determine the type of nullable `exceptId`.
- Repository remediation: explicit PostgreSQL UUID casts and JDBC `Types.OTHER` bindings.
- Regression evidence: PostgreSQL 16 Testcontainers.

The protected closure workflow must:

1. publish `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`;
2. deploy Vercel Production and Render from the same exact merge SHA;
3. enforce the direct Render runtime contract:
   - `SPRING_PROFILES_ACTIVE=prod`;
   - `FLYWAY_ENABLED=true`;
   - `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`;
4. verify Render health, liveness and readiness;
5. verify Flyway `20260721.2` as `SQL / true` using read-only PostgreSQL checks;
6. verify `crm_idempotency_records` with 12 columns, its uniqueness constraint and both indexes;
7. dispatch CRM-G1 and require exact-SHA success;
8. dispatch CRM-007 and require exact-SHA authenticated lifecycle and two-tenant success;
9. upload immutable success or failure evidence.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA.
- Render is `live` on the exact immutable backend image.
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200.
- Address and communication lifecycle operations return their exact accepted statuses without HTTP 5xx.
- Tenant B receives HTTP 404 for Tenant A records.
- Audit, idempotency and ownership evidence is tied to the same SHA.
- No Flyway migrate/repair by the closure verifier, no schema-history edit, no manual Production SQL, no test skip, no timeout increase and no `continue-on-error`.

This marker contains no executable application or migration change.