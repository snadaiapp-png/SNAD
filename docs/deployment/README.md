# SANAD Deployment Documentation

## Current Architecture

```
Frontend:  Vercel (Next.js + BFF)
Backend:   Self-Hosted Server (Spring Boot + Docker)
Database:  Supabase PostgreSQL
Tunnel:    Cloudflare Tunnel (HTTPS)
```

## Documents

| Document | Purpose |
|---|---|
| [self-hosted-backend-deployment.md](./self-hosted-backend-deployment.md) | Complete deployment guide for the self-hosted backend |
| [../operations/self-hosted-production-runbook.md](../operations/self-hosted-production-runbook.md) | Daily operations and deployment procedures |
| [../operations/local-server-disaster-recovery.md](../operations/local-server-disaster-recovery.md) | Disaster recovery plan |
| [../operations/local-server-security-baseline.md](../operations/local-server-security-baseline.md) | Security requirements and hardening |

## Legacy (Render)

The following Render-related documents are archived but kept for reference during the transition period:

- `render-backend-deployment.md` (archived)
- `render-production-control-plane.md` (archived)

Render workflows are disabled:
- `.github/workflows/render-blueprint-validation.yml.disabled`
- `.github/workflows/render-production-preflight.yml.disabled`

## Migration Status

- [x] Backend code fixes complete (Instant→Timestamp, AuditLengthGuard)
- [x] Docker Compose self-hosted configuration ready
- [x] Cloudflare Tunnel setup guide ready
- [x] Production runbook ready
- [x] Disaster recovery plan ready
- [x] Security baseline ready
- [x] Render workflows disabled
- [ ] Server provisioned and configured
- [ ] Cloudflare Tunnel installed and tested
- [ ] Vercel BACKEND_API_BASE_URL updated
- [ ] End-to-end verification complete
- [ ] Render service deactivated
