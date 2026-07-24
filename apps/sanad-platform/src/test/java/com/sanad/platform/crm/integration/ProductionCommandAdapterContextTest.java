package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.ProductionWorkflowStubGuard;
import com.sanad.platform.crm.integration.application.CompositeConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpAiGatewayAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpWorkflowIntegrationAdapter;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Executes the production fail-closed guard against a real Spring context. */
class ProductionCommandAdapterContextTest {

    private static final String SECRET = "crm-009-production-context-secret-0123456789";
    private static final String WORKFLOW_URL = "https://workflow.sanad.cloud";
    private static final String AI_URL = "https://ai.sanad.cloud";

    @Test
    void validProductionContextPassesGuard() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, WORKFLOW_URL, AI_URL, SECRET,
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatCode(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void activeStubBeanFailsProductionContext() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("stubConfirmedRecommendationCommandAdapter",
                    StubConfirmedRecommendationCommandAdapter.class,
                    StubConfirmedRecommendationCommandAdapter::new);
            context.refresh();
            ProductionWorkflowStubGuard guard = guard(context, WORKFLOW_URL, AI_URL, SECRET,
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stub ConfirmedRecommendationCommandAdapter");
        }
    }

    @Test
    void missingServiceSecretFailsProductionContext() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, WORKFLOW_URL, AI_URL, "",
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt-secret");
        }
    }

    @Test
    void shortServiceSecretFailsProductionContext() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, WORKFLOW_URL, AI_URL, "too-short",
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("<32 chars");
        }
    }

    @Test
    void httpServiceUrlFailsProductionContext() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, "http://workflow.sanad.cloud", AI_URL, SECRET,
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must use HTTPS");
        }
    }

    @Test
    void localServiceUrlFailsProductionContext() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, "https://localhost", AI_URL, SECRET,
                    mock(CompositeConfirmedRecommendationCommandAdapter.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("local/test host");
        }
    }

    @Test
    void nonCompositeCommandPortFailsProductionContext() {
        try (GenericApplicationContext context = emptyContext()) {
            ProductionWorkflowStubGuard guard = guard(context, WORKFLOW_URL, AI_URL, SECRET,
                    mock(ConfirmedRecommendationCommandPort.class));
            assertThatThrownBy(() -> guard.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("real ConfirmedRecommendationCommandPort");
        }
    }

    private static GenericApplicationContext emptyContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        return context;
    }

    private static ProductionWorkflowStubGuard guard(
            GenericApplicationContext context,
            String workflowUrl,
            String aiUrl,
            String secret,
            ConfirmedRecommendationCommandPort commandPort) {
        ObjectMapper mapper = new ObjectMapper();
        ServiceJwtProvider serviceJwtProvider = new ServiceJwtProvider(
                SECRET, "sanad-platform", "sanad-crm", 60);
        return new ProductionWorkflowStubGuard(
                workflowUrl, aiUrl, secret, true, commandPort,
                new HttpWorkflowIntegrationAdapter(
                        mapper, serviceJwtProvider, workflowUrl,
                        "sanad-workflow-engine", 1_000),
                new HttpAiGatewayAdapter(
                        mapper, serviceJwtProvider, aiUrl,
                        "sanad-ai-gateway", 1_000),
                context);
    }
}
