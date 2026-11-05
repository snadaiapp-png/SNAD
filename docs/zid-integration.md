# Zid ↔ SANAD Integration — Setup, Operations & Troubleshooting

> Branch: `feature/zid-integration` · Status: **implementation complete, sandbox verification pending**
> All Zid-facing behavior follows the **official documentation at docs.zid.sa** (accessed 2026-10-28).
> Anything that could not be verified from official sources is marked **UNVERIFIED** below and is
> implemented fail-closed (disabled until confirmed). Nothing was invented.

---

## 1. What this integration does

| Capability | Behavior | Source of truth |
|---|---|---|
| Store linking | OAuth authorization-code flow, one store ⇄ one tenant (DB-enforced) | Verified: docs.zid.sa/authorization |
| Token lifecycle | Encrypted at rest (AES-256-GCM, tenant+purpose-bound AAD), atomic refresh, proactive refresh before expiry | Verified |
| Products sync | Pull `GET /v1/products/` (scope `products.read`), idempotent mirror | Verified |
| Inventory sync | Derived from verified per-product `stocks[]` — **pull-only, no write-back** | Verified |
| Orders sync | Pull `GET /v1/managers/store/orders` (scope `orders.read`), minimal PII, **no financial actions** | Verified |
| Customers | Schema + blind-indexed upsert ready; **pull disabled** (endpoint path UNVERIFIED) | UNVERIFIED |
| Webhooks | HMAC-SHA256 raw-body verification, fail-closed (503/401/422/409), idempotent inbox, background processor with bounded retries | Mechanism verified-by-pattern; header/format UNVERIFIED |
| Reconciliation | Periodic drift check re-pulling verified APIs vs mirror | Verified endpoints only |
| Admin UI | `/integrations/zid` page in SANAD web (Arabic RTL first), capability-gated | — |

## 2. Required environment variables (provision via the approved secret store)

| Variable | Purpose | Secret? |
|---|---|---|
| `SANAD_ZID_ENABLED` | Master switch (`true` to activate; default `false` = everything 503s/inert) | No |
| `SANAD_ZID_CLIENT_ID` | Zid app client id | No |
| `SANAD_ZID_CLIENT_SECRET` | Zid app client secret | **Yes** |
| `SANAD_ZID_REDIRECT_URL` | Absolute callback URL (must EXACTLY match the partner-dashboard allowlist) | No |
| `SANAD_ZID_WEBHOOK_SECRET` | Webhook HMAC shared secret (webhook endpoint stays 503 without it) | **Yes** |
| `SANAD_ZID_OAUTH_BASE_URL` | Default `https://oauth.zid.sa` (verified) | No |
| `SANAD_ZID_API_BASE_URL` | Default `https://api.zid.sa/v1` (verified) | No |
| `SANAD_ZID_AUTHORIZE_PATH` | **UNVERIFIED** default `/oauth2/authorize` — confirm from partner dashboard | No |
| `SANAD_ZID_TOKEN_PATH` | **UNVERIFIED** default `/oauth2/token` — confirm from partner dashboard | No |
| `SANAD_ZID_WEBHOOK_SIGNATURE_HEADER` | **UNVERIFIED** default `X-Zid-Signature` — confirm actual header name | No |
| `SANAD_ZID_LINK_REDIRECT_URL` | SPA route receiving the callback outcome (default `/integrations/zid`) | No |

Never place real values in `.env` files, tests, or CI logs. Test and production credentials are separate.

## 3. Before production enablement (operator checklist)

1. **Confirm the three UNVERIFIED values** from the Zid partner dashboard: authorize path, token path,
   webhook signature header/format. Update env vars (no code change needed).
2. **Register the webhook** pointing to `https://<your-domain>/api/v1/integrations/zid/webhook`
   (webhook registration is done from the Zid partner dashboard; the programmatic webhook-management
   API paths were not verifiable and were therefore not implemented).
3. **Confirm granted scopes** cover `products.read` and `orders.read` (verified scope names from the
   endpoint docs).
4. Provision secrets, set `SANAD_ZID_ENABLED=true`, redeploy.
5. Grant `ZID_INTEGRATION_VIEW` / `ZID_INTEGRATION_MANAGE` capabilities to the operator roles via the
   access management UI (they are seeded but deliberately NOT auto-granted).
