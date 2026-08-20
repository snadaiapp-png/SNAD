# OPERATOR ACTION REQUIRED — SNAD Production Recovery

**DATE**: 2026-08-11
**STATUS**: BLOCKED — 6 environment variables missing
**RENDER SERVICE**: srv-d8ragqkm0tmc73bviqq0 (sanad-backend)
**BACKEND URL**: https://sanad-backend-mcrj.onrender.com

---

## COMPLETED ACTIONS

| Action | Status |
|--------|--------|
| Bootstrap variables removed (SECURITY FIX) | ✅ DONE |
| Known values restored from render.yaml (16 vars) | ✅ DONE |
| JWT_SECRET generated (new, 48 bytes) | ✅ DONE |
| SANAD_SERVICE_AUTH_JWT_SECRET generated (new, 48 bytes) | ✅ DONE |

**Current env var count**: 20

---

## MISSING VARIABLES — OPERATOR MUST PROVIDE

### 1. DATABASE_URL (CRITICAL — app crashes without this)

**REQUIRED BY**: `ProductionDatasourceGuard.java` line 59
**RENDER KEY**: `DATABASE_URL`
**SOURCE**: GitHub Secret `SPRING_DATASOURCE_URL` OR Render Dashboard
**FORMAT**: `jdbc:postgresql://<host>:5432/<database>?sslmode=require`
**ACTION**: 
1. Go to GitHub Repo → Settings → Secrets and variables → Actions
2. Find `SPRING_DATASOURCE_URL`
3. Copy the value
4. Set in Render Dashboard → Environment → Add: `DATABASE_URL` = `<value>`

### 2. DATABASE_USERNAME (CRITICAL — app crashes without this)

**REQUIRED BY**: `application-prod.yml` → `sanad.database.username`
**RENDER KEY**: `DATABASE_USERNAME`
**SOURCE**: GitHub Secret `SPRING_DATASOURCE_USERNAME` OR Render Dashboard
**ACTION**:
1. Find `SPRING_DATASOURCE_USERNAME` in GitHub Secrets
2. Set in Render Dashboard → Environment → Add: `DATABASE_USERNAME` = `<value>`

### 3. DATABASE_PASSWORD (CRITICAL — app crashes without this)

**REQUIRED BY**: `application-prod.yml` → `sanad.database.password`
**RENDER KEY**: `DATABASE_PASSWORD`
**SOURCE**: GitHub Secret `SPRING_DATASOURCE_PASSWORD` OR Render Dashboard
**ACTION**:
1. Find `SPRING_DATASOURCE_PASSWORD` in GitHub Secrets
2. Set in Render Dashboard → Environment → Add: `DATABASE_PASSWORD` = `<value>`

### 4. CRM_CUSTOM_FIELD_ENCRYPTION_KEY (CRITICAL — app crashes without this)

**REQUIRED BY**: `ProductionSecurityGuard.java` line 74, `CrmEncryptionKeyValidator.java` line 49
**RENDER KEY**: `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`
**SOURCE**: Render Dashboard audit log OR generate new
**FORMAT**: Base64-encoded AES-128/192/256 key (16/24/32 bytes decoded)
**ACTION**:
1. Check Render Dashboard → Environment → History for original value
2. If found: use original value
3. If NOT found: generate new key with:
   ```
   openssl rand -base64 32
   ```
4. Set in Render Dashboard → Environment → Add: `CRM_CUSTOM_FIELD_ENCRYPTION_KEY` = `<value>`
5. ⚠️ **WARNING**: If you generate a new key, any previously encrypted CRM custom field values will be UNREADABLE. If no CRM data exists yet, this is safe.

### 5. SANAD_WORKFLOW_ENGINE_BASE_URL (CRITICAL — app crashes without this)

**REQUIRED BY**: `ProductionWorkflowStubGuard.java` line 86
**RENDER KEY**: `SANAD_WORKFLOW_ENGINE_BASE_URL`
**FORMAT**: `https://<hostname>` (must be HTTPS, non-localhost)
**SOURCE**: Render Dashboard audit log OR deployment documentation
**ACTION**:
1. Check Render Dashboard → Environment → History for original value
2. If not found, check deployment docs or ask the team
3. Set in Render Dashboard → Environment → Add: `SANAD_WORKFLOW_ENGINE_BASE_URL` = `<value>`

### 6. SANAD_AI_GATEWAY_BASE_URL (CRITICAL — app crashes without this)

**REQUIRED BY**: `ProductionWorkflowStubGuard.java` line 90
**RENDER KEY**: `SANAD_AI_GATEWAY_BASE_URL`
**FORMAT**: `https://<hostname>` (must be HTTPS, non-localhost)
**SOURCE**: Render Dashboard audit log OR deployment documentation
**ACTION**:
1. Check Render Dashboard → Environment → History for original value
2. If not found, check deployment docs or ask the team
3. Set in Render Dashboard → Environment → Add: `SANAD_AI_GATEWAY_BASE_URL` = `<value>`

---

## HOW TO SET VARIABLES IN RENDER DASHBOARD

1. Go to https://dashboard.render.com/web/srv-d8ragqkm0tmc73bviqq0
2. Click **Environment** in the left sidebar
3. For each variable:
   - Click **Add Environment Variable**
   - Enter the **Key** (e.g., `DATABASE_URL`)
   - Enter the **Value** (the actual credential)
   - Click **Save**
4. After ALL 6 variables are set, click **Manual Deploy** → **Deploy latest commit**

---

## VERIFICATION AFTER OPERATOR ACTION

After setting all 6 variables and triggering deploy, the backend should:
1. Pass `ProductionDatasourceGuard` (DATABASE_URL present)
2. Pass `ProductionSecurityGuard` (CRM key present)
3. Pass `ProductionWorkflowStubGuard` (workflow + AI URLs present)
4. Start successfully
5. Return `{"status":"UP"}` from `/actuator/health`

---

## SECURITY NOTES

- **DO NOT** paste secret values into this chat
- **DO NOT** commit secrets to Git
- **DO NOT** use credentials from Git history (they are COMPROMISED)
- **DO NOT** use the old Render API key for anything other than verified operations
- **BOOTSTRAP_ENABLED** is now set to `false` (secure)
- **All bootstrap credentials have been removed** from Render env vars

---

## EMERGENCY CONTACTS

If you cannot find the original values:
1. Check Render Dashboard → Environment → **History** tab for audit trail
2. Check GitHub Settings → Secrets for `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
3. Contact the team member who originally configured the Render service

---

**NEXT STEP**: After operator sets all 6 variables and triggers deploy, report back with:
- Deploy status (success/failure)
- `/actuator/health` response

Then I will proceed with:
- Phase 12: Startup verification
- Phase 13-14: Database verification
- Phase 15: Authentication verification
- Phase 16: Control-plane bootstrap (cp-admin)
- Phase 17-21: Final certification
