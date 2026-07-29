# CRM-008 Team Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement comprehensive team management capabilities including scheduling, availability, skills, capacity, workload, and dashboard for the SANAD CRM platform.

**Architecture:** Extend the existing `crm/ownership` module with sub-packages for scheduling, availability, skills, capacity, and workload. Follow existing CRM patterns: domain records, JDBC repositories, UseCase facades, and REST controllers with RBAC.

**Tech Stack:** Java 21, Spring Boot 3, JDBC (NamedParameterJdbcTemplate), PostgreSQL, Flyway, JWT Authentication, OpenAPI

## Global Constraints

- Multi-tenant SaaS with `tenant_id` isolation in every query
- JWT authentication with `tenant_id` and `user_id` in `Authentication.getDetails()`
- RBAC via `@RequireCapability` annotation
- Optimistic locking via `version BIGINT` column
- Audit trail via `AuditPort` and `TimelineEventPort`
- API versioning: `/api/v1/crm/...`
- Database migrations: Flyway with `V<date>_<seq>__<description>.sql`

---

# Sprint 1: Infrastructure + Scheduling + Availability

## Task 1: Create Database Migration for Shift Templates

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_1__create_crm_shift_templates.sql`

**Interfaces:**
- Produces: `crm_shift_templates` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260728_1__create_crm_shift_templates.sql
CREATE TABLE IF NOT EXISTS crm_shift_templates (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    days_of_week VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_shift_templates PRIMARY KEY (id),
    CONSTRAINT uk_crm_shift_templates_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_shift_templates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_shift_templates_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_crm_shift_templates_tenant_status ON crm_shift_templates (tenant_id, status);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260728_1 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_1__create_crm_shift_templates.sql
git commit -m "feat(crm-008): create crm_shift_templates table"
```

---

## Task 2: Create Database Migration for Shift Assignments

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_2__create_crm_shift_assignments.sql`

**Interfaces:**
- Produces: `crm_shift_assignments` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260728_2__create_crm_shift_assignments.sql
CREATE TABLE IF NOT EXISTS crm_shift_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    team_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    shift_template_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_shift_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_shift_assignments_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_shift_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_shift_assignments_team FOREIGN KEY (team_id) REFERENCES crm_sales_teams (id),
    CONSTRAINT fk_crm_shift_assignments_template FOREIGN KEY (shift_template_id) REFERENCES crm_shift_templates (id),
    CONSTRAINT ck_crm_shift_assignments_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_crm_shift_assignments_team ON crm_shift_assignments (tenant_id, team_id, status);
CREATE INDEX idx_crm_shift_assignments_staff ON crm_shift_assignments (tenant_id, staff_id, start_date);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260728_2 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_2__create_crm_shift_assignments.sql
git commit -m "feat(crm-008): create crm_shift_assignments table"
```

---

## Task 3: Create Database Migration for Staff Availability

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_3__create_crm_staff_availability.sql`

**Interfaces:**
- Produces: `crm_staff_availability` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260728_3__create_crm_staff_availability.sql
CREATE TABLE IF NOT EXISTS crm_staff_availability (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    reason VARCHAR(500),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_staff_availability PRIMARY KEY (id),
    CONSTRAINT uk_crm_staff_availability_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_staff_availability_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_staff_availability_type CHECK (type IN ('AVAILABLE', 'UNAVAILABLE', 'ON_LEAVE'))
);

CREATE INDEX idx_crm_staff_availability_staff ON crm_staff_availability (tenant_id, staff_id, start_date);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260728_3 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_3__create_crm_staff_availability.sql
git commit -m "feat(crm-008): create crm_staff_availability table"
```

---

## Task 4: Create Database Migration for RBAC Permissions

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_4__seed_crm_team_capabilities.sql`

**Interfaces:**
- Produces: RBAC capabilities for team management

- [ ] **Step 1: Create migration file**

```sql
-- V20260728_4__seed_crm_team_capabilities.sql
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TEAM.SCHEDULE.READ',  'Read Team Scheduling',  'View team shift templates and assignments'),
    ('CRM.TEAM.SCHEDULE.WRITE', 'Write Team Scheduling', 'Create and update team shift templates and assignments'),
    ('CRM.TEAM.SKILL.READ',     'Read Team Skills',      'View team staff skills and proficiency'),
    ('CRM.TEAM.SKILL.WRITE',    'Write Team Skills',     'Create and update team staff skills'),
    ('CRM.TEAM.CAPACITY.READ',  'Read Team Capacity',    'View team capacity plans'),
    ('CRM.TEAM.CAPACITY.WRITE', 'Write Team Capacity',   'Create and update team capacity plans')
) AS capability(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code);

-- Grant to ADMIN role
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability ON capability.code IN (
    'CRM.TEAM.SCHEDULE.READ', 'CRM.TEAM.SCHEDULE.WRITE',
    'CRM.TEAM.SKILL.READ', 'CRM.TEAM.SKILL.WRITE',
    'CRM.TEAM.CAPACITY.READ', 'CRM.TEAM.CAPACITY.WRITE'
)
WHERE role.code = 'ADMIN' AND role.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id AND existing.role_id = role.id AND existing.capability_id = capability.id);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260728_4 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260728_4__seed_crm_team_capabilities.sql
git commit -m "feat(crm-008): seed CRM team management capabilities"
```

---

## Task 5: Implement ShiftTemplate Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftTemplate.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftTemplateStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftTemplateRepository.java`

**Interfaces:**
- Produces: `ShiftTemplate` record, `ShiftTemplateStatus` enum, `ShiftTemplateRepository` interface

- [ ] **Step 1: Create ShiftTemplateStatus enum**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

public enum ShiftTemplateStatus {
    ACTIVE, INACTIVE
}
```

- [ ] **Step 2: Create ShiftTemplate record**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ShiftTemplate(
    UUID id,
    UUID tenantId,
    String name,
    LocalTime startTime,
    LocalTime endTime,
    List<DayOfWeek> daysOfWeek,
    ShiftTemplateStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public ShiftTemplate {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shift template name is required");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start and end times are required");
        }
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            throw new IllegalArgumentException("At least one day of week is required");
        }
    }
}
```

- [ ] **Step 3: Create ShiftTemplateRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftTemplateRepository {

    record CreateShiftTemplateCommand(
        UUID tenantId,
        String name,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        List<java.time.DayOfWeek> daysOfWeek,
        UUID createdBy
   ) {}

    record UpdateShiftTemplateCommand(
        String name,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        List<java.time.DayOfWeek> daysOfWeek,
        ShiftTemplateStatus status,
        UUID updatedBy,
        long expectedVersion
   ) {}

    Optional<ShiftTemplate> findById(UUID tenantId, UUID id);

    List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset);

    ShiftTemplate create(CreateShiftTemplateCommand command);

    Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand command);

    boolean existsByName(UUID tenantId, String name, UUID excludeId);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/
git commit -m "feat(crm-008): add ShiftTemplate domain model"
```

---

## Task 6: Implement ShiftAssignment Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftAssignment.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftAssignmentStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/ShiftAssignmentRepository.java`

**Interfaces:**
- Produces: `ShiftAssignment` record, `ShiftAssignmentStatus` enum, `ShiftAssignmentRepository` interface

- [ ] **Step 1: Create ShiftAssignmentStatus enum**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

public enum ShiftAssignmentStatus {
    SCHEDULED, ACTIVE, COMPLETED, CANCELLED
}
```

- [ ] **Step 2: Create ShiftAssignment record**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShiftAssignment(
    UUID id,
    UUID tenantId,
    UUID teamId,
    UUID staffId,
    UUID shiftTemplateId,
    LocalDate startDate,
    LocalDate endDate,
    ShiftAssignmentStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public ShiftAssignment {
        if (teamId == null) {
            throw new IllegalArgumentException("Team ID is required");
        }
        if (staffId == null) {
            throw new IllegalArgumentException("Staff ID is required");
        }
        if (shiftTemplateId == null) {
            throw new IllegalArgumentException("Shift template ID is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
```

- [ ] **Step 3: Create ShiftAssignmentRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository {

    record CreateShiftAssignmentCommand(
        UUID tenantId,
        UUID teamId,
        UUID staffId,
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy
    ) {}

    record UpdateShiftAssignmentCommand(
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate,
        ShiftAssignmentStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<ShiftAssignment> findById(UUID tenantId, UUID id);

    List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset);

    List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    ShiftAssignment create(CreateShiftAssignmentCommand command);

    Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand command);

    boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/scheduling/
git commit -m "feat(crm-008): add ShiftAssignment domain model"
```

---

## Task 7: Implement StaffAvailability Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/availability/StaffAvailability.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/availability/AvailabilityType.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/availability/AvailabilityRepository.java`

**Interfaces:**
- Produces: `StaffAvailability` record, `AvailabilityType` enum, `AvailabilityRepository` interface

- [ ] **Step 1: Create AvailabilityType enum**

```java
package com.sanad.platform.crm.ownership.domain.availability;

public enum AvailabilityType {
    AVAILABLE, UNAVAILABLE, ON_LEAVE
}
```

- [ ] **Step 2: Create StaffAvailability record**

```java
package com.sanad.platform.crm.ownership.domain.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record StaffAvailability(
    UUID id,
    UUID tenantId,
    UUID staffId,
    AvailabilityType type,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    String reason,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public StaffAvailability {
        if (staffId == null) {
            throw new IllegalArgumentException("Staff ID is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Availability type is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
```

