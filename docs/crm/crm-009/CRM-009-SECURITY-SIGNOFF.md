# CRM-009 Security Signoff

> **Agent:** Agent 4 — Security Signoff Auditor
> **Command:** CRM-009-CLOSURE-SPRINT
> **Date:** 2026-07-29
> **Status:** PASS

---

## 1. Executive Summary

| Metric | Value | Status |
|--------|-------|--------|
| Authentication Mechanisms | 2 (JWT, HMAC) | ✅ VERIFIED |
| Authorization Mechanisms | 1 (RBAC via @RequireCapability) | ✅ VERIFIED |
| Replay Protection | Database-backed atomic | ✅ VERIFIED |
| Fail-Closed Design | Production guard + adapter fallback | ✅ VERIFIED |
| Secret Management | Externalized, minimum 32 bytes | ✅ VERIFIED |
| CSRF/CORS | Stateless API, no credentials | ✅ VERIFIED |
| Security Test Coverage | 3 test classes | ✅ VERIFIED |
| **OVERALL VERDICT** | | **PASS** |

---

## 2. Authentication Mechanisms

### 2.1 Service-to-Service JWT (ServiceJwtProvider)

| Attribute | Value | Status |
|-----------|-------|--------|
| Algorithm | HMAC-SHA256 | ✅ |
| Minimum Secret Length | 32 bytes (enforced at startup) | ✅ |
| TTL | 15-300 seconds (clamped) | ✅ |
| Claims | iss, sub, jti, iat, exp, aud, service_name, tenant_id, correlation_id, contract_version | ✅ |
| Validation | Signature, issuer, expiration, audience, service_name | ✅ |
| Unconfigured Fallback | Random 32-byte key (dev only) | ✅ |

### 2.2 HMAC Body Signature (WorkflowCallbackSecurity)

| Attribute | Value | Status |
|-----------|-------|--------|
| Algorithm | HMAC-SHA256 | ✅ |
| Inputs | timestamp.nonce.sha256Hex(body) | ✅ |
| Timestamp Window | 30-900 seconds (configurable) | ✅ |
| Constant-Time Comparison | MessageDigest.isEqual() | ✅ |
| Minimum Secret Length | 32 bytes (enforced) | ✅ |

### 2.3 Dual Authentication on Callback

| Layer | Mechanism | Status |
|-------|-----------|--------|
| Layer 1 | JWT validation via Authorization header | ✅ |
| Layer 2 | HMAC signature via X-SNAD-Signature header | ✅ |
| Layer 3 | Tenant/correlation/contract binding | ✅ |
| Layer 4 | Replay protection (JTI + nonce) | ✅ |

---

## 3. Authorization Mechanisms

### 3.1 RBAC via @RequireCapability

| Controller | Endpoint | Capability | Status |
|------------|----------|------------|--------|
| CrmWorkflowController | POST /api/v2/crm/integrations/workflows | CRM.WORKFLOW.EXECUTE | ✅ |
| CrmWorkflowController | GET /api/v2/crm/integrations/workflows/{id} | CRM.WORKFLOW.EXECUTE | ✅ |
| CrmWorkflowController | POST /api/v2/crm/integrations/workflows/{id}/cancel | CRM.WORKFLOW.EXECUTE | ✅ |
| CrmIntegrationController | POST /api/v2/crm/integrations/ai | CRM.AI.READ | ✅ |
| CrmIntegrationController | GET /api/v2/crm/integrations/{id} | CRM.AI.READ | ✅ |
| CrmIntegrationController | POST /api/v2/crm/integrations/{id}/confirm | CRM.AI.CONFIRM | ✅ |
| CrmIntegrationController | POST /api/v2/crm/integrations/{id}/reject | CRM.AI.CONFIRM | ✅ |

### 3.2 Internal Endpoint (No @RequireCapability)

| Endpoint | Auth Mechanism | Status |
|----------|---------------|--------|
| POST /internal/crm/integrations/workflows/callback | Service JWT + HMAC (programmatic) | ✅ INTENTIONAL |

---

## 4. Replay Protection

| Attribute | Value | Status |
|-----------|-------|--------|
| Storage | PostgreSQL (durable) | ✅ |
| Uniqueness | service_callback_replay_jti_uq, service_callback_replay_nonce_uq | ✅ |
| Detection | INSERT with DuplicateKeyException catch | ✅ |
| Cleanup | Cron schedule (every hour at minute 17) | ✅ |
| Test Coverage | WorkflowCallbackSecurityPostgresTest (5 tests) | ✅ |

---

## 5. Fail-Closed Design

### 5.1 ProductionWorkflowStubGuard

