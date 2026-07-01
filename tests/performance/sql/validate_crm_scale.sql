\set ON_ERROR_STOP on

DO $checks$
DECLARE
    missing_extensions text[];
    account_rows bigint;
    contact_rows bigint;
    invalid_contacts bigint;
    partition_count integer;
BEGIN
    SELECT array_agg(required.name)
    INTO missing_extensions
    FROM (
      VALUES
        ('vector'),
        ('pg_stat_statements'),
        ('pg_trgm'),
        ('unaccent'),
        ('btree_gin'),
        ('btree_gist'),
        ('citext'),
        ('pgcrypto')
    ) AS required(name)
    WHERE NOT EXISTS (
      SELECT 1 FROM pg_extension installed WHERE installed.extname = required.name
    );

    IF missing_extensions IS NOT NULL THEN
      RAISE EXCEPTION 'Missing required CRM extensions: %', missing_extensions;
    END IF;

    SELECT count(*) INTO account_rows FROM crm_benchmark.account;
    SELECT count(*) INTO contact_rows FROM crm_benchmark.contact;

    IF account_rows = 0 OR contact_rows = 0 THEN
      RAISE EXCEPTION 'CRM benchmark dataset is empty: accounts %, contacts %', account_rows, contact_rows;
    END IF;

    SELECT count(*)
    INTO invalid_contacts
    FROM crm_benchmark.contact contact
    LEFT JOIN crm_benchmark.account account
      ON account.tenant_id = contact.tenant_id
     AND account.id = contact.account_id
    WHERE account.id IS NULL;

    IF invalid_contacts <> 0 THEN
      RAISE EXCEPTION 'Cross-tenant or orphan contacts detected: %', invalid_contacts;
    END IF;

    SELECT count(*)
    INTO partition_count
    FROM pg_inherits inheritance
    JOIN pg_class parent ON parent.oid = inheritance.inhparent
    JOIN pg_namespace namespace ON namespace.oid = parent.relnamespace
    WHERE namespace.nspname = 'crm_runtime'
      AND parent.relname = 'event_outbox';

    IF partition_count < 2 THEN
      RAISE EXCEPTION 'CRM event outbox partitioning is not active';
    END IF;
END
$checks$;

DO $tenant_scope$
DECLARE
    selected_tenant uuid;
    expected_count bigint;
    scoped_count bigint;
    leaked_count bigint;
BEGIN
    SELECT tenant_id INTO selected_tenant
    FROM crm_benchmark.account
    ORDER BY tenant_id
    LIMIT 1;

    SELECT count(*) INTO expected_count
    FROM crm_benchmark.account
    WHERE tenant_id = selected_tenant;

    EXECUTE format(
      'SELECT count(*) FROM crm_benchmark.account WHERE tenant_id = %L',
      selected_tenant
    ) INTO scoped_count;

    SELECT count(*) INTO leaked_count
    FROM crm_benchmark.account
    WHERE tenant_id <> selected_tenant
      AND id IN (
        SELECT id FROM crm_benchmark.account WHERE tenant_id = selected_tenant
      );

    IF scoped_count <> expected_count THEN
      RAISE EXCEPTION 'Tenant-scoped account query returned % instead of %', scoped_count, expected_count;
    END IF;

    IF leaked_count <> 0 THEN
      RAISE EXCEPTION 'Composite tenant identity collision detected: %', leaked_count;
    END IF;
END
$tenant_scope$;

INSERT INTO crm_runtime.event_outbox (
    id,
    tenant_id,
    aggregate_type,
    aggregate_id,
    event_type,
    payload
)
SELECT
    gen_random_uuid(),
    account.tenant_id,
    'CRM_ACCOUNT',
    account.id,
    'crm.account.benchmark-ready',
    jsonb_build_object('accountId', account.id, 'tenantId', account.tenant_id)
FROM crm_benchmark.account account
ORDER BY account.tenant_id, account.id
LIMIT 10000;

INSERT INTO crm_runtime.search_index_queue (
    id,
    tenant_id,
    entity_type,
    entity_id,
    operation,
    document_version,
    payload
)
SELECT
    gen_random_uuid(),
    account.tenant_id,
    'ACCOUNT',
    account.id,
    'UPSERT',
    1,
    jsonb_build_object('displayName', account.display_name)
FROM crm_benchmark.account account
ORDER BY account.tenant_id, account.id
LIMIT 10000;

INSERT INTO crm_runtime.semantic_document (
    id,
    tenant_id,
    entity_type,
    entity_id,
    content_hash,
    model_id,
    source_version,
    locale,
    content,
    embedding
)
SELECT
    gen_random_uuid(),
    account.tenant_id,
    'ACCOUNT',
    account.id,
    encode(digest(account.display_name, 'sha256'), 'hex'),
    'benchmark-1536',
    1,
    'ar-SA',
    account.display_name,
    ('[' || array_to_string(array_fill(0.001::real, ARRAY[1536]), ',') || ']')::vector
FROM crm_benchmark.account account
ORDER BY account.tenant_id, account.id
LIMIT 100;
ON CONFLICT (tenant_id, entity_type, entity_id, model_id) DO NOTHING;

ANALYZE crm_runtime.event_outbox;
ANALYZE crm_runtime.search_index_queue;
ANALYZE crm_runtime.semantic_document;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, display_name, annual_value
FROM crm_benchmark.account
WHERE tenant_id = (SELECT min(tenant_id) FROM crm_benchmark.account)
  AND lifecycle_status = 'ACTIVE'
ORDER BY updated_at DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT tenant_id, id, display_name
FROM crm_benchmark.account
WHERE tenant_id = (SELECT min(tenant_id) FROM crm_benchmark.account)
  AND display_name ILIKE '%Customer Account%'
LIMIT 25;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, entity_id, content
FROM crm_runtime.semantic_document
WHERE tenant_id = (SELECT min(tenant_id) FROM crm_runtime.semantic_document)
ORDER BY embedding <=> ('[' || array_to_string(array_fill(0.001::real, ARRAY[1536]), ',') || ']')::vector
LIMIT 10;

SELECT
    (SELECT count(*) FROM crm_benchmark.account) AS account_count,
    (SELECT count(*) FROM crm_benchmark.contact) AS contact_count,
    (SELECT count(*) FROM crm_runtime.event_outbox) AS outbox_count,
    (SELECT count(*) FROM crm_runtime.search_index_queue) AS search_queue_count,
    (SELECT count(*) FROM crm_runtime.semantic_document) AS semantic_document_count;