- [ ] **Step 3: Create AvailabilityRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.availability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRepository {

    record CreateAvailabilityCommand(
        UUID tenantId,
        UUID staffId,
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        String reason,
        UUID createdBy
    ) {}

    record UpdateAvailabilityCommand(
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        java.time.LocalTime startTime,
        java.time.LocalTime endTime,
        String reason,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<StaffAvailability> findById(UUID tenantId, UUID id);

    List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    StaffAvailability create(CreateAvailabilityCommand command);

    Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand command);

    boolean delete(UUID tenantId, UUID id);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/availability/
git commit -m "feat(crm-008): add StaffAvailability domain model"
```

---

## Task 8: Implement JDBC Repository for Shift Templates

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcShiftTemplateRepository.java`

**Interfaces:**
- Consumes: `ShiftTemplateRepository` interface
- Produces: `JdbcShiftTemplateRepository` implementation

- [ ] **Step 1: Create JDBC repository**

```java
package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JdbcShiftTemplateRepository implements ShiftTemplateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShiftTemplateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ShiftTemplate> rowMapper = (rs, rowNum) -> {
        String daysStr = rs.getString("days_of_week");
        List<DayOfWeek> days = Arrays.stream(daysStr.split(","))
            .map(s -> DayOfWeek.of(Integer.parseInt(s.trim())))
            .collect(Collectors.toList());

        return new ShiftTemplate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("name"),
            rs.getObject("start_time", LocalTime.class),
            rs.getObject("end_time", LocalTime.class),
            days,
            ShiftTemplateStatus.valueOf(rs.getString("status")),
            rs.getObject("created_by", UUID.class),
            rs.getObject("updated_by", UUID.class),
            rs.getObject("created_at", Instant.class),
            rs.getObject("updated_at", Instant.class),
            rs.getLong("version")
        );
    };

    @Override
    public Optional<ShiftTemplate> findById(UUID tenantId, UUID id) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id);
        var sql = "SELECT * FROM crm_shift_templates WHERE tenant_id = :tenantId AND id = :id";
        var results = jdbc.query(sql, params, rowMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("limit", limit)
            .addValue("offset", offset);
        var sql = "SELECT * FROM crm_shift_templates WHERE tenant_id = :tenantId ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, rowMapper);
    }

    @Override
    public ShiftTemplate create(CreateShiftTemplateCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String daysStr = command.daysOfWeek().stream()
            .map(d -> String.valueOf(d.getValue()))
            .collect(Collectors.joining(","));

        var params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", command.tenantId())
            .addValue("name", command.name())
            .addValue("startTime", command.startTime())
            .addValue("endTime", command.endTime())
            .addValue("daysOfWeek", daysStr)
            .addValue("status", ShiftTemplateStatus.ACTIVE.name())
            .addValue("createdBy", command.createdBy())
            .addValue("updatedBy", command.createdBy())
            .addValue("createdAt", now)
            .addValue("updatedAt", now)
            .addValue("version", 0L);

        var sql = """
            INSERT INTO crm_shift_templates (id, tenant_id, name, start_time, end_time, days_of_week, status, created_by, updated_by, created_at, updated_at, version)
            VALUES (:id, :tenantId, :name, :startTime, :endTime, :daysOfWeek, :status, :createdBy, :updatedBy, :createdAt, :updatedAt, :version)
            """;
        jdbc.update(sql, params);

        return findById(command.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand command) {
        String daysStr = command.daysOfWeek().stream()
            .map(d -> String.valueOf(d.getValue()))
            .collect(Collectors.joining(","));

        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id)
            .addValue("name", command.name())
            .addValue("startTime", command.startTime())
            .addValue("endTime", command.endTime())
            .addValue("daysOfWeek", daysStr)
            .addValue("status", command.status().name())
            .addValue("updatedBy", command.updatedBy())
            .addValue("updatedAt", Instant.now())
            .addValue("expectedVersion", command.expectedVersion());

        var sql = """
            UPDATE crm_shift_templates
            SET name = :name, start_time = :startTime, end_time = :endTime, days_of_week = :daysOfWeek,
                status = :status, updated_by = :updatedBy, updated_at = :updatedAt, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """;
        int rows = jdbc.update(sql, params);

        if (rows == 0) {
            return Optional.empty();
        }

        return findById(tenantId, id);
    }

    @Override
    public boolean existsByName(UUID tenantId, String name, UUID excludeId) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("name", name)
            .addValue("excludeId", excludeId);
        var sql = "SELECT COUNT(*) FROM crm_shift_templates WHERE tenant_id = :tenantId AND name = :name AND id != :excludeId";
        return jdbc.queryForObject(sql, params, Integer.class) > 0;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcShiftTemplateRepository.java
git commit -m "feat(crm-008): implement JdbcShiftTemplateRepository"
```

---

## Task 9: Implement JDBC Repository for Shift Assignments

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcShiftAssignmentRepository.java`

**Interfaces:**
- Consumes: `ShiftAssignmentRepository` interface
- Produces: `JdbcShiftAssignmentRepository` implementation

- [ ] **Step 1: Create JDBC repository**

```java
package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JdbcShiftAssignmentRepository implements ShiftAssignmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShiftAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ShiftAssignment> rowMapper = (rs, rowNum) -> new ShiftAssignment(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getObject("team_id", UUID.class),
        rs.getObject("staff_id", UUID.class),
        rs.getObject("shift_template_id", UUID.class),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        ShiftAssignmentStatus.valueOf(rs.getString("status")),
        rs.getObject("created_by", UUID.class),
        rs.getObject("updated_by", UUID.class),
        rs.getObject("created_at", Instant.class),
        rs.getObject("updated_at", Instant.class),
        rs.getLong("version")
    );

    @Override
    public Optional<ShiftAssignment> findById(UUID tenantId, UUID id) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id);
        var sql = "SELECT * FROM crm_shift_assignments WHERE tenant_id = :tenantId AND id = :id";
        var results = jdbc.query(sql, params, rowMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("teamId", teamId)
            .addValue("limit", limit)
            .addValue("offset", offset);
        var sql = "SELECT * FROM crm_shift_assignments WHERE tenant_id = :tenantId AND team_id = :teamId ORDER BY start_date DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, rowMapper);
    }

    @Override
    public List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("staffId", staffId)
            .addValue("from", from)
            .addValue("to", to);
        var sql = "SELECT * FROM crm_shift_assignments WHERE tenant_id = :tenantId AND staff_id = :staffId AND start_date >= :from AND end_date <= :to ORDER BY start_date";
        return jdbc.query(sql, params, rowMapper);
    }

    @Override
    public ShiftAssignment create(CreateShiftAssignmentCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        var params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", command.tenantId())
            .addValue("teamId", command.teamId())
            .addValue("staffId", command.staffId())
            .addValue("shiftTemplateId", command.shiftTemplateId())
            .addValue("startDate", command.startDate())
            .addValue("endDate", command.endDate())
            .addValue("status", ShiftAssignmentStatus.SCHEDULED.name())
            .addValue("createdBy", command.createdBy())
            .addValue("updatedBy", command.createdBy())
            .addValue("createdAt", now)
            .addValue("updatedAt", now)
            .addValue("version", 0L);

        var sql = """
            INSERT INTO crm_shift_assignments (id, tenant_id, team_id, staff_id, shift_template_id, start_date, end_date, status, created_by, updated_by, created_at, updated_at, version)
            VALUES (:id, :tenantId, :teamId, :staffId, :shiftTemplateId, :startDate, :endDate, :status, :createdBy, :updatedBy, :createdAt, :updatedAt, :version)
            """;
        jdbc.update(sql, params);

        return findById(command.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand command) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id)
            .addValue("shiftTemplateId", command.shiftTemplateId())
            .addValue("startDate", command.startDate())
            .addValue("endDate", command.endDate())
            .addValue("status", command.status().name())
            .addValue("updatedBy", command.updatedBy())
            .addValue("updatedAt", Instant.now())
            .addValue("expectedVersion", command.expectedVersion());

        var sql = """
            UPDATE crm_shift_assignments
            SET shift_template_id = :shiftTemplateId, start_date = :startDate, end_date = :endDate,
                status = :status, updated_by = :updatedBy, updated_at = :updatedAt, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """;
        int rows = jdbc.update(sql, params);

        if (rows == 0) {
            return Optional.empty();
        }

        return findById(tenantId, id);
    }

    @Override
    public boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("staffId", staffId)
            .addValue("startDate", startDate)
            .addValue("endDate", endDate)
            .addValue("excludeId", excludeId);
        var sql = """
            SELECT COUNT(*) FROM crm_shift_assignments
            WHERE tenant_id = :tenantId AND staff_id = :staffId AND id != :excludeId
            AND start_date <= :endDate AND end_date >= :startDate
            AND status NOT IN ('CANCELLED')
            """;
        return jdbc.queryForObject(sql, params, Integer.class) > 0;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcShiftAssignmentRepository.java
git commit -m "feat(crm-008): implement JdbcShiftAssignmentRepository"
```

---

## Task 10: Implement JDBC Repository for Staff Availability

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcAvailabilityRepository.java`

**Interfaces:**
- Consumes: `AvailabilityRepository` interface
- Produces: `JdbcAvailabilityRepository` implementation

- [ ] **Step 1: Create JDBC repository**

