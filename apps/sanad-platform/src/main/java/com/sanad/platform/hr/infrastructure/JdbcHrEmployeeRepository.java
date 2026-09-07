package com.sanad.platform.hr.infrastructure;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JdbcHrEmployeeRepository implements HrEmployeeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcHrEmployeeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HrEmployee save(HrEmployee emp) {
        if (emp.id() == null) {
            UUID id = UUID.randomUUID();
            String sql = """ 
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, email, phone, department_id, position_id, manager_id,
                    employment_type, status, hire_date, created_at, updated_at)
                VALUES (:id, :tenantId, :userId, :empNum, :firstName, :lastName,
                    :displayName, :email, :phone, :deptId, :posId, :mgrId,
                    :empType, :status, :hireDate, NOW(), NOW())
                RETURNING id
                """;
            var params = new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", emp.tenantId())
                .addValue("userId", emp.userId()).addValue("empNum", emp.employeeNumber())
                .addValue("firstName", emp.firstName()).addValue("lastName", emp.lastName())
                .addValue("displayName", emp.displayName()).addValue("email", emp.email())
                .addValue("phone", emp.phone()).addValue("deptId", emp.departmentId())
                .addValue("posId", emp.positionId()).addValue("mgrId", emp.managerId())
                .addValue("empType", emp.employmentType()).addValue("status", emp.status())
                .addValue("hireDate", emp.hireDate() != null ? Timestamp.valueOf(emp.hireDate().atStartOfDay()) : null);
            jdbc.queryForObject(sql, params, UUID.class);
            return new HrEmployee(id, emp.tenantId(), emp.userId(), emp.employeeNumber(),
                emp.firstName(), emp.lastName(), emp.displayName(), emp.email(), emp.phone(),
                emp.departmentId(), emp.positionId(), emp.managerId(), emp.employmentType(),
                emp.status(), emp.hireDate(), emp.terminationDate());
        } else {
            String sql = """
                UPDATE hr_employees SET first_name=:firstName, last_name=:lastName, display_name=:displayName,
                    email=:email, phone=:phone, department_id=:deptId, position_id=:posId,
                    manager_id=:mgrId, employment_type=:empType, status=:status, updated_at=NOW()
                WHERE id=:id AND tenant_id=:tenantId
                """;
            var params = new MapSqlParameterSource()
                .addValue("id", emp.id()).addValue("tenantId", emp.tenantId())
                .addValue("firstName", emp.firstName()).addValue("lastName", emp.lastName())
                .addValue("displayName", emp.displayName()).addValue("email", emp.email())
                .addValue("phone", emp.phone()).addValue("deptId", emp.departmentId())
                .addValue("posId", emp.positionId()).addValue("mgrId", emp.managerId())
                .addValue("empType", emp.employmentType()).addValue("status", emp.status());
            jdbc.update(sql, params);
            return emp;
        }
    }

    @Override
    public Optional<HrEmployee> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM hr_employees WHERE id=:id AND tenant_id=:tenantId";
        var params = new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapEmployee).stream().findFirst();
    }

    @Override
    public Optional<HrEmployee> findByUserId(UUID tenantId, UUID userId) {
        String sql = "SELECT * FROM hr_employees WHERE tenant_id=:tenantId AND user_id=:userId";
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId);
        return jdbc.query(sql, params, this::mapEmployee).stream().findFirst();
    }

    @Override
    public List<HrEmployee> findAll(UUID tenantId, int limit, String search) {
        String sql = "SELECT * FROM hr_employees WHERE tenant_id=:tenantId";
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("limit", Math.min(limit, 200));
        if (search != null && !search.isBlank()) {
            sql += " AND (display_name ILIKE :search OR employee_number ILIKE :search OR email ILIKE :search)";
            params.addValue("search", "%" + search + "%");
        }
        sql += " ORDER BY created_at DESC LIMIT :limit";
        return jdbc.query(sql, params, this::mapEmployee);
    }

    @Override
    public List<HrEmployee> findActiveByDepartment(UUID tenantId, UUID departmentId) {
        String sql = """
                SELECT * FROM hr_employees
                WHERE tenant_id=:tenantId AND department_id=:departmentId AND status='ACTIVE'
                ORDER BY display_name ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("departmentId", departmentId);
        return jdbc.query(sql, params, this::mapEmployee);
    }

    @Override
    public List<HrEmployee> findActiveByPosition(UUID tenantId, UUID positionId) {
        String sql = """
                SELECT * FROM hr_employees
                WHERE tenant_id=:tenantId AND position_id=:positionId AND status='ACTIVE'
                ORDER BY display_name ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("positionId", positionId);
        return jdbc.query(sql, params, this::mapEmployee);
    }

    @Override
    public List<HrEmployee> findActiveByUserIds(UUID tenantId, java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT * FROM hr_employees
                WHERE tenant_id=:tenantId AND status='ACTIVE' AND user_id IN (:userIds)
                ORDER BY display_name ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userIds", userIds);
        return jdbc.query(sql, params, this::mapEmployee);
    }


    @Override
    public long count(UUID tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM hr_employees WHERE tenant_id=:tenantId",
            new MapSqlParameterSource().addValue("tenantId", tenantId), Long.class);
    }

    private HrEmployee mapEmployee(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new HrEmployee(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getObject("user_id", UUID.class), rs.getString("employee_number"),
            rs.getString("first_name"), rs.getString("last_name"), rs.getString("display_name"),
            rs.getString("email"), rs.getString("phone"),
            rs.getObject("department_id", UUID.class), rs.getObject("position_id", UUID.class),
            rs.getObject("manager_id", UUID.class), rs.getString("employment_type"),
            rs.getString("status"), rs.getDate("hire_date") != null ? rs.getDate("hire_date").toLocalDate() : null,
            rs.getDate("termination_date") != null ? rs.getDate("termination_date").toLocalDate() : null
        );
    }
}
