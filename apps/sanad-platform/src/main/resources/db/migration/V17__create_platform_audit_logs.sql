CREATE TABLE platform_audit_logs (
    id UUID NOT NULL,
    actor_tenant_id UUID,
    actor_user_id UUID,
    target_tenant_id UUID,
    action VARCHAR(150) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    reason VARCHAR(500),
    before_state TEXT,
    after_state TEXT,
    result VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    correlation_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_platform_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_platform_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX idx_platform_audit_created ON platform_audit_logs(created_at);
CREATE INDEX idx_platform_audit_target_tenant ON platform_audit_logs(target_tenant_id, created_at);
CREATE INDEX idx_platform_audit_actor ON platform_audit_logs(actor_user_id, created_at);