```java
package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.availability.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Repository
public class JdbcAvailabilityRepository implements AvailabilityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAvailabilityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<StaffAvailability> rowMapper = (rs, rowNum) -> new StaffAvailability(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getObject("staff_id", UUID.class),
        AvailabilityType.valueOf(rs.getString("type")),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        rs.getObject("start_time", LocalTime.class),
        rs.getObject("end_time", LocalTime.class),
        rs.getString("reason"),
        rs.getObject("created_by", UUID.class),
        rs.getObject("updated_by", UUID.class),
        rs.getObject("created_at", Instant.class),
        rs.getObject("updated_at", Instant.class),
        rs.getLong("version")
    );

    @Override
    public Optional<StaffAvailability> findById(UUID tenantId, UUID id) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id);
        var sql = "SELECT * FROM crm_staff_availability WHERE tenant_id = :tenantId AND id = :id";
        var results = jdbc.query(sql, params, rowMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("staffId", staffId)
            .addValue("from", from)
            .addValue("to", to);
        var sql = "SELECT * FROM crm_staff_availability WHERE tenant_id = :tenantId AND staff_id = :staffId AND start_date >= :from AND end_date <= :to ORDER BY start_date";
        return jdbc.query(sql, params, rowMapper);
    }

    @Override
    public StaffAvailability create(CreateAvailabilityCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        var params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", command.tenantId())
            .addValue("staffId", command.staffId())
            .addValue("type", command.type().name())
            .addValue("startDate", command.startDate())
            .addValue("endDate", command.endDate())
            .addValue("startTime", command.startTime())
            .addValue("endTime", command.endTime())
            .addValue("reason", command.reason())
            .addValue("createdBy", command.createdBy())
            .addValue("updatedBy", command.createdBy())
            .addValue("createdAt", now)
            .addValue("updatedAt", now)
            .addValue("version", 0L);

        var sql = """
            INSERT INTO crm_staff_availability (id, tenant_id, staff_id, type, start_date, end_date, start_time, end_time, reason, created_by, updated_by, created_at, updated_at, version)
            VALUES (:id, :tenantId, :staffId, :type, :startDate, :endDate, :startTime, :endTime, :reason, :createdBy, :updatedBy, :createdAt, :updatedAt, :version)
            """;
        jdbc.update(sql, params);

        return findById(command.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand command) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id)
            .addValue("type", command.type().name())
            .addValue("startDate", command.startDate())
            .addValue("endDate", command.endDate())
            .addValue("startTime", command.startTime())
            .addValue("endTime", command.endTime())
            .addValue("reason", command.reason())
            .addValue("updatedBy", command.updatedBy())
            .addValue("updatedAt", Instant.now())
            .addValue("expectedVersion", command.expectedVersion());

        var sql = """
            UPDATE crm_staff_availability
            SET type = :type, start_date = :startDate, end_date = :endDate,
                start_time = :startTime, end_time = :endTime, reason = :reason,
                updated_by = :updatedBy, updated_at = :updatedAt, version = version + 1
            WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion
            """;
        int rows = jdbc.update(sql, params);

        if (rows == 0) {
            return Optional.empty();
        }

        return findById(tenantId, id);
    }

    @Override
    public boolean delete(UUID tenantId, UUID id) {
        var params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", id);
        var sql = "DELETE FROM crm_staff_availability WHERE tenant_id = :tenantId AND id = :id";
        return jdbc.update(sql, params) > 0;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcAvailabilityRepository.java
git commit -m "feat(crm-008): implement JdbcAvailabilityRepository"
```

---

## Task 11: Implement SchedulingUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/SchedulingUseCases.java`

