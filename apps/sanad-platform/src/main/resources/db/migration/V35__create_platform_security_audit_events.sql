-- ============================================================
-- SANAD Platform - Flyway migration V35 (H2 + PostgreSQL)
-- ------------------------------------------------------------
-- Stage 05A.2.8 §6 — Create platform_security_audit_events table.
--
-- Stores pre-authentication security denials (before tenant
-- identity is verified). Never stores raw JWT, Authorization
-- headers, or unverified tenant IDs as trusted.
--
-- Append-only via application logic (insert-only writer).
-- No runtime read grant.
-- ============================================================

CREATE TABLE platform_security_audit_events (
    id                    UUID            NOT NULL,
    occurred_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    request_id            VARCHAR(128),
    path                  VARCHAR(500),
    http_method           VARCHAR(10),
    source_ip             VARCHAR(45),
    user_agent            VARCHAR(500),
    failure_category      VARCHAR(50)     NOT NULL,
    error_code            VARCHAR(50),
    token_fingerprint     VARCHAR(128),
    metadata              TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_platform_security_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_platform_sec_audit_failure_cat
        CHECK (failure_category IN (
            'MISSING_JWT', 'MALFORMED_JWT', 'INVALID_SIGNATURE',
            'EXPIRED_JWT', 'INVALID_SUBJECT', 'UNKNOWN_SESSION',
            'REVOKED_SESSION', 'UNVERIFIED_TENANT'
        ))
);

CREATE INDEX idx_platform_sec_audit_occurred
    ON platform_security_audit_events (occurred_at);
CREATE INDEX idx_platform_sec_audit_request_id
    ON platform_security_audit_events (request_id);
