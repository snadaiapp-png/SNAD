package com.sanad.platform.crm.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all CRM module configurations register their beans correctly
 * via Spring Context — not just class existence checks.
 */
@SpringBootTest
@ActiveProfiles("local")
class CrmModuleWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("AccountUseCases bean count = 1")
    void accountUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.party.application.AccountUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 AccountUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("ContactUseCases bean count = 1")
    void contactUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.party.application.ContactUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 ContactUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("LeadUseCases bean count = 1")
    void leadUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.lead.application.LeadUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 LeadUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("OpportunityUseCases bean count = 1")
    void opportunityUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.opportunity.application.OpportunityUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 OpportunityUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("ActivityUseCases bean count = 1")
    void activityUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.activity.application.ActivityUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 ActivityUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("ConfigurationUseCases bean count = 1")
    void configurationUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.configuration.application.ConfigurationUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 ConfigurationUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("QueryUseCases bean count = 1")
    void queryUseCasesBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.query.application.QueryUseCases.class);
        assertEquals(1, beans.size(), "Exactly 1 QueryUseCases bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("AccountRepository bean count = 1")
    void accountRepositoryBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.party.domain.AccountRepository.class);
        assertEquals(1, beans.size(), "Exactly 1 AccountRepository bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("ContactRepository bean count = 1")
    void contactRepositoryBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.party.domain.ContactRepository.class);
        assertEquals(1, beans.size(), "Exactly 1 ContactRepository bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("AuditPort bean count = 1")
    void auditPortBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.integration.domain.AuditPort.class);
        assertEquals(1, beans.size(), "Exactly 1 AuditPort bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("TimelineEventPort bean count = 1")
    void timelineEventPortBeanCount() {
        var beans = context.getBeansOfType(com.sanad.platform.crm.integration.domain.TimelineEventPort.class);
        assertEquals(1, beans.size(), "Exactly 1 TimelineEventPort bean expected, got " + beans.size());
    }

    @Test
    @DisplayName("Application Context loads successfully")
    void applicationContextLoads() {
        assertNotNull(context, "ApplicationContext must load");
    }
}
