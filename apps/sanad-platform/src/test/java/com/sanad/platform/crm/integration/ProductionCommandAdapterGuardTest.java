package com.sanad.platform.crm.integration;

import com.sanad.platform.config.ProductionWorkflowStubGuard;
import com.sanad.platform.crm.integration.application.CompositeConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.application.CreateFollowUpActivityCommandAdapter;
import com.sanad.platform.crm.integration.application.RequestOpportunityReviewCommandAdapter;
import com.sanad.platform.crm.integration.application.ScheduleContactCommandAdapter;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpAiGatewayAdapter;
import com.sanad.platform.crm.integration.orchestration.HttpWorkflowIntegrationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the production guard's wiring expectations.
 *
 * <p>This is a static architectural test — it does NOT load the Spring context
 * (which would require either prod profile or stub beans). Instead, it asserts
 * that the class hierarchy is correct:</p>
 * <ul>
 *   <li>{@link CompositeConfirmedRecommendationCommandAdapter} exists with
 *       {@code @Profile("!test & !local & !crm-acceptance)")}.</li>
 *   <li>{@link StubConfirmedRecommendationCommandAdapter} exists with
 *       {@code @Profile({"test","local","crm-acceptance"})}.</li>
 *   <li>Real command adapters ({@link CreateFollowUpActivityCommandAdapter},
 *       {@link ScheduleContactCommandAdapter},
 *       {@link RequestOpportunityReviewCommandAdapter}) exist with
 *       {@code @Profile("!test & !local & !crm-acceptance")}.</li>
 *   <li>{@link ProductionWorkflowStubGuard} exists with {@code @Profile("prod")}.</li>
 * </ul>
 */
class ProductionCommandAdapterGuardTest {

    @Test
    void stubAdapterHasTestOnlyProfile() {
        Profile profile = StubConfirmedRecommendationCommandAdapter.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("test", "local", "crm-acceptance");
    }

    @Test
    void compositeAdapterHasProdOnlyProfile() {
        Profile profile = CompositeConfirmedRecommendationCommandAdapter.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("!test", "!local", "!crm-acceptance");
    }

    @Test
    void compositeAdapterIsPrimary() {
        Primary primary = CompositeConfirmedRecommendationCommandAdapter.class.getAnnotation(Primary.class);
        assertThat(primary).isNotNull();
    }

    @Test
    void realAdaptersHaveProdOnlyProfile() {
        for (Class<?> adapterClass : new Class<?>[]{
                CreateFollowUpActivityCommandAdapter.class,
                ScheduleContactCommandAdapter.class,
                RequestOpportunityReviewCommandAdapter.class}) {
            Profile profile = adapterClass.getAnnotation(Profile.class);
            assertThat(profile)
                    .as("%s must have @Profile", adapterClass.getSimpleName())
                    .isNotNull();
            assertThat(profile.value())
                    .as("%s profile values", adapterClass.getSimpleName())
                    .contains("!test", "!local", "!crm-acceptance");
        }
    }

    @Test
    void productionGuardHasProdProfile() {
        Profile profile = ProductionWorkflowStubGuard.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("prod");
    }

    @Test
    void httpAdaptersAreNotProfileRestricted() {
        // HttpAiGatewayAdapter and HttpWorkflowIntegrationAdapter must be
        // available in ALL profiles so the prod guard can verify their binding.
        Profile aiProfile = HttpAiGatewayAdapter.class.getAnnotation(Profile.class);
        Profile workflowProfile = HttpWorkflowIntegrationAdapter.class.getAnnotation(Profile.class);
        assertThat(aiProfile).isNull();
        assertThat(workflowProfile).isNull();
    }
}
