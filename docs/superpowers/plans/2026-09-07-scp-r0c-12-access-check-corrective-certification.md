# SCP R0C-12 — Real-JWT Access-Check Corrective Recertification

- **Date:** 2026-09-07
- **Mission:** `SCP_REAL_JWT_ACCESS_CHECK_RELEASE_BLOCKER_CORRECTION`
- **Classification:** P1_RELEASE_BLOCKER — FORWARD-ONLY CORRECTIVE RECERTIFICATION (THIS_IS_NOT_R0C13)
- **Branch:** `scp/r0c-12-access-check-jwt-correction`
- **Corrective base (`CERTIFIED_R0C12_HEAD`):** `83228cec83a2bc7a7a99cc1a72726f27f82fe378`
- **Corrected engineering head (`CORRECTED_ENGINEERING_HEAD`):** see §10
- **Mode:** EXECUTE_AUTONOMOUSLY · TDD=MANDATORY · POSTGRESQL_DIRECT=MANDATORY
- **MERGE=NO · DEPLOY=NO · PRODUCTION_MUTATION=NO · R0C13=FORBIDDEN**

---

## 1. Root cause

```
ROOT_CAUSE=
JWT_DETAILS_STRING_VS_ACCESS_CHECK_UUID_TYPE_MISMATCH
```

The production `JwtAuthenticationFilter` establishes the platform identity
details contract as **String** values (`JwtAuthenticationFilter.java`,
details map — unchanged in this correction, per mission §4):

```java
details.put("user_id",  claims.getSubject());   // String
details.put("tenant_id", jwtTenantIdStr);       // String
```

The consumer `ControlPlaneAccessService.accessCheck(...)` (endpoint
`GET /api/v1/executive/access-check/v2`, `GovernanceController`) required:

```java
details.get("tenant_id") instanceof UUID
details.get("user_id")   instanceof UUID
```

Therefore every genuine JWT-authenticated request failed the type checks and
was reported as `authenticated=false, capabilities={}` — while the same
request passed the `@RequireCapability("EXECUTIVE_VIEW")` aspect
(`CapabilityAuthorizationAspect.contextUuid` uses
`UUID.fromString(raw.toString())`, which accepts Strings) and rendered the
overview. Authorization components diverged on a type question, not a
permission question.

```
OBSERVED_SYMPTOM=
AUTHENTICATED_OVERVIEW_BUT_NAV_UNAUTHORIZED
```

The frontend `ScpNav` faithfully rendered the defective backend response:
`authenticated=false` → navigation notice "لا تملك صلاحية الوصول إلى منظومة
التحكم بالاشتراكات" while the overview page rendered server-derived SCP
metrics. The frontend was fail-closed-correct; the backend consumer was
wrong.

## 2. Fix (consumer-side, canonical, fail-closed)

`ControlPlaneAccessService.accessCheck` now extracts identity through the
canonical `com.sanad.platform.security.SecurityContextUtils` accessors —
which accept **both String and UUID** representations and are shared with
`CapabilityAuthorizationAspect` and `ControlPlaneAccessGuard`:

1. require `Authentication != null` and `authentication.isAuthenticated()`;
2. extract `tenantId`/`userId` safely via `SecurityContextUtils`;
3. any extraction failure (malformed/missing principal, non-map details)
   fails **closed**: `authenticated=false, capabilities={}`;
4. otherwise `authenticated=true` and the granular capability map is
   evaluated (evaluator exceptions degrade the individual capability to
   `false`, never to unauthenticated).

The `JwtAuthenticationFilter` was **not** modified (verified:
`JWT_FILTER_MODIFIED=NO` in the security audit). Identity is never taken
from request parameters; nothing defaults to full capabilities; nothing
fails open.

## 3. RED → GREEN evidence (mission §3)

Unit suite `ControlPlaneAccessServiceJwtDetailsTest` (11 tests):

| Case | Scenario | RED (before fix) | GREEN (after fix) |
|---|---|---|---|
| A | valid String details → `authenticated=true` + capabilities | **FAIL** (authenticated=false) | PASS |
| B | malformed tenant_id String | PASS (fail-closed) | PASS |
| C | malformed user_id String | PASS (fail-closed) | PASS |
| D | missing tenant_id | PASS (fail-closed) | PASS |
| E | missing user_id | PASS (fail-closed) | PASS |
| F | unauthenticated / null Authentication | PASS (fail-closed) | PASS |
| G | UUID-valued details backward-compat | PASS | PASS |
| §6 | denied capability stays `authenticated=true` | **FAIL** | PASS |
| §6 | evaluator failure degrades capability, not authentication | **FAIL** | PASS |
| §4 | no identity never defaults to full capabilities | PASS | PASS |

