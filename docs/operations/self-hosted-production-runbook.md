# SANAD Self-Hosted Production Runbook

## Daily Operations

### Check System Health

```bash
# Backend health
curl -sS http://localhost:8080/actuator/health | jq

# Container status
docker compose -f /srv/sanad/repo/apps/sanad-platform/docker-compose.self-hosted.yml ps

# Cloudflare Tunnel status
sudo systemctl status cloudflared

# Disk space
df -h /srv/sanad

# Memory usage
free -h

# Docker resource usage
docker stats --no-stream
```

### View Logs

```bash
# Backend logs (last 100 lines)
docker compose -f /srv/sanad/repo/apps/sanad-platform/docker-compose.self-hosted.yml logs --tail 100 backend

# Follow backend logs
docker compose -f /srv/sanad/repo/apps/sanad-platform/docker-compose.self-hosted.yml logs -f backend

# Cloudflare Tunnel logs
sudo journalctl -u cloudflared -f

# Health check log
tail -f /var/log/sanad/health-check.log
```

## Deployment Procedures

### Deploy a New Version

```bash
cd /srv/sanad/repo

# 1. Pull latest code
git pull origin main

# 2. Verify the commit
git log --oneline -1

# 3. Build and deploy
cd apps/sanad-platform
docker compose -f docker-compose.self-hosted.yml \
  --env-file /srv/sanad/config/.env \
  up -d --build

# 4. Wait for health check
for i in $(seq 1 30); do
  HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
  if [ "$HEALTH" = "200" ]; then
    echo "✅ Backend healthy after $i attempts"
    break
  fi
  echo "  [$i] Health: $HEALTH"
  sleep 5
done

# 5. Verify public endpoint
curl -sS https://api.snad.sa/actuator/health

# 6. Verify Vercel → Backend
curl -sS https://snad-app.vercel.app/api/system/backend-status
```

### Rollback to Previous Version

```bash
cd /srv/sanad/repo

# 1. Find last working commit
git log --oneline -10

# 2. Checkout previous commit
git checkout <commit-hash>

# 3. Rebuild and deploy
cd apps/sanad-platform
docker compose -f docker-compose.self-hosted.yml \
  --env-file /srv/sanad/config/.env \
  up -d --build

# 4. Verify health
curl -sS http://localhost:8080/actuator/health
```

## Emergency Procedures

### Backend Down (Container Crashing)

```bash
# 1. Check container status
docker compose -f docker-compose.self-hosted.yml ps

# 2. Check logs for error
docker compose -f docker-compose.self-hosted.yml logs --tail 50 backend

# 3. If database connection issue:
#    a. Verify Supabase is up: curl https://hxhvfqxzigrqoxxnnzje.supabase.co/rest/v1/tenants?select=id&limit=1 -H "apikey: $SUPABASE_KEY"
#    b. Check DATABASE_PASSWORD in .env matches Supabase
#    c. Restart: docker compose -f docker-compose.self-hosted.yml restart backend

# 4. If OOM (out of memory):
#    a. Check: dmesg | grep -i oom
#    b. Reduce -Xmx in JAVA_OPTS
#    c. Increase server RAM

# 5. If Flyway failure:
#    a. Set FLYWAY_ENABLED=false in .env temporarily
#    b. Restart: docker compose -f docker-compose.self-hosted.yml restart backend
#    c. Fix schema issue manually
#    d. Re-enable Flyway
```

### Cloudflare Tunnel Down

```bash
# 1. Check service status
sudo systemctl status cloudflared

# 2. Restart service
sudo systemctl restart cloudflared

# 3. If still failing, check config
cat /etc/cloudflared/config.yml

# 4. Test manually
cloudflared tunnel run sanad-backend

# 5. If credentials issue, re-authenticate
cloudflared tunnel login
```

### Database Connection Lost

```bash
# 1. Test Supabase connectivity
curl -sS https://hxhvfqxzigrqoxxnnzje.supabase.co/rest/v1/tenants?select=id&limit=1 \
  -H "apikey: $SUPABASE_SERVICE_KEY" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_KEY"

# 2. If Supabase is down:
#    a. Wait for Supabase to recover (check https://status.supabase.com)
#    b. Backend will auto-reconnect via HikariCP

# 3. If password changed on Supabase:
#    a. Update /srv/sanad/config/.env with new DATABASE_PASSWORD
#    b. Restart: docker compose -f docker-compose.self-hosted.yml restart backend
```

## Backup Procedures

### Database Backup (Supabase)

Supabase automatically backs up the database daily. For manual backups:

```bash
# Export via Supabase Dashboard:
# https://supabase.com/dashboard/project/hxhvfqxzigrqoxxnnzje/database/backups

# Or via pg_dump (if you have direct access):
pg_dump "postgresql://sanad_app.hxhvfqxzigrqoxxnnzje:PASSWORD@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require" \
  -F c -f /srv/sanad/backups/db-$(date +%Y%m%d).dump
```

### Configuration Backup

```bash
# Backup .env and Cloudflare config
tar czf /srv/sanad/backups/config-$(date +%Y%m%d).tar.gz \
  /srv/sanad/config/.env \
  /etc/cloudflared/config.yml \
  /root/.cloudflared/
```

### Restore from Backup

```bash
# Database restore (via Supabase Dashboard or psql)
pg_restore -d "postgresql://..." /srv/sanad/backups/db-YYYYMMDD.dump

# Config restore
tar xzf /srv/sanad/backups/config-YYYYMMDD.tar.gz -C /
docker compose -f docker-compose.self-hosted.yml restart backend
sudo systemctl restart cloudflared
```

## Monitoring Checklist

| Check | Command | Expected | Frequency |
|---|---|---|---|
| Backend health | `curl localhost:8080/actuator/health` | `{"status":"UP"}` | Every 5 min |
| Public endpoint | `curl https://api.snad.sa/actuator/health` | `{"status":"UP"}` | Every 15 min |
| Vercel → Backend | `curl https://snad-app.vercel.app/api/system/backend-status` | `{"reachable":true}` | Every 15 min |
| Disk space | `df -h /srv/sanad` | `< 80%` | Daily |
| Memory | `free -h` | available > 2GB | Daily |
| Docker containers | `docker ps` | All running | Hourly |
| Cloudflare Tunnel | `systemctl status cloudflared` | active (running) | Hourly |
| Log errors | `docker logs backend \| grep ERROR` | No new errors | Daily |

## Render Fallback (Emergency Only)

If the self-hosted server is completely down and cannot be recovered quickly:

1. Update Vercel `BACKEND_API_BASE_URL` back to `https://sanad-backend-mcrj.onrender.com`
2. Redeploy Vercel
3. Render backend (commit `060cf4a`) will serve as fallback
4. Note: Render backend has incomplete fixes (only tenant creation works)
5. Work on restoring the self-hosted server ASAP
