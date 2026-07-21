# CRM Contact Fix Production Rollout

This marker triggers the protected exact-SHA Production chain.

Runtime remediation generation: `render-flyway-runtime-v2`.

The remediation workflow:

1. enforces the direct Render runtime contract:
   - `SPRING_PROFILES_ACTIVE=prod`;
   - `FLYWAY_ENABLED=true`;
   - `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`;
2. waits for `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`;
3. redeploys that exact image;
4. verifies health and `20260721.1 = SQL / true` read-only;
5. dispatches CRM-G1 and waits for exact-SHA success;
6. explicitly dispatches CRM-007.

The obsolete workflow-run G1 trigger is removed to prevent duplicate G1 evidence and duplicate CRM-007 queues.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA;
- Render is `live` on the exact immutable image;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- Tenant B receives HTTP 404 for Tenant A's Contact;
- CRM-007 authenticated lifecycle and isolation pass after CRM-G1 success;
- closure workflows perform no Flyway migrate, repair, schema-history edit, manual Production SQL, test skip, or `continue-on-error`.
