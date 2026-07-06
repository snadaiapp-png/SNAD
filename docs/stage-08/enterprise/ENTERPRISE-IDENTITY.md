# SANAD Stage 08 — Enterprise Identity

**Document ID:** `SANAD-ST08-ENT-ID-001`
**Track:** 8.6
**Date:** 2026-07-06

---

## 1. SSO Integration

* SAML 2.0 with metadata exchange.
* OIDC with discovery document.
* Automatic user attribute mapping.
* Group claim → role mapping.

---

## 2. SCIM 2.0 Endpoints

* `/scim/v2/Users` (CRUD).
* `/scim/v2/Groups` (CRUD).
* Pagination per SCIM spec.
* Filtering per SCIM spec.

---

## 3. Session Policies

| Policy               | Default        | Enterprise configurable |
|----------------------|----------------|-------------------------|
| Idle timeout         | 30 min         | 5–60 min                |
| Absolute timeout     | 12 hours       | 1–24 hours              |
| IP allowlist         | disabled       | enabled                 |
| Device fingerprint   | disabled       | enabled                 |
| Concurrent sessions  | unlimited      | 1–5                     |

---

## 4. User Lifecycle

* Provisioned via SCIM or SSO JIT.
* Suspended on IdP signal.
* Deprovisioned on IdP signal + retention period.
* Audit of all lifecycle events.
