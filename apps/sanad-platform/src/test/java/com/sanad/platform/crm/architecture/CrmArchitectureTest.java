package com.sanad.platform.crm.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the final CRM-004 modular boundaries.
 */
@AnalyzeClasses(packages = "com.sanad.platform.crm")
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
            .that().resideInAPackage("..crm.web..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("CRM controllers must call application services");

    @ArchTest
    static final ArchRule modulesShouldBeFreeOfCycles = slices()
            .matching("com.sanad.platform.crm.(*)..")
            .should().beFreeOfCycles()
            .because("CRM bounded modules must not form dependency cycles");
}
