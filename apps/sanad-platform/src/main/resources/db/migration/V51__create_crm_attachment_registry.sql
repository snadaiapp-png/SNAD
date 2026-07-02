CREATE TABLE IF NOT EXISTS crm_platform.attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 CHAR(64) NOT NULL,
    scan_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (scan_status IN ('PENDING','SCANNING','CLEAN','INFECTED','FAILED','QUARANTINED','DELETED')),
    scan_engine VARCHAR(100),
    scan_result TEXT,
    storage_version VARCHAR(255),
    retention_until TIMESTAMPTZ,
    legal_hold BOOLEAN NOT NULL DEFAULT false,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    scanned_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    UNIQUE (tenant_id, object_key),
    UNIQUE (tenant_id, sha256, size_bytes)
);

CREATE INDEX IF NOT EXISTS idx_crm_attachment_tenant_status
    ON crm_platform.attachment (tenant_id, scan_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_attachment_retention
    ON crm_platform.attachment (scan_status, retention_until)
    WHERE retention_until IS NOT NULL;
