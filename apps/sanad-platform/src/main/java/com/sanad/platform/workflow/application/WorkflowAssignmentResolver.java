package com.sanad.platform.workflow.application;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.workflow.domain.WorkflowAssignmentRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assignment resolver (design decisions D3/N3). Turns an assignment rule
 * into concrete, tenant-safe {@code Employee.id} candidates with immutable
 * resolution evidence at activation time.
 *
 * <p>Resolution is evidence, not authorization: every human command must
 * revalidate the acting user's actionability and current capabilities
 * server-side at execution time. Role and permission rules read the
 * canonical RBAC tables only — never HR position names.</p>
 */
@Service
public class WorkflowAssignmentResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAssignmentResolver.class);

    private final HrEmployeeRepository employeeRepo;
    private final WorkflowAuthorizationReadPort authorizationPort;

    public WorkflowAssignmentResolver(HrEmployeeRepository employeeRepo,
                                      WorkflowAuthorizationReadPort authorizationPort) {
        this.employeeRepo = employeeRepo;
        this.authorizationPort = authorizationPort;
    }

    /**
     * @throws WorkflowAssignmentResolutionException when the rule cannot
     *         resolve to at least one ACTIVE employee in the tenant — the
     *         caller activates an incident instead of an unresolvable task.
     */
    public ResolvedAssignment resolve(UUID tenantId, WorkflowAssignmentRule rule,
                                      WorkflowAssignmentContext context) {
        final List<HrEmployee> resolved;
        if (rule instanceof WorkflowAssignmentRule.Employee employee) {
            resolved = resolveEmployee(tenantId, employee.employeeId());
        } else if (rule instanceof WorkflowAssignmentRule.Manager manager) {
            resolved = resolveManager(tenantId, manager.subjectEmployeeId());
        } else if (rule instanceof WorkflowAssignmentRule.Position position) {
            resolved = employeeRepo.findActiveByPosition(tenantId, position.positionId());
        } else if (rule instanceof WorkflowAssignmentRule.Department department) {
            resolved = employeeRepo.findActiveByDepartment(tenantId, department.departmentId());
        } else if (rule instanceof WorkflowAssignmentRule.Role role) {
            resolved = resolveByLinkedUsers(tenantId,
                    authorizationPort.findActiveUserIdsByRole(tenantId, role.roleCode()));
        } else if (rule instanceof WorkflowAssignmentRule.Permission permission) {
            resolved = resolveByLinkedUsers(tenantId,
                    authorizationPort.findActiveUserIdsByCapability(tenantId, permission.capabilityCode()));
        } else {
            throw new IllegalArgumentException("Unsupported assignment rule: " + rule);
        }

        List<UUID> employeeIds = resolved.stream().map(HrEmployee::id).distinct().toList();
        if (employeeIds.isEmpty()) {
            throw new WorkflowAssignmentResolutionException(
                    "Assignment rule " + rule + " resolved to no ACTIVE employee in tenant " + tenantId);
        }
        var result = new ResolvedAssignment(employeeIds, rule, rule.getClass().getSimpleName(),
                Instant.now());
        log.debug("Assignment resolved: tenant={} rule={} employees={} source={}",
                tenantId, rule, employeeIds.size(), result.resolutionSource());
        return result;
    }

    private List<HrEmployee> resolveEmployee(UUID tenantId, UUID employeeId) {
        return employeeRepo.findById(tenantId, employeeId)
                .filter(e -> "ACTIVE".equals(e.status()))
                .map(List::of)
                .orElse(List.of());
    }

    private List<HrEmployee> resolveManager(UUID tenantId, UUID subjectEmployeeId) {
        return employeeRepo.findById(tenantId, subjectEmployeeId)
                .filter(e -> "ACTIVE".equals(e.status()))
                .map(HrEmployee::managerId)
                .filter(managerId -> managerId != null)
                .flatMap(managerId -> employeeRepo.findById(tenantId, managerId))
                .filter(e -> "ACTIVE".equals(e.status()))
                .map(List::of)
                .orElse(List.of());
    }

    private List<HrEmployee> resolveByLinkedUsers(UUID tenantId, List<UUID> userIds) {
        return new ArrayList<>(employeeRepo.findActiveByUserIds(tenantId, userIds));
    }

    /** Unresolvable required assignee — the caller opens an incident (AN3). */
    public static class WorkflowAssignmentResolutionException extends IllegalStateException {
        public WorkflowAssignmentResolutionException(String message) {
            super(message);
        }
    }
}
