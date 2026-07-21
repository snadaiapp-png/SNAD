# Render Production Cutover

- Production frontend: `https://snad-app.vercel.app`
- Production backend: `https://sanad-backend-mcrj.onrender.com`
- Vercel BFF upstream: Render only
- Self-hosted automatic deployment: disabled
- Local/ngrok production tunnel: retired
- Health contract: `/actuator/health` must return HTTP 200 and `status=UP`
- BFF unauthenticated boundary: `/api/platform/api/v1/auth/me` must return HTTP 401

## Operational boundary

Production configuration must not reference a local machine, ngrok endpoint, or self-hosted runner. Any future backend cutover requires an approved exact-SHA deployment, health/readiness verification, BFF authentication verification, rollback evidence, and an explicit update to the fail-closed production readiness gate.