**Interfaces:**
- Consumes: `ShiftTemplateRepository`, `ShiftAssignmentRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `SchedulingUseCases` with business logic

- [ ] **Step 1: Create SchedulingUseCases**

```java
package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.scheduling.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SchedulingUseCases {

    private final ShiftTemplateRepository shiftTemplateRepo;
    private final ShiftAssignmentRepository shiftAssignmentRepo;
    private final AuditPort auditPort;
    private final TimelineEventPort timelineEventPort;
    private final ObjectMapper objectMapper;

    public SchedulingUseCases(
        ShiftTemplateRepository shiftTemplateRepo,
        ShiftAssignmentRepository shiftAssignmentRepo,
        AuditPort auditPort,
        TimelineEventPort timelineEventPort,
        ObjectMapper objectMapper
    ) {
        this.shiftTemplateRepo = shiftTemplateRepo;
        this.shiftAssignmentRepo = shiftAssignmentRepo;
        this.auditPort = auditPort;
        this.timelineEventPort = timelineEventPort;
        this.objectMapper = objectMapper;
    }

    // Shift Template operations

    @Transactional
    public ShiftTemplate createShiftTemplate(
        UUID tenantId, UUID actorId, String name,
        java.time.LocalTime startTime, java.time.LocalTime endTime,
        List<java.time.DayOfWeek> daysOfWeek
    ) {
        if (shiftTemplateRepo.existsByName(tenantId, name, null)) {
            throw new IllegalArgumentException("Shift template name already exists");
        }

        var command = new ShiftTemplateRepository.CreateShiftTemplateCommand(
            tenantId, name, startTime, endTime, daysOfWeek, actorId
        );

        ShiftTemplate template = shiftTemplateRepo.create(command);

        auditPort.record(tenantId, actorId, "CREATE", "SHIFT_TEMPLATE",
            template.id(), null, objectMapper.valueToTree(template), Instant.now());

        timelineEventPort.record(tenantId, "SHIFT_TEMPLATE", template.id(),
            "crm.shift_template.created", "Created shift template: " + name,
            "CRM", template.id(), actorId, Instant.now());

        return template;
    }

    public List<ShiftTemplate> listShiftTemplates(UUID tenantId, int limit, int offset) {
        return shiftTemplateRepo.findAll(tenantId, limit, offset);
    }

    public ShiftTemplate getShiftTemplate(UUID tenantId, UUID id) {
        return shiftTemplateRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Shift template not found"));
    }

    @Transactional
    public ShiftTemplate updateShiftTemplate(
        UUID tenantId, UUID id, UUID actorId, String name,
        java.time.LocalTime startTime, java.time.LocalTime endTime,
        List<java.time.DayOfWeek> daysOfWeek, ShiftTemplateStatus status, long expectedVersion
    ) {
        if (name != null && shiftTemplateRepo.existsByName(tenantId, name, id)) {
            throw new IllegalArgumentException("Shift template name already exists");
        }

        var current = shiftTemplateRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Shift template not found"));

        var command = new ShiftTemplateRepository.UpdateShiftTemplateCommand(
            name != null ? name : current.name(),
            startTime != null ? startTime : current.startTime(),
            endTime != null ? endTime : current.endTime(),
            daysOfWeek != null ? daysOfWeek : current.daysOfWeek(),
            status != null ? status : current.status(),
            actorId,
            expectedVersion
        );

        return shiftTemplateRepo.update(tenantId, id, command)
            .orElseThrow(() -> new IllegalArgumentException("Concurrent modification detected"));
    }

    // Shift Assignment operations

    @Transactional
    public ShiftAssignment createShiftAssignment(
        UUID tenantId, UUID teamId, UUID staffId, UUID shiftTemplateId,
        java.time.LocalDate startDate, java.time.LocalDate endDate, UUID actorId
    ) {
        if (shiftAssignmentRepo.hasOverlap(tenantId, staffId, startDate, endDate, null)) {
            throw new IllegalArgumentException("Shift assignment overlaps with existing assignment");
        }

        var command = new ShiftAssignmentRepository.CreateShiftAssignmentCommand(
            tenantId, teamId, staffId, shiftTemplateId, startDate, endDate, actorId
        );

        ShiftAssignment assignment = shiftAssignmentRepo.create(command);

        auditPort.record(tenantId, actorId, "CREATE", "SHIFT_ASSIGNMENT",
            assignment.id(), null, objectMapper.valueToTree(assignment), Instant.now());

        timelineEventPort.record(tenantId, "SHIFT_ASSIGNMENT", assignment.id(),
            "crm.shift_assignment.created", "Created shift assignment for staff: " + staffId,
            "CRM", assignment.id(), actorId, Instant.now());

        return assignment;
    }

    public List<ShiftAssignment> listShiftAssignments(UUID tenantId, UUID teamId, int limit, int offset) {
        return shiftAssignmentRepo.findByTeamId(tenantId, teamId, limit, offset);
    }

    public List<ShiftAssignment> listStaffShifts(UUID tenantId, UUID staffId, java.time.LocalDate from, java.time.LocalDate to) {
        return shiftAssignmentRepo.findByStaffId(tenantId, staffId, from, to);
    }

    @Transactional
    public ShiftAssignment updateShiftAssignment(
        UUID tenantId, UUID id, UUID actorId, UUID shiftTemplateId,
        java.time.LocalDate startDate, java.time.LocalDate endDate,
        ShiftAssignmentStatus status, long expectedVersion
    ) {
        var current = shiftAssignmentRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Shift assignment not found"));

        UUID effectiveStaffId = current.staffId();
        java.time.LocalDate effectiveStart = startDate != null ? startDate : current.startDate();
        java.time.LocalDate effectiveEnd = endDate != null ? endDate : current.endDate();

        if (shiftAssignmentRepo.hasOverlap(tenantId, effectiveStaffId, effectiveStart, effectiveEnd, id)) {
            throw new IllegalArgumentException("Shift assignment overlaps with existing assignment");
        }

        var command = new ShiftAssignmentRepository.UpdateShiftAssignmentCommand(
            shiftTemplateId != null ? shiftTemplateId : current.shiftTemplateId(),
            effectiveStart,
            effectiveEnd,
            status != null ? status : current.status(),
            actorId,
            expectedVersion
        );

        return shiftAssignmentRepo.update(tenantId, id, command)
            .orElseThrow(() -> new IllegalArgumentException("Concurrent modification detected"));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/SchedulingUseCases.java
git commit -m "feat(crm-008): implement SchedulingUseCases"
```

---

## Task 12: Implement AvailabilityUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/AvailabilityUseCases.java`

**Interfaces:**
- Consumes: `AvailabilityRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `AvailabilityUseCases` with business logic

- [ ] **Step 1: Create AvailabilityUseCases**

```java
package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.availability.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AvailabilityUseCases {

    private final AvailabilityRepository availabilityRepo;
    private final AuditPort auditPort;
    private final TimelineEventPort timelineEventPort;
    private final ObjectMapper objectMapper;

    public AvailabilityUseCases(
        AvailabilityRepository availabilityRepo,
        AuditPort auditPort,
        TimelineEventPort timelineEventPort,
        ObjectMapper objectMapper
    ) {
        this.availabilityRepo = availabilityRepo;
        this.auditPort = auditPort;
        this.timelineEventPort = timelineEventPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StaffAvailability createAvailability(
        UUID tenantId, UUID staffId, AvailabilityType type,
        LocalDate startDate, LocalDate endDate,
        java.time.LocalTime startTime, java.time.LocalTime endTime,
        String reason, UUID actorId
    ) {
        var command = new AvailabilityRepository.CreateAvailabilityCommand(
            tenantId, staffId, type, startDate, endDate, startTime, endTime, reason, actorId
        );

        StaffAvailability availability = availabilityRepo.create(command);

        auditPort.record(tenantId, actorId, "CREATE", "STAFF_AVAILABILITY",
            availability.id(), null, objectMapper.valueToTree(availability), Instant.now());

        timelineEventPort.record(tenantId, "STAFF_AVAILABILITY", availability.id(),
            "crm.staff_availability.created", "Created availability record for staff: " + staffId,
            "CRM", availability.id(), actorId, Instant.now());

        return availability;
    }

    public List<StaffAvailability> listAvailability(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        return availabilityRepo.findByStaffId(tenantId, staffId, from, to);
    }

    public StaffAvailability getAvailability(UUID tenantId, UUID id) {
        return availabilityRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Availability record not found"));
    }

    @Transactional
    public StaffAvailability updateAvailability(
        UUID tenantId, UUID id, UUID actorId, AvailabilityType type,
        LocalDate startDate, LocalDate endDate,
        java.time.LocalTime startTime, java.time.LocalTime endTime,
        String reason, long expectedVersion
    ) {
        var current = availabilityRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Availability record not found"));

        var command = new AvailabilityRepository.UpdateAvailabilityCommand(
            type != null ? type : current.type(),
            startDate != null ? startDate : current.startDate(),
            endDate != null ? endDate : current.endDate(),
            startTime != null ? startTime : current.startTime(),
            endTime != null ? endTime : current.endTime(),
            reason != null ? reason : current.reason(),
            actorId,
            expectedVersion
        );

        return availabilityRepo.update(tenantId, id, command)
            .orElseThrow(() -> new IllegalArgumentException("Concurrent modification detected"));
    }

    @Transactional
    public boolean deleteAvailability(UUID tenantId, UUID id, UUID actorId) {
        var availability = availabilityRepo.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Availability record not found"));

        boolean deleted = availabilityRepo.delete(tenantId, id);

        if (deleted) {
            auditPort.record(tenantId, actorId, "DELETE", "STAFF_AVAILABILITY",
                id, objectMapper.valueToTree(availability), null, Instant.now());

            timelineEventPort.record(tenantId, "STAFF_AVAILABILITY", id,
                "crm.staff_availability.deleted", "Deleted availability record",
                "CRM", id, actorId, Instant.now());
        }

        return deleted;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/AvailabilityUseCases.java
git commit -m "feat(crm-008): implement AvailabilityUseCases"
```

---

## Task 13: Update OwnershipModuleConfiguration

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/OwnershipModuleConfiguration.java`

**Interfaces:**
- Consumes: All repositories and integration ports
- Produces: Spring beans for SchedulingUseCases and AvailabilityUseCases

- [ ] **Step 1: Read existing configuration**

Read: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/OwnershipModuleConfiguration.java`

- [ ] **Step 2: Add new beans**

Add to existing class:

```java
@Bean
public SchedulingUseCases schedulingUseCases(
    ShiftTemplateRepository shiftTemplateRepo,
    ShiftAssignmentRepository shiftAssignmentRepo,
    AuditPort auditPort,
    TimelineEventPort timelineEventPort,
    ObjectMapper objectMapper
) {
    return new SchedulingUseCases(shiftTemplateRepo, shiftAssignmentRepo, auditPort, timelineEventPort, objectMapper);
}

@Bean
public AvailabilityUseCases availabilityUseCases(
    AvailabilityRepository availabilityRepo,
    AuditPort auditPort,
    TimelineEventPort timelineEventPort,
    ObjectMapper objectMapper
) {
    return new AvailabilityUseCases(availabilityRepo, auditPort, timelineEventPort, objectMapper);
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/OwnershipModuleConfiguration.java
git commit -m "feat(crm-008): wire SchedulingUseCases and AvailabilityUseCases"
```

---

## Task 14: Implement ShiftController

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/ShiftController.java`

**Interfaces:**
- Consumes: `SchedulingUseCases`
- Produces: REST API endpoints for shift templates and assignments

- [ ] **Step 1: Create ShiftController**

```java
package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.SchedulingUseCases;
import com.sanad.platform.crm.ownership.domain.scheduling.*;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/teams/{teamId}")
public class ShiftController {

    private final SchedulingUseCases schedulingUseCases;

    public ShiftController(SchedulingUseCases schedulingUseCases) {
        this.schedulingUseCases = schedulingUseCases;
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return UUID.fromString(details.get(key).toString());
    }

    // Shift Template endpoints

    @RequireCapability("CRM.TEAM.SCHEDULE.WRITE")
    @PostMapping("/shift-templates")
    public ResponseEntity<Map<String, Object>> createShiftTemplate(
        @PathVariable UUID teamId,
        @RequestBody CreateShiftTemplateRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftTemplate template = schedulingUseCases.createShiftTemplate(
            tenantId, actorId, request.name(), request.startTime(),
            request.endTime(), request.daysOfWeek()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(template));
    }

    @RequireCapability("CRM.TEAM.SCHEDULE.READ")
    @GetMapping("/shift-templates")
    public ResponseEntity<List<Map<String, Object>>> listShiftTemplates(
        @PathVariable UUID teamId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<ShiftTemplate> templates = schedulingUseCases.listShiftTemplates(tenantId, safeLimit, offset);
        return ResponseEntity.ok(templates.stream().map(this::toMap).toList());
    }

    @RequireCapability("CRM.TEAM.SCHEDULE.READ")
    @GetMapping("/shift-templates/{id}")
    public ResponseEntity<Map<String, Object>> getShiftTemplate(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        ShiftTemplate template = schedulingUseCases.getShiftTemplate(tenantId, id);
        return ResponseEntity.ok(toMap(template));
    }

    @RequireCapability("CRM.TEAM.SCHEDULE.WRITE")
    @PatchMapping("/shift-templates/{id}")
    public ResponseEntity<Map<String, Object>> updateShiftTemplate(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        @RequestBody UpdateShiftTemplateRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftTemplate template = schedulingUseCases.updateShiftTemplate(
            tenantId, id, actorId, request.name(), request.startTime(),
            request.endTime(), request.daysOfWeek(), request.status(),
            request.version()
        );

        return ResponseEntity.ok(toMap(template));
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @DeleteMapping("/shift-templates/{id}")
    public ResponseEntity<Void> deleteShiftTemplate(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        Authentication authentication
    ) {
        // Soft delete by setting status to INACTIVE
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftTemplate template = schedulingUseCases.getShiftTemplate(tenantId, id);
        schedulingUseCases.updateShiftTemplate(
            tenantId, id, actorId, null, null, null, null,
            ShiftTemplateStatus.INACTIVE, template.version()
        );

        return ResponseEntity.noContent().build();
    }

    // Shift Assignment endpoints

    @RequireCapability("CRM.TEAM.SCHEDULE.WRITE")
    @PostMapping("/shift-assignments")
    public ResponseEntity<Map<String, Object>> createShiftAssignment(
        @PathVariable UUID teamId,
        @RequestBody CreateShiftAssignmentRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftAssignment assignment = schedulingUseCases.createShiftAssignment(
            tenantId, teamId, request.staffId(), request.shiftTemplateId(),
            request.startDate(), request.endDate(), actorId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(assignment));
    }

    @RequireCapability("CRM.TEAM.SCHEDULE.READ")
    @GetMapping("/shift-assignments")
    public ResponseEntity<List<Map<String, Object>>> listShiftAssignments(
        @PathVariable UUID teamId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<ShiftAssignment> assignments = schedulingUseCases.listShiftAssignments(tenantId, teamId, safeLimit, offset);
        return ResponseEntity.ok(assignments.stream().map(this::toMap).toList());
    }

    private Map<String, Object> toMap(ShiftTemplate t) {
        return Map.of(
            "id", t.id().toString(),
            "name", t.name(),
            "start_time", t.startTime().toString(),
            "end_time", t.endTime().toString(),
            "days_of_week", t.daysOfWeek().stream().map(DayOfWeek::getValue).toList(),
            "status", t.status().name(),
            "version", t.version()
        );
    }

    private Map<String, Object> toMap(ShiftAssignment a) {
        return Map.of(
            "id", a.id().toString(),
            "team_id", a.teamId().toString(),
            "staff_id", a.staffId().toString(),
            "shift_template_id", a.shiftTemplateId().toString(),
            "start_date", a.startDate().toString(),
            "end_date", a.endDate().toString(),
            "status", a.status().name(),
            "version", a.version()
        );
    }

    public record CreateShiftTemplateRequest(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> daysOfWeek
    ) {}

    public record UpdateShiftTemplateRequest(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> daysOfWeek,
        ShiftTemplateStatus status,
        long version
    ) {}

    public record CreateShiftAssignmentRequest(
        UUID staffId,
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate
    ) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/ShiftController.java
git commit -m "feat(crm-008): implement ShiftController"
```

---

## Task 15: Implement AvailabilityController

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/AvailabilityController.java`

**Interfaces:**
- Consumes: `AvailabilityUseCases`
- Produces: REST API endpoints for staff availability

- [ ] **Step 1: Create AvailabilityController**

```java
package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.AvailabilityUseCases;
import com.sanad.platform.crm.ownership.domain.availability.*;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/teams/{teamId}/availability")
public class AvailabilityController {

    private final AvailabilityUseCases availabilityUseCases;

    public AvailabilityController(AvailabilityUseCases availabilityUseCases) {
        this.availabilityUseCases = availabilityUseCases;
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return UUID.fromString(details.get(key).toString());
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAvailability(
        @PathVariable UUID teamId,
        @RequestBody CreateAvailabilityRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        StaffAvailability availability = availabilityUseCases.createAvailability(
            tenantId, request.staffId(), request.type(),
            request.startDate(), request.endDate(),
            request.startTime(), request.endTime(),
            request.reason(), actorId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(availability));
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAvailability(
        @PathVariable UUID teamId,
        @RequestParam UUID staffId,
        @RequestParam(defaultValue = "2026-01-01") LocalDate from,
        @RequestParam(defaultValue = "2026-12-31") LocalDate to,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);

        List<StaffAvailability> availability = availabilityUseCases.listAvailability(tenantId, staffId, from, to);
        return ResponseEntity.ok(availability.stream().map(this::toMap).toList());
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAvailability(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        StaffAvailability availability = availabilityUseCases.getAvailability(tenantId, id);
        return ResponseEntity.ok(toMap(availability));
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAvailability(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        @RequestBody UpdateAvailabilityRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        StaffAvailability availability = availabilityUseCases.updateAvailability(
            tenantId, id, actorId, request.type(),
            request.startDate(), request.endDate(),
            request.startTime(), request.endTime(),
            request.reason(), request.version()
        );

        return ResponseEntity.ok(toMap(availability));
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAvailability(
        @PathVariable UUID teamId,
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        availabilityUseCases.deleteAvailability(tenantId, id, actorId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(StaffAvailability a) {
        return Map.of(
            "id", a.id().toString(),
            "staff_id", a.staffId().toString(),
            "type", a.type().name(),
            "start_date", a.startDate().toString(),
            "end_date", a.endDate().toString(),
            "start_time", a.startTime() != null ? a.startTime().toString() : null,
            "end_time", a.endTime() != null ? a.endTime().toString() : null,
            "reason", a.reason() != null ? a.reason() : "",
            "version", a.version()
        );
    }

    public record CreateAvailabilityRequest(
        UUID staffId,
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason
    ) {}

    public record UpdateAvailabilityRequest(
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        long version
    ) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/AvailabilityController.java
git commit -m "feat(crm-008): implement AvailabilityController"
```

---

## Task 16: Write Unit Tests for SchedulingUseCases

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/application/SchedulingUseCasesTest.java`

**Interfaces:**
- Consumes: `SchedulingUseCases`
- Produces: Unit tests for scheduling business logic

- [ ] **Step 1: Create unit test**

```java
package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.scheduling.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulingUseCasesTest {

    @Mock
    private ShiftTemplateRepository shiftTemplateRepo;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepo;

    @Mock
    private AuditPort auditPort;

    @Mock
    private TimelineEventPort timelineEventPort;

    private ObjectMapper objectMapper;
    private SchedulingUseCases schedulingUseCases;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        schedulingUseCases = new SchedulingUseCases(
            shiftTemplateRepo, shiftAssignmentRepo,
            auditPort, timelineEventPort, objectMapper
        );
    }

    @Test
    void createShiftTemplate_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(shiftTemplateRepo.existsByName(tenantId, "Morning Shift", null)).thenReturn(false);
        when(shiftTemplateRepo.create(any())).thenReturn(new ShiftTemplate(
            UUID.randomUUID(), tenantId, "Morning Shift",
            LocalTime.of(8, 0), LocalTime.of(16, 0),
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            ShiftTemplateStatus.ACTIVE, actorId, actorId,
            java.time.Instant.now(), java.time.Instant.now(), 0
        ));

        ShiftTemplate result = schedulingUseCases.createShiftTemplate(
            tenantId, actorId, "Morning Shift",
            LocalTime.of(8, 0), LocalTime.of(16, 0),
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        );

        assertNotNull(result);
        assertEquals("Morning Shift", result.name());
        verify(auditPort).record(any(), any(), any(), any(), any(), any(), any(), any());
        verify(timelineEventPort).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createShiftTemplate_DuplicateName_ThrowsException() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(shiftTemplateRepo.existsByName(tenantId, "Morning Shift", null)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            schedulingUseCases.createShiftTemplate(
                tenantId, actorId, "Morning Shift",
                LocalTime.of(8, 0), LocalTime.of(16, 0),
                List.of(DayOfWeek.MONDAY)
            )
        );
    }

    @Test
    void createShiftAssignment_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(shiftAssignmentRepo.hasOverlap(tenantId, staffId,
            java.time.LocalDate.of(2026, 8, 1),
            java.time.LocalDate.of(2026, 8, 7), null)).thenReturn(false);
        when(shiftAssignmentRepo.create(any())).thenReturn(new ShiftAssignment(
            UUID.randomUUID(), tenantId, teamId, staffId, templateId,
            java.time.LocalDate.of(2026, 8, 1),
            java.time.LocalDate.of(2026, 8, 7),
            ShiftAssignmentStatus.SCHEDULED, actorId, actorId,
            java.time.Instant.now(), java.time.Instant.now(), 0
        ));

        ShiftAssignment result = schedulingUseCases.createShiftAssignment(
            tenantId, teamId, staffId, templateId,
            java.time.LocalDate.of(2026, 8, 1),
            java.time.LocalDate.of(2026, 8, 7), actorId
        );

        assertNotNull(result);
        assertEquals(ShiftAssignmentStatus.SCHEDULED, result.status());
    }

    @Test
    void createShiftAssignment_Overlap_ThrowsException() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(shiftAssignmentRepo.hasOverlap(tenantId, staffId,
            java.time.LocalDate.of(2026, 8, 1),
            java.time.LocalDate.of(2026, 8, 7), null)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            schedulingUseCases.createShiftAssignment(
                tenantId, UUID.randomUUID(), staffId, UUID.randomUUID(),
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 7), UUID.randomUUID()
            )
        );
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd apps/sanad-platform && ./mvnw test -Dtest=SchedulingUseCasesTest`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/application/SchedulingUseCasesTest.java
git commit -m "test(crm-008): add SchedulingUseCases unit tests"
```

---

## Task 17: Write Unit Tests for AvailabilityUseCases

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/application/AvailabilityUseCasesTest.java`

**Interfaces:**
- Consumes: `AvailabilityUseCases`
- Produces: Unit tests for availability business logic

- [ ] **Step 1: Create unit test**

```java
package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.availability.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityUseCasesTest {

    @Mock
    private AvailabilityRepository availabilityRepo;

    @Mock
    private AuditPort auditPort;

    @Mock
    private TimelineEventPort timelineEventPort;

    private ObjectMapper objectMapper;
    private AvailabilityUseCases availabilityUseCases;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        availabilityUseCases = new AvailabilityUseCases(
            availabilityRepo, auditPort, timelineEventPort, objectMapper
        );
    }

    @Test
    void createAvailability_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(availabilityRepo.create(any())).thenReturn(new StaffAvailability(
            UUID.randomUUID(), tenantId, staffId,
            AvailabilityType.ON_LEAVE,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7),
            null, null, "Vacation",
            actorId, actorId, Instant.now(), Instant.now(), 0
        ));

        StaffAvailability result = availabilityUseCases.createAvailability(
            tenantId, staffId, AvailabilityType.ON_LEAVE,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7),
            null, null, "Vacation", actorId
        );

        assertNotNull(result);
        assertEquals(AvailabilityType.ON_LEAVE, result.type());
        verify(auditPort).record(any(), any(), any(), any(), any(), any(), any(), any());
        verify(timelineEventPort).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteAvailability_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(availabilityRepo.findById(tenantId, id)).thenReturn(Optional.of(new StaffAvailability(
            id, tenantId, UUID.randomUUID(),
            AvailabilityType.ON_LEAVE,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7),
            null, null, "Vacation",
            actorId, actorId, Instant.now(), Instant.now(), 0
        )));
        when(availabilityRepo.delete(tenantId, id)).thenReturn(true);

        boolean result = availabilityUseCases.deleteAvailability(tenantId, id, actorId);

        assertTrue(result);
        verify(auditPort).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd apps/sanad-platform && ./mvnw test -Dtest=AvailabilityUseCasesTest`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/application/AvailabilityUseCasesTest.java
git commit -m "test(crm-008): add AvailabilityUseCases unit tests"
```

---

## Task 18: Write Integration Tests for Repositories

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/infrastructure/JdbcShiftTemplateRepositoryIT.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/infrastructure/JdbcAvailabilityRepositoryIT.java`

**Interfaces:**
- Consumes: JDBC repositories
- Produces: Integration tests with H2 database

- [ ] **Step 1: Create ShiftTemplate repository integration test**

```java
package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcShiftTemplateRepositoryIT {

    @Autowired
    private JdbcShiftTemplateRepository repository;

    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    void createAndFindById() {
        var command = new ShiftTemplateRepository.CreateShiftTemplateCommand(
            tenantId, "Morning Shift", LocalTime.of(8, 0), LocalTime.of(16, 0),
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), actorId
        );

        ShiftTemplate created = repository.create(command);

        assertNotNull(created);
        assertEquals("Morning Shift", created.name());

        var found = repository.findById(tenantId, created.id());
        assertTrue(found.isPresent());
        assertEquals(created.id(), found.get().id());
    }

    @Test
    void existsByName() {
        var command = new ShiftTemplateRepository.CreateShiftTemplateCommand(
            tenantId, "Evening Shift", LocalTime.of(16, 0), LocalTime.of(0, 0),
            List.of(DayOfWeek.WEDNESDAY), actorId
        );

        repository.create(command);

        assertTrue(repository.existsByName(tenantId, "Evening Shift", null));
        assertFalse(repository.existsByName(tenantId, "Night Shift", null));
    }
}
```

- [ ] **Step 2: Create Availability repository integration test**

```java
package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.availability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcAvailabilityRepositoryIT {

    @Autowired
    private JdbcAvailabilityRepository repository;

    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    void createAndFindByStaffId() {
        UUID staffId = UUID.randomUUID();

        var command = new AvailabilityRepository.CreateAvailabilityCommand(
            tenantId, staffId, AvailabilityType.ON_LEAVE,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7),
            null, null, "Vacation", actorId
        );

        StaffAvailability created = repository.create(command);

        assertNotNull(created);
        assertEquals(AvailabilityType.ON_LEAVE, created.type());

        List<StaffAvailability> found = repository.findByStaffId(
            tenantId, staffId,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 9, 1)
        );

        assertFalse(found.isEmpty());
        assertEquals(created.id(), found.get(0).id());
    }
}
```

- [ ] **Step 3: Run integration tests**

Run: `cd apps/sanad-platform && ./mvnw test -Dtest="JdbcShiftTemplateRepositoryIT,JdbcAvailabilityRepositoryIT"`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/infrastructure/
git commit -m "test(crm-008): add repository integration tests"
```

---

## Task 19: Sprint 1 Verification

**Files:**
- No new files
- Verify all Sprint 1 deliverables

**Interfaces:**
- Consumes: All Sprint 1 tasks
- Produces: Sprint 1 verification report

- [ ] **Step 1: Run all tests**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass

- [ ] **Step 2: Verify database migrations**

Run: `cd apps/sanad-platform && ./mvnw flyway:info`
Expected: V20260728_1 through V20260728_4 applied

- [ ] **Step 3: Verify API endpoints**

Run: `curl http://localhost:8080/api/v1/crm/teams/{teamId}/shift-templates`
Expected: 200 OK with empty list

- [ ] **Step 4: Commit Sprint 1 completion**

```bash
git add -A
git commit -m "feat(crm-008): complete Sprint 1 - Infrastructure + Scheduling + Availability"
```

---

# Sprint 2: Skills + Capacity + Workload

## Task 20: Create Database Migration for Staff Skills

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_1__create_crm_staff_skills.sql`

**Interfaces:**
- Produces: `crm_staff_skills` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260729_1__create_crm_staff_skills.sql
CREATE TABLE IF NOT EXISTS crm_staff_skills (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL,
    proficiency INTEGER NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_staff_skills PRIMARY KEY (id),
    CONSTRAINT uk_crm_staff_skills_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_staff_skills_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_staff_skills_level CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT ck_crm_staff_skills_proficiency CHECK (proficiency BETWEEN 1 AND 100)
);

CREATE INDEX idx_crm_staff_skills_staff ON crm_staff_skills (tenant_id, staff_id);
CREATE INDEX idx_crm_staff_skills_name ON crm_staff_skills (tenant_id, skill_name);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260729_1 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_1__create_crm_staff_skills.sql
git commit -m "feat(crm-008): create crm_staff_skills table"
```

---

## Task 21: Create Database Migration for Capacity Plans

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_2__create_crm_capacity_plans.sql`

**Interfaces:**
- Produces: `crm_capacity_plans` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260729_2__create_crm_capacity_plans.sql
CREATE TABLE IF NOT EXISTS crm_capacity_plans (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    team_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    max_capacity INTEGER NOT NULL,
    allocated_capacity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_capacity_plans PRIMARY KEY (id),
    CONSTRAINT uk_crm_capacity_plans_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_capacity_plans_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_capacity_plans_team FOREIGN KEY (team_id) REFERENCES crm_sales_teams (id),
    CONSTRAINT ck_crm_capacity_plans_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_crm_capacity_plans_team ON crm_capacity_plans (tenant_id, team_id, status);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260729_2 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_2__create_crm_capacity_plans.sql
git commit -m "feat(crm-008): create crm_capacity_plans table"
```

---

## Task 22: Create Database Migration for Workload Assignments

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_3__create_crm_workload_assignments.sql`

**Interfaces:**
- Produces: `crm_workload_assignments` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260729_3__create_crm_workload_assignments.sql
CREATE TABLE IF NOT EXISTS crm_workload_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    service_id UUID,
    job_id UUID,
    estimated_hours INTEGER NOT NULL,
    actual_hours INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    start_date DATE NOT NULL,
    end_date DATE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_workload_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_workload_assignments_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_workload_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_workload_assignments_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_crm_workload_assignments_staff ON crm_workload_assignments (tenant_id, staff_id, status);
CREATE INDEX idx_crm_workload_assignments_service ON crm_workload_assignments (tenant_id, service_id);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260729_3 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260729_3__create_crm_workload_assignments.sql
git commit -m "feat(crm-008): create crm_workload_assignments table"
```

---

## Task 23: Implement StaffSkill Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/skills/StaffSkill.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/skills/SkillLevel.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/skills/SkillRepository.java`

**Interfaces:**
- Produces: `StaffSkill` record, `SkillLevel` enum, `SkillRepository` interface

- [ ] **Step 1: Create SkillLevel enum**

```java
package com.sanad.platform.crm.ownership.domain.skills;

public enum SkillLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}
```

- [ ] **Step 2: Create StaffSkill record**

```java
package com.sanad.platform.crm.ownership.domain.skills;

import java.time.Instant;
import java.util.UUID;

public record StaffSkill(
    UUID id,
    UUID tenantId,
    UUID staffId,
    String skillName,
    SkillLevel level,
    int proficiency,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public StaffSkill {
        if (staffId == null) {
            throw new IllegalArgumentException("Staff ID is required");
        }
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (level == null) {
            throw new IllegalArgumentException("Skill level is required");
        }
        if (proficiency < 1 || proficiency > 100) {
            throw new IllegalArgumentException("Proficiency must be between 1 and 100");
        }
    }
}
```

- [ ] **Step 3: Create SkillRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.skills;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository {

    record CreateSkillCommand(
        UUID tenantId,
        UUID staffId,
        String skillName,
        SkillLevel level,
        int proficiency,
        UUID createdBy
    ) {}

    record UpdateSkillCommand(
        SkillLevel level,
        int proficiency,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<StaffSkill> findById(UUID tenantId, UUID id);

    List<StaffSkill> findByStaffId(UUID tenantId, UUID staffId);

    List<StaffSkill> findBySkillName(UUID tenantId, String skillName);

    StaffSkill create(CreateSkillCommand command);

    Optional<StaffSkill> update(UUID tenantId, UUID id, UpdateSkillCommand command);

    boolean delete(UUID tenantId, UUID id);

    boolean existsByStaffAndSkill(UUID tenantId, UUID staffId, String skillName, UUID excludeId);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/skills/
git commit -m "feat(crm-008): add StaffSkill domain model"
```

---

## Task 24: Implement CapacityPlan Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/capacity/CapacityPlan.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/capacity/CapacityStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/capacity/CapacityRepository.java`

**Interfaces:**
- Produces: `CapacityPlan` record, `CapacityStatus` enum, `CapacityRepository` interface

- [ ] **Step 1: Create CapacityStatus enum**

```java
package com.sanad.platform.crm.ownership.domain.capacity;

public enum CapacityStatus {
    DRAFT, ACTIVE, COMPLETED
}
```

- [ ] **Step 2: Create CapacityPlan record**

```java
package com.sanad.platform.crm.ownership.domain.capacity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CapacityPlan(
    UUID id,
    UUID tenantId,
    UUID teamId,
    LocalDate periodStart,
    LocalDate periodEnd,
    int maxCapacity,
    int allocatedCapacity,
    CapacityStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public CapacityPlan {
        if (teamId == null) {
            throw new IllegalArgumentException("Team ID is required");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Period start and end dates are required");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end must be after period start");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Max capacity must be positive");
        }
        if (allocatedCapacity < 0) {
            throw new IllegalArgumentException("Allocated capacity cannot be negative");
        }
    }

    public int remainingCapacity() {
        return maxCapacity - allocatedCapacity;
    }

    public double utilizationPercentage() {
        return maxCapacity > 0 ? (double) allocatedCapacity / maxCapacity * 100 : 0;
    }
}
```

- [ ] **Step 3: Create CapacityRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityRepository {

    record CreateCapacityPlanCommand(
        UUID tenantId,
        UUID teamId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int maxCapacity,
        UUID createdBy
    ) {}

    record UpdateCapacityPlanCommand(
        Integer maxCapacity,
        Integer allocatedCapacity,
        CapacityStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<CapacityPlan> findById(UUID tenantId, UUID id);

    List<CapacityPlan> findByTeamId(UUID tenantId, UUID teamId);

    Optional<CapacityPlan> findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date);

    CapacityPlan create(CreateCapacityPlanCommand command);

    Optional<CapacityPlan> update(UUID tenantId, UUID id, UpdateCapacityPlanCommand command);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/capacity/
git commit -m "feat(crm-008): add CapacityPlan domain model"
```

---

## Task 25: Implement WorkloadAssignment Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/workload/WorkloadAssignment.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/workload/WorkloadStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/workload/WorkloadRepository.java`

**Interfaces:**
- Produces: `WorkloadAssignment` record, `WorkloadStatus` enum, `WorkloadRepository` interface

- [ ] **Step 1: Create WorkloadStatus enum**

```java
package com.sanad.platform.crm.ownership.domain.workload;

public enum WorkloadStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
}
```

- [ ] **Step 2: Create WorkloadAssignment record**

```java
package com.sanad.platform.crm.ownership.domain.workload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkloadAssignment(
    UUID id,
    UUID tenantId,
    UUID staffId,
    UUID serviceId,
    UUID jobId,
    int estimatedHours,
    Integer actualHours,
    WorkloadStatus status,
    LocalDate startDate,
    LocalDate endDate,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public WorkloadAssignment {
        if (staffId == null) {
            throw new IllegalArgumentException("Staff ID is required");
        }
        if (estimatedHours <= 0) {
            throw new IllegalArgumentException("Estimated hours must be positive");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }
    }
}
```

- [ ] **Step 3: Create WorkloadRepository interface**

```java
package com.sanad.platform.crm.ownership.domain.workload;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkloadRepository {

    record CreateWorkloadCommand(
        UUID tenantId,
        UUID staffId,
        UUID serviceId,
        UUID jobId,
        int estimatedHours,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy
    ) {}

    record UpdateWorkloadCommand(
        Integer actualHours,
        WorkloadStatus status,
        LocalDate endDate,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<WorkloadAssignment> findById(UUID tenantId, UUID id);

    List<WorkloadAssignment> findByStaffId(UUID tenantId, UUID staffId, WorkloadStatus status);

    List<WorkloadAssignment> findByServiceId(UUID tenantId, UUID serviceId);

    WorkloadAssignment create(CreateWorkloadCommand command);

    Optional<WorkloadAssignment> update(UUID tenantId, UUID id, UpdateWorkloadCommand command);

    boolean delete(UUID tenantId, UUID id);

    int sumEstimatedHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    int sumActualHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/workload/
git commit -m "feat(crm-008): add WorkloadAssignment domain model"
```

---

## Task 26: Implement JDBC Repositories for Sprint 2

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcSkillRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcCapacityRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/JdbcWorkloadRepository.java`

**Interfaces:**
- Consumes: Repository interfaces
- Produces: JDBC implementations

- [ ] **Step 1: Create JdbcSkillRepository**

Follow the same pattern as JdbcShiftTemplateRepository from Task 8.

- [ ] **Step 2: Create JdbcCapacityRepository**

Follow the same pattern as JdbcShiftTemplateRepository from Task 8.

- [ ] **Step 3: Create JdbcWorkloadRepository**

Follow the same pattern as JdbcShiftTemplateRepository from Task 8.

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/infrastructure/Jdbc*.java
git commit -m "feat(crm-008): implement JDBC repositories for Sprint 2"
```

---

## Task 27: Implement SkillUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/SkillUseCases.java`

**Interfaces:**
- Consumes: `SkillRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `SkillUseCases` with business logic

- [ ] **Step 1: Create SkillUseCases**

Follow the same pattern as SchedulingUseCases from Task 11, implementing:
- `createSkill()` - with duplicate check
- `listSkills()` - by staff member
- `getSkill()` - by ID
- `updateSkill()` - with version check
- `deleteSkill()` - with audit trail
- `getSkillsMatrix()` - aggregate skills by team

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/SkillUseCases.java
git commit -m "feat(crm-008): implement SkillUseCases"
```

---

## Task 28: Implement CapacityUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/CapacityUseCases.java`

**Interfaces:**
- Consumes: `CapacityRepository`, `WorkloadRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `CapacityUseCases` with business logic

- [ ] **Step 1: Create CapacityUseCases**

Implement:
- `createCapacityPlan()` - with period validation
- `listCapacityPlans()` - by team
- `getCapacityPlan()` - by ID
- `updateCapacityPlan()` - with version check
- `getCapacityUtilization()` - calculate utilization percentage

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/CapacityUseCases.java
git commit -m "feat(crm-008): implement CapacityUseCases"
```

---

## Task 29: Implement WorkloadUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/WorkloadUseCases.java`

**Interfaces:**
- Consumes: `WorkloadRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `WorkloadUseCases` with business logic

- [ ] **Step 1: Create WorkloadUseCases**

Implement:
- `createWorkloadAssignment()` - with hours validation
- `listWorkloadAssignments()` - by staff or service
- `getWorkloadAssignment()` - by ID
- `updateWorkloadAssignment()` - with version check
- `deleteWorkloadAssignment()` - with audit trail
- `getWorkloadDistribution()` - aggregate by staff/service

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/WorkloadUseCases.java
git commit -m "feat(crm-008): implement WorkloadUseCases"
```

---

## Task 30: Implement Controllers for Sprint 2

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/SkillController.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/CapacityController.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/WorkloadController.java`

**Interfaces:**
- Consumes: UseCases
- Produces: REST API endpoints

- [ ] **Step 1: Create SkillController**

Follow the same pattern as ShiftController from Task 14, implementing:
- `POST /skills` - create skill
- `GET /skills` - list skills by staff
- `GET /skills/{id}` - get skill
- `PATCH /skills/{id}` - update skill
- `DELETE /skills/{id}` - delete skill
- `GET /skills/matrix` - get skills matrix

- [ ] **Step 2: Create CapacityController**

Follow the same pattern as ShiftController from Task 14, implementing:
- `POST /capacity` - create capacity plan
- `GET /capacity` - list capacity plans
- `GET /capacity/{id}` - get capacity plan
- `PATCH /capacity/{id}` - update capacity plan

- [ ] **Step 3: Create WorkloadController**

Follow the same pattern as ShiftController from Task 14, implementing:
- `POST /workload` - create workload assignment
- `GET /workload` - list workload assignments
- `GET /workload/{id}` - get workload assignment
- `PATCH /workload/{id}` - update workload assignment
- `DELETE /workload/{id}` - delete workload assignment
- `GET /workload/distribution` - get workload distribution

- [ ] **Step 4: Update OwnershipModuleConfiguration**

Add beans for SkillUseCases, CapacityUseCases, WorkloadUseCases.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/OwnershipModuleConfiguration.java
git commit -m "feat(crm-008): implement controllers for Sprint 2"
```

---

## Task 31: Write Tests for Sprint 2

**Files:**
- Create: Unit and integration tests for Sprint 2 components

**Interfaces:**
- Consumes: Sprint 2 components
- Produces: Test coverage

- [ ] **Step 1: Write unit tests for SkillUseCases**

Follow the same pattern as SchedulingUseCasesTest from Task 16.

- [ ] **Step 2: Write unit tests for CapacityUseCases**

Follow the same pattern as SchedulingUseCasesTest from Task 16.

- [ ] **Step 3: Write unit tests for WorkloadUseCases**

Follow the same pattern as SchedulingUseCasesTest from Task 16.

- [ ] **Step 4: Write integration tests for repositories**

Follow the same pattern as JdbcShiftTemplateRepositoryIT from Task 18.

- [ ] **Step 5: Run all tests**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/test/
git commit -m "test(crm-008): add tests for Sprint 2"
```

---

## Task 32: Sprint 2 Verification

**Files:**
- No new files
- Verify all Sprint 2 deliverables

**Interfaces:**
- Consumes: All Sprint 2 tasks
- Produces: Sprint 2 verification report

- [ ] **Step 1: Run all tests**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass

- [ ] **Step 2: Verify database migrations**

Run: `cd apps/sanad-platform && ./mvnw flyway:info`
Expected: V20260729_1 through V20260729_3 applied

- [ ] **Step 3: Verify API endpoints**

Run: `curl http://localhost:8080/api/v1/crm/teams/{teamId}/skills`
Expected: 200 OK with empty list

- [ ] **Step 4: Commit Sprint 2 completion**

```bash
git add -A
git commit -m "feat(crm-008): complete Sprint 2 - Skills + Capacity + Workload"
```

---

# Sprint 3: Dashboard + KPIs + Service Assignment

## Task 33: Create Database Migration for Service Assignment

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260730_1__create_crm_service_assignments.sql`

**Interfaces:**
- Produces: `crm_service_assignments` table

- [ ] **Step 1: Create migration file**

```sql
-- V20260730_1__create_crm_service_assignments.sql
CREATE TABLE IF NOT EXISTS crm_service_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    team_id UUID NOT NULL,
    service_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_service_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_service_assignments_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_service_assignments_team_service UNIQUE (tenant_id, team_id, service_id),
    CONSTRAINT fk_crm_service_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_service_assignments_team FOREIGN KEY (team_id) REFERENCES crm_sales_teams (id),
    CONSTRAINT ck_crm_service_assignments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_crm_service_assignments_team ON crm_service_assignments (tenant_id, team_id, status);
CREATE INDEX idx_crm_service_assignments_service ON crm_service_assignments (tenant_id, service_id);
```

- [ ] **Step 2: Run migration**

Run: `cd apps/sanad-platform && ./mvnw flyway:migrate`
Expected: Migration V20260730_1 applied successfully

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260730_1__create_crm_service_assignments.sql
git commit -m "feat(crm-008): create crm_service_assignments table"
```

---

## Task 34: Implement ServiceAssignment Domain Model

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/service/ServiceAssignment.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/service/ServiceAssignmentStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/service/ServiceAssignmentRepository.java`

**Interfaces:**
- Produces: `ServiceAssignment` record, `ServiceAssignmentStatus` enum, `ServiceAssignmentRepository` interface

- [ ] **Step 1: Create domain models**

Follow the same pattern as ShiftTemplate from Task 5.

- [ ] **Step 2: Create repository interface**

Follow the same pattern as ShiftTemplateRepository from Task 5.

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/service/
git commit -m "feat(crm-008): add ServiceAssignment domain model"
```

---

## Task 35: Implement ServiceAssignmentUseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/ServiceAssignmentUseCases.java`

**Interfaces:**
- Consumes: `ServiceAssignmentRepository`, `AuditPort`, `TimelineEventPort`
- Produces: `ServiceAssignmentUseCases` with business logic

- [ ] **Step 1: Create ServiceAssignmentUseCases**

Implement:
- `createServiceAssignment()` - with duplicate check
- `listServiceAssignments()` - by team
- `getServiceAssignment()` - by ID
- `updateServiceAssignment()` - with version check
- `deleteServiceAssignment()` - with audit trail

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/ServiceAssignmentUseCases.java
git commit -m "feat(crm-008): implement ServiceAssignmentUseCases"
```

---

## Task 36: Implement TeamDashboard UseCases

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/TeamDashboardUseCases.java`

**Interfaces:**
- Consumes: All repositories
- Produces: `TeamDashboardUseCases` with aggregate queries

- [ ] **Step 1: Create TeamDashboardUseCases**

Implement:
- `getTeamDashboard()` - aggregate team metrics
- `getTeamKPIs()` - calculate KPIs
- `getTeamActivityTimeline()` - aggregate timeline events

```java
package com.sanad.platform.crm.ownership.application;

import java.util.Map;
import java.util.UUID;

public class TeamDashboardUseCases {

    private final com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository shiftAssignmentRepo;
    private final com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository availabilityRepo;
    private final com.sanad.platform.crm.ownership.domain.skills.SkillRepository skillRepo;
    private final com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository capacityRepo;
    private final com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository workloadRepo;

    public TeamDashboardUseCases(
        com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository shiftAssignmentRepo,
        com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository availabilityRepo,
        com.sanad.platform.crm.ownership.domain.skills.SkillRepository skillRepo,
        com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository capacityRepo,
        com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository workloadRepo
    ) {
        this.shiftAssignmentRepo = shiftAssignmentRepo;
        this.availabilityRepo = availabilityRepo;
        this.skillRepo = skillRepo;
        this.capacityRepo = capacityRepo;
        this.workloadRepo = workloadRepo;
    }

    public Map<String, Object> getTeamDashboard(UUID tenantId, UUID teamId) {
        // Aggregate team metrics
        return Map.of(
            "team_id", teamId.toString(),
            "total_shifts", shiftAssignmentRepo.findByTeamId(tenantId, teamId, 1000, 0).size(),
            "active_capacity", capacityRepo.findByTeamId(tenantId, teamId).stream()
                .filter(c -> c.status() == com.sanad.platform.crm.ownership.domain.capacity.CapacityStatus.ACTIVE)
                .count()
        );
    }

    public Map<String, Object> getTeamKPIs(UUID tenantId, UUID teamId) {
        // Calculate KPIs
        var capacityPlans = capacityRepo.findByTeamId(tenantId, teamId);
        double avgUtilization = capacityPlans.stream()
            .mapToDouble(com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan::utilizationPercentage)
            .average()
            .orElse(0.0);

        return Map.of(
            "team_id", teamId.toString(),
            "average_utilization", avgUtilization,
            "total_capacity_plans", capacityPlans.size()
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/TeamDashboardUseCases.java
git commit -m "feat(crm-008): implement TeamDashboardUseCases"
```

---

## Task 37: Implement TeamDashboardController

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/TeamDashboardController.java`

**Interfaces:**
- Consumes: `TeamDashboardUseCases`, `ServiceAssignmentUseCases`
- Produces: REST API endpoints for dashboard, KPIs, and service assignment

- [ ] **Step 1: Create TeamDashboardController**

```java
package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.TeamDashboardUseCases;
import com.sanad.platform.crm.ownership.application.ServiceAssignmentUseCases;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/teams/{teamId}")
public class TeamDashboardController {

    private final TeamDashboardUseCases dashboardUseCases;
    private final ServiceAssignmentUseCases serviceAssignmentUseCases;

    public TeamDashboardController(
        TeamDashboardUseCases dashboardUseCases,
        ServiceAssignmentUseCases serviceAssignmentUseCases
    ) {
        this.dashboardUseCases = dashboardUseCases;
        this.serviceAssignmentUseCases = serviceAssignmentUseCases;
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return UUID.fromString(details.get(key).toString());
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
        @PathVariable UUID teamId,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        return ResponseEntity.ok(dashboardUseCases.getTeamDashboard(tenantId, teamId));
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKPIs(
        @PathVariable UUID teamId,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        return ResponseEntity.ok(dashboardUseCases.getTeamKPIs(tenantId, teamId));
    }

    // Service Assignment endpoints

    @RequireCapability("CRM.TEAM.WRITE")
    @PostMapping("/services")
    public ResponseEntity<Map<String, Object>> createServiceAssignment(
        @PathVariable UUID teamId,
        @RequestBody CreateServiceAssignmentRequest request,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = UUID.fromString(((Map<?, ?>) authentication.getDetails()).get("user_id").toString());

        var assignment = serviceAssignmentUseCases.createServiceAssignment(
            tenantId, teamId, request.serviceId(), actorId
        );

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(Map.of(
            "id", assignment.id().toString(),
            "team_id", assignment.teamId().toString(),
            "service_id", assignment.serviceId().toString(),
            "status", assignment.status().name()
        ));
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/services")
    public ResponseEntity<java.util.List<Map<String, Object>>> listServiceAssignments(
        @PathVariable UUID teamId,
        Authentication authentication
    ) {
        UUID tenantId = tenantId(authentication);
        var assignments = serviceAssignmentUseCases.listServiceAssignments(tenantId, teamId);
        return ResponseEntity.ok(assignments.stream().map(a -> Map.<String, Object>of(
            "id", a.id().toString(),
            "service_id", a.serviceId().toString(),
            "status", a.status().name()
        )).toList());
    }

    public record CreateServiceAssignmentRequest(UUID serviceId) {}
}
```

- [ ] **Step 2: Update OwnershipModuleConfiguration**

Add beans for ServiceAssignmentUseCases and TeamDashboardUseCases.

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/web/TeamDashboardController.java
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/application/OwnershipModuleConfiguration.java
git commit -m "feat(crm-008): implement TeamDashboardController and ServiceAssignment endpoints"
```

---

## Task 38: Write Tests for Sprint 3

**Files:**
- Create: Unit and integration tests for Sprint 3 components

**Interfaces:**
- Consumes: Sprint 3 components
- Produces: Test coverage

- [ ] **Step 1: Write unit tests for ServiceAssignmentUseCases**

Follow the same pattern as SchedulingUseCasesTest from Task 16.

- [ ] **Step 2: Write unit tests for TeamDashboardUseCases**

Follow the same pattern as SchedulingUseCasesTest from Task 16.

- [ ] **Step 3: Write integration tests for ServiceAssignmentRepository**

Follow the same pattern as JdbcShiftTemplateRepositoryIT from Task 18.

- [ ] **Step 4: Run all tests**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/test/
git commit -m "test(crm-008): add tests for Sprint 3"
```

---

## Task 39: Sprint 3 Verification

**Files:**
- No new files
- Verify all Sprint 3 deliverables

**Interfaces:**
- Consumes: All Sprint 3 tasks
- Produces: Sprint 3 verification report

- [ ] **Step 1: Run all tests**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass

- [ ] **Step 2: Verify database migrations**

Run: `cd apps/sanad-platform && ./mvnw flyway:info`
Expected: V20260730_1 applied

- [ ] **Step 3: Verify API endpoints**

Run: `curl http://localhost:8080/api/v1/crm/teams/{teamId}/dashboard`
Expected: 200 OK with team dashboard data

- [ ] **Step 4: Commit Sprint 3 completion**

```bash
git add -A
git commit -m "feat(crm-008): complete Sprint 3 - Dashboard + KPIs + Service Assignment"
```

---

## Task 40: CRM-008 Final Verification

**Files:**
- No new files
- Verify complete CRM-008 implementation

**Interfaces:**
- Consumes: All 3 Sprints
- Produces: CRM-008 completion report

- [ ] **Step 1: Run complete test suite**

Run: `cd apps/sanad-platform && ./mvnw test`
Expected: All tests pass (unit + integration)

- [ ] **Step 2: Verify all database migrations**

Run: `cd apps/sanad-platform && ./mvnw flyway:info`
Expected: All migrations V20260728_1 through V20260730_1 applied

- [ ] **Step 3: Verify all API endpoints**

Test all endpoints:
- `/api/v1/crm/teams/{teamId}/shift-templates`
- `/api/v1/crm/teams/{teamId}/shift-assignments`
- `/api/v1/crm/teams/{teamId}/availability`
- `/api/v1/crm/teams/{teamId}/skills`
- `/api/v1/crm/teams/{teamId}/capacity`
- `/api/v1/crm/teams/{teamId}/workload`
- `/api/v1/crm/teams/{teamId}/services`
- `/api/v1/crm/teams/{teamId}/dashboard`
- `/api/v1/crm/teams/{teamId}/kpis`

- [ ] **Step 4: Verify RBAC permissions**

Test that endpoints require proper capabilities.

- [ ] **Step 5: Create CRM-008 completion commit**

```bash
git add -A
git commit -m "feat(crm-008): complete Team Management implementation

- Shift scheduling and assignment
- Staff availability tracking
- Skills matrix management
- Capacity planning
- Workload allocation
- Service assignment
- Team dashboard and KPIs

All 3 Sprints completed successfully."
```

- [ ] **Step 6: Tag release**

```bash
git tag -a v0.8.0 -m "CRM-008 Team Management Release"
```

---

## Summary

**Total Tasks:** 40
**Sprint 1:** 19 tasks (Infrastructure + Scheduling + Availability)
**Sprint 2:** 13 tasks (Skills + Capacity + Workload)
**Sprint 3:** 8 tasks (Dashboard + KPIs + Service Assignment)

**Deliverables:**
- 7 new database tables
- 30+ REST API endpoints
- 9 RBAC permissions
- 7 domain models
- 7 JDBC repositories
- 6 UseCase classes
- 7 REST controllers
- Complete test coverage

**Next Steps:**
1. Execute Sprint 1 tasks
2. Review and verify Sprint 1
3. Execute Sprint 2 tasks
4. Review and verify Sprint 2
5. Execute Sprint 3 tasks
6. Final verification and release
