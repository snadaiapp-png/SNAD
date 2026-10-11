# G1 — public.users RLS controlled cutover (NOT AUTHORIZED FOR PROD)

Observed on 2026-10-11: G1 live run [#38141159062](https://github.com/snadaiapp-png/SNAD/actions/runs/38141159062) failed at `users_rls_enabled`. Independent read-only Supabase query: `public.users.relrowsecurity=false` and **zero policies**; `sanad` has SELECT/INSERT/UPDATE/DELETE grants. `auth.users` (Supabase-owned) is a different table.

## Change
Forward-only Flyway migration enables **ENABLE + FORCE** RLS on `public.users`, scoped to the authenticated database role `sanad`, using matching `USING` and `WITH CHECK` tenant GUC; absent GUC is fail-closed. There is no universal read/insert exception and no modification of Supabase `auth.users`.

## Mandatory pre-deployment gates
1. Confirm Flyway migration number does not conflict with any newer `main` migration; verify exact backend release SHA.
2. Run PostgreSQL Direct integration tests using restricted `sanad` and separate migration/admin credentials. Verify no context SELECT yields zero records; INSERT/UPDATE/DELETE denied, tenant A sees only A, tenant B sees only B, cross-tenant UPDATE/DELETE and INSERT blocked.
3. **Authentication compatibility**: validate existing login by username/email, token refresh, password reset/first-login, platform bootstrap/control tenant, and create/update/disable user across tenant contexts on transaction-bound connections. These may currently rely on global queries without `app.tenant_id` and break after RLS. Fix their trusted scoping before migration deployment; never use a privileged runtime role to bypass RLS.
4. Confirm Render app `current_user=sanad` under real pooler connection and verify that tenant GUC is applied on the same database transaction as the `users` DML/queries. Demonstrate no tenant leakage with connection pooling.
5. Obtain owner + Security/DBA approval and a production rollback/forward-fix plan; apply via authorized protected backend migration path, NOT via ad-hoc Supabase SQL. Keep payments OFF and P0 HOLD.
6. Run G1 live security certification on exact `main`, inspect evidence for all tables, then perform authenticated A/B negative tests and independent risk sign-off. Passing this migration alone does not close P0.

## Stop conditions
Any user-login disruption, missing trusted tenant context, bypass privilege, policy mismatch, or unverified rollback => **NO GO**. Do not merge/deploy to production until cleared.
