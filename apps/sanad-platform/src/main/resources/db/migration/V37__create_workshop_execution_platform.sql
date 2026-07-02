-- SANAD Stage 06 — Workshop Execution Platform
-- H2 (PostgreSQL mode) + PostgreSQL compatible core schema.

CREATE TABLE workshops (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(30) NOT NULL,
    planned_start TIMESTAMP WITH TIME ZONE,
    planned_end TIMESTAMP WITH TIME ZONE,
    actual_start TIMESTAMP WITH TIME ZONE,
    actual_end TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_workshops PRIMARY KEY (id),
    CONSTRAINT uk_workshops_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_workshops_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_workshops_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workshops_org FOREIGN KEY (tenant_id, organization_id)
        REFERENCES organizations(tenant_id, id),
    CONSTRAINT ck_workshops_status CHECK (status IN ('DRAFT','ACTIVE','PAUSED','COMPLETED','ARCHIVED')),
    CONSTRAINT ck_workshops_plan CHECK (planned_end IS NULL OR planned_start IS NULL OR planned_end >= planned_start),
    CONSTRAINT ck_workshops_actual CHECK (actual_end IS NULL OR actual_start IS NULL OR actual_end >= actual_start)
);

CREATE TABLE workshop_work_items (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    parent_item_id UUID,
    code VARCHAR(80) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(4000),
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    primary_assignee_user_id UUID,
    due_at TIMESTAMP WITH TIME ZONE,
    estimated_minutes INTEGER,
    actual_minutes INTEGER NOT NULL DEFAULT 0,
    sequence_no INTEGER NOT NULL DEFAULT 0,
    blocked_reason VARCHAR(1000),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_workshop_work_items PRIMARY KEY (id),
    CONSTRAINT uk_workshop_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_workshop_items_code UNIQUE (tenant_id, workshop_id, code),
    CONSTRAINT fk_workshop_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workshop_items_workshop FOREIGN KEY (tenant_id, workshop_id)
        REFERENCES workshops(tenant_id, id),
    CONSTRAINT fk_workshop_items_parent FOREIGN KEY (tenant_id, parent_item_id)
        REFERENCES workshop_work_items(tenant_id, id),
    CONSTRAINT ck_workshop_items_status CHECK (status IN ('BACKLOG','READY','IN_PROGRESS','BLOCKED','IN_REVIEW','DONE','CANCELLED')),
    CONSTRAINT ck_workshop_items_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_workshop_items_estimate CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    CONSTRAINT ck_workshop_items_actual CHECK (actual_minutes >= 0),
    CONSTRAINT ck_workshop_items_sequence CHECK (sequence_no >= 0)
);

CREATE TABLE workshop_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    work_item_id UUID NOT NULL,
    user_id UUID NOT NULL,
    assignment_role VARCHAR(30) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshop_assignments PRIMARY KEY (id),
    CONSTRAINT uk_workshop_assignment UNIQUE (tenant_id, work_item_id, user_id, assignment_role),
    CONSTRAINT fk_workshop_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workshop_assignment_workshop FOREIGN KEY (tenant_id, workshop_id)
        REFERENCES workshops(tenant_id, id),
    CONSTRAINT fk_workshop_assignment_item FOREIGN KEY (tenant_id, work_item_id)
        REFERENCES workshop_work_items(tenant_id, id),
    CONSTRAINT ck_workshop_assignment_role CHECK (assignment_role IN ('OWNER','FACILITATOR','EXECUTOR','REVIEWER','OBSERVER'))
);

CREATE TABLE workshop_dependencies (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    predecessor_item_id UUID NOT NULL,
    successor_item_id UUID NOT NULL,
    dependency_type VARCHAR(30) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshop_dependencies PRIMARY KEY (id),
    CONSTRAINT uk_workshop_dependency UNIQUE (tenant_id, predecessor_item_id, successor_item_id),
    CONSTRAINT fk_workshop_dependency_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workshop_dependency_workshop FOREIGN KEY (tenant_id, workshop_id)
        REFERENCES workshops(tenant_id, id),
    CONSTRAINT fk_workshop_dependency_predecessor FOREIGN KEY (tenant_id, predecessor_item_id)
        REFERENCES workshop_work_items(tenant_id, id),
    CONSTRAINT fk_workshop_dependency_successor FOREIGN KEY (tenant_id, successor_item_id)
        REFERENCES workshop_work_items(tenant_id, id),
    CONSTRAINT ck_workshop_dependency_self CHECK (predecessor_item_id <> successor_item_id),
    CONSTRAINT ck_workshop_dependency_type CHECK (dependency_type IN ('FINISH_TO_START','START_TO_START','FINISH_TO_FINISH'))
);

CREATE TABLE workshop_activities (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    work_item_id UUID NOT NULL,
    activity_type VARCHAR(30) NOT NULL,
    body VARCHAR(4000),
    minutes INTEGER,
    external_uri VARCHAR(1000),
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshop_activities PRIMARY KEY (id),
    CONSTRAINT uk_workshop_activities_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_workshop_activity_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_workshop_activity_workshop FOREIGN KEY (tenant_id, workshop_id)
        REFERENCES workshops(tenant_id, id),
    CONSTRAINT fk_workshop_activity_item FOREIGN KEY (tenant_id, work_item_id)
        REFERENCES workshop_work_items(tenant_id, id),
    CONSTRAINT ck_workshop_activity_type CHECK (activity_type IN ('COMMENT','CHECKLIST','TIME','ATTACHMENT','STATUS_CHANGE')),
    CONSTRAINT ck_workshop_activity_minutes CHECK (minutes IS NULL OR minutes > 0),
    CONSTRAINT ck_workshop_activity_period CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_workshops_tenant_org_status ON workshops(tenant_id, organization_id, status);
CREATE INDEX idx_workshop_items_board ON workshop_work_items(tenant_id, workshop_id, status, sequence_no);
CREATE INDEX idx_workshop_items_assignee ON workshop_work_items(tenant_id, primary_assignee_user_id, status);
CREATE INDEX idx_workshop_assignments_item ON workshop_assignments(tenant_id, work_item_id);
CREATE INDEX idx_workshop_dependencies_successor ON workshop_dependencies(tenant_id, successor_item_id);
CREATE INDEX idx_workshop_activities_item ON workshop_activities(tenant_id, work_item_id, created_at);
