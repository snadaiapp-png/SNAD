package com.sanad.platform.crm.integration;

import com.sanad.platform.config.ProductionWorkflowStubGuard;
import com.sanad.platform.crm.integration.application.CompositeConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpAiGatewayAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpWorkflowIntegrationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 architectural test: production Spring context wiring expectations.
 *
 * <p>Verifies Item 8: the production Spring context must start only with
 * real adapters. This is a static test — it inspects annotations rather
 * than loading the Spring context (which would require a full application
 * startup with all dependencies).</p>
 *
 * <p>The full context-load test is performed by the existing
 * {@code ProductionProfileTest} and {@code CrmModuleWiringTest} which
 * run in the {@code local} profile (stub adapter active, composite absent).
 * The production profile context-load is enforced by
 * {@link ProductionWorkflowStubGuard} at startup.</p>
 */
class ProductionCommandAdapterContextTest {

    @Test
    void compositeAdapterIsPrimaryAndProdOnly() {
        Primary primary = CompositeConfirmedRecommendationCommandAdapter.class.getAnnotation(Primary.class);
        assertThat(primary).as("CompositeConfirmedRecommendationCommandAdapter must be @Primary").isNotNull();

        Profile profile = CompositeConfirmedRecommendationCommandAdapter.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("!test", "!local", "!crm-acceptance");
    }

    @Test
    void stubAdapterIsTestOnly() {
        Profile profile = StubConfirmedRecommendationCommandAdapter.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("test", "local", "crm-acceptance");
        assertThat(profile.value()).doesNotContain("prod");
    }

    @Test
    void productionGuardIsProdOnly() {
        Profile profile = ProductionWorkflowStubGuard.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("prod");
    }

    @Test
    void httpAdaptersAreNotProfileRestricted() {
        // HttpAiGatewayAdapter and HttpWorkflowIntegrationAdapter must be
        // available in ALL profiles so the prod guard can verify their binding.
        assertThat(HttpAiGatewayAdapter.class.getAnnotation(Profile.class)).isNull();
        assertThat(HttpWorkflowIntegrationAdapter.class.getAnnotation(Profile.class)).isNull();
    }

    @Test
    void productionGuardConstructorAcceptsAllDependencies() {
        // Verify the guard's constructor signature includes all required dependencies:
        // workflowEngineBaseUrl, aiGatewayBaseUrl, serviceAuthJwtSecret, guardEnabled,
        // commandPort, workflowPort, aiPort, ApplicationContext
        try {
            ProductionWorkflowStubGuard.class.getConstructor(
                    String.class, String.class, String.class, boolean.class,
                    com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort.class,
                    com.sanad.platform.crm.integration.orchestration.WorkflowIntegrationPort.class,
                    com.sanad.platform.crm.integration.orchestration.AiGatewayPort.class,
                    org.springframework.context.ApplicationContext.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("ProductionWorkflowStubGuard missing required constructor: " + e.getMessage(), e);
        }
    }
}
