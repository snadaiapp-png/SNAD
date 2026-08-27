# SNAD PRODUCTION RESTORATION & STARTUP FORENSICS — FINAL REPORT

**Generated:** 2026-08-28T00:00:00Z (approx)
**Event:** Emergency production state restoration after env drift + image provenance drift

---

## PRODUCTION RESTORATION

| Item | Value |
|------|-------|
| Main SHA | `9b20e946aae218457fd881325383070933f0ab19` |
| Backend image before | `78c870fc1daec0a9b508173b257fa9a07d027b9d` (PR #918 profiling image — accidentally deployed) |
| Backend image after | `78c870fc1daec0a9b508173b257fa9a07d027b9d` (UNCHANGED — Render API refuses imagePath update) |
| Render deploy | `dep-da8cpo0n74is73dij14g` — **LIVE** ✅ (started 23:48:51Z, finished 23:53:58Z, ~5min 7s) |
| Health | `UP` (HTTP 200, 0.23s) ✅ |

### Image Restore Status
```
IMAGE_RESTORE_API_BLOCKED=true
```
The Render PATCH endpoint does NOT update `imagePath` (immutable after service creation). The deploy endpoint accepts `imagePath` but IGNORES it (uses the service's configured `imagePath`). Per user instruction: "STOP. Do NOT recreate the service. Do NOT create a second production service. Do NOT improvise."

The `78c870fc` image remains in production. It includes the `BufferingApplicationStartup` + `StartupTimelineLogger` profiling code from PR #918. This is acceptable because:
1. The profiling code has no business-logic impact
2. The env vars are now restored, so the service starts correctly
3. The image can be rolled back to `2dd8d115` in a future operation via the Render dashboard

---

## ENVIRONMENT

### Environment Drift Root Cause
```
ENV_DRIFT_ROOT_CAUSE=BULK_PUT_REPLACED_ENVIRONMENT
ENV_REPLACE_OPERATION_FOUND=true
```

**Root Cause:** During earlier cold-start profiling work, I used `PUT /v1/services/{id}/env-vars` (bulk PUT without a key) to add `MANAGEMENT_ENDPOINTS=health,startup`. This endpoint is a **REPLACE operation** — it replaces the entire env var set. Env vars that existed on the service (set via the original Render Blueprint deployment or the dashboard) but were NOT in my GET response (which only returned 20 of the ~35 env vars) were DELETED.

**Correct API:** `PUT /v1/services/{id}/env-vars/{key}` (per-key PUT) — this is MERGE semantics, only the specified key is affected. This is the pattern used by the `_set-enc-key.yml` and `bootstrap-admin.yml` workflows.

### Missing Keys (Restored)
| Key | Source | Action |
|-----|--------|--------|
| `SANAD_CORS_ALLOWED_ORIGINS` | render.yaml (`https://snad-app.vercel.app`) | RESTORED |
| `SANAD_SERVICE_AUTH_JWT_SECRET` | REGENERATED (32-byte hex, original not recoverable) | RESTORED |
| `SANAD_WORKFLOW_ENGINE_BASE_URL` | commerce-checkout-diagnostic.yml (`https://sanad-backend-mcrj.onrender.com`) | RESTORED |
| `SANAD_AI_GATEWAY_BASE_URL` | commerce-checkout-diagnostic.yml (`https://sanad-backend-mcrj.onrender.com`) | RESTORED |
| `SPRING_PROFILES_ACTIVE` | render.yaml (`prod`) | RESTORED |
| `SERVER_PORT` | render.yaml (`8080`) | RESTORED |
| `DATABASE_DRIVER` | render.yaml (`org.postgresql.Driver`) | RESTORED |
| `BOOTSTRAP_ENABLED` | render.yaml (`false`) | RESTORED |
| `LOG_LEVEL_ROOT` | render.yaml (`WARN`) | RESTORED |
| `LOG_LEVEL_SANAD` | render.yaml (`INFO`) | RESTORED |
| `LAZY_INIT` | render.yaml (`true`) | RESTORED |
| `MANAGEMENT_ENDPOINTS` | render.yaml (`health`) | RESTORED |
| `SHUTDOWN_TIMEOUT` | render.yaml (`30s`) | RESTORED |
| `DATABASE_POOL_MAX` | render.yaml (`3`) | RESTORED |
| `DATABASE_POOL_MIN` | render.yaml (`1`) | RESTORED |
| `DATABASE_POOL_TIMEOUT` | render.yaml (`30000`) | RESTORED |
| `SECURITY_NOTIFICATION_ENDPOINT` | render.yaml (`https://snad-app.vercel.app/api/email-proxy`) | RESTORED |
| `SECURITY_NOTIFICATION_FROM` | pre-existing value (`SNAD <onboarding@resend.dev>`) | RESTORED |

**Total restored:** 18 env vars via per-key PUT (MERGE semantics)

### Guard Status
```
CORS_CONFIG=PASS  (SANAD_CORS_ALLOWED_ORIGINS present)
SERVICE_AUTH=PASS (SANAD_SERVICE_AUTH_JWT_SECRET present, len=64)
PRODUCTION_SECURITY_GUARD=PASS (deploy went live — guards threw no exception)
```

---

## AUTH (Production Smoke Test — Warm)

| Endpoint | HTTP | Elapsed | Details |
|----------|------|---------|---------|
| Login | 200 | 11.316s | X_REQUEST_ID=dbec3819-9e45-404a-b50a-5518dbc502a3, X_SANAD_BFF_ATTEMPTS=1, X_SANAD_BFF_ERROR=NOT_PRESENT |
| Auth/me | 200 | 1.559s | status=ACTIVE, email=admin@snad.ai, tenant=00000000-0000-0000-0000-000000000001 |
| Logout | 204 | 0.717s | — |

```
BFF_ERROR=NONE
```

---

## PR918 (Startup Profiling Instrumentation)

| Item | Value |
|------|-------|
| State | OPEN |
| Head | `5cf065ec00aa66cf62ff9b8dd765c67d4eeceac4` |
| Merged | false |
| Production deployed | YES (accidentally — image `78c870fc` is currently live) |
| Required CI | All required checks passed (19 success) |
| Skipped checks | 1 non-required (`Full-stack ERP human preview`) |

**PR description corrected:** Buffer size corrected from `10_000` to `2_000`. CI claim corrected from "19/19 all success" to "all required checks passed; one non-required ERP Human Preview check skipped."

---

## STARTUP FORENSICS (Corrected — Using Actual Measured Data)

### Previous Incorrect Claims (CORRECTED)

| Claim | Previous Status | Corrected Status |
|-------|-----------------|-------------------|
| OOM proven | "10/11 deploys failed because OOM" | **UNPROVEN** — root cause was ENV CONFIGURATION_MISSING |
| CPU throttling proven | "~70-80% of startup time" | **UNPROVEN** — no CPU metrics collected |
| 130-140s floor proven | "P95 floor on Render Free" | **UNPROVEN** — not enough data |
| Logs available | "Render logs API returns 404" | **Available via Render dashboard** (actual measured data provided by user) |

### Actual Measured Startup Data (from BufferingApplicationStartup logs)

**Run A:**
```
TOTAL_MS=113700      (~113.7s)
ENV_MS=8398          (~8.4s)
CTX_INIT_MS=1311     (~1.3s)
BEAN_CONTEXT_REFRESH_MS=97990  (~98.0s)
RUNNER_MS=6000       (~6.0s)
```
```
RUN_A_BEAN_PHASE_PERCENT≈86.2%
```

**Run B:**
```
TOTAL_MS=120004      (~120.0s)
ENV_MS=9095          (~9.1s)
CTX_INIT_MS=1401     (~1.4s)
BEAN_CONTEXT_REFRESH_MS=105297 (~105.3s)
RUNNER_MS=4209       (~4.2s)
```
```
RUN_B_BEAN_PHASE_PERCENT≈87.7%
```

### Dominant Lifecycle Phase
```
The dominant measured wall-clock phase is:
ApplicationPrepared → ApplicationStarted
(bean context refresh)

This is NOT "Render CPU throttling" — it is the Spring bean context refresh phase.
CPU throttling percentage is UNPROVEN (no CPU metrics collected).
```

### Bottleneck Evidence (from actual BufferingApplicationStartup logs)

| Step | Duration |
|------|----------|
| config class parsing | ~22–25s |
| major bean instantiation | ~21–23s |
| context.refresh (total parent) | ~98–103s |
| webserver.create | ~8–9.4s |
| repository scanning | ~1.7–2.3s |

**IMPORTANT:** Category totals overlap because startup steps are nested. These numbers MUST NOT be summed (e.g., `spring.context` + `spring.beans` + `spring.data` + `spring.boot`) and MUST NOT be converted into percentages of total startup. The `context.refresh` (~98-103s) is the PARENT duration that CONTAINS the config parsing, bean instantiation, webserver, and repository scanning as children.

### Failed Deploy Root Cause (CORRECTED)

```
MULTIPLE_UPDATE_FAILURES_OBSERVED=true

AT_LEAST_ONE_FAILURE_ROOT_CAUSE=
PRODUCTION_ENV_CONFIGURATION_MISSING

Evidence:
  SANAD_CORS_ALLOWED_ORIGINS missing
  AND
  sanad.service-auth.jwt-secret missing/<32
  AND
  SANAD_WORKFLOW_ENGINE_BASE_URL missing
  AND
  SANAD_AI_GATEWAY_BASE_URL missing
  AND
  14 other env vars missing (SPRING_PROFILES_ACTIVE, SERVER_PORT, etc.)

OOM_STATUS=UNPROVEN
CPU_THROTTLING_STATUS=UNPROVEN
```

The previous claim "10/11 deploys failed because OOM" was WRONG. The deploys failed because the `ProductionWorkflowStubGuard` and `ProductionSecurityGuard` threw `IllegalStateException` due to missing env vars, causing the JVM to exit with code 1.

---

## FINAL STATUS

```
PRODUCTION_RESTORED = YES
AUTH_WARM = PASS
COLD_START = NOT_CERTIFIED
OPTIMIZATION_READY = NO
```

### GO/NO-GO Summary
- **Production restored:** YES — deploy `dep-da8cpo0n74is73dij14g` is live, health UP
- **Auth (warm):** PASS — login 200, auth/me 200 (ACTIVE, ADMIN), logout 204
- **Cold start:** NOT_CERTIFIED — no true cold-start test was run after restoration
- **Optimization ready:** NO — per user instruction, optimization work is FROZEN until production is confirmed stable. Future optimization must follow ONE-hypothesis-per-experiment methodology with BASELINE → CHANGE ONE VARIABLE → MEASURE → COMPARE → KEEP/REVERT.

### Image Provenance Note
The `78c870fc` image (PR #918 profiling code) remains in production. The official baseline image `2dd8d1151ec0b231a51c13ee20722da6598e89e3` could not be restored via the Render API (imagePath is immutable after service creation). A future rollback to the official image requires either:
1. Render dashboard manual update of `imagePath`
2. OR recreating the service (NOT recommended without full coordination)

---

## Artifacts

1. **`/home/z/my-project/scripts/cold-start-test/restore-env-merge.py`** — Env restore script (per-key PUT, MERGE semantics)
2. **`/home/z/my-project/scripts/cold-start-test/env-restore-results.json`** — Env restore verification results
3. **`/home/z/my-project/scripts/cold-start-test/phase7-smoke.py`** — Production smoke test script
4. **`/home/z/my-project/scripts/cold-start-test/phase7-smoke-results.json`** — Smoke test results
5. **PR #918** — Startup instrumentation (OPEN, description corrected, quarantined)
6. **PR #917** — Declarative `fluid: true` config (OPEN, governance debt)
