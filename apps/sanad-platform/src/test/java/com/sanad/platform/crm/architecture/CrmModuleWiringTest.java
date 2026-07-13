package com.sanad.platform.crm.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that modular CRM use cases are NOT registered as Spring beans
 * until their repository adapters exist. This prevents ApplicationContext
 * failures from unsatisfied port dependencies.
 *
 * Branch: crm/004-modular-domain-architecture
 */
class CrmModuleWiringTest {

    @Test
    @DisplayName("AccountUseCases is NOT a Spring bean (no adapter yet)")
    void accountUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.party.application.AccountUseCases");
    }

    @Test
    @DisplayName("ContactUseCases is NOT a Spring bean (no adapter yet)")
    void contactUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.party.application.ContactUseCases");
    }

    @Test
    @DisplayName("LeadUseCases is NOT a Spring bean (no adapter yet)")
    void leadUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.lead.application.LeadUseCases");
    }

    @Test
    @DisplayName("OpportunityUseCases is NOT a Spring bean (no adapter yet)")
    void opportunityUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.opportunity.application.OpportunityUseCases");
    }

    @Test
    @DisplayName("ActivityUseCases is NOT a Spring bean (no adapter yet)")
    void activityUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.activity.application.ActivityUseCases");
    }

    @Test
    @DisplayName("ConfigurationUseCases is NOT a Spring bean (no adapter yet)")
    void configurationUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.configuration.application.ConfigurationUseCases");
    }

    @Test
    @DisplayName("QueryUseCases is NOT a Spring bean (no adapter yet)")
    void queryUseCasesIsNotAutowired() {
        assertDoesNotHaveServiceAnnotation("com.sanad.platform.crm.query.application.QueryUseCases");
    }

    /**
     * Asserts that the given class does NOT have @Service, @Component, or @Repository
     * annotation — meaning it will not be auto-registered as a Spring bean.
     */
    private void assertDoesNotHaveServiceAnnotation(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            boolean hasServiceAnnotation = false;
            for (var annotation : clazz.getAnnotations()) {
                String annType = annotation.annotationType().getName();
                if (annType.contains("Service") || annType.contains("Component") || annType.contains("Repository")) {
                    hasServiceAnnotation = true;
                    break;
                }
            }
            assertFalse(hasServiceAnnotation,
                    className + " must NOT be annotated as a Spring bean until its repository adapter exists");
        } catch (ClassNotFoundException e) {
            fail("Class not found: " + className);
        }
    }
}
