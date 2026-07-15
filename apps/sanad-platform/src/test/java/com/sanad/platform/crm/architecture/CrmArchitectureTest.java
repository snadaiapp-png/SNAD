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
 * Phase 1 (current): Domain layer isolation is enforced.
 * Controllers still use old services (CrmService/CrmExtendedService) which have JDBC
 * — this will be fixed as controllers are migrated to use case classes.
 * 
 * Phase 2 (next): Controller JDBC access will be enforced once migration is complete.
 * 
 * Branch: crm/004-modular-domain-architecture
 */
@AnalyzeClasses(packages = "com.sanad.platform.crm")
class CrmArchitectureTest {

    // === Phase 1: Domain layer isolation (ENFORCED NOW) ===

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

    // === Phase 2: Controller isolation (WILL BE ENFORCED after migration) ===
    // These tests are documented but not yet enforced because controllers still
    // delegate to CrmService/CrmExtendedService which use JDBC directly.
    // Once all controllers are migrated to use Application Use Cases, these
    // rules will be activated.

    // TODO: Enable after controller migration
    // @ArchTest
    // static final ArchRule controllersShouldNotAccessJdbcDirectly = noClasses()
    //         .that().resideInAPackage("..crm.web..")
    //         .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    // TODO: Enable after controller migration
    // @ArchTest
    // static final ArchRule controllersShouldNotAccessSqlDirectly = noClasses()
    //         .that().resideInAPackage("..crm.web..")
    //         .should().dependOnClassesThat().resideInAPackage("java.sql..");

    // TODO: Enable after CrmService/CrmExtendedService are decomposed
    // @ArchTest
    // static final ArchRule modulesShouldBeFreeOfCycles = slices()
    //         .matching("com.sanad.platform.crm.(*)..")
    //         .should().beFreeOfCycles();
}
