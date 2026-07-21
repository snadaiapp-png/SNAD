# CRM Contact Fix Production Rollout

This marker triggers the protected `Publish Render Backend Image` workflow from the final closure SHA.

The publish workflow must first reconcile the direct Render runtime variables:

- `FLYWAY_ENABLED=true`
- `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`

It then deploys `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`. A successful publish dispatches the read-only CRM-G1 gate, and CRM-007 is chained only after CRM-G1 succeeds.

Runtime remediation generation: `render-flyway-runtime-v1`.

Before the exact image is redeployed, the protected remediation workflow enforces the direct Render runtime contract:

- `SPRING_PROFILES_ACTIVE=prod`;
- `FLYWAY_ENABLED=true`;
- `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}`.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA;
- Render is `live` on the exact immutable image;
- Flyway records `20260721.1` as `SQL / true` using read-only verification;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- Tenant B receives HTTP 404 for Tenant A's Contact;
- CRM-007 authenticated lifecycle and isolation pass after CRM-G1 success;
- closure workflows perform no Flyway migrate, repair, schema-history edit, or manual Production SQL.
