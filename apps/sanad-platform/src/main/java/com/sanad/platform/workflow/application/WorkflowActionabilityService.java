package com.sanad.platform.workflow.application;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Resolves the authenticated User identity to the canonical workflow Employee
 * identity and verifies that both sides are currently actionable.
 */
@Service
public final class WorkflowActionabilityService {

    private final HrEmployeeRepository employees;
    private final UserRepository users;
    private final TransactionTemplate readOnlyTransaction;

    public WorkflowActionabilityService(HrEmployeeRepository employees,
                                        UserRepository users,
                                        PlatformTransactionManager transactionManager) {
        this.employees = employees;
        this.users = users;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * RLS-protected employee/user reads must share an explicit transaction so
     * TenantRlsDataSource can apply the authenticated tenant with SET LOCAL.
     * Programmatic transaction management is intentional here: this service is
     * final, so method-level @Transactional would require an invalid CGLIB
     * subclass and prevent the application context from starting.
     */
    public HrEmployee requireActionableEmployee(UUID tenantId, UUID userId) {
        return readOnlyTransaction.execute(status -> requireActionableEmployeeInTransaction(tenantId, userId));
    }

    private HrEmployee requireActionableEmployeeInTransaction(UUID tenantId, UUID userId) {
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
