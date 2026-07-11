-- Create subscription_change_events table (was missing from database)
CREATE TABLE IF NOT EXISTS subscription_change_events (
    id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action VARCHAR(120) NOT NULL,
    old_plan_id UUID,
    new_plan_id UUID,
    effective_mode VARCHAR(40) NOT NULL,
    adjustment_minor BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(500),
    actor_tenant_id UUID,
    actor_user_id UUID,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_change_events PRIMARY KEY (id),
    CONSTRAINT fk_sub_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX IF NOT EXISTS idx_subscription_events_sub ON subscription_change_events (tenant_id, subscription_id, created_at DESC);
