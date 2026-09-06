# SNAD SCP R0C-11 — Product Catalog Runtime Design (Frozen)

- **Date:** 2026-09-06
- **Mission:** R0C11_PRODUCT_CATALOG_RUNTIME_CLOSURE
- **Base SHA:** `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` (`docs(scp): certify and close R0C-10 multiplicity runtime`)
- **Branch:** `scp/r0c-11-product-catalog-runtime` (created from the exact R0C-10 final SHA; main is read-only and is NOT the base)
- **Predecessors:** `docs/superpowers/specs/2026-08-29-subscription-control-plane-design.md`,
  `docs/superpowers/plans/2026-08-29-subscription-control-plane-implementation.md`,
  `docs/superpowers/plans/2026-09-06-scp-r0c-10-final-certification.md`
- **Scope ruling:** no newer explicit R0C-11 source exists anywhere in the repository
  (searched all docs for `R0C-11`, `product catalog`, `catalog runtime`,
  `executive/products`); the only mentions are the R0C-10 design §"Release gate"
  (R0C-11 deferred) and the R0C-10 certification §12 (`R0C11=FORBIDDEN` at R0C-10
  time). This file freezes the scope from the R0C-11 run directive.

---

## 1. Forensic Facts (verified at Base SHA — preserve, do not rewrite)

| Fact | Evidence |
|---|---|
| `products` table EXISTS with exact contract | `V20260829_2__scp_products_and_plan_versions.sql` — `id UUID PK`, `code VARCHAR(50) UNIQUE (uk_products_code)`, `name VARCHAR(200)`, `description`, `application_id UUID NULL FK→applications`, `product_type CHECK IN (APPLICATION, ADD_ON, METERED, OTHER) DEFAULT 'OTHER'`, `status CHECK IN (ACTIVE, INACTIVE, ARCHIVED) DEFAULT 'ACTIVE'`, `created_at/updated_at` |
| Indexes exist | `idx_products_application`, `idx_products_status` |
| No physical delete is possible through FK graph | `prices.product_id` FK ON DELETE CASCADE, `product_entitlements.product_id` FK ON DELETE CASCADE, `subscription_items.product_id` FK (NO ACTION) — but no DELETE method exists or will exist in the runtime |
| No seed rows for `products` | catalog is data entered through the API (applications are seeded from `modules`; products are not seeded) |
| `products` is platform-scoped, NO RLS | consistent with the SCP design §11 ("catalog tables are platform-scoped like `saas_plans`"); `V20260816_6` RLS touches only `commerce_*` tables |
| PRODUCT_CATALOG_RUNTIME = **ABSENT** | no `ProductEntity`, no `ProductRepository`, no `ProductCatalogService` anywhere under `subscription/**`; the only runtime consumer is `SubscriptionItemService.itemName()` (raw `SELECT name FROM products`) |
| PRODUCT_PUBLIC_API = **PARTIAL** | `PriceController` exposes `GET/POST /api/v1/executive/products/{productId}/prices`; the catalog endpoints `GET/POST/PUT /api/v1/executive/products` are MISSING (the original SCP-G1 §3 line-item: `/api/v1/executive/products GET`) |
| Pricing integration EXISTS | `PriceService.createForProduct` validates product FK fail-closed (`requireExists("products", …)`), `PriceRepository.findByProduct` |
| Entitlement integration EXISTS | `ItemEntitlementRepository` joins `subscription_items → product_entitlements` on `product_id`; `ItemAwareEntitlementResolver` merges over the plan-derived context; `UsageMeteringService` joins `product_entitlements` for limit kinds |
| Product-backed item gap | `SubscriptionItemService.addItem` accepts `productId` and only reads its `name` for the snapshot; it does NOT verify the product exists, is ACTIVE, or is type-compatible with the item type |
| Commerce module is a DIFFERENT domain | `commerce/application/ProductService` operates on `commerce_products` (tenant+store scoped, `V20260816_5`) — out of R0C-11 scope, untouched |
| RBAC capability codes already seeded | `V20260830_2` seeds `catalog.read` / `catalog.manage`; `ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES` lists both; no endpoint currently consumes them |
| Audit conventions | `PlatformAuditService.success(authentication, targetTenantId, action, resourceType, resourceId, reason, beforeState, afterState)`; existing catalog actions: `APPLICATION_CREATE`, `APPLICATION_UPDATE` |
| `PRODUCT_REFERENCE_UNKNOWN = 0` | every product reference at Base SHA is enumerated in this table and §5; no unclassified reference exists |

