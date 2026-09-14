-- HRM-G1 T7 CLOSURE (directive §10 / T7-A26): the engine idempotency index is
-- the last line of defense against duplicate authoritative approvals.
--
-- DEFECT: both HRM G1 T7 Y2 approval adapters start instances with
-- trigger_type NULL. The uq_wf_instances_idempotency index was created under
-- PostgreSQL's default NULLS DISTINCT semantics, so for those rows the index
-- did NOT enforce (tenant_id, trigger_type, workflow_definition_id,
-- idempotency_key): a racing or replayed start carrying the SAME idempotency
-- key but a different business entity could create a SECOND workflow instance
-- — exactly the "same key + different fingerprint" conflict that T7-A26
-- forbids.
--
-- FIX: rebuild the same partial unique index with NULLS NOT DISTINCT. The
-- index definition, its columns, its predicate and its name are preserved —
-- only the NULL-equality semantics are strengthened. No existing row can
-- collide (adapter keys embed a random UUID suffix per started instance), so
-- the rebuild is safe on any populated environment.
--
-- Forward-only: no prior migration bytes are touched.

DROP INDEX IF EXISTS uq_wf_instances_idempotency;

CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_instances_idempotency
    ON workflow_instances (tenant_id, trigger_type, workflow_definition_id, idempotency_key)
    NULLS NOT DISTINCT
    WHERE idempotency_key IS NOT NULL;
