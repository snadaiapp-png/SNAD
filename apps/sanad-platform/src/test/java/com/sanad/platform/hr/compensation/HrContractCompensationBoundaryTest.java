package com.sanad.platform.hr.compensation;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * HRM-G0 / Master Task 5 / WS6 Task 4 — Payroll/Accounting boundary guard.
 *
 * <p>HRM-G0 does NOT implement payroll calculation, payslips, statutory
 * deduction engines, GOSI calculation, WPS, bank payment execution, GL
 * posting or journal-entry posting. This test proves the boundary: no
 * dependency from {@code hr.contract} / {@code hr.compensation} onto
 * payroll/accounting/finance infrastructure, and no executable payroll
 * concepts inside production HR contract/compensation code.</p>
 */
class HrContractCompensationBoundaryTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.sanad.platform..");

    @Test
    void contractMustNotDependOnPayrollAccountingFinanceInfrastructure() {
        noClasses().that().resideInAnyPackage("..hr.contract..", "..hr.compensation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..payroll..infrastructure..",
                        "..accounting..infrastructure..",
                        "..finance..infrastructure..")
                .check(importedClasses);
    }

    @Test
    void productionContractCompensationCodeContainsNoPayrollBehavior() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> forbiddenConcepts = List.of(
                "payslip", "payslips",
                "payroll run", "payrollrun",
                "statutory deduction calculator",
                "gl posting", "glposting",
                "journal entry posting",
                "bank payment execution",
                "gosi calculation engine",
                "wpsfile", "wps file");
        for (String pkg : new String[]{
                "src/main/java/com/sanad/platform/hr/contract",
                "src/main/java/com/sanad/platform/hr/compensation"}) {
            Path root = Path.of(pkg);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                    String content;
                    try {
                        content = Files.readString(file);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    // Strip comments so documentation about the boundary itself
                    // does not trip the scan; only executable surface counts.
                    String codeOnly = content
                            .replaceAll("(?s)/\\*.*?\\*/", "")
                            .replaceAll("(?m)//.*$", "");
                    String lower = codeOnly.toLowerCase();
                    for (String concept : forbiddenConcepts) {
                        if (lower.contains(concept)) {
                            violations.add(file + " contains payroll/accounting behavior: " + concept);
                        }
                    }
                });
            }
        }
        org.assertj.core.api.Assertions.assertThat(violations)
                .as("PAYROLL_ACCOUNTING_BOUNDARY must hold — no payroll/accounting execution inside "
                        + "HR contract/compensation production code")
                .isEmpty();
    }
}
