CREATE TABLE system_services (
    id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    version VARCHAR(80),
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(24) NOT NULL,
    health_url VARCHAR(500),
    owner_name VARCHAR(160),
    criticality VARCHAR(20) NOT NULL,
    dependencies VARCHAR(1000),
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_latency_ms BIGINT,
    last_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_system_services PRIMARY KEY (id),
    CONSTRAINT uk_system_services_code UNIQUE (code),
    CONSTRAINT ck_system_services_status CHECK (status IN ('OPERATIONAL', 'DEGRADED', 'MAINTENANCE', 'DISABLED', 'INCIDENT')),
    CONSTRAINT ck_system_services_criticality CHECK (criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

INSERT INTO system_services (
    id, code, name, description, version, environment, status, health_url,
    owner_name, criticality, dependencies, created_at, updated_at
) VALUES
    (CAST('b2000000-0000-0000-0000-000000000001' AS uuid), 'WEB', 'SNAD Web', 'Next.js administration and tenant workspace', NULL, 'pilot', 'OPERATIONAL', NULL, 'Platform Engineering', 'HIGH', 'API', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('b2000000-0000-0000-0000-000000000002' AS uuid), 'API', 'SNAD Platform API', 'Spring Boot application service', NULL, 'pilot', 'OPERATIONAL', '/actuator/health', 'Platform Engineering', 'CRITICAL', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('b2000000-0000-0000-0000-000000000003' AS uuid), 'DATABASE', 'SNAD PostgreSQL', 'Multi-tenant transactional database', '16', 'pilot', 'OPERATIONAL', NULL, 'Data Engineering', 'CRITICAL', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('b2000000-0000-0000-0000-000000000004' AS uuid), 'NOTIFICATIONS', 'Security Notifications', 'Account recovery and security notification gateway', NULL, 'pilot', 'DISABLED', NULL, 'Platform Engineering', 'HIGH', 'EXTERNAL_PROVIDER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
