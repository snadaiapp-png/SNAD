# SANAD Stage 08 — Integration Certification

**Document ID:** `SANAD-ST08-DEV-CERT-001`
**Track:** 8.8
**Date:** 2026-07-06

---

## 1. Certification Levels

| Level       | Requirements                                            |
|-------------|---------------------------------------------------------|
| Self-Service| Manifest valid; sandbox tests pass                      |
| Certified   | + Manual review; security review; production test       |
| Verified    | + Penetration test; supply-chain provenance             |

---

## 2. Required Tests

* OAuth flow works.
* Webhook delivery verified.
* Error handling for 4xx and 5xx.
* Rate limit handling (respects Retry-After).
* Idempotency keys used for writes.
* Pagination handled.
* Sandbox tenant clean.
