package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowOperationalQueryService.OperationalTaskRow;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 3 / Task 19 RED contract.
 *
 * <p>This source-level contract deliberately targets the remaining gaps in the
 * pre-existing operational read-model implementation. It does not use read
 * models as authorization evidence; it only locks the shape and bounded
 * observability surface required by the approved Task 19 plan.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowTask19ObservabilityContractTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void operationalTaskRowsExposeTenantAndAssignmentIdentity() {
        List<String> components = Arrays.stream(OperationalTaskRow.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(components)
                .contains("tenantId", "assigneeEmployeeId", "claimedByEmployeeId");
    }

    @Test
    void task19RegistersEveryRequiredBoundedOperationalMeasurement() {
        Set<String> meterNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toSet());

        assertThat(meterNames).containsAll(List.of(
                "workflow_queue_depth",
                "workflow_task_oldest_age_seconds",
                "workflow_approval_oldest_age_seconds",
                "workflow_action_retry_rate_per_minute",
                "workflow_action_failure_rate_per_minute",
                "workflow_sla_breach_total",
                "workflow_inbox_lag_seconds",
                "workflow_outbox_lag_seconds",
                "workflow_scheduler_lag_seconds",
                "workflow_open_incident_age_minutes",
                "workflow_open_incidents",
                "workflow_stuck_joins",
                "workflow_notification_failures"
        ));
    }

    @Test
    void workflowOperationalMetersDoNotUseUnboundedIdentityLabels() {
        var forbiddenTagKeys = Set.of(
                "tenant", "tenant_id", "tenantId",
                "user", "user_id", "userId",
                "employee", "employee_id", "employeeId",
                "instance", "instance_id", "instanceId",
                "incident", "incident_id", "incidentId",
                "free_text", "reason", "title"
        );

        assertThat(meterRegistry.getMeters())
                .filteredOn(meter -> meter.getId().getName().startsWith("workflow_"))
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> forbiddenTagKeys.contains(tag.getKey())));
    }
}
