-- HRM: Employee management tables
-- Requirements: HR-001 (Employee Records), HR-002 (Department), HR-003 (Position)

-- ============================================================
-- 1. hr_departments
-- ============================================================
CREATE TABLE IF NOT EXISTS hr_departments (
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    parent_department_id UUID REFERENCES hr_departments(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_departments_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

-- ============================================================
-- 2. hr_positions
-- ============================================================
CREATE TABLE IF NOT EXISTS hr_positions (
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    title VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    department_id UUID REFERENCES hr_departments(id),
    grade VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_hr_positions_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

-- ============================================================
-- 3. hr_employees
-- ============================================================
CREATE TABLE IF NOT EXISTS hr_employees (
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID REFERENCES users(id),
    employee_number VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    department_id UUID REFERENCES hr_departments(id),
    position_id UUID REFERENCES hr_positions(id),
    manager_id UUID REFERENCES hr_employees(id),
    employment_type VARCHAR(30) NOT NULL DEFAULT 'FULL_TIME',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hire_date DATE,
    termination_date DATE,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_hr_employees_type CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERN','CONSULTANT')),
    CONSTRAINT ck_hr_employees_status CHECK (status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    CONSTRAINT uq_hr_employees_number_tenant UNIQUE (tenant_id, employee_number)
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_hr_departments_tenant ON hr_departments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_hr_positions_tenant ON hr_positions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_hr_employees_tenant ON hr_employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_hr_employees_department ON hr_employees(department_id);
CREATE INDEX IF NOT EXISTS idx_hr_employees_position ON hr_employees(position_id);
CREATE INDEX IF NOT EXISTS idx_hr_employees_status ON hr_employees(status);

-- ============================================================
-- RLS Policies (idempotent: PostgreSQL does not support
-- "CREATE POLICY IF NOT EXISTS", so each CREATE POLICY is
-- wrapped in a DO block that catches duplicate_object and
-- ignores the case where the policy was already applied
-- manually outside Flyway — see HRM production-drift runbook.)
-- ============================================================
DO $$ BEGIN
    ALTER TABLE hr_departments ENABLE ROW LEVEL SECURITY;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE hr_positions ENABLE ROW LEVEL SECURITY;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE hr_employees ENABLE ROW LEVEL SECURITY;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Helper: create the tenant_isolation policy on a table only if it
-- does not already exist. Inline DO block per table so a duplicate
-- on one table does not skip the others.
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON hr_departments FOR ALL USING (
        (current_setting('app.tenant_id'::text, true) IS NULL) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))
    );
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'Policy tenant_isolation already exists on hr_departments — skipped';
END $$;

DO $$ BEGIN
    CREATE POLICY tenant_isolation ON hr_positions FOR ALL USING (
        (current_setting('app.tenant_id'::text, true) IS NULL) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))
    );
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'Policy tenant_isolation already exists on hr_positions — skipped';
END $$;

DO $$ BEGIN
    CREATE POLICY tenant_isolation ON hr_employees FOR ALL USING (
        (current_setting('app.tenant_id'::text, true) IS NULL) OR ((tenant_id)::text = current_setting('app.tenant_id'::text, true))
    );
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'Policy tenant_isolation already exists on hr_employees — skipped';
END $$;

-- ============================================================
-- HR Capabilities
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'HR.EMPLOYEE.READ', 'Read Employees', 'View employee records', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.EMPLOYEE.WRITE', 'Write Employees', 'Create and update employee records', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.EMPLOYEE.ARCHIVE', 'Archive Employees', 'Archive/terminate employees', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.DEPARTMENT.READ', 'Read Departments', 'View department records', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.DEPARTMENT.WRITE', 'Write Departments', 'Create and update departments', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.POSITION.READ', 'Read Positions', 'View position records', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.POSITION.WRITE', 'Write Positions', 'Create and update positions', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'HR.ADMIN', 'HR Administration', 'Full HR module access', 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Assign HR capabilities to ADMIN role
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000100', ac.id, NOW()
FROM access_capabilities ac
WHERE ac.code LIKE 'HR.%'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.role_id='00000000-0000-0000-0000-000000000100' AND rc.capability_id=ac.id
);
