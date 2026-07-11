# SANAD Self-Hosted Backend Deployment Guide

## Overview

This guide covers deploying the SANAD Spring Boot backend on a self-hosted server, replacing Render. The deployment uses Docker Compose for reliability, Cloudflare Tunnel for public HTTPS access, and Vercel for the frontend.

## Architecture

```
User Browser
    ↓
https://snad-app.vercel.app (Vercel — Next.js + BFF)
    ↓
https://api.snad.sa (Cloudflare Tunnel — HTTPS)
    ↓
SANAD Backend (Docker — Spring Boot :8080)
    ↓
PostgreSQL (Supabase or Local Docker)
```

## Prerequisites

### Server Requirements
- **OS**: Ubuntu Server 22.04 LTS or 24.04 LTS
- **CPU**: 4 cores minimum
- **RAM**: 8 GB minimum (4 GB for backend + 2 GB for PostgreSQL + 2 GB OS)
- **Storage**: 50 GB SSD minimum
- **Network**: Stable internet connection (static IP recommended but not required with Cloudflare Tunnel)
- **Power**: UPS recommended for 24/7 uptime

### Software Requirements
- Docker 24+
- Docker Compose v2+
- Cloudflare account (free tier sufficient)
- Domain name (e.g., `snad.sa`) with DNS managed by Cloudflare

## Step 1: Prepare the Server

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Install Docker Compose v2 (included in Docker install)
docker compose version

# Create SANAD directories
sudo mkdir -p /srv/sanad/config /srv/sanad/logs /var/log/sanad
sudo chown -R $USER:$USER /srv/sanad
```

## Step 2: Clone the Repository

```bash
cd /srv/sanad
git clone https://github.com/snadaiapp-png/SNAD.git repo
cd repo
```

## Step 3: Configure Environment

```bash
# Copy the template
cp scripts/.env.example /srv/sanad/config/.env

# Edit with your real values
nano /srv/sanad/config/.env
```

### Critical Values to Set

| Variable | Value | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require` | Supabase pooler URL |
| `DATABASE_USERNAME` | `sanad_app.hxhvfqxzigrqoxxnnzje` | Supabase database user |
| `DATABASE_PASSWORD` | `[your Supabase DB password]` | Set from Supabase Dashboard |
| `JWT_SECRET` | `[64-char random string]` | **Generate ONCE, never change** |
| `SANAD_CONTROL_PLANE_TENANT_ID` | `958bbb1c-eece-4839-bca8-a5bfa14e6ac1` | Control plane tenant UUID |
| `SANAD_CORS_ALLOWED_ORIGINS` | `https://snad-app.vercel.app` | Your Vercel frontend URL |

### Generate JWT Secret

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(48))"
# Or:
openssl rand -base64 48
```

### Secure the .env file

```bash
chmod 600 /srv/sanad/config/.env
```

## Step 4: Deploy with Docker Compose

### Option A: Using External Database (Supabase)

```bash
cd /srv/sanad/repo/apps/sanad-platform

# Start with external database (Supabase)
docker compose -f docker-compose.self-hosted.yml \
  --env-file /srv/sanad/config/.env \
  up -d --build
```

### Option B: Using Local PostgreSQL

```bash
# Set USE_EXTERNAL_DB=false in .env
# Add to .env:
# POSTGRES_DB=sanad

cd /srv/sanad/repo/apps/sanad-platform

docker compose -f docker-compose.self-hosted.yml \
  --profile local-db \
  --env-file /srv/sanad/config/.env \
  up -d --build
```

### Verify Deployment

```bash
# Check container status
docker compose -f docker-compose.self-hosted.yml ps

# Check health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP","groups":["liveness","readiness"]}

# Check logs
docker compose -f docker-compose.self-hosted.yml logs -f backend
```

## Step 5: Set Up Cloudflare Tunnel

Cloudflare Tunnel provides a public HTTPS endpoint without exposing your server's IP or opening ports.

### Install cloudflared

```bash
# Download and install
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb
```

### Authenticate with Cloudflare

```bash
cloudflared tunnel login
# This opens a browser — select your domain (e.g., snad.sa)
```

### Create a Tunnel

```bash
cloudflared tunnel create sanad-backend
# Note the tunnel ID and credentials file path
```

### Configure the Tunnel

```bash
sudo mkdir -p /etc/cloudflared
sudo nano /etc/cloudflared/config.yml
```

```yaml
tunnel: <your-tunnel-id>
credentials-file: /root/.cloudflared/<your-tunnel-id>.json

ingress:
  - hostname: api.snad.sa
    service: http://localhost:8080
    originRequest:
      noTLSVerify: true
  - service: http_status:404
