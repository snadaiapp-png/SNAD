\set ON_ERROR_STOP on
\if :{?tenant_count}
\else
  \set tenant_count 1000
\endif
\if :{?accounts_per_tenant}
\else
  \set accounts_per_tenant 100
\endif
\if :{?contacts_per_account}
\else
  \set contacts_per_account 2
\endif

CREATE SCHEMA IF NOT EXISTS crm_benchmark;

DROP TABLE IF EXISTS crm_benchmark.contact CASCADE;
DROP TABLE IF EXISTS crm_benchmark.account CASCADE;

CREATE TABLE crm_benchmark.account (
    tenant_id        uuid NOT NULL,
    id               uuid NOT NULL,
    display_name     text NOT NULL,
    normalized_name  text NOT NULL,
    lifecycle_status varchar(24) NOT NULL,
    owner_user_id    uuid,
    annual_value     numeric(19,2) NOT NULL,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE crm_benchmark.contact (
    tenant_id        uuid NOT NULL,
    id               uuid NOT NULL,
    account_id       uuid NOT NULL,
    display_name     text NOT NULL,
    email            citext,
    phone_e164       varchar(32),
    lifecycle_status varchar(24) NOT NULL,
    created_at       timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_benchmark_contact_account
      FOREIGN KEY (tenant_id, account_id)
      REFERENCES crm_benchmark.account (tenant_id, id)
      ON DELETE CASCADE
);

INSERT INTO crm_runtime.tenant_capacity (
    tenant_id,
    shard_bucket,
    placement_region,
    service_tier,
    record_quota,
    storage_quota_bytes
)
SELECT
    gen_random_uuid(),
    ((series.tenant_number - 1) % 128)::smallint,
    CASE (series.tenant_number % 4)
      WHEN 0 THEN 'sa-central-1'
      WHEN 1 THEN 'eu-central-1'
      WHEN 2 THEN 'us-east-1'
      ELSE 'ap-southeast-1'
    END,
    CASE
      WHEN series.tenant_number % 100 = 0 THEN 'DEDICATED'
      WHEN series.tenant_number % 20 = 0 THEN 'ENTERPRISE'
      WHEN series.tenant_number % 5 = 0 THEN 'GROWTH'
      ELSE 'STANDARD'
    END,
    5000000,
    107374182400
FROM generate_series(1, :tenant_count) AS series(tenant_number)
ON CONFLICT (tenant_id) DO NOTHING;

CREATE TEMP TABLE benchmark_tenant AS
SELECT tenant_id, row_number() OVER (ORDER BY tenant_id) AS tenant_number
FROM crm_runtime.tenant_capacity
ORDER BY tenant_id
LIMIT :tenant_count;

INSERT INTO crm_benchmark.account (
    tenant_id,
    id,
    display_name,
    normalized_name,
    lifecycle_status,
    owner_user_id,
    annual_value,
    created_at,
    updated_at
)
SELECT
    tenant.tenant_id,
    gen_random_uuid(),
    CASE
      WHEN series.account_number % 3 = 0 THEN 'شركة العميل ' || tenant.tenant_number || '-' || series.account_number
      ELSE 'Customer Account ' || tenant.tenant_number || '-' || series.account_number
    END,
    lower(
      CASE
        WHEN series.account_number % 3 = 0 THEN 'شركة العميل ' || tenant.tenant_number || '-' || series.account_number
        ELSE 'Customer Account ' || tenant.tenant_number || '-' || series.account_number
      END
    ),
    CASE WHEN series.account_number % 97 = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END,
    gen_random_uuid(),
    ((tenant.tenant_number * 1000 + series.account_number) % 1000000)::numeric(19,2),
    now() - make_interval(days => (series.account_number % 365)::integer),
    now() - make_interval(mins => (series.account_number % 1440)::integer)
FROM benchmark_tenant tenant
CROSS JOIN generate_series(1, :accounts_per_tenant) AS series(account_number);

INSERT INTO crm_benchmark.contact (
    tenant_id,
    id,
    account_id,
    display_name,
    email,
    phone_e164,
    lifecycle_status,
    created_at
)
SELECT
    account.tenant_id,
    gen_random_uuid(),
    account.id,
    'Contact ' || series.contact_number || ' - ' || account.display_name,
    ('contact-' || series.contact_number || '-' || replace(account.id::text, '-', '') || '@example.test')::citext,
    '+9665' || lpad(((abs(hashtext(account.id::text)::bigint) + series.contact_number) % 100000000)::text, 8, '0'),
    'ACTIVE',
    account.created_at
FROM crm_benchmark.account account
CROSS JOIN generate_series(1, :contacts_per_account) AS series(contact_number);

CREATE INDEX idx_benchmark_account_tenant_status
    ON crm_benchmark.account (tenant_id, lifecycle_status, updated_at DESC);
CREATE INDEX idx_benchmark_account_tenant_name
    ON crm_benchmark.account (tenant_id, normalized_name, id);
CREATE INDEX idx_benchmark_account_name_trgm
    ON crm_benchmark.account USING gin (tenant_id, display_name gin_trgm_ops);
CREATE INDEX idx_benchmark_contact_tenant_account
    ON crm_benchmark.contact (tenant_id, account_id, created_at DESC);
CREATE INDEX idx_benchmark_contact_email
    ON crm_benchmark.contact (tenant_id, email)
    WHERE email IS NOT NULL;
CREATE INDEX idx_benchmark_contact_name_trgm
    ON crm_benchmark.contact USING gin (tenant_id, display_name gin_trgm_ops);

ANALYZE crm_runtime.tenant_capacity;
ANALYZE crm_benchmark.account;
ANALYZE crm_benchmark.contact;

SELECT
    :tenant_count::bigint AS requested_tenants,
    count(*) AS loaded_tenants
FROM benchmark_tenant;

SELECT
    count(*) AS account_count,
    pg_size_pretty(pg_total_relation_size('crm_benchmark.account')) AS account_storage
FROM crm_benchmark.account;

SELECT
    count(*) AS contact_count,
    pg_size_pretty(pg_total_relation_size('crm_benchmark.contact')) AS contact_storage
FROM crm_benchmark.contact;
