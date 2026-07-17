# REM-P0-002 Live Production Check — 2026-07-17

**Timezone:** Asia/Riyadh  
**Finding:** REM-P0-002 — BFF authentication and session reliability  
**Status:** OPEN / PRODUCTION ACCEPTANCE BLOCKED

## Observed evidence

At the live verification time on 2026-07-17:

- `GET https://snad-app.vercel.app/api/system/backend-status`
  - HTTP `200`
  - `configured=true`
  - `reachable=true`
  - `statusCode=200`
  - backend target: `streak-train-empower.ngrok-free.dev`
- `GET https://snad-app.vercel.app/api/platform/api/v1/auth/me`
  - returned HTTP `502` twice instead of the required unauthenticated HTTP `401` contract.

## Decision

The application remediation merged through PR #533 is implemented and CI-verified, but REM-P0-002 cannot be closed because the live BFF authentication path is currently failing.

The 72-hour acceptance window has not started. A successful window begins only after:

1. the exact remediation SHA is deployed to Production;
2. the unauthenticated `/auth/me` contract returns `401` consistently;
3. the protected authenticated synthetic identity is configured;
4. the hourly login, `/me`, refresh, session restoration, logout and post-logout rejection journey passes;
5. no unexplained `502`, `503` or `504` is observed for 72 consecutive hourly cycles;
6. REM-P0-001 is removed or formally accepted as residual risk.

Missing or failed telemetry is not success. No closure decision is authorized by this record.
