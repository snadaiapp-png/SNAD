package com.sanad.platform.crm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces CRM modular boundaries on production classes. */
@AnalyzeClasses(
        packages = "com.sanad.platform.crm",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CrmArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnSpringWeb = noClasses()
            .that().resideInAPackage("..crm..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
            .because("Domain layer must not depend on Spring Web");

    @ArchTest
    static final ArchRule domainShouldNotDependOnJdbc = noClasses()
            .that().resideInAPackage("..crm..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("Domain layer must not depend on JDBC");

    @ArchTest
    static final ArchRule domainShouldNotDependOnJpa = noClasses()
            .that().resideInAPackage("..crm..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "javax.persistence..")
            .because("Domain layer must not depend on JPA");

    @ArchTest
    static final ArchRule queryModuleShouldNotContainSpringTransactional = noClasses()
            .that().resideInAPackage("..crm.query..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("Query module is read-only");

    @ArchTest
    static final ArchRule crmWebMustNotDependOnJdbc = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("CRM web packages are HTTP boundaries, not persistence infrastructure");

    @ArchTest
    static final ArchRule crmWebMustNotOwnTransactions = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.transaction..")
            .because("Transaction ownership belongs outside CRM web packages");

    @ArchTest
    static final ArchRule crmServiceShouldNotDependOnJdbc = noClasses()
            .that().haveSimpleName("CrmService")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("CrmService is a compatibility facade, not an infrastructure adapter");

    @ArchTest
    static final ArchRule crmServiceShouldNotOwnTransactions = noClasses()
            .that().haveSimpleName("CrmService")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("Application use cases own transaction boundaries");

    @ArchTest
    static final ArchRule controllersShouldNotAccessJdbcDirectly = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("CRM controllers must call application services");

    @ArchTest
    static final ArchRule accountMasterMustUseProjectionContractsForExternalDomains = noClasses()
            .that().resideInAPackage("..crm.party..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..erp..", "..accounting..", "..ecommerce..", "..customerservice..")
            .because("Account Master may consume projection contracts but must never query source domains directly");

    @ArchTest
    static final ArchRule partyMustNotDependOnLeadOpportunityOrActivity = noClasses()
            .that().resideInAPackage("..crm.party..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..crm.lead..", "..crm.opportunity..", "..crm.activity..")
            .because("Party is foundational and must not depend on downstream CRM modules");

    @ArchTest
    static final ArchRule opportunityMustNotDependOnLeadOrActivity = noClasses()
            .that().resideInAPackage("..crm.opportunity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..crm.lead..", "..crm.activity..")
            .because("Opportunity must not create a reverse dependency into Lead or Activity");

    @ArchTest
    static final ArchRule activityMustNotDependOnPartyLeadOrOpportunity = noClasses()
            .that().resideInAPackage("..crm.activity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..crm.party..", "..crm.lead..", "..crm.opportunity..")
            .because("Activity remains an independent bounded module");
}
