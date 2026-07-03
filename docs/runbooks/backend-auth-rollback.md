# Backend Authentication Rollback Runbook

**Service**: SANAD Platform — Backend Authentication
**Owner**: Abdulrhman Senen
**Last Updated**: 2026-06-24

---

## 1. Purpose

This runbook provides the procedure for rolling back the SANAD backend authentication service to a previous known-good deployment when a critical regression is detected in production.

---

## 2. Scope

- Covers Render-hosted backend deployment rollback
- Covers database migration compatibility verification
- Does NOT cover destructive database operations (`flyway clean` is forbidden)
- Does NOT cover frontend rollback (see Vercel dashboard)

---

## 3. Prerequisites

- Access to the Render dashboard or Render API key
- Access to the GitHub repository
- Knowledge of the current and target deployment SHAs
- Confirmation that no destructive Flyway migration was included in the failing deployment

---

## 4. Rollback Procedure

### 4.1 Identify Current and Target Revisions

```bash
# Current deployed SHA (from Render API)
RENDER_API_KEY=<key>
RENDER_SERVICE_ID=<id>
CURRENT_SHA=$(curl -s -H "Accept: application/json" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys?limit=1" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['commitId'])")

# Target: previous known-good SHA
TARGET_SHA=<previous-good-sha>
```

### 4.2 Verify Database Migration Compatibility

Before rolling back, verify that no additive Flyway migration in the current deployment would conflict with the target revision:

1. Check the Flyway schema history on the production database (read-only query).
2. Verify that all migration versions present in the target revision are a subset of the current migration versions.
3. Additive migrations (V1–V15) are forward-compatible — rolling back the application code is safe.
4. If a migration was DROPPED or RENAMED in the current deployment, do NOT rollback without database consultation.

### 4.3 Trigger Rollback Deploy

**Option A: Via Render Dashboard**
1. Navigate to the SANAD backend service in Render.
2. Click "Manual Deploy" → "Deploy a specific commit".
3. Enter the target SHA.
4. Wait for the deploy to complete and health check to pass.

**Option B: Via Render API**
```bash
curl -s -X POST \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys" \
  -d "{\"commitId\": \"$TARGET_SHA\", \"clearCache\": \"do_not_clear\"}"
```

### 4.4 Post-Rollback Verification

1. **Health check**: `curl -s $BASE_URL/actuator/health` — must return `{"status":"UP"}`
2. **Login smoke**: POST `/api/v1/auth/login` with test credentials — must return HTTP 200
3. **Tenant isolation**: Verify cross-tenant login rejection still works
4. **Refresh rotation**: Verify token refresh still rotates tokens
5. **Logout revocation**: Verify post-logout refresh is rejected

### 4.5 Redeploy Current Revision (Recovery)

After the rollback root cause is fixed:

```bash
# Deploy the fixed current revision
curl -s -X POST \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys" \
  -d "{\"commitId\": \"$FIXED_SHA\", \"clearCache\": \"do_not_clear\"}"
```

Verify health check passes after recovery deployment.

---

## 5. Decision Authority

| Action | Approver |
|--------|----------|
| Rollback to previous SHA | Abdulrhman Senen |
| Redeploy current SHA | Abdulrhman Senen |
| Database intervention | Abdulrhman Senen |

---

## 6. Rollback Drill Record

| Field | Value |
|-------|-------|
| Current revision | To be recorded during drill |
| Rollback target | To be recorded during drill |
| Migration compatibility | To be verified during drill |
| Health after rollback | To be verified during drill |
| Health after recovery | To be verified during drill |
| Duration | To be recorded during drill |
| Rollback decision owner | Abdulrhman Senen |

---

## 7. Emergency Contacts

- **Project Owner**: Abdulrhman Senen
- **Render Status**: https://status.render.com/
- **GitHub Actions**: https://github.com/snadaiapp-png/SNAD/actions
