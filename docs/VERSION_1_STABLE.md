# SNAD Platform — Version 1.0 (Stable Restore Point)

## Overview

This document marks the current state of the SNAD platform as **Version 1.0 — Stable Restore Point**. This is the recommended baseline for disaster recovery and future development.

## Git Reference

- **Tag**: `v1.0-stable`
- **Commit**: `46e9cb38` - feat(workspace): remove duplicate Control Plane card - use Executive instead
- **Date**: 2026-08-13
- **Branch**: `main`

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Frontend (Vercel)                                              │
│  https://snad-app.vercel.app                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ /workspace  │  │ /crm        │  │ /executive              │  │
│  │ 3 cards     │  │ CRM module  │  │ Executive module        │  │
│  │ - CRM       │  │             │  │ (replaces /control-plane)│  │
│  │ - Executive │  │             │  │                         │  │
│  │ - SysHealth │  │             │  ├─────────────────────────┤  │
│  │             │  │             │  │ /system-health          │  │
│  │             │  │             │  │ System Health module    │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Backend (Render)                                               │
│  https://sanad-backend-mcrj.onrender.com                       │
│  Spring Boot • Java 17 • Flyway • JPA                          │
│                                                                 │
│  Modules:                                                       │
│  ├── /api/v1/auth          (login, logout, refresh)            │
│  ├── /api/v1/executive     (8 endpoints)                        │
│  ├── /api/v1/system-health (2 endpoints + 5 actions)           │
│  ├── /api/v1/crm           (5+ endpoints)                       │
│  └── /api/v1/access        (capabilities, roles)                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Database (Supabase PostgreSQL)                                 │
│  Project: hxhvfqxzigrqoxxnnzje                                 │
│  Region: eu-central-1                                          │
│                                                                 │
│  Tables: 100+                                                   │
│  Migrations: 55+ Flyway migrations applied                     │
│  Seed: V20260813_1 (admin user + capabilities)                 │
└─────────────────────────────────────────────────────────────────┘
```

## Credentials

| Field | Value |
|---|---|
| Login URL | https://snad-app.vercel.app |
| Email | `admin@snad.ai` |
| Password | `Senen1985` |
| Backend URL | https://sanad-backend-mcrj.onrender.com |
| Supabase Project | `hxhvfqxzigrqoxxnnzje` |

## Workspace Dashboard

The `/workspace` page now shows **3 cards** (duplicate Control Plane removed):

| # | Card (Arabic) | Card (English) | Route | Description |
|---|---|---|---|---|
| 1 | نظام CRM | CRM | `/crm` | Customer relationship management |
| 2 | الإدارة التنفيذية | Executive | `/executive` | Tenant management, subscriptions, billing |
| 3 | صحة النظام | System Health | `/system-health` | Platform monitoring, diagnostics, alerts |

**Note**: `/control-plane` redirects to `/executive` (no separate page).

## Verification Results (2026-08-13)

### Backend
- ✅ Health: UP
- ✅ Login: HTTP 200, 116 capabilities
- ✅ Default destination: `/control-plane` (redirects to `/executive`)
- ✅ Available destinations: `/workspace`, `/crm`, `/crm/command-center`, `/control-plane`

### Executive Module (8 endpoints)
| Endpoint | Status |
|---|---|
| GET /api/v1/executive/dashboard | ✅ 200 |
| GET /api/v1/executive/tenants | ✅ 200 |
| GET /api/v1/executive/tenants/{id} | ✅ 200 |
| GET /api/v1/executive/systems | ✅ 200 |
| GET /api/v1/executive/audit | ✅ 200 |
| GET /api/v1/executive/access-check | ✅ 200 |
| GET /api/v1/executive/plans | ✅ 200 |
| GET /api/v1/executive/subscriptions | ✅ 200 |
| GET /api/v1/executive/billing/invoices | ✅ 200 |

### System Health Module (2 endpoints + actions)
| Endpoint | Status |
|---|---|
| GET /api/v1/system-health | ✅ 200 (HEALTHY, score: 90, risk: LOW) |
| GET /api/v1/system-health/systems | ✅ 200 (4 services) |
| POST /api/v1/system-health/actions | ✅ 200 (RUN_DIAGNOSTICS, AUTO_HEAL, etc.) |

### CRM Module (5 endpoints)
| Endpoint | Status |
|---|---|
| GET /api/v1/crm/accounts | ✅ 200 |
| GET /api/v1/crm/contacts | ✅ 200 |
| GET /api/v1/crm/leads | ✅ 200 |
| GET /api/v1/crm/opportunities | ✅ 200 |
| GET /api/v1/crm/tasks | ✅ 200 |

### Frontend Pages
| Path | Status |
|---|---|
| / | ✅ 200 (login) |
| /workspace | ✅ 200 (3 cards) |
| /crm | ✅ 307 (redirect to /crm/overview) |
| /executive | ✅ 200 |
| /system-health | ✅ 200 |
| /control-plane | ✅ 200 (redirects to /executive) |

## Recovery Procedures

### If Render restarts and data is wiped:

```bash
# Option 1: Apply seed migration only (fast, ~5 seconds)
python3 /home/z/my-project/scripts/apply_all_migrations.py --seed-only

# Option 2: Apply all migrations (comprehensive, ~2 minutes)
python3 /home/z/my-project/scripts/apply_all_migrations.py

# Option 3: Verify state without changes
python3 /home/z/my-project/scripts/apply_all_migrations.py --verify-only
```

### If GitHub PAT expires:

Generate a new PAT at https://github.com/settings/tokens and update:
```bash
cd /home/z/my-project/SNAD
git remote set-url origin "https://x-access-token:<NEW_PAT>@github.com/snadaiapp-png/SNAD.git"
```

### If Supabase Access Token expires:

Generate at https://supabase.com/dashboard/account/tokens and update the token in:
- `/home/z/my-project/scripts/apply_all_migrations.py`
- `/home/z/my-project/scripts/check_db_state.py`

## Key Files

### Migrations
- `apps/sanad-platform/src/main/resources/db/migration/V20260813_1__seed_control_plane_admin_and_capabilities.sql`
  - Seeds: tenant, ADMIN role, admin@snad.ai user, EXECUTIVE/SYSTEM_HEALTH capabilities, system services, SaaS plans

### Scripts (Supabase-managed migration solution)
- `/home/z/my-project/scripts/apply_all_migrations.py` — Main migration applier
- `/home/z/my-project/scripts/apply_migrations_robust.py` — Robust version
- `/home/z/my-project/scripts/check_db_state.py` — State checker

### Backend Code
- `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/LoginDestinationResolver.java`
  - Added: `/system-health`, `/executive` destinations
  - Added: EXECUTIVE_*, SYSTEM_HEALTH_* capability prefixes

### Frontend Code
- `apps/web/app/workspace/page.tsx` — 3 cards (CRM, Executive, System Health)
- `apps/web/app/executive/` — Executive module page
- `apps/web/app/system-health/` — System Health module page
- `apps/web/app/control-plane/page.tsx` — Redirects to `/executive`

## Known Limitations

1. **Render free tier**: Container may suspend after idle, causing data loss on restart
2. **Flyway on Render**: Disabled (`FLYWAY_ENABLED=false`) to prevent OOM crashes
3. **Manual migration**: Run seed migration script after Render restarts

## Future Improvements

1. Upgrade Render to paid tier (prevents suspension)
2. Enable Flyway on Render (after memory upgrade)
3. Add automated health monitoring
4. Implement database backup strategy
