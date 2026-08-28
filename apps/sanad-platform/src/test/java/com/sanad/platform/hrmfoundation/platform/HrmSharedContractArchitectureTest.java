package com.sanad.platform.hrmfoundation.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HrmSharedContractArchitectureTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("com.sanad.platform..");

    @Test
    void hrContractsDoNotDependOnCrmIdempotencyImplementation() {
        noClasses().that().resideInAPackage("..hrmfoundation..")
                .should().dependOnClassesThat().resideInAnyPackage("..crm.idempotency..")
                .check(importedClasses);
    }

    @Test
    void hrContractsDoNotDependOnCrmIntegrationImplementation() {
        noClasses().that().resideInAPackage("..hrmfoundation..")
                .should().dependOnClassesThat().resideInAnyPackage("..crm.integration..")
                .check(importedClasses);
    }

    @Test
    void platformContractsDoNotForceCentralSharedOutboxDatabase() {
        noClasses().that().resideInAnyPackage(
                        "..integration.events..", "..audit..", "..idempotency..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..javax.persistence..", "..org.springframework.jdbc..")
                .check(importedClasses);
    }
}
