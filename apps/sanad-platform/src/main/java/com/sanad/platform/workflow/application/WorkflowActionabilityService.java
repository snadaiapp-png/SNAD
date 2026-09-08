package com.sanad.platform.workflow.application;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the authenticated User identity to the canonical workflow Employee
 * identity and verifies that both sides are currently actionable.
 */
@Service
public final class WorkflowActionabilityService {

    private final HrEmployeeRepository employees;
    private final UserRepository users;

    public WorkflowActionabilityService(HrEmployeeRepository employees, UserRepository users) {
        this.employees = employees;
        this.users = users;
    }

    /**
     * RLS-protected employee/user reads must share an explicit transaction so
     * TenantRlsDataSource can apply the authenticated tenant with SET LOCAL.
     * Without this boundary a real HTTP request borrows an autocommit
     * connection and fail-closed RLS makes a valid employee appear absent.
     */
    @Transactional(readOnly = true)
    public HrEmployee requireActionableEmployee(UUID tenantId, UUID userId) {
        var employee = employees.findByUserId(tenantId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is not linked to an employee"));

        if (!"ACTIVE".equals(employee.status())) {
            throw new AccessDeniedException("Employee is not actionable");
        }

        var user = users.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new AccessDeniedException("User is not actionable"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("User is not actionable");
        }

        return employee;
    }
}
