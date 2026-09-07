package com.sanad.platform.hr.integration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 9 — module boundary architecture guard.
 *
 * <p>HR production packages must not depend concretely on other bounded
 * contexts' implementation packages (CRM idempotency/integration, accounting,
 * ERP, payroll infrastructure). Shared Platform contracts/ports are the only
 * permitted coupling surface. HR production SQL must never reference other
 * modules' tables directly (CROSS_MODULE_DB_ACCESS = NO).</p>
 *
 * <p>Production-only analysis: test classes are excluded from the ArchUnit
 * import.</p>
 */
class HrModuleBoundaryArchitectureTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.sanad.platform..");

    @Test
    void hrMustNotDependOnCrmIdempotencyImplementation() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..crm.idempotency..")
                .check(importedClasses);
    }

    @Test
    void hrMustNotDependOnCrmIntegrationImplementation() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..crm.integration..")
                .check(importedClasses);
    }

    @Test
    void hrMustNotDependOnAccountingInfrastructure() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..accounting.infrastructure..")
                .check(importedClasses);
    }

    @Test
    void hrMustNotDependOnErpInfrastructure() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..erp.infrastructure..")
                .check(importedClasses);
    }

    @Test
    void hrMustNotDependOnPayrollInfrastructure() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..payroll.infrastructure..")
                .check(importedClasses);
    }

    @Test
    void hrProductionSqlMustNotTouchOtherModulesTables() throws IOException {
        Path hrMain = Path.of("src/main/java/com/sanad/platform/hr");
        assertThatPathExists(hrMain);
        // Cross-module SQL coupling scan: HR production sources must not issue
        // SQL against other bounded contexts' tables (crm_, accounting_, erp_,
        // payroll_ prefixed tables). Shared core tables (tenants, users,
        // legal_entities, flyway_schema_history ...) are platform tables and
        // remain permitted.
        Pattern crossModuleSql = Pattern.compile(
                "(?i)(FROM|JOIN|INTO|UPDATE)\\s+(crm_|accounting_|erp_|payroll_)[a-z_]+");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(hrMain)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                String content;
                try {
                    content = Files.readString(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                var matcher = crossModuleSql.matcher(content);
                while (matcher.find()) {
                    violations.add(file + " references another module's table: " + matcher.group());
                }
            });
        }
        org.assertj.core.api.Assertions.assertThat(violations)
                .as("CROSS_MODULE_DB_ACCESS must be NO — HR production SQL never touches other modules' tables")
                .isEmpty();
    }

    private void assertThatPathExists(Path path) {
        org.assertj.core.api.Assertions.assertThat(Files.isDirectory(path)).isTrue();
    }
}
