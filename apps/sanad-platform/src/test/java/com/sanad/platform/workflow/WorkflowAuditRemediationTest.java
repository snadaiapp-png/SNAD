package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowDefinitionValidator;
import com.sanad.platform.workflow.application.WorkflowSimulationService;
import com.sanad.platform.workflow.domain.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowAuditRemediationTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEFINITION = UUID.randomUUID();
    private static final UUID START = UUID.randomUUID();
    private static final UUID HIGH = UUID.randomUUID();
    private static final UUID LOW = UUID.randomUUID();

    @Test
    void simulationUsesContextAndReturnsDeterministicTraversalOrder() {
        var repo = mock(WorkflowDefinitionRepository.class);
        var validator = mock(WorkflowDefinitionValidator.class);
        when(validator.validate(TENANT, DEFINITION)).thenReturn(WorkflowDefinitionValidation.ok());
        when(repo.findSteps(DEFINITION)).thenReturn(List.of(
                step(START, "start", WorkflowStep.StepType.START),
                step(HIGH, "high", WorkflowStep.StepType.END),
                step(LOW, "low", WorkflowStep.StepType.END)));
        when(repo.findTransitions(DEFINITION)).thenReturn(List.of(
                transition("low", LOW, 20, "{\"type\":\"AND\",\"conditions\":[{\"field\":\"payload.amount\",\"operator\":\"LT\",\"value\":\"100\"}]}"),
                transition("high", HIGH, 10, "{\"type\":\"AND\",\"conditions\":[{\"field\":\"payload.amount\",\"operator\":\"GTE\",\"value\":\"100\"}]}")));

        var result = new WorkflowSimulationService(validator, repo)
                .simulate(TENANT, DEFINITION, Map.of("payload", Map.of("amount", 150)));

        assertThat(result.visitedStepIds()).containsExactly(START, HIGH);
        assertThat(result.notes()).anyMatch(note -> note.contains("supplied context"));
    }

    @Test
    void checksumChangesForEveryMaterialStepAndTransitionField() throws Exception {
        var baseStep = new WorkflowStep(START, TENANT, DEFINITION, "start", "Start",
                WorkflowStep.StepType.START, 1, "{}", 2, "CAP", "ROLE",
                "BUSINESS_TIME", UUID.randomUUID(), 0, Instant.EPOCH, Instant.EPOCH);
        var baseTransition = new WorkflowTransition(UUID.randomUUID(), TENANT, DEFINITION, START, HIGH,
                "route", "YES", "{}", 1, "{}", Instant.EPOCH, Instant.EPOCH);
        String baseline = checksum(List.of(baseStep), List.of(baseTransition));
        var changedStep = new WorkflowStep(START, TENANT, DEFINITION, "start", "Renamed",
                WorkflowStep.StepType.START, 1, "{\"changed\":true}", 4, "OTHER", "OTHER_ROLE",
                "WALL_CLOCK", null, 0, Instant.EPOCH, Instant.EPOCH);
        var changedTransition = new WorkflowTransition(baseTransition.id(), TENANT, DEFINITION, LOW, HIGH,
                "route", "NO", "{\"conditions\":[]}", 9, "{\"owner\":\"ops\"}", Instant.EPOCH, Instant.EPOCH);

        assertThat(checksum(List.of(changedStep), List.of(baseTransition))).isNotEqualTo(baseline);
        assertThat(checksum(List.of(baseStep), List.of(changedTransition))).isNotEqualTo(baseline);
    }

    private static WorkflowStep step(UUID id, String key, WorkflowStep.StepType type) {
        return new WorkflowStep(id, TENANT, DEFINITION, key, key, type, 1, "{}", null,
                null, null, null, null, 0, Instant.EPOCH, Instant.EPOCH);
    }

    private static WorkflowTransition transition(String key, UUID target, int priority, String condition) {
        return new WorkflowTransition(UUID.randomUUID(), TENANT, DEFINITION, START, target,
                key, null, condition, priority, "{}", Instant.EPOCH, Instant.EPOCH);
    }

    private static String checksum(List<WorkflowStep> steps, List<WorkflowTransition> transitions)
            throws Exception {
        var repo = mock(WorkflowDefinitionRepository.class);
        when(repo.findSteps(DEFINITION)).thenReturn(steps);
        when(repo.findTransitions(DEFINITION)).thenReturn(transitions);
        var service = new WorkflowDefinitionService(repo, mock(WorkflowDefinitionValidator.class),
                mock(WorkflowDefinitionAuditPort.class));
        Method method = WorkflowDefinitionService.class.getDeclaredMethod("checksum", WorkflowDefinition.class);
        method.setAccessible(true);
        var definition = new WorkflowDefinition(DEFINITION, TENANT, DEFINITION, "code", "name", "description",
                "GENERAL", 1, WorkflowDefinition.Status.DRAFT, WorkflowDefinition.TriggerType.MANUAL,
                UUID.randomUUID(), 0, WorkflowDefinition.EngineGeneration.Y2,
                WorkflowDefinition.PublicationState.DRAFT, null, null, null, null, 1,
                Instant.EPOCH, Instant.EPOCH);
        return (String) method.invoke(service, definition);
    }
}
