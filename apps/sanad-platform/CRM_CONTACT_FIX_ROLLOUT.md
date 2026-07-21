# CRM Contact Fix Production Rollout

This marker triggers the existing `Publish Render Backend Image` workflow from the current `main` after the forward-only Flyway reconciliation `V20260721_1` was merged.

Required closure evidence:

- immutable backend image tagged with the merge SHA;
- Render deployment reaches `live` on that exact image;
- Flyway records `20260721.1` as `SQL / true`;
- Contact Create returns HTTP 201 and Contact Detail returns HTTP 200;
- no Flyway repair, schema-history edit, or manual Production SQL mutation.