## 2. Frozen Scope

```
R0C11_SCOPE=PRODUCT_CATALOG_RUNTIME_CLOSURE
```

Target: the existing `products` storage gains a complete domain/service, a complete
API, RBAC, audit, subscription-item integrity, pricing integration, entitlement
integration, PostgreSQL Direct acceptance, full regression, certification.

**The Executive UI is NOT rebuilt.** No `apps/web` change is in scope.

## 3. Frozen Semantics (non-negotiable)

1. **Product is platform catalog data** — like `applications`, not tenant data.
   No RLS is added, none is weakened.
2. **Product types (existing CHECK, unchanged):** `APPLICATION`, `ADD_ON`,
   `METERED`, `OTHER`.
3. **Product statuses (existing CHECK, unchanged):** `ACTIVE`, `INACTIVE`,
   `ARCHIVED`.
4. **No physical DELETE** — no `DELETE` method on the domain/service/repository,
   no `DELETE` endpoint, no status value `DELETED`. Removal from sale is
   expressed as `INACTIVE` (not sellable) or `ARCHIVED` (fully retired).
   The existing FK `ON DELETE CASCADE` clauses are legacy storage facts and are
   never exercised by the runtime.
5. **Product code is stable identity.** Codes are normalized
   (`trim` + uppercase) before insert and before uniqueness checks, must match
   `^[A-Za-z0-9_-]+$`, and never change after creation (update must not mutate
   `code`). Uniqueness is enforced fail-closed in the domain (unique violation
   maps to a deterministic domain error, not a raw SQL exception) with
   `uk_products_code` as the final DB guard.
6. **Historical references remain valid after INACTIVE/ARCHIVED.** Existing
   subscription items keep their `product_id`; the name snapshot already lives
   on the item. No runtime path may cancel, delete, or "fix" historical items
   when a product later becomes inactive or archived.
7. **Do not duplicate pricing** — product pricing stays in `prices` via
   `prices.product_id` (`PriceService.createForProduct` unchanged).
8. **Do not duplicate the entitlement engine** — item-derived entitlements stay
   in `product_entitlements` + `ItemAwareEntitlementResolver` (unchanged).
9. **No subscription multiplicity redesign** — R0C-10 semantics (MODEL_B,
   effective 0..1, history 0..N, single canonical status writer, feature gate
   default OFF) are preserved byte-for-byte.
10. **No R0C-10 semantic change** — no file under the R0C-10 acceptance suites'
    production contracts may change behavior.

## 4. Target Runtime (all additive, under `subscription/**`)

```
subscription/catalog/
  ProductEntity            — id, code, name, description, applicationId,
                             productType, status, createdAt, updatedAt
  ProductRepository        — JdbcTemplate repository (ApplicationRepository
                             conventions): findById, findByCode, findAll,
                             findAvailable, existsByCode, insert, update
  ProductCatalogService    — create, update, findById, findByCode, findAll,
                             findAvailable (NO delete)
subscription/api/
  CatalogController        — additive endpoints (same controller as
                             applications):
    GET    /api/v1/executive/products            → catalog.read
    GET    /api/v1/executive/products/{id}       → catalog.read
    POST   /api/v1/executive/products            → catalog.manage
    PUT    /api/v1/executive/products/{id}       → catalog.manage
    (NO DELETE — structurally absent)
  ScpDtos                  — additive ProductRequest / ProductResponse
subscription/item/
  SubscriptionItemService  — product-backed item integrity (see §6)
```

## 5. Validation Rules (domain, fail-closed)

