# REM-P0-002 — BFF Authentication and Session Reliability

**Effective date:** 2026-07-17  
**Finding:** REM-P0-002  
**Severity:** P0  
**Current status:** `REMEDIATION IMPLEMENTED / OPEN PENDING PRODUCTION OBSERVATION AND INFRASTRUCTURE DEPENDENCY`

## 1. Purpose

This control set addresses intermittent `502`, `503`, `504` and timeout behavior across the same-origin production BFF and the authentication/session lifecycle.

It covers:

- anonymous `/auth/me` behavior;
- login;
- authenticated `/auth/me`;
- refresh-token rotation;
- session restoration;
- logout;
- refresh rejection after logout;
- BFF timeout, retry and request-correlation behavior;
- incident creation and sanitized evidence.

## 2. Application corrections

### BFF reliability

The BFF now:

1. Treats `BACKEND_REQUEST_TIMEOUT_MS` as one end-to-end request budget.
2. Splits that budget across no more than two idempotent attempts.
3. Retries only `GET` and `HEAD` requests.
4. Retries only transient upstream `502`, `503` and `504` responses or network failures.
5. Never retries login, refresh, logout or other state-changing requests automatically.
6. Returns deterministic `504` for upstream timeout and `502` for upstream network failure.
7. Emits a request ID and sanitized failure classification without exposing upstream secrets.
8. Clears an invalid refresh cookie after refresh `401` or `403`.
9. Clears the local refresh cookie on logout even when upstream revocation fails.

### Browser session reliability

The frontend now:

1. Uses a single-flight refresh rotation for concurrent `401` responses.
2. Shares one refresh result across all waiting requests.
3. Releases the single-flight operation after success or failure so recovery can be retried.
4. Invalidates late refresh responses after logout or a newer login generation.
5. Prevents a stale refresh response from silently restoring a session the user ended.

## 3. Production synthetic journey

`.github/workflows/bff-auth-session-synthetic.yml` runs hourly and can also be dispatched manually.

The synthetic uses a protected Production test identity and calls the public web origin through:

```text
/api/platform/api/v1/auth
```

Every cycle verifies:

1. Anonymous `/me` returns `401`.
2. Login returns `200`, a valid access response and a first-party HttpOnly refresh cookie.
3. Authenticated `/me` returns `200` with the expected tenant and active user.
4. Refresh returns `200` and rotates the access session.
5. `/me` succeeds with the rotated access token.
6. Logout returns `204` and clears the browser refresh cookie.
7. Refresh after logout returns `401`.

The workflow uploads sanitized JSON evidence containing only:

- step name;
- HTTP status;
- duration;
- request ID;
- BFF attempt count;
- sanitized BFF error class.

Access tokens, refresh tokens, passwords, cookie values and secret identifiers are never written to the evidence artifact.

## 4. Incident behavior

A failed scheduled journey opens or updates one GitHub incident titled:

```text
[Synthetic Incident] Production BFF authentication journey failing
```

A later successful journey records recovery and closes the active synthetic incident. Failure and recovery do not by themselves close REM-P0-002; they are inputs to incident review and the observation window.

All `502`, `503`, `504`, timeout, login, refresh, identity, cookie and logout failures are bad events under the active SLA/SLO and error-budget policy.

## 5. Required protected configuration

The Production environment must provide:

- `AUTH_SMOKE_TENANT_A_ID`;
- `AUTH_SMOKE_TENANT_A_EMAIL`;
- `AUTH_SMOKE_TENANT_A_PASSWORD`.

The public web origin defaults to `https://snad-app.vercel.app` and may be overridden with the repository variable `PRODUCTION_WEB_BASE_URL`.

## 6. Closure gate

REM-P0-002 remains open until all of the following are true:

1. The implementation and validation workflows pass on an exact merged SHA.
2. The Production secrets are configured and the full synthetic passes.
3. At least 72 consecutive hourly cycles pass after the final relevant deployment.
4. No unexplained BFF/authentication `502`, `503` or `504` occurs in that window.
5. Login, refresh, session restoration, logout, lockout and audit journeys have accepted evidence.
6. Availability and latency results are reported under the active service-level policy.
7. Any incident in the observation window has a completed PIR and corrective actions.
8. Identity, Platform Operations and Infrastructure owners approve the evidence.
9. The dependency on REM-P0-001 is removed or explicitly accepted as residual risk by authorized executive and security owners.

## 7. Current boundary

The application-level defects addressed by this package can be implemented and verified independently. Full closure cannot be claimed while production still relies on the temporary development tunnel and before the required production observation window completes.

Therefore the accepted interim status is:

```text
REM-P0-002: OPEN
APPLICATION CONTROLS: IMPLEMENTED
PRODUCTION SYNTHETIC: ESTABLISHED
OBSERVATION WINDOW: PENDING
REM-P0-001 DEPENDENCY: OPEN
BROAD COMMERCIAL GO-LIVE: NOT APPROVED
```
