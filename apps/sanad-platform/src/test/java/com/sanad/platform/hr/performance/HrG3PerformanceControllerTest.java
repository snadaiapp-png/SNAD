package com.sanad.platform.hr.performance;

import com.sanad.platform.hr.api.v2.performance.HrPerformanceGoalV2Controller;
import com.sanad.platform.hr.api.v2.performance.HrPerformanceReviewV2Controller;
import com.sanad.platform.hr.performance.application.HrPerformanceReviewService;
import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrG3PerformanceControllerTest {

    @Test
    void teamGoalReadBindsTenantThenRequiresCanonicalManagedEmployment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HrEmploymentScopeResolver scope = mock(HrEmploymentScopeResolver.class);
        TenantRlsTransactionContext rls = mock(TenantRlsTransactionContext.class);
        HrPerformanceGoalV2Controller controller =
                new HrPerformanceGoalV2Controller(jdbc, scope, rls);

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        Authentication authentication = authentication(tenantId, managerUserId);

        controller.listTeamGoals(authentication, employmentId);

        var order = inOrder(rls, scope, jdbc);
        order.verify(rls).applyForCurrentTransaction(tenantId);
        order.verify(scope).requireManagedEmployment(tenantId, managerUserId, employmentId);
        order.verify(jdbc).query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<
                        HrPerformanceGoalV2Controller.GoalResponse>>any(),
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(employmentId));
    }

    @Test
    void teamReviewCreateDelegatesOnlyAfterTenantBinding() {
        HrPerformanceReviewService service = mock(HrPerformanceReviewService.class);
        TenantRlsTransactionContext rls = mock(TenantRlsTransactionContext.class);
        HrPerformanceReviewV2Controller controller =
                new HrPerformanceReviewV2Controller(service, rls);

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        Authentication authentication = authentication(tenantId, managerUserId);
        var request = new HrPerformanceReviewV2Controller.ReviewWriteRequest(
                "2026-H2",
                java.time.LocalDate.of(2026, 7, 1),
                java.time.LocalDate.of(2026, 12, 31),
                4,
                "Manager review");

        controller.createTeamReview(authentication, employmentId, request);

        var order = inOrder(rls, service);
        order.verify(rls).applyForCurrentTransaction(tenantId);
        order.verify(service).createManagerReview(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(managerUserId),
                org.mockito.ArgumentMatchers.eq(employmentId),
                org.mockito.ArgumentMatchers.any());
    }

    private static Authentication authentication(UUID tenantId, UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn(Map.of(
                "tenant_id", tenantId,
                "user_id", userId));
        return authentication;
    }
}
