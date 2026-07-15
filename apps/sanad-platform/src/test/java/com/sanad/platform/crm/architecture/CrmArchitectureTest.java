package com.sanad.platform.crm.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing CRM modular architecture boundaries.
 *
 * Phase 1: Domain layer isolation (ENFORCED).
 * Phase 2: Web layer (controllers + legacy services) JDBC/SQL isolation (ENFORCED).
 * Phase 3: Module cycle detection (ENFORCED).
 *
 * After the CRM-004 remediation, CrmService is a thin delegate with no JDBC.
 * CrmExtendedService still uses JDBC for some legacy features (dashboard,
 * customer360, imports, custom fields) but those are outside the scope of
 * the five core entities (Account/Contact/Lead/Opportunity/Activity) and
 * timeline — they will be migrated in subsequent work items.
 *
 * Branch: crm/004-remediation-timeline-decomposition
 */
@AnalyzeClasses(packages = "com.sanad.platform.crm")
class CrmArchitectureTest {

    // === Phase 1: Domain layer isolation (ENFORCED) ===

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
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
            .because("Domain layer must not depend on JPA");

    @ArchTest
    static final ArchRule queryModuleShouldNotContainSpringTransactional = noClasses()
            .that().resideInAPackage("..crm.query..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("Query module is read-only — no @Transactional allowed");

    // === Phase 2: CrmService JDBC/SQL isolation (ENFORCED after remediation) ===
    // CrmService is now a thin V1 compatibility delegate. It must not depend on
    // JDBC or java.sql directly — all persistence goes through UseCases, ports,
    // or QueryUseCases.

    @ArchTest
    static final ArchRule crmServiceShouldNotDependOnJdbc = noClasses()
            .that().resideInAPackage("..crm.web..")
            .and().haveSimpleName("CrmService")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("CrmService must be a thin delegate — no direct JDBC after CRM-004 remediation");

    @ArchTest
    static final ArchRule crmServiceShouldNotDependOnSql = noClasses()
            .that().resideInAPackage("..crm.web..")
            .and().haveSimpleName("CrmService")
            .should().dependOnClassesThat().resideInAPackage("java.sql..")
            .because("CrmService must not use java.sql types directly after CRM-004 remediation");

    // === Phase 3: Module cycle detection (ENFORCED) ===
    // The seven CRM modules (party, lead, opportunity, activity, configuration,
    // query, integration) must not form cyclic dependencies.

    @ArchTest
    static final ArchRule modulesShouldBeFreeOfCycles = slices()
            .matching("com.sanad.platform.crm.(*)..")
            .should().beFreeOfCycles()
            .because("CRM modules must not form cyclic dependencies — modular architecture requires acyclic module graph");
}
