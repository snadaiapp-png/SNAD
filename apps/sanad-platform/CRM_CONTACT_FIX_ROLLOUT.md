# CRM Contact Fix Production Rollout

This marker triggers the protected `Publish Render Backend Image` workflow from the final CRM-G1 / CRM-007 closure merge SHA.

Required closure evidence:

- Vercel Production is `READY` on the exact merge SHA;
- Render is `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`;
- Flyway records `20260721.1` as `SQL / true` using read-only verification;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- Tenant B receives HTTP 404 for Tenant A's Contact;
- CRM-007 authenticated lifecycle and isolation pass after CRM-G1 success;
- no Flyway migrate, repair, schema-history edit, or manual Production SQL is performed by closure workflows.
