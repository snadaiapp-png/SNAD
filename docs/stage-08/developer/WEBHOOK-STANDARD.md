# SANAD Stage 08 — Webhook Standard

**Document ID:** `SANAD-ST08-DEV-WEBHOOK-001`
**Track:** 8.8
**Date:** 2026-07-06

---

## 1. Payload Schema

```json
{
  "event_id": "evt_abc123",
  "event_type": "invoice.created",
  "tenant_id": "tnt_xyz",
  "timestamp": "2026-07-06T12:34:56Z",
  "signature": "t=1625571296,v1=abc123...",
  "data": { ... }
}
```

---

## 2. Signing

* HMAC-SHA256 with tenant-specific secret.
* Header: `X-SANAD-Signature: t=<timestamp>,v1=<hmac>`.
* Timestamp window: 5 minutes.

---

## 3. Replay Protection

* Server stores `event_id` for 24 hours.
* Duplicate `event_id` within window: rejected.

---

## 4. Retry Policy

* 5 retries: 1m, 5m, 30m, 2h, 12h.
* After 5 failures: dead-letter queue + alert.

---

## 5. Verification Endpoint

* `POST /webhooks/{subscription_id}/verify` returns recent events.
* Used by integrators to reconcile.