6. Run one sandbox store through: link → products sync → orders sync → unlink.

## 4. Data model (all tables FORCE RLS, fail-closed)

`zid_store_connections`, `zid_link_states`, `zid_products`, `zid_product_stocks`, `zid_orders`,
`zid_customers`, `zid_webhook_events`, `zid_sync_audit`, `zid_reconciliation_reports`
— migration `V20261022_1__zid_integration_schema.sql` (forward-only; no prior migration touched).

Security invariants:
- Tenant isolation comes exclusively from the JWT→GUC (`app.tenant_id`)→FORCE RLS chain. The webhook
  path resolves the tenant ONLY from the stored store binding, never from payload data.
- `UNIQUE(zid_store_id)` on connections makes one-store-two-tenants impossible.
- Token columns contain AES-GCM ciphertext only; OAuth link states are stored as SHA-256 hashes.
- Customer contact data is stored as tenant-bound HMAC blind indexes, never plaintext.
- Audit/error text passes a redaction filter (JWT-shaped and long opaque tokens → `[REDACTED]`).

## 5. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| All admin endpoints return 503 `NOT_CONFIGURED` | `SANAD_ZID_ENABLED=false` or client id/secret missing | Provision env vars, redeploy |
| Webhook returns 503 `ZID_WEBHOOK_NOT_CONFIGURED` | `SANAD_ZID_WEBHOOK_SECRET` empty | Provision the secret |
| Webhook returns 401 | Signature header missing or HMAC mismatch | Confirm header name/format against partner dashboard; verify secret value |
| Webhook returns 422 | Store not bound (unknown store id) | Complete the link flow first |
| Webhook returns 409 `REPLAY_MISMATCH` | Same event id with different payload | Investigate the sender; event NOT processed |
| Connection shows `REAUTH_REQUIRED` | Zid rejected stored credentials (401) or refresh failed | Operator re-links the store |
| Sync returns 0 items but connection healthy | Page-size/pagination or scope issue | Check `zid_sync_audit.error_summary`; verify scopes in partner dashboard |
| Duplicate-store error at link time | Store already bound to another tenant | Intentional protection; unbind first or use the correct tenant |

## 6. Known limitations (documented, deliberate)

- **No inventory write-back** to Zid (no documented+authorized write API used anywhere).
- **No order status changes, refunds, captures or cancellations** from SANAD — mirror observes only.
- **Customer pull disabled** until the endpoint path is verified (`snad.zid.customers-path` stays unset).
- **Webhook event-type names treated as opaque**: order/product-family events trigger verified re-pulls;
  unknown families are stored but not acted upon (no guessing).
- Exact Zid rate-limit numbers were not verifiable; the connector honors `Retry-After` on 429 and the
  scheduler keeps page counts bounded (`snad.zid.max-pages-per-sync`).

## 7. Test coverage map

| Suite | What it proves |
|---|---|
| `ZidWebhookVerifierTest` | Valid/prefixed/base64 signatures, tamper/wrong-secret/missing-header rejection, unconfigured fail-closed |
| `ZidWebhookIngestServiceTest` | Decision matrix incl. nothing-persisted-on-reject, GUC from stored binding, duplicate vs replay |
| `ZidAuthServiceTest` | Verified token-response parsing, encryption of all three tokens, atomic refresh, fail-closed paths, secret-free errors |
| `ZidSyncAuditRedactionTest` | Redaction of JWT/opaque/base64 tokens |
| `ZidWebhookProcessorTest` | Routing, bounded backoff, FAILED_MANUAL, REAUTH_REQUIRED short-circuit |
| `ZidTenantBindingServiceTest` | State minting, expiry/replay/cross-tenant rejection, duplicate-store prevention, verified identity binding |
| `ZidConnectorHttpStubTest` | Wire-level header families actually sent, authorize URL secret-free, 401/429(Retry-After)/5xx classification |
| `ZidTenantIsolationPostgresTest` | Real-Flyway RLS matrix: same-tenant visible / cross-tenant invisible / unset-GUC fail-closed / WITH CHECK rejection / store-rebinding prevention / FORCE RLS on all tables (skips gracefully without PG; wired to CI in `zid-integration-tests.yml`) |