```

### Create DNS Record

```bash
cloudflared tunnel route dns sanad-backend api.snad.sa
```

### Run as a Service

```bash
sudo cloudflared service install
sudo systemctl start cloudflared
sudo systemctl enable cloudflared
sudo systemctl status cloudflared
```

### Verify Public Access

```bash
curl https://api.snad.sa/actuator/health
# Expected: {"status":"UP","groups":["liveness","readiness"]}
```

## Step 6: Update Vercel Environment

In your Vercel project settings (https://vercel.com/dashboard):

1. Go to **Settings → Environment Variables**
2. Add/Update:
   - `BACKEND_API_BASE_URL` = `https://api.snad.sa`
3. Remove (if exists):
   - Any Render URL references
4. **Redeploy** the Vercel project

## Step 7: Verify End-to-End

```bash
# Test frontend → BFF → backend
curl -sS https://snad-app.vercel.app/api/system/backend-status
# Expected: {"configured":true,"reachable":true,"statusCode":200}

# Test login
curl -sS -X POST https://snad-app.vercel.app/api/platform/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cp-admin@sanad-control-plane.internal","password":"YOUR_PASSWORD"}'
# Expected: {"accessToken":"...","user":{...}}
```

## Step 8: Set Up Auto-Restart

Docker Compose with `restart: unless-stopped` handles container crashes automatically. For system reboots:

```bash
# Enable Docker to start on boot
sudo systemctl enable docker

# Docker Compose containers with restart:unless-stopped will start automatically
```

## Step 9: Set Up Log Rotation

```bash
# Docker logs are already rotated via docker-compose.self-hosted.yml:
# logging:
#   driver: "json-file"
#   options:
#     max-size: "50m"
#     max-file: "5"

# For application logs
sudo nano /etc/logrotate.d/sanad
```

```
/var/log/sanad/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 sanad sanad
}
```

## Step 10: Set Up Monitoring

### Health Check Script

```bash
sudo nano /srv/sanad/scripts/health-check.sh
```

```bash
#!/bin/bash
HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
if [ "$HEALTH" != "200" ]; then
    echo "[$(date)] Backend unhealthy (HTTP $HEALTH), restarting..." >> /var/log/sanad/health-check.log
    cd /srv/sanad/repo/apps/sanad-platform
    docker compose -f docker-compose.self-hosted.yml restart backend
fi
```

```bash
sudo chmod +x /srv/sanad/scripts/health-check.sh

# Add to crontab (check every 5 minutes)
sudo crontab -e
# Add: */5 * * * * /srv/sanad/scripts/health-check.sh
```

## Deployment Updates

To deploy a new version after code changes:

```bash
cd /srv/sanad/repo
git pull origin main
cd apps/sanad-platform
docker compose -f docker-compose.self-hosted.yml --env-file /srv/sanad/config/.env up -d --build
```

## Rollback

If the new version fails:

```bash
cd /srv/sanad/repo
git log --oneline -5  # find the last working commit
git checkout <previous-commit>
cd apps/sanad-platform
docker compose -f docker-compose.self-hosted.yml --env-file /srv/sanad/config/.env up -d --build
```

## Security Checklist

- [ ] `.env` file has `chmod 600` permissions
- [ ] `.env` file is NOT in Git
- [ ] JWT_SECRET is a fixed 64+ character string
- [ ] DATABASE_PASSWORD is different from any development password
- [ ] Cloudflare Tunnel is configured (no open ports on router)
- [ ] Firewall allows only SSH (22) and Cloudflare Tunnel
- [ ] Docker containers run as non-root user
- [ ] Log rotation is configured
- [ ] Health check monitoring is active
- [ ] Backup strategy is in place

## Troubleshooting

### Backend won't start

```bash
# Check logs
docker compose -f docker-compose.self-hosted.yml logs backend

# Common issues:
# 1. Database connection: verify DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
# 2. Flyway migration failure: set FLYWAY_ENABLED=false temporarily, fix schema, re-enable
# 3. Memory: increase RAM or reduce -Xmx in JAVA_OPTS
```

### Cloudflare Tunnel not working

```bash
# Check tunnel status
sudo systemctl status cloudflared

# Check tunnel logs
sudo journalctl -u cloudflared -f

# Verify DNS
dig api.snad.sa
```

### Database connection issues

```bash
# Test direct connection (from server)
psql "postgresql://sanad_app.hxhvfqxzigrqoxxnnzje:PASSWORD@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require" -c "SELECT 1;"

# If using local PostgreSQL:
docker compose -f docker-compose.self-hosted.yml exec postgres psql -U sanad_app -d sanad -c "SELECT 1;"
```
