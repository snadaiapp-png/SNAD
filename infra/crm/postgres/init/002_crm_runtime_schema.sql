\set ON_ERROR_STOP on

CREATE SCHEMA IF NOT EXISTS crm_runtime;

CREATE TABLE IF NOT EXISTS crm_runtime.tenant_capacity (
    tenant_id           uuid PRIMARY KEY,
    shard_bucket        smallint NOT NULL CHECK (shard_bucket BETWEEN 0 AND 127),
    placement_region    varchar(32) NOT NULL DEFAULT 'local',
    service_tier        varchar(24) NOT NULL DEFAULT 'STANDARD',
    lifecycle_status    varchar(24) NOT NULL DEFAULT 'ACTIVE',
    record_quota        bigint NOT NULL DEFAULT 1000000 CHECK (record_quota > 0),
    storage_quota_bytes bigint NOT NULL DEFAULT 10737418240 CHECK (storage_quota_bytes > 0),
    search_enabled      boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tenant_capacity_tier
        CHECK (service_tier IN ('STANDARD', 'GROWTH', 'ENTERPRISE', 'DEDICATED')),
    CONSTRAINT ck_tenant_capacity_status
        CHECK (lifecycle_status IN ('ACTIVE', 'SUSPENDED', 'MIGRATING', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_capacity_bucket
    ON crm_runtime.tenant_capacity (shard_bucket, lifecycle_status);
CREATE INDEX IF NOT EXISTS idx_tenant_capacity_region
    ON crm_runtime.tenant_capacity (placement_region, lifecycle_status);

CREATE TABLE IF NOT EXISTS crm_runtime.event_outbox (
    id               uuid NOT NULL,
    tenant_id        uuid NOT NULL,
    aggregate_type   varchar(80) NOT NULL,
    aggregate_id     uuid NOT NULL,
    event_type       varchar(160) NOT NULL,
    event_version    integer NOT NULL DEFAULT 1,
    trace_id         varchar(80),
    payload          jsonb NOT NULL,
    headers          jsonb NOT NULL DEFAULT '{}'::jsonb,
    delivery_status  varchar(24) NOT NULL DEFAULT 'PENDING',
    available_at     timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    delivered_at     timestamptz,
    attempt_count    integer NOT NULL DEFAULT 0,
    last_error       text,
    PRIMARY KEY (id, created_at),
    CONSTRAINT ck_event_outbox_status
        CHECK (delivery_status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED', 'DEAD_LETTER'))
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS crm_runtime.event_outbox_default
    PARTITION OF crm_runtime.event_outbox DEFAULT;

CREATE INDEX IF NOT EXISTS idx_event_outbox_dispatch
    ON crm_runtime.event_outbox (delivery_status, available_at, created_at)
    WHERE delivery_status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_event_outbox_tenant
    ON crm_runtime.event_outbox (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_outbox_aggregate
    ON crm_runtime.event_outbox (tenant_id, aggregate_type, aggregate_id, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_runtime.search_index_queue (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    entity_type      varchar(80) NOT NULL,
    entity_id        uuid NOT NULL,
    operation        varchar(16) NOT NULL,
    document_version bigint NOT NULL DEFAULT 0,
    payload          jsonb,
    status           varchar(24) NOT NULL DEFAULT 'PENDING',
    available_at     timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    completed_at     timestamptz,
    attempt_count    integer NOT NULL DEFAULT 0,
    last_error       text,
    CONSTRAINT ck_search_queue_operation CHECK (operation IN ('UPSERT', 'DELETE')),
    CONSTRAINT ck_search_queue_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_search_queue_entity_pending
    ON crm_runtime.search_index_queue (tenant_id, entity_type, entity_id)
    WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_search_queue_dispatch
    ON crm_runtime.search_index_queue (status, available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE IF NOT EXISTS crm_runtime.attachment_registry (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    entity_type      varchar(80) NOT NULL,
    entity_id        uuid NOT NULL,
    storage_provider varchar(24) NOT NULL,
    storage_key      varchar(1024) NOT NULL,
    original_name    varchar(512) NOT NULL,
    media_type       varchar(255) NOT NULL,
    size_bytes       bigint NOT NULL CHECK (size_bytes >= 0),
    checksum_sha256  char(64) NOT NULL,
    scan_status      varchar(24) NOT NULL DEFAULT 'PENDING',
    retention_class  varchar(32) NOT NULL DEFAULT 'STANDARD',
    created_by       uuid NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    CONSTRAINT uq_attachment_storage_key UNIQUE (tenant_id, storage_key),
    CONSTRAINT ck_attachment_scan_status
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'QUARANTINED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_attachment_entity
    ON crm_runtime.attachment_registry (tenant_id, entity_type, entity_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_attachment_scan
    ON crm_runtime.attachment_registry (scan_status, created_at)
    WHERE scan_status IN ('PENDING', 'QUARANTINED');

CREATE TABLE IF NOT EXISTS crm_runtime.semantic_document (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    entity_type     varchar(80) NOT NULL,
    entity_id       uuid NOT NULL,
    content_hash    char(64) NOT NULL,
    model_id        varchar(160) NOT NULL,
    source_version  bigint NOT NULL,
    locale          varchar(35),
    content         text NOT NULL,
    embedding       vector(1536),
    metadata        jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_semantic_document_source
        UNIQUE (tenant_id, entity_type, entity_id, model_id)
);

CREATE INDEX IF NOT EXISTS idx_semantic_document_entity
    ON crm_runtime.semantic_document (tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_semantic_document_metadata
    ON crm_runtime.semantic_document USING gin (metadata jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_semantic_document_embedding_hnsw
    ON crm_runtime.semantic_document USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128)
    WHERE embedding IS NOT NULL;

DO $partition_setup$
DECLARE
    month_start date;
    month_end date;
    partition_name text;
BEGIN
    FOR offset_month IN -1..3 LOOP
        month_start := (date_trunc('month', current_date) + make_interval(months => offset_month))::date;
        month_end := (month_start + interval '1 month')::date;
        partition_name := 'event_outbox_' || to_char(month_start, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS crm_runtime.%I PARTITION OF crm_runtime.event_outbox FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            month_start,
            month_end
        );
    END LOOP;
END
$partition_setup$;

COMMENT ON SCHEMA crm_runtime IS 'Operational CRM scale primitives; PostgreSQL remains the source of truth';
COMMENT ON TABLE crm_runtime.event_outbox IS 'Transactional integration event staging with monthly partitions';
COMMENT ON TABLE crm_runtime.search_index_queue IS 'Idempotent asynchronous OpenSearch indexing queue';
COMMENT ON TABLE crm_runtime.semantic_document IS 'Tenant-scoped semantic retrieval materialization';