| Rule | Behavior |
|---|---|
| Code normalization | `trim().toUpperCase(Locale.ROOT)`; `^[A-Z0-9_-]{1,50}$` after normalization (DB allows 50 chars) |
| Name required | `@NotBlank` at the DTO boundary AND explicit domain check |
| Product type | must be one of the four existing values (domain whitelist mirrors the CHECK) |
| Status | must be one of the three existing values; default `ACTIVE` on create; validated on update |
| Unique code | `existsByCode` pre-check → deterministic `IllegalStateException("Product code already exists: …")`; DB unique index is the backstop (race-safe: a concurrent insert surfaces as the same deterministic error via the repository mapping) |
| Unknown application FK | `application_id != null` requires an existing `applications` row, else deterministic `IllegalArgumentException("Unknown application: …")` (mirrors `PriceService.requireExists` conventions) |
| Nullable application | `application_id = null` is valid (standalone products) |
| No delete | method simply does not exist; attempt is a compile error |

## 6. Subscription Item Integrity (R0C-5/R0C-10 preserved)

For **NEW product-backed items** (ADD_ON / METERED items carrying `productId`;
`OTHER` items may carry a product of any type; PLAN items are plan-anchored and
do not use `productId`):

1. The product must exist → else deterministic `IllegalArgumentException("Unknown product: …")`.
2. `ADD_ON` item → the referenced product must be type `ADD_ON` and status
   `ACTIVE` (compatible active product).
3. `METERED` item → the referenced product must be type `METERED` and status
   `ACTIVE`.
4. `OTHER` item → product must exist; type compatibility is not constrained
   beyond existence.
5. PLAN anchor semantics are preserved exactly: PLAN items ignore `productId`
   for validation, `tenant_subscriptions.plan_id` stays the compatibility
   anchor, R0C-10 effective/history cardinalities are untouched.
6. When a product later becomes `INACTIVE`/`ARCHIVED`: no existing item is
   cancelled or mutated (proven by test), and `nameSnapshot` already recorded
   stays.

## 7. RBAC (granular, additive)

- `GET` endpoints: `@RequireCapability("catalog.read")` + `ControlPlaneAccessGuard.require`.
- `POST/PUT` endpoints: `@RequireCapability("catalog.manage")` + `ControlPlaneAccessGuard.require`.
- Existing `EXECUTIVE_*` usages elsewhere remain untouched (no weakening, no
  role privilege expansion). `catalog.read`/`catalog.manage` are already seeded
  (`V20260830_2`) and granted per the same migration's grant statements; this
  effort only starts consuming them.

## 8. Audit

- Successful `POST /api/v1/executive/products` → `PRODUCT_CREATE`,
  `resourceType="product"`, `resourceId=<UUID>`.
- Successful `PUT /api/v1/executive/products/{id}` → `PRODUCT_UPDATE`,
  `resourceType="product"`, `resourceId=<UUID>`, with before/after states.
- Rejected mutations (validation failure, duplicate code, unknown FK,
  authorization denial) write **no success audit**. Denials surface through the
  existing deny-by-default authorization machinery.

## 9. Migrations

```
R0C11_NEW_MIGRATIONS=0
```

The storage contract is complete and correct at Base SHA. A migration may only
be created if a RED DB proof establishes a real schema defect. None is known.

## 10. Acceptance (PostgreSQL Direct, no Docker, no Testcontainers, no H2)

Host-native PostgreSQL 16.2 (`127.0.0.1:5433`, pgserver distribution cluster,
disposable databases, least-privilege role `sanad`: no SUPERUSER, no CREATEDB,
no CREATEROLE, no BYPASSRLS). Acceptance class:
`ProductCatalogRuntimePostgresTest` covering the full §12 battery of the run
directive (schema, types, statuses, uniqueness incl. concurrent collision,
CRUD, available-only filtering, nullable/valid/invalid application, stable code,
archive-without-delete, price FK, entitlement FK, ADD_ON/METERED attachment,
unknown-product rejection, inactive/archived new-sale rejection, historical
reference preservation, audit, RBAC read/manage, unauthorized denial, guard,
no physical delete, R0C-10 effective-subscription regression).

## 11. Out of Scope (frozen)

- Executive UI rebuild (no `apps/web` change).
- `commerce_products` / commerce storefront domain.
- Entitlement engine or pricing engine rewrites.
- R0C-10 semantics, `post-merge-verification.yml`, CI timeout, legal review,
  production, deployment, merge.
