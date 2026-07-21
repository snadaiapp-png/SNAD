# Render Production Cutover

## Approved production topology

- Production frontend: `https://snad-app.vercel.app`
- Production backend: `https://sanad-backend-mcrj.onrender.com`
- Vercel BFF upstream: Render only
- Backend deployment source: immutable image published through GitHub Actions
- Self-hosted automatic deployment: disabled
- Local/ngrok production tunnel: retired

## Fail-closed contracts

- The Production BFF accepts only the hostname `sanad-backend-mcrj.onrender.com`.
- Any other Production backend hostname is rejected as unavailable.
- `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` must return HTTP `200` with `status=UP` during deployment.
- `/api/system/backend-status` must return HTTP `200` with `configured=true`, `reachable=true`, and `statusCode=200`.
- `/api/platform/api/v1/auth/me` without a session must return HTTP `401`.

## Decommissioned paths

- The local PowerShell tunnel connector is intentionally non-operational.
- The SSH Self-Hosted deployment workflow is archived with a `.disabled` suffix and is not executable by GitHub Actions.
- Production readiness rejects any backend host other than the approved Render service.
