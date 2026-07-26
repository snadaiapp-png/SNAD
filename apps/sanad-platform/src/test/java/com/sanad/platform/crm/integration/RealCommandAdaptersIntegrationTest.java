package com.sanad.platform.crm.integration;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort;
import com.sanad.platform.crm.integration.application.CreateFollowUpActivityCommandAdapter;
import com.sanad.platform.crm.integration.application.RequestOpportunityReviewCommandAdapter;
import com.sanad.platform.crm.integration.application.ScheduleContactCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.task.application.TaskUseCases;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regression tests for CRM command adapter boundaries and rollback semantics. */
class RealCommandAdaptersIntegrationTest {

    private static final List<Class<?>> ADAPTERS = List.of(
            CreateFollowUpActivityCommandAdapter.class,
            ScheduleContactCommandAdapter.class,
            RequestOpportunityReviewCommandAdapter.class);

    @Test
    void commandAdaptersDoNotDependOnJdbc() {
        for (Class<?> adapter : ADAPTERS) {
            assertThat(Arrays.stream(adapter.getDeclaredFields())
                    .map(Field::getType)
                    .map(Class::getName)
                    .noneMatch(name -> name.startsWith("org.springframework.jdbc")))
                    .as(adapter.getSimpleName() + " fields must not depend on JDBC")
                    .isTrue();

            assertThat(Arrays.stream(adapter.getConstructors())
                    .map(Constructor::getParameterTypes)
                    .flatMap(Arrays::stream)
                    .map(Class::getName)
                    .noneMatch(name -> name.startsWith("org.springframework.jdbc")))
                    .as(adapter.getSimpleName() + " constructor must not depend on JDBC")
                    .isTrue();
        }
    }

    @Test
    void commandExecutionMethodsOwnExplicitTransactionBoundaries() throws Exception {
        for (Class<?> adapter : ADAPTERS) {
            Method execute = adapter.getMethod("execute",
                    ConfirmedRecommendationCommandPort.ConfirmedRecommendation.class);
            assertThat(execute.getAnnotation(Transactional.class))
                    .as(adapter.getSimpleName() + " execute must remain transactional")
                    .isNotNull();
        }
    }

    @Test
    void followUpInfrastructureFailurePropagatesForRollback() {
        CrmIntegrationStore store = mock(CrmIntegrationStore.class);
        IllegalStateException failure = new IllegalStateException("reservation failed");
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                recommendation("CREATE_FOLLOW_UP_ACTIVITY", "ACCOUNT");
        when(store.reserveOrGetArtifact(
                recommendation.tenantId(), recommendation.decisionId(),
                "CREATE_FOLLOW_UP_ACTIVITY", "ACTIVITY"))
                .thenThrow(failure);

        CreateFollowUpActivityCommandAdapter adapter =
                new CreateFollowUpActivityCommandAdapter(mock(ActivityUseCases.class), store);

        assertThatThrownBy(() -> adapter.execute(recommendation)).isSameAs(failure);
    }

    @Test
    void scheduleInfrastructureFailurePropagatesForRollback() {
        CrmIntegrationStore store = mock(CrmIntegrationStore.class);
        IllegalStateException failure = new IllegalStateException("reservation failed");
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                recommendation("SCHEDULE_CONTACT", "CONTACT");
        when(store.reserveOrGetArtifact(
                recommendation.tenantId(), recommendation.decisionId(),
                "SCHEDULE_CONTACT", "SCHEDULED_ACTIVITY"))
                .thenThrow(failure);

        ScheduleContactCommandAdapter adapter =
                new ScheduleContactCommandAdapter(mock(ActivityUseCases.class), store);

        assertThatThrownBy(() -> adapter.execute(recommendation)).isSameAs(failure);
    }

    @Test
    void reviewInfrastructureFailurePropagatesForRollback() {
        CrmIntegrationStore store = mock(CrmIntegrationStore.class);
        IllegalStateException failure = new IllegalStateException("reservation failed");
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                recommendation("REQUEST_OPPORTUNITY_REVIEW", "OPPORTUNITY");
        when(store.reserveOrGetArtifact(
                recommendation.tenantId(), recommendation.decisionId(),
                "REQUEST_OPPORTUNITY_REVIEW", "REVIEW_TASK"))
                .thenThrow(failure);

        RequestOpportunityReviewCommandAdapter adapter =
                new RequestOpportunityReviewCommandAdapter(mock(TaskUseCases.class), store);

        assertThatThrownBy(() -> adapter.execute(recommendation)).isSameAs(failure);
    }

    private static ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation(
            String actionCode, String entityType) {
        return new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                actionCode, entityType, UUID.randomUUID(),
                1L, "correlation", UUID.randomUUID());
    }
}
