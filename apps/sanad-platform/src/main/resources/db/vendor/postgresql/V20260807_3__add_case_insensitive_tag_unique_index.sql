-- B-07: Case-insensitive unique constraint on tag names per tenant.
-- Prevents "VIP" and "vip" from coexisting for the same tenant.
-- Uses a partial unique index on LOWER(name) alongside the existing
-- case-sensitive UNIQUE constraint.
CREATE UNIQUE INDEX IF NOT EXISTS uk_crm_tags_tenant_name_ci
    ON crm_tags (tenant_id, LOWER(name));
