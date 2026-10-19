# G1 public.users RLS — Authentication Compatibility Gate

Status: DESIGN / RED BASELINE — NOT PRODUCTION AUTHORIZED
Baseline: b2d96428f38a6563dc6f5a7689815390ada66e70
Production changes: NONE. Secrets / Render / Vercel: UNCHANGED.

## Confirmed runtime risks

- `UserRepository.findAllByEmail` and `findAllByUsername` deliberately query multiple tenants for tenantless login.
- `AuthService.login` calls those methods before obtaining a validated principal or trusted tenant context, and persists `lastLoginAt`.
- `LoginRequest.tenantId` is optional and supplied by an unauthenticated caller; it is a lookup hint, **not** an authorization decision.
- `RegistrationProvisioner.provision` writes a newly created user after creating the tenant, within the registration operation.
- `SessionVersionCache` queries `users` using tenantId/userId while authentication is being reconstructed.
- `TenantRlsConnectionHandler` sets `app.tenant_id` only in a non-autocommit transaction when authenticated security details include tenant_id. No such details exist during anonymous login.
- Live database: `public.users` RLS disabled and zero policies; `sanad` currently has SELECT/INSERT/UPDATE/DELETE.
- `G1 #14` fails at `users_rls_enabled`. Credential rotation previously passed.

## Required architecture before any migration

1. Define a **trusted pre-authentication tenant resolution** contract. It must preserve existing email/username login (including multi-tenant ambiguous identity behavior) without revealing cross-tenant user records to an arbitrary caller. A tenant identifier coming from the request cannot by itself authorize visibility.
2. Resolve the trusted tenant identity before tenant-scoped SQL, then set transaction-local `app.tenant_id` on the **same physical datasource/transaction** that JPA uses. Explicitly handle tenantless login and ambiguity through a separately authorized, minimum-privilege resolver; never grant general `sanad` RLS bypass.
3. Cover registration bootstrap: new tenant creation, user insertion, role grants, and immediate login. The tenant GUC must exist on the user write transaction.
4. Cover refresh, revoke, password reset and session-version lookup, control-plane owner access, background jobs and cross-tenant administration with explicit trusted scopes.
5. Only after successful characterization/compatibility tests, introduce the versioned Flyway RLS policy migration. Policies must provide SELECT visibility and INSERT/UPDATE/DELETE WITH CHECK, deny missing/invalid scope, and forbid cross-tenant mutation. Evaluate FORCE RLS and table ownership. Never change production DDL through this draft.

## Mandatory PostgreSQL Direct RED-to-GREEN evidence

- [ ] Email login with explicitly supplied tenant ID succeeds, invalid password denies, absent tenant context cannot read arbitrary tenants.
- [ ] Username login with explicitly supplied tenant ID succeeds; tenant A cannot access B.
- [ ] Legacy email-only/username-only login preserves its documented unique-vs-ambiguous behavior, with no leaked tenant user rows.
- [ ] Wrong tenant ID plus correct password is denied.
- [ ] New tenant registration creates a user and permits initial login under RLS.
- [ ] Refresh and session-version/revocation checks retain tenant scope.
- [ ] Reset/recovery and owner/control-plane flows work; non-owner cannot elevate.
- [ ] Direct SQL as non-bypass `sanad` verifies RLS on `public.users`, blocked cross-tenant SELECT and INSERT/UPDATE/DELETE, and missing-GUC fail-closed.
- [ ] Verify A/B data, canonical owner, independent reviewer, CI exact HEAD, no production mutation.

## Execution gate

This document is an implementation gate and inventory, **not a functioning code fix**. Do not merge as an RLS fix and do not execute `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` until the source paths and integration tests are implemented and green. Current statuses: G1 SECRETS REMEDIATION=CLOSED; G1 SECURITY CERTIFICATION=HOLD; P0 SECURITY HOLD=ACTIVE; PAYMENTS=DISABLED.
