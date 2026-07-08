# Stage 14 — Data Growth Planning

**Date**: 2026-07-08

---

## Data Growth Projections

### Per-Tenant Data Estimates

```
Per user:
  - Profile data: ~1KB
  - Preferences: ~0.5KB
  - Session data: ~0.5KB
  Total per user: ~2KB

Per tenant (10 users average):
  - User data: ~20KB
  - Organization data: ~5KB
  - CRM contacts (100): ~100KB
  - CRM deals (50): ~50KB
  - CRM activities (500): ~250KB
  - Audit log (1 year): ~500KB
  Total per tenant: ~925KB ≈ 1MB
```

### Growth Projection (12 months)

```
Month 1-3 (Pilot, 5 tenants):
  Data size: ~5MB
  Monthly growth: ~1MB
  Database size: < 100MB (including indexes)

Month 4-6 (Soft Launch, 30 tenants):
  Data size: ~30MB
  Monthly growth: ~5MB
  Database size: ~500MB

Month 7-12 (Full Launch, 200 tenants):
  Data size: ~200MB
  Monthly growth: ~20MB
  Database size: ~2GB

Year 2 (500 tenants):
  Data size: ~500MB
  Database size: ~5GB
```

## Data Retention Policy

```
User data: Retained while account is active
  - On deletion: Hard delete after 30 days (grace period)

Audit log: 1 year
  - After 1 year: Archive to cold storage

CRM data: Retained while tenant is active
  - On tenant cancellation: Hard delete after 90 days

Session data: 24 hours (refresh token lifetime)
  - Expired sessions: Automatically cleaned up

Telemetry/Analytics: 90 days
  - After 90 days: Aggregated, raw data deleted
```

## Storage Planning

### Database Storage

```
Current: H2 (in-memory) / PostgreSQL (production)
Provisioned: TBD (based on Render plan)
Recommended:
  - Pilot: 1GB PostgreSQL
  - Soft Launch: 10GB PostgreSQL
  - Full Launch: 50GB PostgreSQL + read replica
  - Enterprise: 100GB+ PostgreSQL + replicas + archiving
```

### File Storage

```
Current: No file upload functionality
Planned: Vercel Blob or AWS S3 (when file upload added)
Estimate: 100MB per tenant (documents, attachments)
```

### Log Storage

```
Current: GitHub Actions logs (90-day retention)
Vercel logs: Retained per Vercel plan
Recommended: Centralized logging (ELK or Datadog) for production
```

## Data Archival Strategy

```
Archive candidates:
  - Audit logs older than 1 year
  - Deleted user data (after grace period)
  - Inactive tenant data (after cancellation grace period)

Archive storage:
  - Cold storage (AWS S3 Glacier or equivalent)
  - Compressed format (gzip)
  - Index for legal/compliance retrieval

Archive schedule:
  - Monthly: Move audit logs > 1 year to archive
  - Monthly: Purge expired session data
  - Quarterly: Review inactive tenants for archival
```

## Data Growth Readiness

```
Growth projections: DOCUMENTED ✅
Retention policy: DEFINED ✅
Storage planning: DOCUMENTED ✅
Archival strategy: DEFINED ✅
Database capacity: SUFFICIENT for pilot ✅
File storage: NOT YET NEEDED (no file upload) ✅
Log storage: ACTIVE (GitHub + Vercel) ✅

Data Growth Planning: COMPLETE
  → Pilot (5 tenants): < 100MB — NO CONCERN
  → Soft Launch (30 tenants): ~500MB — PLAN FOR 1GB
  → Full Launch (200 tenants): ~2GB — PLAN FOR 5GB+ replica
```
