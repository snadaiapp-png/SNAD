# Users Production Certification Release Control

Exact main baseline: `935a4ccb27d520fbf004ea91966fbe857e2c4a15`

Purpose: authorize the canonical production release path solely so the already-certified Users/Identity/RBAC state can be verified live in production.

Governance constraints:
- No Users application code change.
- No HR/CRM/Accounting/Workflow implementation change.
- PostgreSQL Direct remains authoritative.
- No Docker/Testcontainers test-path changes.
- Production deployment must proceed only through the canonical protected release chain.
- `rollback_on_failure=true` is mandatory.
- Any drift of `main` from the exact baseline above before merge invalidates this authorization.
- Independent approval and exact-head CI are required before merge.
- The protected merge commit message must contain the literal marker `PRODUCTION-RELEASE-AUTHORIZED`.

Post-release Users-only acceptance sequence:
1. Tenant A authenticated smoke.
2. Tenant B authenticated smoke.
3. Cross-tenant denial.
4. Control Plane canonical-owner invariant.
5. RBAC allow/deny evaluation.
6. Session revoke/invalidation.
7. Evidence reconciliation against the exact production SHA.

This file does not itself deploy production or mutate production state.