RED run: `Tests run: 11, Failures: 3, Errors: 0` (the three failures are
exactly the genuine-String-details happy paths — the release blocker
reproduced mechanically). GREEN run: `Tests run: 11, Failures: 0, Errors:
0, Skipped: 0`.

## 4. RBAC grant-chain verification (mission §5) — PostgreSQL Direct

Proven on host-native PostgreSQL 16.2 @ 127.0.0.1:5433 (pgserver bundle),
least-privilege role `sanad` (NOSUPERUSER/NOCREATEDB/NOCREATEROLE/
NOBYPASSRLS), real Flyway migration chain — NO Docker, NO Testcontainers,
NO H2:

- **Migration semantics (V20260830_2)**: roles holding the broad
  `EXECUTIVE_VIEW` capability receive all `*.read` granular codes
  (read-only mirror); roles holding `EXECUTIVE_MANAGE` receive
  read+manage+action codes. Additive, idempotent, tenant-scoped.
- **Capability catalog case convention**: codes are stored UPPERCASE
  (V20260901_1 canonicalization); production lookups normalize via
  `AccessCapabilityService.requireCode` (`toUpperCase(Locale.ROOT)`), so
  the lowercase response codes of `/access-check/v2` resolve correctly.
- **Grant chain** proven end-to-end on the real schema:
  `users → user_role_assignments → roles → role_capabilities →
  access_capabilities` (RB-07/RB-08 below).
- **Schema-enforced tenant isolation of grants**: `user_role_assignments`
  carries composite FK `fk_user_role_user (tenant_id, user_id) →
  users(tenant_id, id)` — a cross-tenant grant row cannot exist at all.
- **SECONDARY_BLOCKER=NONE** — the migration/backfill contract is sound
  (`RBAC_GRANT_BACKFILL_DEFECT` not raised).

## 5. PostgreSQL Direct acceptance matrix (mission §12)

`RbacAccessCheckPostgresAcceptanceTest` (15 scenarios) — result
**15/15 PASS, RBAC_PG_FAILURES=0, RBAC_PG_ERRORS=0, RBAC_PG_SKIPPED=0**:

| # | Scenario | Result |
|---|---|---|
| RB-01 | String principal IDs evaluate capabilities (REAL filter shape) | PASS |
| RB-02 | UUID principal IDs backward-compatible | PASS |
| RB-03 | malformed tenant string fails closed | PASS |
| RB-04 | malformed user string fails closed | PASS |
| RB-05 | missing IDs fail closed | PASS |
| RB-06 | unauthenticated fails closed | PASS |
| RB-07 | EXECUTIVE_VIEW role → all ten mandatory read capabilities; no manage powers | PASS |
| RB-08 | EXECUTIVE_MANAGE role → manage/action + read capabilities | PASS |
| RB-09 | unrelated role → `authenticated=true` with every SCP capability denied | PASS |
| RB-10 | tenant isolation — composite FK blocks cross-tenant grants; foreign-tenant evaluation rejected fail-closed | PASS |
| RB-11 | revoked assignment removes capability (stays authenticated) | PASS |
| RB-12 | inactive capability denied for everyone (restored afterwards) | PASS |
| RB-13 | REAL application-issued JWT over HTTP → 200 `authenticated=true` | PASS |
| RB-14 | required read capability map correct over HTTP (10 read `true`; manage explicitly `false`) | PASS |
| RB-15 | cross-tenant HTTP denied — tenant-binding violation 403 + control-plane guard 403 | PASS |

The HTTP scenarios exercise the REAL chain: JWT → `JwtAuthenticationFilter`
→ `SecurityContext` → `GovernanceController` →
`CapabilityAuthorizationAspect` → `ControlPlaneAccessGuard` →
`ControlPlaneAccessService` → `CapabilityEvaluationService` — with a REAL
application-issued token whose `tenant_id`/`user_id` claims are Strings.

Note recorded for the release file: a foreign-tenant identity can never
reach the consumer over HTTP at all — the filter rejects unknown
tenant/user pairs with 401 before any controller runs.

