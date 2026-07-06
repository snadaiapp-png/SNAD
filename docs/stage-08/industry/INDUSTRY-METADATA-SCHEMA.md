# SANAD Stage 08 — Industry Metadata Schema

**Document ID:** `SANAD-ST08-IND-SCHEMA-001`
**Track:** 8.4
**Date:** 2026-07-06

---

## 1. Pack Manifest Schema

```yaml
pack:
  id: sanad.industry.retail
  version: 1.0.0
  industry: retail
  locale_support: [ar-SA, en-US]
  permissions:
    - erp:inventory:read
    - erp:inventory:write
    - crm:customer:read
  roles:
    - id: store_manager
      label_ar: مدير متجر
      label_en: Store Manager
      permissions: [erp:inventory:read, erp:inventory:write]
  workflows:
    - id: stock_replenishment
      version: 1.0.0
  forms:
    - id: stock_count
  reports:
    - id: daily_sales
  dashboards:
    - id: store_overview
  kpis:
    - id: inventory_turnover
  ai_skills:
    - id: demand_forecast
  seed_data:
    - file: seed/categories.json
  demo_data:
    - file: demo/sample_store.json
  migrations:
    - up: migrations/001_init.sql
    - down: migrations/001_init_down.sql
```

---

## 2. Validation Rules

* `id` must match `^sanad\.industry\.[a-z_]+$`.
* `version` must be semantic.
* `permissions` must exist in permission catalog.
* `roles` must reference valid permissions.
* `workflows` must reference existing workflow definitions.
* `seed_data` files must exist and pass schema validation.
* `migrations` must have both `up` and `down`.
