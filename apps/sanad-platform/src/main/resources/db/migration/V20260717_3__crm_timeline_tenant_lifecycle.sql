-- CRM timeline rows are tenant-owned operational history. When a tenant is
-- physically removed by controlled administration or isolated integration
-- tests, its timeline must not orphan or block the tenant lifecycle.
ALTER TABLE crm_timeline_events
    DROP CONSTRAINT fk_crm_timeline_events_tenant;

ALTER TABLE crm_timeline_events
    ADD CONSTRAINT fk_crm_timeline_events_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE;