## 6. Authorization consistency contract (mission §6/§8)

One invariant, now pinned by tests (RB-07/09/13/14):

```
AUTHENTICATED_USER + EXECUTIVE_VIEW + expected granular read grants
  → accessCheckV2.authenticated = true
  → subscription.read .. audit.read (all ten) = true
A user without a granular permission → authenticated=true, that
  capability=false. A missing capability is NEVER mapped to
  authenticated=false.
```

Overview/nav alignment (verified, no code change required — smallest
correction is zero because the compatibility model is already correct):

```
OVERVIEW_ACCESS=EXECUTIVE_VIEW          (broad code; authoritative per
  docs/superpowers/specs/2026-08-29-subscription-control-plane-design.md —
  "Executive API base /api/v1/executive, capabilities EXECUTIVE_VIEW /
  EXECUTIVE_MANAGE / EXECUTIVE_BILLING")
NAV_OVERVIEW_CAPABILITY=subscription.read
  (conservative granular proxy; every role holding EXECUTIVE_VIEW holds all
  *.read via the V20260830_2 mirror, so the nav link is visible whenever the
  page is accessible — proven by RB-07 + RB-13/14)
```

Required user-visible invariant holds: a legitimate control-plane read user
who can read the overview is never reported "unauthenticated" by the
navigation.

## 7. Frontend state contract (mission §9) — fail-closed preserved

`ScpNav` distinguishes three failure modes (previously conflated):

| State | Trigger | Render |
|---|---|---|
| `AUTHENTICATION_FAILURE` | `authenticated=false` | session/no-access notice (`scp.nav.unauthorized`), no links |
| `AUTHENTICATED_BUT_NO_CAPABILITIES` | `authenticated=true`, zero capabilities `=== true` | explicit "signed in, no access" notice (`scp.nav.noAccess`, added with full ar/en parity) instead of an empty nav |
| `CAPABILITY_SERVICE_FAILURE` | request/network/server failure | degraded notice + retry control, links hidden |
| authorized | explicit capability map | only allowed links; missing keys stay hidden |

Tests: `scp-nav.test.tsx` **8/8 PASS** (incl. the two new
authenticated-no-access cases and the negative assertion that a granted
capability does NOT trigger the no-access state).

## 8. Production smoke blind spot (mission §10) — FIXED

```
ACCESS_CHECK_SMOKE_FAIL_OPEN_GAP=FIXED
```

RED (proven against the former script with the contract harness
`scripts/production/tests/`): the smoke script accepted
`authenticated=false` and accepted a denied mandatory read capability —
both PASSED the production gate while the deployed backend was violating
the access contract.

GREEN: `verify-scp-contract-smoke.sh` now requires, for the documented
CONTROL_PLANE_ADMIN read-capable smoke identity:

- `.authenticated == true`, and
- all ten mandatory read capabilities `== true`
  (subscription/catalog/application/plan/pricing/entitlement/usage/billing/
  provisioning/audit `.read`).

No manage/write capability is asserted — production smoke remains
read-only. Harness final result: **ALL SMOKE CONTRACT CASES PASS** (case 1
unauthorized → rejected; case 2 missing capability → rejected; case 3
authorized → accepted).

## 9. BFF identity equivalence (mission §11)

`route.access-identity.test.ts` (**2/2 PASS**) pins the
`VERCEL_BFF_EQUIVALENT` path for `/access-check/v2`: the BFF forwards the
`authorization` header untouched (FORWARDED_REQUEST_HEADERS allow-list) and
streams the backend identity body unchanged — equivalent to
`BACKEND_DIRECT` (which is proven end-to-end on real PostgreSQL by RB-13/14).

```
DIRECT_ACCESS_CHECK_AUTHENTICATED=true
BFF_ACCESS_CHECK_AUTHENTICATED=true
```

No tokens are exposed by the tests.

## 10. Gates summary

