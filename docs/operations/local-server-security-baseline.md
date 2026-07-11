# SANAD Local Server — Security Baseline

## Overview

This document defines the minimum security requirements for the SANAD self-hosted backend server.

## Server Hardening

### OS-Level

```bash
# 1. Disable root SSH login
sudo sed -i 's/PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config

# 2. Disable password authentication (key-based only)
sudo sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config

# 3. Update SSH port (optional, change from 22 to a high port)
# sudo sed -i 's/Port 22/Port 22222/' /etc/ssh/sshd_config

# 4. Install fail2ban
sudo apt install -y fail2ban
sudo systemctl enable fail2ban
sudo systemctl start fail2ban

# 5. Enable automatic security updates
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades

# 6. Configure firewall (UFW)
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22222/tcp  # SSH (or 22 if not changed)
# Do NOT allow 8080 — Cloudflare Tunnel connects locally
sudo ufw enable
```

### Docker Security

```bash
# 1. Run containers as non-root user (already configured in Dockerfile)
# Verify:
docker exec sanad-backend id
# Should show: uid=1000(sanad) gid=1000(sanad)

# 2. Limit container resources
# In docker-compose.self-hosted.yml:
# deploy:
#   resources:
#     limits:
#       memory: 2G
#       cpus: '2'

# 3. Read-only root filesystem (optional, may break some features)
# read_only: true
# tmpfs:
#   - /tmp
```

## Secret Management

### Rules

1. **NEVER** commit `.env` files to Git
2. **NEVER** print secrets in logs
3. **NEVER** share secrets in chat or email
4. **ALWAYS** use `chmod 600` on `.env` files
5. **ALWAYS** rotate secrets if compromised
6. **ALWAYS** use different secrets for dev/staging/prod

### .gitignore Verification

```bash
# Verify .env is ignored
echo ".env" >> .gitignore
echo "*.env" >> .gitignore
echo "!*.env.example" >> .gitignore

# Verify no secrets in Git history
git log --all --diff-filter=D -- "*.env"
# If found, use BFG or git-filter-repo to purge
```

### Secret Rotation Schedule

| Secret | Rotation Frequency | Method |
|---|---|---|
| JWT_SECRET | Every 12 months | Generate new, update .env, restart backend (all sessions invalidated) |
| DATABASE_PASSWORD | Every 6 months | Change in Supabase Dashboard, update .env, restart backend |
| Cloudflare API token | Every 12 months | Regenerate in Cloudflare Dashboard |
| SSH keys | Every 6 months | Generate new keypair, update authorized_keys |
| Admin password | Every 3 months | Via Supabase SQL API with bcrypt hash |

## Network Security

### Cloudflare Tunnel (Primary)

- No inbound ports open on router (no port forwarding)
- All traffic flows through Cloudflare's network
- Cloudflare provides DDoS protection
- Real server IP is hidden

### Firewall Rules

```bash
# Allow SSH only from specific IPs (optional, more secure)
sudo ufw allow from YOUR_ADMIN_IP to any port 22222

# Block all other inbound
sudo ufw default deny incoming

# Cloudflare Tunnel connects locally (no port opening needed)
# Docker port 8080 is bound to localhost only:
# In docker-compose.self-hosted.yml:
# ports:
#   - "127.0.0.1:8080:8080"  # Only localhost, not 0.0.0.0
```

## Application Security

### CORS Configuration

```env
# In .env — only allow your Vercel frontend
SANAD_CORS_ALLOWED_ORIGINS=https://snad-app.vercel.app
```

### Rate Limiting

The backend has built-in rate limiting:
- Login: 5 attempts per 5 minutes per email
- API: Configurable per endpoint

### Audit Logging

All control-plane operations are logged:
```sql
SELECT action, resource_type, result, created_at
FROM platform_audit_logs
ORDER BY created_at DESC
LIMIT 100;
```

## Monitoring & Alerting

### Security Monitoring Script

```bash
#!/bin/bash
# /srv/sanad/scripts/security-check.sh

# Check for failed login attempts
FAILED=$(curl -sS http://localhost:8080/actuator/health 2>/dev/null)

# Check for unusual API patterns
docker logs sanad-backend --since 1h 2>&1 | grep -c "401\|403" > /tmp/failed-auths
if [ $(cat /tmp/failed-auths) -gt 100 ]; then
    echo "ALERT: High number of failed auth attempts: $(cat /tmp/failed-auths)"
    # Send alert via email/webhook
fi

# Check disk space
USAGE=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$USAGE" -gt 80 ]; then
    echo "ALERT: Disk usage at ${USAGE}%"
fi

# Check if backend is running
if ! docker ps | grep -q sanad-backend; then
    echo "ALERT: Backend container is not running!"
fi
```

### Cron Job

```bash
# Check every 10 minutes
*/10 * * * * /srv/sanad/scripts/security-check.sh >> /var/log/sanad/security.log 2>&1
```

## Compliance Checklist

- [ ] SSH key-based authentication only
- [ ] Firewall enabled (UFW)
- [ ] fail2ban installed and active
- [ ] Automatic security updates enabled
- [ ] .env file has 600 permissions
- [ ] No secrets in Git
- [ ] Docker containers run as non-root
- [ ] Cloudflare Tunnel configured (no open ports)
- [ ] CORS restricted to Vercel domain
- [ ] Audit logging enabled
- [ ] Rate limiting active
- [ ] Backup strategy documented and tested
- [ ] Disaster recovery plan documented
- [ ] Secret rotation schedule defined
- [ ] Security monitoring active
