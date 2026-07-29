# CRM-009 Risk Register

> **Module:** CRM-009 — Workflow Engine & AI Gateway Integration
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Risk Overview

| Metric | Value |
|--------|-------|
| Total Risks | 12 |
| Critical | 1 |
| High | 3 |
| Medium | 4 |
| Low | 4 |

---

## 2. Critical Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-01 | Callback security breach | LOW | CRITICAL | HMAC + JWT + replay protection | Agent 2 |

---

## 3. High Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-02 | Workflow engine unavailable | LOW | HIGH | Fail-closed design, retry logic | Agent 2 |
| R-03 | AI gateway unavailable | LOW | HIGH | Fail-closed design, graceful degradation | Agent 2 |
| R-04 | Outbox event loss | LOW | HIGH | Durable outbox with recovery | Agent 3 |

---

## 4. Medium Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-05 | Concurrent modification | MEDIUM | MEDIUM | Optimistic locking, If-Match | Agent 3 |
| R-06 | JWT secret compromise | LOW | MEDIUM | Minimum 32 bytes, rotation | Agent 2 |
| R-07 | Outbox worker failure | LOW | MEDIUM | Exponential backoff, dead letter | Agent 3 |
| R-08 | Callback replay attack | LOW | MEDIUM | JTI + nonce, durable store | Agent 2 |

---

## 5. Low Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-09 | Configuration error | MEDIUM | LOW | Production guard, validation | Agent 7 |
| R-10 | Test coverage gap | LOW | LOW | Mandatory test suite | Agent 6 |
| R-11 | Documentation gap | LOW | LOW | Automated generation | Agent 8 |
| R-12 | Performance degradation | LOW | LOW | Timeout configuration | Agent 7 |

---

## 6. Risk Assessment Matrix

|  | Low Impact | Medium Impact | High Impact | Critical Impact |
---|------------|---------------|-------------|-----------------|
| **High Probability** | R-09 | — | — | — |
| **Medium Probability** | — | R-05 | — | — |
| **Low Probability** | R-10, R-11, R-12 | R-06, R-07, R-08 | R-02, R-03, R-04 | R-01 |

---

## 7. Risk Mitigation Strategies

### 7.1 Fail-Closed Design

All external integrations (Workflow Engine, AI Gateway) use fail-closed design:
- Missing configuration → UNAVAILABLE status
- Expired envelopes → EXPIRED status
- Transport failures → UNAVAILABLE status
- Authentication failures → UNAVAILABLE status

### 7.2 Transactional Outbox

All external dispatches use transactional outbox:
- Request + outbox event created atomically
- CTE-based atomic claim with FOR UPDATE SKIP LOCKED
- Claim token ownership verification
- Exponential backoff retry
- Dead letter for permanent failures

### 7.3 Callback Security

All callbacks use dual protection:
- Signed service JWT validation
- HMAC body signature verification
- JTI + nonce replay protection
- Durable replay store

### 7.4 Production Guard

Production startup guard enforces:
- Real adapters (not stubs)
- HTTPS endpoints
- Service-auth configuration
- Minimum JWT secret length

---

## 8. Risk Monitoring

| Metric | Threshold | Action |
|--------|-----------|--------|
| Callback replay attempts | > 10/hour | Alert security team |
| Outbox dead letters | > 5/hour | Alert operations |
| Workflow failures | > 10% | Alert engineering |
| AI failures | > 10% | Alert engineering |
| JWT validation failures | > 20/hour | Alert security team |

---

**Risk Register Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