| Gate | Value |
|---|---|
| REAL_JWT_ACCESS_CHECK | PASS (unit 11/11 + HTTP RB-13/14 on real PG) |
| GRANULAR_RBAC | PASS (RB-07/08/09/11/12) |
| TENANT_ISOLATION | PASS (RB-10/15; FK + filter + guard layers) |
| R0C12_PG_ACCEPTANCE (15 scenarios) | 15 / 0F / 0E / 0S |
| R0C12_REGRESSION (17-class cross-workstream PG battery) | 183 / 0F / 0E / 0S — exact canonical baseline match |
| Predecessor recertification (22 classes) | 231 / 0F / 0E / 0S |
| FULL_MAVEN (single canonical run, `mvn clean` + exactly one `mvn test -B -ntp -Dsurefire.useFile=false`, 15:04) | **2738 tests / 0F / 0E / 21S — BUILD SUCCESS** |
| FULL_MAVEN_COUNT_RECONCILIATION | PASS — 351 fresh XMLs, testcase-element sum 2738 == Maven aggregate == distinct identities; STALE=0 |
| Skip composition | 6 pre-existing env-guarded `CommerceOrderPostgresConcurrencyTest` (identical to R0C-10/11/12 baseline) + 15 env-gated `RbacAccessCheckPostgresAcceptanceTest` (default-profile convention; separately executed 15/15 under `pg-acceptance`) |
| Test-count delta vs R0C-12 baseline | 2712 + 11 (new unit) + 15 (new env-gated acceptance) = 2738 |
| WEB_GATE | `npm ci` PASS · lint 0 errors (41 pre-existing warnings, byte-identical baseline) · `tsc --noEmit` clean · vitest **757/757** · `next build` PASS |
| SDS / LOGO / BRAND governance | 371 files 0 violations · 127 files 0 violations · 396 files 0 violations |
| I18N parity | en=ar=1058 keys (new `scp.nav.noAccess` present in BOTH locales) |
| Performance budget | PASS |
| SECURITY_AUDIT | PASS — 0 fail-open suspects, 0 production-direct grants, 0 RLS weakening, 0 guard removals, JWT filter untouched |
| Canonical secret scan | PASS — 0 secrets, 0 scan errors, full tree |
| No weakening of | ControlPlaneAccessGuard · @RequireCapability · tenant isolation · RLS · session-version checks (all unchanged; regression suites green) |

## 11. Forward-only chain

| Commit | Subject |
|---|---|
| `83228cec…` (base) | docs(scp): certify final Subscription Control Plane engineering closure |
| `210fe545…` | fix(scp): align access-check identity extraction with real JWT details contract |
| `25dd2eb4…` | test(scp): RBAC real-JWT access-check PostgreSQL Direct acceptance battery |
| `75aaf6ed…` | test(web): pin ScpNav authenticated-no-access state and BFF access identity |
| `7c4510b5…` | fix(scp): require authenticated read-capable smoke identity in SCP contract smoke |
| `2b5b6916…` | chore(scp): generate mock smoke credentials per run (canonical scanner hygiene) |
| final docs commit (this file) | docs(scp): certify R0C-12 real-JWT access correction |

`83228cec83a2bc7a7a99cc1a72726f27f82fe378` is an ancestor of the final head;
no history rewrite; no amend; no force push; `scp/r0c-12-final-module-closure`
untouched.

## 12. Verdict

```
SCP_ACCESS_CHECK_CORRECTION=PASS
REAL_JWT_IDENTITY_CONTRACT=PASS
GRANULAR_RBAC=PASS
PRODUCTION_SMOKE_RBAC_ASSERTION=PASS
BFF_ACCESS_IDENTITY=PASS
TENANT_ISOLATION=PASS
R0C12_REGRESSION=PASS
FULL_MAVEN=PASS
WEB_GATE=PASS
SECURITY_AUDIT=PASS
CORRECTED_ENGINEERING_HEAD=<see git rev-parse HEAD at certification>
MODULE_ENGINEERING_STATUS=CLOSED_CORRECTED
MODULE_RELEASE_READY=YES
PROTECTED_RELEASE_ALLOWED=YES
```

**MODULE_ENGINEERING_COMPLETE still does NOT mean PRODUCTION_DEPLOYED.**
The engineering chain ends here; the next action is the previously defined
PROTECTED RELEASE WORKFLOW (protected PR → merge → merged-SHA
certification → migration rehearsal → rollback rehearsal → explicit
authorization → deploy → smoke), whose engineering source SHA MUST be
`CORRECTED_ENGINEERING_HEAD` — not `83228cec…`.

`R0C13` remains **FORBIDDEN** — the SCP engineering R0C chain is complete;
release governance is a release gate, not a new engineering phase.
