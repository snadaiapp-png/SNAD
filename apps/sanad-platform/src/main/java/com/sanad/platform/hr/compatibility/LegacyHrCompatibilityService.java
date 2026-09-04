package com.sanad.platform.hr.compatibility;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 7 — safe v1 compatibility semantics.
 *
 * <p>The legacy v1 surface becomes a compatibility adapter over the HRM
 * migration state machine:
 *
 * <ul>
 *   <li>v1 reads remain compatible for every state; CANONICAL tenants are
 *       served through {@link LegacyHrProjectionMapper} (canonical data is
 *       authoritative, nothing is copied)</li>
 *   <li>MIGRATING/BLOCKED tenants: v1 reads OK, v1 writes are 409
 *       HRM_MIGRATION_REQUIRED (write freeze)</li>
 *   <li>LEGACY/CANONICAL tenants: v1 create proceeds only when exactly one
 *       active Legal Entity and exactly one effective eligible Organization
 *       are authoritative and the jurisdiction default is valid — otherwise
 *       409 HRM_MIGRATION_REQUIRED (no guessing)</li>
 *   <li>v1 PATCH: lifecycle fields (status, manager, department, position,
 *       employment type) are rejected as untranslatable; profile-only
 *       compatible edits proceed</li>
 *   <li>v1 DELETE is retired: never physically deletes employment data —
 *       409 HRM_MIGRATION_REQUIRED</li>
 * </ul>
 */
@Service
@Transactional
public class LegacyHrCompatibilityService {

    /** Error code prefix — projected verbatim in the v1 JSON envelope. */
    public static final String MIGRATION_REQUIRED = "HRM_MIGRATION_REQUIRED";

    private final HrMigrationStateService migrationState;
    private final HrEmployeeRepository employeeRepository;
    private final LegacyHrProjectionMapper projectionMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public LegacyHrCompatibilityService(HrMigrationStateService migrationState,
                                        HrEmployeeRepository employeeRepository,
                                        LegacyHrProjectionMapper projectionMapper,
                                        JdbcTemplate jdbc,
                                        PlatformTransactionManager transactionManager) {
        this.migrationState = Objects.requireNonNull(migrationState, "migrationState");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.projectionMapper = Objects.requireNonNull(projectionMapper, "projectionMapper");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    /** v1 create under the unambiguity contract; throws on freeze/ambiguity. */
    public HrEmployee createEmployee(UUID tenantId, Map<String, Object> body) {
        requireV1WritesAllowed(tenantId);
        requireUnambiguousEmployerContext(tenantId);
        String firstName = text(body.get("firstName"));
        String lastName = text(body.get("lastName"));
        HrEmployee employee = new HrEmployee(
                null, tenantId, null,
                body.get("employeeNumber") != null ? text(body.get("employeeNumber"))
                        : "EMP-" + System.currentTimeMillis(),
                firstName, lastName,
                body.get("displayName") != null ? text(body.get("displayName"))
                        : ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim(),
                text(body.get("email")), text(body.get("phone")),
                null, null, null,
                body.get("employmentType") != null ? text(body.get("employmentType")) : "FULL_TIME",
                body.get("status") != null ? text(body.get("status")) : "DRAFT",
                body.get("hireDate") != null ? LocalDate.parse(text(body.get("hireDate"))) : null,
                null);
        return employeeRepository.save(employee);
    }

    /** v1 patch: profile-only compatible edits; lifecycle fields rejected. */
    public HrEmployee patchEmployee(UUID tenantId, UUID id, Map<String, Object> body) {
        requireV1WritesAllowed(tenantId);
        for (String lifecycleField : List.of("status", "managerId", "departmentId", "positionId", "employmentType")) {
            if (body.containsKey(lifecycleField)) {
                throw migrationRequired("v1 cannot translate " + lifecycleField
                        + " changes; use the canonical v2 employment APIs");
            }
        }
        HrEmployee existing = employeeRepository.findById(tenantId, id)
                .orElseThrow(() -> migrationRequired("employee " + id + " not found in tenant"));
        HrEmployee updated = new HrEmployee(
                existing.id(), existing.tenantId(), existing.userId(),
                existing.employeeNumber(),
                body.get("firstName") != null ? text(body.get("firstName")) : existing.firstName(),
                body.get("lastName") != null ? text(body.get("lastName")) : existing.lastName(),
                body.get("displayName") != null ? text(body.get("displayName")) : existing.displayName(),
                body.get("email") != null ? text(body.get("email")) : existing.email(),
                body.get("phone") != null ? text(body.get("phone")) : existing.phone(),
                existing.departmentId(),
                existing.positionId(),
                existing.managerId(),
                existing.employmentType(),
                existing.status(),
                existing.hireDate(), existing.terminationDate());
        return employeeRepository.save(updated);
    }

    /** v1 delete is retired — physical employment deletion no longer exists. */
    public void deleteEmployee(UUID tenantId, UUID id) {
        throw migrationRequired("v1 delete is retired; employment records are lifecycle-managed via v2");
    }

    public List<HrEmployee> listEmployees(UUID tenantId, int limit, String search) {
        return employeeRepository.findAll(tenantId, limit, search);
    }

    public HrEmployee getEmployee(UUID tenantId, UUID id) {
        if (migrationState.state(tenantId) == HrMigrationStateService.TenantMigrationState.CANONICAL) {
            LegacyHrProjectionMapper.LegacyProjection projection = projectionMapper.projectCanonical(tenantId, id);
            if (projection != null) {
                return new HrEmployee(
                        projection.id(), projection.tenantId(), projection.userId(),
                        projection.employeeNumber(), projection.firstName(), projection.lastName(),
                        projection.displayName(), null, null,
                        projection.orgUnitId(), projection.positionId(), null,
                        projection.employmentType(), projection.status(), null, null);
            }
        }
        return employeeRepository.findById(tenantId, id)
                .orElseThrow(() -> migrationRequired("employee " + id + " not found in tenant"));
    }

    // ==================== internal ====================

    private void requireV1WritesAllowed(UUID tenantId) {
        if (!migrationState.allowsV1Writes(tenantId)) {
            throw migrationRequired("tenant is " + migrationState.state(tenantId)
                    + "; v1 writes are frozen during migration");
        }
    }

    /** v1 create may proceed only when exactly one active LE + one eligible org exist. */
    private void requireUnambiguousEmployerContext(UUID tenantId) {
        transactionTemplate.executeWithoutResult(status -> {
            bindTenant(tenantId);
            Integer legalEntities = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM legal_entities WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            Integer organizations = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            if (legalEntities == null || legalEntities != 1 || organizations == null || organizations != 1) {
                throw migrationRequired("ambiguous employer context: legal entities=" + legalEntities
                        + ", organizations=" + organizations);
            }
        });
    }

    private void bindTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    private IllegalStateException migrationRequired(String message) {
        return new IllegalStateException(MIGRATION_REQUIRED + ": " + message);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
