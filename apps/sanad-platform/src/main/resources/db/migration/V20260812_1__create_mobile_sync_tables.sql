-- G7: Create mobile sync metadata tables
-- Requirements: DATA-001 (Sync Tables), DATA-005 (Conflict Log)
-- Date: 2026-08-12

-- ============================================================
-- 1. mobile_device_registry
-- Tracks registered mobile devices per tenant/user
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_device_registry (
    device_id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    device_name VARCHAR(255) NOT NULL,
    device_platform VARCHAR(20) NOT NULL CHECK (device_platform IN ('ios', 'android')),
    device_version VARCHAR(50),
    app_version VARCHAR(50),
    push_token VARCHAR(512),
    last_sync_at TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_device_registry_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_device_registry_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_device_registry_tenant_user ON mobile_device_registry(tenant_id, user_id);
CREATE INDEX idx_device_registry_active ON mobile_device_registry(tenant_id, is_active) WHERE is_active = TRUE;

-- ============================================================
-- 2. mobile_sync_cursor
-- Stores per-device, per-entity-type sync cursors
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_sync_cursor (
    cursor_id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    device_id UUID NOT NULL REFERENCES mobile_device_registry(device_id),
    entity_type VARCHAR(80) NOT NULL,
    cursor_value TEXT NOT NULL,
    cursor_hash VARCHAR(64) NOT NULL,
    entity_count INTEGER NOT NULL DEFAULT 0,
    last_sync_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_sync_cursor_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sync_cursor_device FOREIGN KEY (device_id) REFERENCES mobile_device_registry(device_id),
    CONSTRAINT uq_sync_cursor_device_entity UNIQUE (tenant_id, device_id, entity_type)
);

CREATE INDEX idx_sync_cursor_tenant_device ON mobile_sync_cursor(tenant_id, device_id);
CREATE INDEX idx_sync_cursor_entity_type ON mobile_sync_cursor(tenant_id, entity_type);

-- ============================================================
-- 3. mobile_sync_log
-- Audit trail for all sync operations
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_sync_log (
    sync_id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    device_id UUID NOT NULL REFERENCES mobile_device_registry(device_id),
    user_id UUID NOT NULL REFERENCES users(id),
    sync_type VARCHAR(20) NOT NULL CHECK (sync_type IN ('PULL', 'PUSH', 'FULL_RESYNC')),
    entity_type VARCHAR(80),
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    entities_synced INTEGER NOT NULL DEFAULT 0,
    conflicts_detected INTEGER NOT NULL DEFAULT 0,
    conflicts_resolved INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'PARTIAL')),
    error_message TEXT,
    duration_ms INTEGER,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_sync_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sync_log_device FOREIGN KEY (device_id) REFERENCES mobile_device_registry(device_id),
    CONSTRAINT fk_sync_log_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_sync_log_tenant_device ON mobile_sync_log(tenant_id, device_id);
CREATE INDEX idx_sync_log_tenant_status ON mobile_sync_log(tenant_id, status, started_at);
CREATE INDEX idx_sync_log_started_at ON mobile_sync_log(started_at);

-- ============================================================
-- 4. mobile_conflict_log
-- Stores all detected conflicts with full before/after payloads
-- Retention: 1 year (configurable via sanad.conflict.retention-days)
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_conflict_log (
    conflict_id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    device_id UUID NOT NULL REFERENCES mobile_device_registry(device_id),
    user_id UUID NOT NULL REFERENCES users(id),
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    base_version BIGINT NOT NULL,
    client_mutation JSONB NOT NULL,
    server_version BIGINT NOT NULL,
    server_state JSONB NOT NULL,
    conflict_type VARCHAR(40) NOT NULL CHECK (conflict_type IN (
        'VERSION_MISMATCH', 'FIELD_CONFLICT', 'STATE_CONFLICT',
        'DELETE_VS_UPDATE', 'UPDATE_VS_DELETE', 'OWNERSHIP_CONFLICT',
        'SAME_FIELD_BOTH_SIDES', 'NON_OVERLAPPING_FIELDS',
        'STATE_TRANSITION_CONFLICT', 'APPEND_CONFLICT',
        'CROSS_TENANT_ATTEMPT', 'BATCH_PARTIAL_FAILURE'
    )),
    conflict_class VARCHAR(10) NOT NULL CHECK (conflict_class IN ('C1','C2','C3','C4','C5','C6','C7','C8','C9','C10','C11','C12')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLUTION_PENDING', 'RESOLVED', 'EXPIRED', 'ARCHIVED')),
    resolution VARCHAR(40) CHECK (resolution IS NULL OR resolution IN ('CLIENT_WINS', 'SERVER_WINS', 'MERGED', 'USER_CHOICE')),
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    retention_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_conflict_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_conflict_log_device FOREIGN KEY (device_id) REFERENCES mobile_device_registry(device_id),
    CONSTRAINT fk_conflict_log_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_conflict_log_entity ON mobile_conflict_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_conflict_log_status ON mobile_conflict_log(tenant_id, status, created_at);
CREATE INDEX idx_conflict_log_retention ON mobile_conflict_log(retention_expires_at) WHERE status = 'OPEN';
CREATE INDEX idx_conflict_log_device ON mobile_conflict_log(tenant_id, device_id);

-- ============================================================
-- 5. Enable RLS on all sync tables
-- ============================================================
ALTER TABLE mobile_device_registry ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_sync_cursor ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_sync_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_conflict_log ENABLE ROW LEVEL SECURITY;

-- RLS policies: tenant isolation via app.tenant_id (platform standard GUC set by
-- TenantRlsConnectionHandler as SET LOCAL app.tenant_id within each transaction).
-- missing_ok => true so a connection without a tenant context sees NO rows (fail-closed).
CREATE POLICY device_registry_tenant_isolation ON mobile_device_registry
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);

CREATE POLICY sync_cursor_tenant_isolation ON mobile_sync_cursor
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);

CREATE POLICY sync_log_tenant_isolation ON mobile_sync_log
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);

CREATE POLICY conflict_log_tenant_isolation ON mobile_conflict_log
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);
