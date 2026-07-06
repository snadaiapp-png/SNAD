# SANAD Stage 08 — Marketplace Security Model

**Document ID:** `SANAD-ST08-MKT-SEC-001`
**Track:** 8.3
**Date:** 2026-07-06

---

## 1. Threat Model

* Malicious publisher.
* Compromised publisher account.
* Supply-chain attack (dependency tampering).
* Runtime escape attempt.
* Tenant data exfiltration via app.
* Secret exfiltration via app.

---

## 2. Controls

### 2.1 At Submission

* Manifest schema validation.
* Permission declaration enforced.
* Dependency vulnerability scan (Critical = block).
* SAST scan.
* Malware scan.
* Signed package verification.

### 2.2 At Install

* Tenant admin approval required.
* Permission summary displayed.
* Install audit record.
* Sandbox install for Verified tier.

### 2.3 At Runtime

* Tenant-isolated execution.
* No unrestricted DB access (sandboxed API only).
* No unrestricted secret access (vault-issued scoped tokens only).
* Outbound network allowlist enforced.
* Rate limits per install.
* Audit log per install.

### 2.4 Emergency Response

* Kill switch (revocation propagates within 60s).
* Version rollback.
* Publisher suspension.
* Tenant notification.

---

## 3. Audit

* Every install, update, rollback, uninstall logged.
* Every app API call logged with tenant, install ID, action.
* Logs retained per tenant policy.
