# EXEC-PROMPT-AUTH-009 — Implementation Record

## Status

Implemented on `fix/auth-workspace-bootstrap` for review. Production acceptance remains blocked until CI and deployment validation complete.

## Root cause

The browser always attempted silent refresh on first render, including for new visitors. Successful login and refresh then performed a mandatory sequential `/auth/me` request before navigation. The root route displayed a full-screen workspace loading message during that speculative work, and `/workspace` was only a success interstitial.

## Implemented flow

- New visitor: `Page -> Login form` with no refresh request when the non-sensitive session hint is absent.
- Returning visitor: `Page -> session-hint check -> refresh bootstrap -> authorized destination`.
- New login: `POST /auth/login -> bootstrap response -> authorized destination`; `/auth/me` is no longer mandatory.
- Credential rotation: revoke old session, clear browser session cookies, authenticate once with the new credential, then navigate.

## Security model

`sanad_session_hint=1` contains no identity, tenant, role, access token, or refresh token. It is JavaScript-readable because it is only a UX hint. Authentication remains exclusively proven by the HttpOnly refresh cookie and backend-issued access token. The BFF clears both cookies on logout, terminal refresh rejection, and successful credential rotation.

## Destination policy

Only internal known application roots are allowed. Absolute URLs, protocol-relative URLs, backslashes, and control characters are rejected. The selected order is safe return URL, backend default destination, then `/workspace`.

## Compatibility

`GET /api/v1/auth/me` remains available for explicit profile resynchronization. Existing `MeResponse` consumers continue to receive the same view model through `authResponseToMe`.

## Validation required before merge

- Frontend lint, unit tests, production build.
- Backend `AuthBootstrapIntegrationTest` and existing auth regression suite.
- BFF cookie lifecycle tests.
- Preview E2E for new visitor, returning session, ambiguous tenant, credential rotation, timeout, and safe return URL.
- Production check that unauthenticated `/api/platform/api/v1/auth/me` remains HTTP 401.
