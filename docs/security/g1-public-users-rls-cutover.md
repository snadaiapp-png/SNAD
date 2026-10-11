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

## Verified root cause — 2026-10-11 (PR 1380 CI)

**Do not merge:** exact-head CI on `4efbcaa752ba2d9db354f5649c7a886d35528a83` passed Flyway but G2 seed failed at `g2-acceptance-seed.sql:246` with `new row violates row-level security policy for table "users"` ([run 38164483142](https://github.com/snadaiapp-png/SNAD/actions/runs/38164483142)). Platform IAM ([run 38164483155](https://github.com/snadaiapp-png/SNAD/actions/runs/38164483155)) reproduced user INSERT denial and session invalidation failures. CI-only bypasses or permissive PUBLIC policies are prohibited.

**Source-level auth blocker:** `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java` permits `LoginRequest.tenantId == null` and calls `userRepository.findAllByEmail(...)` / `findAllByUsername(...)` across tenants. This pre-auth global discovery cannot be satisfied by a simple single-tenant `app.tenant_id` transaction GUC. A change to users RLS alone will either deny this legitimate login path or leak cross-tenant data. Migration must not reach production while this contract exists without a separately reviewed safe discovery solution.

**Required forward implementation, in order:**
1. Define authenticated tenant selection/discovery preserving the existing ambiguous-tenant behavior without unrestricted user table reads or tenant enumeration. Threat-model timing, error responses, reset flows and rate limiting.
2. Enforce trusted transaction-local tenant context for all post-discovery `users` reads/writes, including login last-login update, refresh/session checks, password reset, user bootstrap, management and service-to-service calls. Verify connection pool reuse cannot carry another tenant's GUC.
3. Separate migration/fixture seed identity from runtime `sanad` and cover forced-RLS behavior; do not disable RLS in tests or silently elevate application credentials.
4. Prove positive email/username login with and without explicit tenant ID, multi-tenant ambiguity, wrong tenant, control-plane bootstrap, token refresh/revocation, password reset, user CRUD, A/B isolation and missing-context deny in PostgreSQL Direct with actual least-privilege credentials.
5. Re-run all exact-head CI and G1 live read-only certification only after independent Security/DBA approval, with payments disabled throughout.

**Status:** BLOCKED / P0 HOLD / NOT PRODUCTION READY. Preview deployment is not production certification.
