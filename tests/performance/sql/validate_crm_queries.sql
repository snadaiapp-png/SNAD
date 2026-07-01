EXPLAIN (ANALYZE, BUFFERS)
SELECT id, display_name, annual_value
FROM crm_benchmark.account
WHERE tenant_id = (
    SELECT tenant_id FROM crm_benchmark.account ORDER BY tenant_id LIMIT 1
)
  AND lifecycle_status = 'ACTIVE'
ORDER BY updated_at DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT tenant_id, id, display_name
FROM crm_benchmark.account
WHERE tenant_id = (
    SELECT tenant_id FROM crm_benchmark.account ORDER BY tenant_id LIMIT 1
)
  AND display_name ILIKE '%Customer Account%'
LIMIT 25;

EXPLAIN (ANALYZE, BUFFERS)
SELECT document.id, document.entity_id, document.content
FROM crm_runtime.semantic_document document
WHERE document.tenant_id = (
    SELECT tenant_id FROM crm_runtime.semantic_document ORDER BY tenant_id LIMIT 1
)
ORDER BY document.embedding <=> (
    SELECT embedding FROM crm_runtime.semantic_document ORDER BY tenant_id, id LIMIT 1
)
LIMIT 10;

SELECT
    (SELECT count(*) FROM crm_benchmark.account) AS account_count,
    (SELECT count(*) FROM crm_benchmark.contact) AS contact_count,
    (SELECT count(*) FROM crm_runtime.event_outbox) AS outbox_count,
    (SELECT count(*) FROM crm_runtime.search_index_queue) AS search_queue_count,
    (SELECT count(*) FROM crm_runtime.semantic_document) AS semantic_document_count;