| Check | Failure Action | Status |
|-------|---------------|--------|
| Stub adapter active | Refuse startup | ✅ |
| Real adapter not bound | Refuse startup | ✅ |
| HttpWorkflowIntegrationAdapter not bound | Refuse startup | ✅ |
| HttpAiGatewayAdapter not bound | Refuse startup | ✅ |
| JWT secret < 32 chars | Refuse startup | ✅ |
| Workflow Engine URL blank | Refuse startup | ✅ |
| AI Gateway URL blank | Refuse startup | ✅ |
| Non-HTTPS URL | Refuse startup | ✅ |
| Local/test host URL | Refuse startup | ✅ |

### 5.2 Adapter Fail-Closed Behavior

| Condition | Response | Status |
|-----------|----------|--------|
| baseUrl blank | UNAVAILABLE | ✅ |
| JWT not configured | UNAVAILABLE | ✅ |
| HTTP 401 | UNAUTHORIZED | ✅ |
| HTTP 403 | REJECTED/POLICY_DENIED | ✅ |
| Timeout | TIMED_OUT | ✅ |
| Other exception | UNAVAILABLE | ✅ |
| Envelope expired | Rejected before outbound call | ✅ |

---

## 6. Secret Management

| Secret | Property | Minimum | Enforcement | Status |
|--------|----------|---------|-------------|--------|
| JWT signing key | sanad.service-auth.jwt-secret | 32 bytes | ServiceJwtProvider + ProductionWorkflowStubGuard | ✅ |
| HMAC callback secret | sanad.service-auth.jwt-secret | 32 bytes | WorkflowCallbackSecurity | ✅ |

---

## 7. CSRF/CORS Configuration

| Attribute | Value | Status |
|-----------|-------|--------|
| CSRF | Disabled (stateless API) | ✅ APPROPRIATE |
| CORS Origins | Exact matching, no wildcards | ✅ |
| CORS Credentials | allowCredentials(false) | ✅ |
| CORS Paths | /api/** only | ✅ |
| Internal Endpoints | Not subject to CORS | ✅ |
| Session Management | STATELESS | ✅ |

---

## 8. Security Test Coverage

| Test Class | Tests | Coverage | Status |
|-----------|-------|----------|--------|
| ServiceJwtProviderTest | 4 | Mint/validate round-trip, wrong audience, tampered token, short secret | ✅ |
| WorkflowCallbackSecurityPostgresTest | 5 | Valid consumption, replay rejection, tamper detection, tenant binding, timestamp skew | ✅ |
| IntegrationContractsTest | 4 | Envelope expiry, mutation permissions, output suppression, confirmation requirement | ✅ |

---

## 9. Findings

### 9.1 PASS Findings

| # | Finding | Evidence |
|---|---------|----------|
| F-01 | Dual authentication (JWT + HMAC) on callback | WorkflowCallbackSecurity |
| F-02 | Database-backed replay protection with atomic constraints | CallbackReplayStore |
| F-03 | Fail-closed production guard with 9 startup checks | ProductionWorkflowStubGuard |
| F-04 | All adapters return explicit status codes on failure | HttpWorkflowIntegrationAdapter, HttpAiGatewayAdapter |
| F-05 | Constant-time signature comparison | MessageDigest.isEqual() |
| F-06 | Minimum 32-byte secret enforcement | ServiceJwtProvider, WorkflowCallbackSecurity |
| F-07 | RBAC enforced on all public endpoints | @RequireCapability |
| F-08 | Stateless session management | SessionCreationPolicy.STATELESS |
| F-09 | CORS with exact origin matching, no credentials | CorsProperties |
| F-10 | Envelope expiry checked before outbound calls | IntegrationEnvelope.isExpired() |

### 9.2 Advisory Findings

| # | Finding | Impact | Recommendation |
|---|---------|--------|----------------|
| A-01 | JWT and HMAC share same secret property | LOW | Acceptable for service-to-service context |
| A-02 | Replay cleanup is time-based, not event-driven | LOW | Security unaffected; only storage grows |
| A-03 | Constant-time comparison includes toLowerCase() | NEGLIGIBLE | No exploitable side channel |

---

## 10. Audit Verdict

| Metric | Result |
|--------|--------|
| Authentication | VERIFIED |
| Authorization | VERIFIED |
| Replay Protection | VERIFIED |
| Fail-Closed Design | VERIFIED |
| Secret Management | VERIFIED |
| CSRF/CORS | VERIFIED |
| Security Tests | ADEQUATE |
| **OVERALL VERDICT** | **PASS** |

---

**Security Signoff Auditor:** Program Governance Coordinator
**Date:** 2026-07-29
**Status:** ✅ PASS
