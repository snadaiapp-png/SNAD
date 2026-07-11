# SANAD Local Server — Disaster Recovery Plan

## Overview

This document defines the disaster recovery procedures for the SANAD self-hosted backend server.

## Recovery Time Objective (RTO)

- **Target**: 30 minutes
- **Maximum acceptable**: 2 hours

## Recovery Point Objective (RPO)

- **Target**: 24 hours (Supabase daily backups)
- **For critical data**: 1 hour (manual pg_dump frequency)

## Disaster Scenarios

### Scenario 1: Server Hardware Failure

**Impact**: Backend completely down, no API access.

**Recovery Steps**:
1. Provision a new server with the same specifications (Ubuntu 22.04+, 8GB RAM)
2. Install Docker and Docker Compose
3. Clone the repository: `git clone https://github.com/snadaiapp-png/SNAD.git`
4. Restore `/srv/sanad/config/.env` from backup
5. Restore Cloudflare Tunnel configuration from backup
6. Run: `docker compose -f docker-compose.self-hosted.yml --env-file /srv/sanad/config/.env up -d --build`
7. Verify health: `curl http://localhost:8080/actuator/health`
8. Verify public access: `curl https://api.snad.sa/actuator/health`

**Estimated Time**: 30-60 minutes

### Scenario 2: Database (Supabase) Outage

**Impact**: Backend running but cannot read/write data.

**Recovery Steps**:
1. Check Supabase status: https://status.supabase.com
2. If Supabase is down, wait for recovery (backend will auto-reconnect)
3. If database is corrupted:
   a. Restore from Supabase backup (Dashboard → Database → Backups)
   b. Or restore from manual pg_dump: `pg_restore -d "postgresql://..." backup.dump`
4. Restart backend: `docker compose restart backend`

**Estimated Time**: 15-60 minutes (depends on Supabase)

### Scenario 3: Cloudflare Tunnel Failure

**Impact**: Backend running locally but Vercel cannot reach it.

**Recovery Steps**:
1. Check: `sudo systemctl status cloudflared`
2. Restart: `sudo systemctl restart cloudflared`
3. If still failing, re-authenticate: `cloudflared tunnel login`
4. Recreate tunnel if needed: `cloudflared tunnel create sanad-backend`
5. Update DNS: `cloudflared tunnel route dns sanad-backend api.snad.sa`
6. If tunnel cannot be restored:
   a. Switch to Render fallback (update Vercel env var)
   b. Or set up temporary Nginx reverse proxy with Let's Encrypt

**Estimated Time**: 10-30 minutes

### Scenario 4: Data Corruption

**Impact**: Database has inconsistent or corrupted data.

**Recovery Steps**:
1. Stop backend: `docker compose down backend`
2. Create a backup of current state: `pg_dump ... > corruption-backup.dump`
3. Restore from last known good backup:
   ```bash
   # Via Supabase Dashboard: Database → Backups → Restore
   # Or via psql:
   dropdb "postgresql://..."
   createdb "postgresql://..."
   pg_restore -d "postgresql://..." last-good-backup.dump
   ```
4. Restart backend: `docker compose up -d backend`
5. Verify data integrity: `curl http://localhost:8080/api/v1/control-plane/dashboard`

**Estimated Time**: 30-90 minutes

### Scenario 5: Security Breach

**Impact**: Unauthorized access detected.

**Recovery Steps**:
1. Immediately stop the backend: `docker compose down`
2. Change ALL passwords:
   - Supabase database password (Dashboard → Database → Settings)
   - Admin user password (via Supabase SQL API)
   - JWT_SECRET in .env (requires all users to re-login)
   - Cloudflare API token
   - SSH keys on server
3. Audit logs for unauthorized access:
   ```bash
   docker logs backend | grep -i "auth\|login\|error"
   ```
4. Review database for unauthorized changes:
   ```sql
   SELECT * FROM platform_audit_logs ORDER BY created_at DESC LIMIT 100;
   ```
5. Patch the vulnerability
6. Restart backend and monitor closely

**Estimated Time**: 2-8 hours

## Backup Strategy

### Automated Backups

| Backup Type | Frequency | Location | Retention |
|---|---|---|---|
| Supabase DB | Daily | Supabase Dashboard | 7 days |
| Config (.env) | Weekly | /srv/sanad/backups/ | 30 days |
| Cloudflare config | Weekly | /srv/sanad/backups/ | 30 days |
| Docker images | On deploy | Docker Hub / local | 3 versions |

### Manual Backup (Before Major Changes)

```bash
# Full backup script
#!/bin/bash
DATE=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR=/srv/sanad/backups/$DATE

mkdir -p $BACKUP_DIR

# Database backup
pg_dump "postgresql://sanad_app.hxhvfqxzigrqoxxnnzje:PASSWORD@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require" \
  -F c -f $BACKUP_DIR/database.dump

# Config backup
cp /srv/sanad/config/.env $BACKUP_DIR/.env
cp /etc/cloudflared/config.yml $BACKUP_DIR/cloudflared-config.yml
cp -r /root/.cloudflared/ $BACKUP_DIR/cloudflared-creds/

# Code version
cd /srv/sanad/repo && git log --oneline -1 > $BACKUP_DIR/git-commit.txt

echo "Backup completed: $BACKUP_DIR"
```

## Render Fallback Plan

The Render backend (commit `060cf4a`) remains available as an emergency fallback.

### When to Activate Render Fallback

- Self-hosted server is down for more than 30 minutes
- Cloudflare Tunnel cannot be restored within 1 hour
- Hardware failure with no quick replacement

### How to Activate

1. Update Vercel environment variable:
   ```
   BACKEND_API_BASE_URL=https://sanad-backend-mcrj.onrender.com
   ```
2. Redeploy Vercel
3. Monitor Render backend health
4. Note: Render has incomplete fixes — only tenant creation works correctly

### When to Deactivate Fallback

- Self-hosted server is restored and healthy
- All acceptance tests pass (see deployment guide)
- Cloudflare Tunnel is stable for 1 hour

## Communication Plan

### During an Incident

1. **Immediate**: Notify the team via the primary communication channel
2. **Status page**: Update at https://status.snad.sa (if configured)
3. **Every 15 minutes**: Provide status update until resolved
4. **Post-incident**: Write a post-mortem within 48 hours

### Escalation Path

1. **Level 1**: On-call developer (responds within 15 minutes)
2. **Level 2**: DevOps engineer (if not resolved in 30 minutes)
3. **Level 3**: CTO (if not resolved in 1 hour or data loss suspected)
